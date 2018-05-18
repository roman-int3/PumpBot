package com.pumpbot;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.*;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.DOTALL;

public class BotConfig extends Properties {
    private String cfgFileName = "pumpbotconfig.txt";

    public static class KEYS {

        public static final String YOBIT_API_KEY = "YOBIT_API_KEY";
        public static final String YOBIT_API_SECRET = "YOBIT_API_SECRET";

        public static final String TELEGRAM_APIKEY = "TELEGRAM_APIKEY";
        public static final String TELEGRAM_APIHASH = "TELEGRAM_APIHASH";
        public static final String TELEGRAM_PHONENUMBER = "TELEGRAM_PHONENUMBER";
        public static final String TELEGRAM_PHONEAUTHCODE = "TELEGRAM_PHONEAUTHCODE";
        public static final String TELEGRAM_PHONECODEHASH = "TELEGRAM_PHONECODEHASH";

        public static final String TRADE_MAX_PUMP_CAMPAIGNS = "TRADE_MAX_PUMP_CAMPAIGNS";
        public static final String TRADE_MAX_BTC_PERCENT_TO_BUYORDER = "TRADE_MAX_BTC_PERCENT_TO_BUYORDER";
        public static final String TRADE_BUYORDER_ADDITIONAL_PRICE_PERCENT = "TRADE_BUYORDER_ADDITIONAL_PRICE_PERCENT";
        public static final String TRADE_BUYORDER_MAX_LOSS_PERCENT = "TRADE_BUYORDER_MAX_LOSS_PERCENT";
        public static final String TRADE_SELLORDER_ADDITIONAL_LOST_PRICE_PERCENT = "TRADE_SELLORDER_ADDITIONAL_LOST_PRICE_PERCENT";
        public static final String TRADE_TRADE_STRATEGY = "TRADE_TRADE_STRATEGY";
        public static final String TRADE_PROFIT_MAX_LOSS_PERCENT = "TRADE_PROFIT_MAX_LOSS_PERCENT";
        public static final String TRADE_FIXED_PROFIT_PERCENT = "TRADE_FIXED_PROFIT_PERCENT";
        public static final String TRADE_STOP_ON_FALL_IF_PROFIT = "TRADE_STOP_ON_FALL_IF_PROFIT";

        public static final String SYSTEM_ACTIVE_ORDER = "SYSTEM_ACTIVE_ORDER";
        public static final String SYSTEM_DEBUG_EMULATE_BUYORDER_PUMPCOIN = "SYSTEM_DEBUG_EMULATE_BUYORDER_PUMPCOIN";
        public static final String SYSTEM_DEBUG_EMULATE_BUYORDER = "SYSTEM_DEBUG_EMULATE_BUYORDER";

        public static final String fixed_profit = "fixed_profit";
        public static final String dynamic_profit = "dynamic_profit";

    }

    public class ChannelLinkAndRegEx{
        public String cName;
        public String clink;
        public List <Pattern> pumpReadyMsgRegExList = new LinkedList<Pattern>();
        public Pattern pumpExchangeUrlRegEx;

    }

    private List <ChannelLinkAndRegEx> channelLinkAndRegEx = new LinkedList<ChannelLinkAndRegEx>();


    public BotConfig() throws Exception {
        super();
        try {
            load(new FileInputStream(cfgFileName));

            for(int i=1;;i++){
                ChannelLinkAndRegEx newItem = new ChannelLinkAndRegEx();
                newItem.cName = getProperty("TELEGRAM_PUMP_CHANNEL_NAME"+i);
                if(newItem.cName==null){
                    break;
                }

                newItem.clink=getProperty("TELEGRAM_PUMP_CHANNEL_"+newItem.cName+"_LINK");
                if(newItem.clink==null){
                    break;
                }
                String tmp;
                for(int j=1;(tmp = getProperty("TELEGRAM_PUMP_CHANNEL_"+newItem.cName+"_PREPUMP_MSG_REGEX"+j)) != null;j++){
                    newItem.pumpReadyMsgRegExList.add(Pattern.compile(tmp,DOTALL));
                }

                tmp = getProperty("TELEGRAM_PUMP_CHANNEL_"+newItem.cName+"_PUMPCOIN_MSG_REGEX");
                newItem.pumpExchangeUrlRegEx = Pattern.compile(tmp,DOTALL);
                channelLinkAndRegEx.add(newItem);
            }

        }catch (FileNotFoundException ex){
            setProperty(KEYS.YOBIT_API_KEY,"");
            setProperty(KEYS.YOBIT_API_SECRET,"");

            setProperty(KEYS.TELEGRAM_APIHASH,"");
            setProperty(KEYS.TELEGRAM_APIKEY,"");
            setProperty(KEYS.TELEGRAM_PHONEAUTHCODE,"");
            setProperty(KEYS.TELEGRAM_PHONECODEHASH,"");
            setProperty(KEYS.TELEGRAM_PHONENUMBER,"");
            setProperty(KEYS.TRADE_BUYORDER_ADDITIONAL_PRICE_PERCENT,"");

            setProperty(KEYS.TRADE_BUYORDER_MAX_LOSS_PERCENT,"");
            setProperty(KEYS.TRADE_FIXED_PROFIT_PERCENT,"");
            setProperty(KEYS.TRADE_MAX_BTC_PERCENT_TO_BUYORDER,"");
            setProperty(KEYS.TRADE_MAX_PUMP_CAMPAIGNS,"");
            setProperty(KEYS.TRADE_PROFIT_MAX_LOSS_PERCENT,"");
            setProperty(KEYS.TRADE_SELLORDER_ADDITIONAL_LOST_PRICE_PERCENT,"");
            setProperty(KEYS.TRADE_TRADE_STRATEGY,"");
            setProperty(KEYS.TRADE_STOP_ON_FALL_IF_PROFIT,"YES");

            setProperty(KEYS.SYSTEM_ACTIVE_ORDER,"");
            setProperty(KEYS.SYSTEM_DEBUG_EMULATE_BUYORDER_PUMPCOIN,"");
            setProperty(KEYS.SYSTEM_DEBUG_EMULATE_BUYORDER,"");

            save();

            throw new Exception("Please configure bot. See "+cfgFileName);

        }
    }

    public List<ChannelLinkAndRegEx> getChannelLinkAndRegEx() {
        return channelLinkAndRegEx;
    }

    public Enumeration keys() {
        Enumeration keysEnum = super.keys();
        Vector<String> keyList = new Vector<String>();
        while(keysEnum.hasMoreElements()){
            keyList.add((String)keysEnum.nextElement());
        }
        Collections.sort(keyList);
        return keyList.elements();
    }

    public void save(HashMap<String,String> optArr) throws Exception{
        for(Map.Entry<String,String> entry : optArr.entrySet()){
            setProperty(entry.getKey(),entry.getValue());
        }

        save();
    }

    public void save() throws Exception{
        store(new FileOutputStream(cfgFileName),"");
    }



}
