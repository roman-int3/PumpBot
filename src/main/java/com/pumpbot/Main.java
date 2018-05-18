package com.pumpbot;

import org.apache.logging.log4j.LogManager;

import org.telegram.mtproto.log.LogInterface;


class DummyInterface implements LogInterface{

    @Override
    public void w(String tag, String message) {

    }

    @Override
    public void d(String tag, String message) {

    }

    @Override
    public void e(String tag, String message) {

    }

    @Override
    public void e(String tag, Throwable t) {

    }
}





public class Main {
    public static BotConfig botConfig;
    public static PumpWatcher pumpWatcher;
    public static YobitPriceMonitor yobitPriceMonitor;

    private static final org.apache.logging.log4j.Logger logger = LogManager.getLogger();


    public static void main(String[] args) {

        org.telegram.mtproto.log.Logger.registerInterface(new DummyInterface());

        logger.debug("PUMP BOT START");


        try {
            botConfig = new BotConfig();

            pumpWatcher =new PumpWatcher();
            Thread pumpThread=new Thread(pumpWatcher);
            pumpThread.start();

            yobitPriceMonitor = new YobitPriceMonitor();
            Thread yobitThread=new Thread(yobitPriceMonitor);
            yobitThread.start();

            String isDebugEnabled = Main.botConfig.getProperty(BotConfig.KEYS.SYSTEM_DEBUG_EMULATE_BUYORDER,"NO");
            if(isDebugEnabled.equals("YES")){
                yobitPriceMonitor.enableEmulateDebugMode();
            }

            String debugPumpCoin=Main.botConfig.getProperty(BotConfig.KEYS.SYSTEM_DEBUG_EMULATE_BUYORDER_PUMPCOIN,"");
            if(debugPumpCoin != null && !debugPumpCoin.isEmpty()) {
                yobitPriceMonitor.startPump(debugPumpCoin);
            }

            yobitThread.join();


        }catch (Exception e){
            logger.error("",e);
            e.printStackTrace();
        }

        logger.debug("PUMP BOT EXIT");
        System.exit(0);

    }
}







