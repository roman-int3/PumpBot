package com.pumpbot;

import org.apache.logging.log4j.LogManager;
import org.telegram.api.TLConfig;
import org.telegram.api.auth.TLAuthorization;
import org.telegram.api.auth.TLSentCode;
import org.telegram.api.chat.channel.TLChannel;
import org.telegram.api.chat.invite.TLChatInviteAlready;
import org.telegram.api.contacts.TLResolvedPeer;
import org.telegram.api.engine.*;
import org.telegram.api.engine.storage.AbsApiState;
import org.telegram.api.functions.auth.TLRequestAuthSendCode;
import org.telegram.api.functions.auth.TLRequestAuthSignIn;
import org.telegram.api.functions.channels.TLRequestChannelsJoinChannel;
import org.telegram.api.functions.contacts.TLRequestContactsResolveUsername;
import org.telegram.api.functions.help.TLRequestHelpGetConfig;
import org.telegram.api.functions.messages.TLRequestMessagesCheckChatInvite;
import org.telegram.api.functions.messages.TLRequestMessagesGetHistory;
import org.telegram.api.functions.messages.TLRequestMessagesImportChatInvite;
import org.telegram.api.input.chat.TLInputChannel;
import org.telegram.api.input.peer.TLInputPeerChannel;
import org.telegram.api.message.TLMessage;
import org.telegram.api.updates.TLAbsUpdates;
import org.telegram.bot.kernel.engine.MemoryApiState;
import org.telegram.bot.services.BotLogger;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TelegramClient
{
    protected TelegramApi api;
    private AbsApiState apiState;
    private int APIKEY;
    private String PHONENUMBER;
    private String APIHASH;
    private String PHONECODEHASH;
    private String PHONECODE;
    private PhoneCodeSignInHandler SignInHandler;

    private static final org.apache.logging.log4j.Logger logger = LogManager.getLogger(TelegramClient.class);

    public TelegramClient(int apikey,String phonenumber,String apihash,String phonecodehash,
                          String phonecode,PhoneCodeSignInHandler signInHandler) throws Exception{

        Logger.getGlobal().addHandler(new java.util.logging.FileHandler());
        Logger.getGlobal().setLevel(Level.OFF);

        Logger.getLogger("Telegram Bot").setLevel(Level.OFF);
        BotLogger.setLevel(Level.OFF);

        logger.debug("TelegramClient constructor");
        logger.debug("apikey="+apikey);
        logger.debug("phonenumber="+phonenumber);
        logger.debug("apihash="+apihash);
        logger.debug("phonecodehash="+phonecodehash);
        logger.debug("phonecode="+phonecode);

        PHONENUMBER = phonenumber;
        PHONECODEHASH = phonecodehash;
        APIHASH = apihash;
        APIKEY = apikey;
        PHONECODE = phonecode;
        SignInHandler = signInHandler;

        apiState = new MemoryApiState("test.log");
        api = new TelegramApi(apiState,
                new AppInfo(APIKEY, "desc", "test", "1", "en"),
                new ApiCallback() {

                    @Override
                    public void onAuthCancelled(TelegramApi api) {
                        System.err.println("onAuthCancelled");
                    }

                    @Override
                    public void onUpdatesInvalidated(TelegramApi api) {
                        System.err.println("onUpdatesInvalidated");
                    }

                    @Override
                    public void onUpdate(TLAbsUpdates updates) {
                        System.out.println("onUpdate");
                    }

                });
        try {
            final TLConfig config = api.doRpcCallNonAuth(new TLRequestHelpGetConfig());
            apiState.setPrimaryDc(config.getThisDc());
            apiState.updateSettings(config);
        } catch (Exception e) {
            logger.error("",e);
        }

        SignIn();

    }

    public String getPHONECODEHASH(){
        return PHONECODEHASH;
    }

    public String getPHONECODE(){
        return PHONECODE;
    }

    private void SignIn() throws Exception{
        try {
            TLSentCode sentCode;
            if (PHONECODEHASH == null || PHONECODEHASH.isEmpty()) {
                TLRequestAuthSendCode asc = new TLRequestAuthSendCode();
                asc.setPhoneNumber(PHONENUMBER);
                asc.setApiHash(APIHASH);
                asc.setApiId(APIKEY);
                sentCode = api.doRpcCallNonAuth(asc);
                PHONECODEHASH = sentCode.getPhoneCodeHash();
                PHONECODE = SignInHandler.getCode();
            }
            TLRequestAuthSignIn ras = new TLRequestAuthSignIn();
            ras.setPhoneNumber(PHONENUMBER);
            ras.setPhoneCodeHash(PHONECODEHASH);
            ras.setPhoneCode(PHONECODE);
            TLAuthorization auth = api.doRpcCallNonAuth(ras);
            apiState.setAuthenticated(apiState.getPrimaryDc(), true);
        }
        catch (Exception e){
            logger.error("",e);
            if(e instanceof RpcException){
                String strArr[];
                String errorTag = ((RpcException)e).getErrorTag();
                if(errorTag.equals("PHONE_CODE_EXPIRED") || errorTag.equals("PHONE_CODE_INVALID")){
                    PHONECODE = null;
                    PHONECODEHASH = null;
                    SignIn();
                }
                else if(errorTag.startsWith("PHONE_MIGRATE_") || errorTag.startsWith("NETWORK_MIGRATE_")) {
                    strArr = errorTag.split("_");
                    if(strArr != null && strArr.length == 3) {
                        api.switchToDc(Integer.parseInt(strArr[2]));
                        SignIn();
                    }
                }
                else{
                    throw new Exception(e);
                }

            }
            else{
                throw new Exception(e);
            }
        }
    }


    public void importChats(String[] szChatsUrl){
        for (String str : szChatsUrl) {
            joinChat(str);
        }
    }


    public TelegramChat joinChat(String szChatUrl){
        if (szChatUrl.contains("/joinchat")) {
            String hash = szChatUrl.split("/")[(szChatUrl.split("/").length) - 1];

            TLRequestMessagesImportChatInvite in = new TLRequestMessagesImportChatInvite();
            in.setHash(hash);
            try {
                TLAbsUpdates bb = api.doRpcCall(in);
            } catch (Exception e) {
                logger.error("",e);
            }
            try {
                TLRequestMessagesCheckChatInvite cci = new TLRequestMessagesCheckChatInvite();
                cci.setHash(hash);
                TLChatInviteAlready ChatInvite = (TLChatInviteAlready)api.doRpcCall(cci);
                //ChatInvite.get()
                TLChannel chat = (TLChannel) ChatInvite.getChat();
                return new TelegramChat(this,chat);

            }catch (Exception e){
                logger.error("",e);
            }
        } else{
            String username = szChatUrl.split("/")[(szChatUrl.split("/").length) - 1];
            try {
                TLRequestContactsResolveUsername ru = new TLRequestContactsResolveUsername();
                ru.setUsername(username);
                TLResolvedPeer peer = api.doRpcCall(ru);
                TLRequestChannelsJoinChannel join = new TLRequestChannelsJoinChannel();
                TLInputChannel ch = new TLInputChannel();
                ch.setChannelId(peer.getChats().get(0).getId());
                ch.setAccessHash(((TLChannel) peer.getChats().get(0)).getAccessHash());

                join.setChannel(ch);
                api.doRpcCall(join);

                return new TelegramChat(this,((TLChannel)peer.getChats().get(0)));

            } catch (Exception e) {
                logger.error("",e);
            }
        }

        return null;

    }


    public class TelegramChat{
        private TelegramClient tClient;
        public TLChannel chatChannel;

        public TelegramChat(TelegramClient apiclient, TLChannel chatChannel){
            tClient = apiclient;
            this.chatChannel = chatChannel;
        }

        public LinkedList<TLMessage> getMessages(int limit,int min_id, RpcCallback callback){
            LinkedList<TLMessage> chatMessagesList = new LinkedList<>();
            TLRequestMessagesGetHistory getHistory = new TLRequestMessagesGetHistory();
            TLInputPeerChannel channel = new TLInputPeerChannel();
            channel.setChannelId(chatChannel.getId());
            channel.setAccessHash(chatChannel.getAccessHash());
            getHistory.setMinId(min_id);
            getHistory.setLimit(limit);

            getHistory.setPeer(channel);
            try {
                api.doRpcCall(getHistory,callback);

            } catch (Exception e) {
                logger.error("",e);
            }

            return chatMessagesList;
        }

        public String getChatTitle(){
            return chatChannel.getTitle();
        }
    }

    static abstract class PhoneCodeSignInHandler{
        abstract public String getCode();
    }




}
