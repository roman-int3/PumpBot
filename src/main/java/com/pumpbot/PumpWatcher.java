package com.pumpbot;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.telegram.api.engine.RpcCallback;
import org.telegram.api.message.TLAbsMessage;
import org.telegram.api.message.TLMessage;
import org.telegram.api.messages.TLAbsMessages;
import org.telegram.tl.TLObject;
import org.telegram.tl.TLVector;

import javax.swing.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class PumpWatcher implements Runnable
{
    private static final Logger logger = LogManager.getLogger(PumpWatcher.class);

    private class ChatInfo{
        public BotConfig.ChannelLinkAndRegEx linkAndRegEx;
        public TelegramClient.TelegramChat chat;
        public int pumpReadyMsgDate;
        public MsgRpcCallBack callback;
    }

    public static List<ChatInfo>activeChats=new LinkedList<>();


    @Override
    public void run()
    {
        while(true){
            try {
                TelegramClient telegramClient = new TelegramClient(Integer.parseInt(Main.botConfig.getProperty(BotConfig.KEYS.TELEGRAM_APIKEY)),
                        Main.botConfig.getProperty(BotConfig.KEYS.TELEGRAM_PHONENUMBER),
                        Main.botConfig.getProperty(BotConfig.KEYS.TELEGRAM_APIHASH),
                        Main.botConfig.getProperty(BotConfig.KEYS.TELEGRAM_PHONECODEHASH),
                        Main.botConfig.getProperty(BotConfig.KEYS.TELEGRAM_PHONEAUTHCODE),
                        new TelegramClient.PhoneCodeSignInHandler() {
                            @Override
                            public String getCode() {
                                String code="";
                                do {
                                    JTextField textField = new JTextField();
                                    JOptionPane new_pane = new JOptionPane(textField);
                                    JDialog dialog = new_pane.createDialog("::PumpBot:: Enter  Telegram Verification Code");
                                    dialog.setModal(true);
                                    dialog.setSize(500, 150);
                                    dialog.setAlwaysOnTop(true);
                                    dialog.setVisible(true);
                                    code = textField.getText();
                                }while(code.isEmpty());

                                return code.trim();
                            }
                        });

                Main.botConfig.setProperty(BotConfig.KEYS.TELEGRAM_PHONECODEHASH, telegramClient.getPHONECODEHASH());
                Main.botConfig.setProperty(BotConfig.KEYS.TELEGRAM_PHONEAUTHCODE, telegramClient.getPHONECODE());
                Main.botConfig.save();

                List<BotConfig.ChannelLinkAndRegEx> linksAndRegexArr = Main.botConfig.getChannelLinkAndRegEx();
                linksAndRegexArr.forEach(e->{
                    try {
                        ChatInfo newItem = new ChatInfo();
                        newItem.chat = telegramClient.joinChat(e.clink);
                        newItem.linkAndRegEx = e;
                        newItem.callback = new MsgRpcCallBack(newItem);
                        activeChats.add(newItem);
                    }catch (Exception ex){
                        logger.error("",ex);
                        ex.printStackTrace();
                    }
                });

                while(true) {
                    for (ChatInfo chatInfo : activeChats) {
                        chatInfo.chat.getMessages(10, -1,chatInfo.callback);
                        Thread.sleep(5 * 1000);
                    }
                }

            }catch (Exception e){
                logger.error("",e);
                if(e.getMessage().contains("FLOOD_WAIT_")){
                     System.out.println("Got FLOOD_WAIT ERROR");
                    String[] strArr=e.getMessage().split("_");
                    int floodWaitTime = Integer.parseInt(strArr[2]);
                    while(floodWaitTime > 0) {
                        System.out.println("Next login attempt through: " + floodWaitTime / 60/60 + " hours");
                        try {
                            Thread.sleep(10000);
                        } catch (Exception ex) {
                        }
                        floodWaitTime-=10;
                    }
                }

            }

        }
    }


    public class MsgRpcCallBack implements RpcCallback
    {
        private ChatInfo chatInfo;
        MessageDigest oldMsgMD5;
        MessageDigest newMsgMD5;
        MessageDigest oldPumpMsgMD5;
        MessageDigest newPumpMsgMD5;
        byte[] oldMsgMD5Digit;
        byte[] oldPumpMD5Digit;

        public MsgRpcCallBack(ChatInfo newItem) throws NoSuchAlgorithmException
        {
            chatInfo = newItem;
            oldMsgMD5 = MessageDigest.getInstance("MD5");
            newMsgMD5 = MessageDigest.getInstance("MD5");
            oldPumpMsgMD5 = MessageDigest.getInstance("MD5");
            newPumpMsgMD5 = MessageDigest.getInstance("MD5");
            oldMsgMD5.update("DFGSRSGBXBXBxxxxxxxxxxxxx".getBytes());
            oldPumpMsgMD5.update("kjge98498rwhghetoe".getBytes());
            oldMsgMD5Digit = oldMsgMD5.digest();
            oldPumpMD5Digit = oldPumpMsgMD5.digest();
        }

        @Override
        public void onResult(TLObject result)
        {
            //byte[] oldMsgMD5=null;
            try {
                TLAbsMessages t = (TLAbsMessages) result;
                TLVector<TLAbsMessage> absMessages = t.getMessages();
                for (int i = 0; i < absMessages.size(); i++) {
                    TLObject absMessage = absMessages.get(i);
                    if (absMessage instanceof TLMessage) {
                        TLMessage msg = (TLMessage) absMessage;

                        String chatMsg = msg.getMessage();
                        newMsgMD5.reset();
                        newMsgMD5.update(chatMsg.getBytes());
                        byte[] newMsgDigit = newMsgMD5.digest();
                        if (MessageDigest.isEqual(newMsgDigit, oldMsgMD5Digit)) {
                            return;
                        }
                        //oldMsgMD5 = oldMsgMD5Digit;
                        oldMsgMD5Digit = newMsgDigit;

                        for (int j = 0; j < absMessages.size(); j++) {
                            if (absMessages.get(j) instanceof TLMessage) {
                                TLMessage tlMsg = (TLMessage) absMessages.get(j);
                                chatMsg = tlMsg.getMessage();

                                if (chatMsg != null || chatMsg.isEmpty() == false) {
                                    logger.debug("=== onResult::NEW TELEGRAM MESSAGE ===========================================");
                                    logger.debug("Msg Date: " + Instant.ofEpochSecond(tlMsg.getDate()).atZone(ZoneId.systemDefault())
                                            .format(DateTimeFormatter
                                                    .ofPattern("HH:mm:ss yyyy-MM-dd")));
                                    logger.debug("Chat Title: " + chatInfo.chat.getChatTitle());
                                    logger.debug("Chat ID: " + msg.getChatId());
                                    logger.debug(chatMsg);
                                    logger.debug("===============================================================================\n\n");

                                    for(Pattern regEx : chatInfo.linkAndRegEx.pumpReadyMsgRegExList) {
                                        Matcher matcher = regEx.matcher(chatMsg);
                                        if (matcher.find()) {
                                            newPumpMsgMD5.reset();
                                            newPumpMsgMD5.update(chatMsg.getBytes());
                                            byte[] newPumpMsgMD5Digit = newPumpMsgMD5.digest();
                                            if (MessageDigest.isEqual(newPumpMsgMD5Digit, oldPumpMD5Digit)) {
                                                return;
                                            }
                                            oldPumpMD5Digit = newPumpMsgMD5Digit;

                                            for (int k = j - 1; k >= 0; k--) {
                                                if (absMessages.get(k) instanceof TLMessage) {
                                                    tlMsg = (TLMessage) absMessages.get(k);
                                                    chatMsg = tlMsg.getMessage();
                                                    matcher = chatInfo.linkAndRegEx.pumpExchangeUrlRegEx.matcher(chatMsg);
                                                    if (matcher.find()) {
                                                        String pumpCoin = matcher.group(1);
                                                        if (pumpCoin != null || pumpCoin.isEmpty() == false) {
                                                            logger.debug("Found PUMP COIN: " + pumpCoin);
                                                            long startPumpMsgDate = tlMsg.getDate();
                                                            long currentTime = System.currentTimeMillis() / 1000L;
                                                            long pumpDelay = currentTime - startPumpMsgDate;
                                                            if (pumpDelay > 120) {
                                                                logger.debug("WARNING PUMP MESSAGE IS TO OLD : " + pumpDelay + " sec");
                                                                logger.debug("PUMP CANCELED");
                                                                return;
                                                            } else {
                                                                logger.debug("PUMP STARTED");
                                                                Main.yobitPriceMonitor.startPump(pumpCoin);
                                                                return;
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        return;

                    }
                }
            }catch (Exception e){
                logger.error("",e);
                e.printStackTrace();
            }
        }

        @Override
        public void onError(int errorCode, String message) {

        }
    }

}
