package com.pumpbot;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.meta.CurrencyPairMetaData;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.yobit.YoBitExchange;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;


import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;



public class YobitPriceMonitor implements Runnable {

    public static Exchange yobit;
    public static MarketDataService marketDataService;
    public static TradeService tradeService;
    public static AccountService accountService;
    public static AccountInfo accInfo;
    public static Wallet wallet;

    protected boolean DEBUG=false;
    boolean isFixedTradeStrategy=false;

    public static BigDecimal maxSellLostPricePercent;
    public static BigDecimal  MAX_BTC_PERCENT_TO_BUYORDER;
    public static BigDecimal BUYORDER_ADDITIONAL_PRICE_PERCENT;
    public static BigDecimal FIXED_PROFIT_PERCENT;
    public static BigDecimal PROFIT_MAX_LOSS_PERCENT;
    public static BigDecimal TRADE_BUYORDER_MAX_LOSS_PERCENT;

    public static String STOP_ON_FALL_IF_PROFIT;

    public static final BigDecimal bd100value = BigDecimal.valueOf(100);
    public static final BigDecimal bd2value   = BigDecimal.valueOf(2);
    public static final BigDecimal bdZero   = BigDecimal.valueOf(0);
    public static final Logger logger = LogManager.getLogger(YobitPriceMonitor.class);
    public final long fiveMinutes = 5 * 60 * 1000;

    public class AverageTradesOrdersPrice{
        public BigDecimal avgBidPrice       = new BigDecimal("0");
        public BigDecimal totalBidAmmount   = new BigDecimal("0");
        public BigDecimal avgAskPrice       = new BigDecimal("0");
        public BigDecimal totalAskAmmount   = new BigDecimal("0");
        public BigDecimal avgTotalPrice     = new BigDecimal("0");
        public BigDecimal spread            = new BigDecimal("0");
        public BigDecimal maxBidPrice = new BigDecimal("0");
        public BigDecimal minBidPrice = new BigDecimal("0");
        public BigDecimal maxAskPrice = new BigDecimal("0");
        public BigDecimal minAskPrice = new BigDecimal("0");
    }

    public class AverageBookOrdersPrice{
        public BigDecimal avgBidPrice;
        public BigDecimal avgAskPrice;
    }

    public class  OrderInfo{
        public String coinName;
        public String buyOrderID;
        public String sellOrderID;
        public long pumpStartTimeStamp;
        public long tradeMonitorTimeStamp;
        public BigDecimal BuyOrderPrice;
        public BigDecimal LastMaxOrderPrice;
        public BigDecimal lastProfitPrice = new BigDecimal(0);
        public BigDecimal totalCoinsToBuy = new BigDecimal(0);
        public BigDecimal totalCoinsToSell = new BigDecimal(0);
        public BigDecimal totalBTCToBuyOrder;
        public BigDecimal sellOrderPumpCoinExchangePrice;
        public TRENDS trend = TRENDS.FALL;
        public BigDecimal MaxOrderPrice=null;
        public double upTrend=0;
        public double downTrend=0;
        public long tradeInterval=0;
    }


    public enum TRENDS{
        FALL,GROW,WAIT_GROW_TREND
    }

    public List<OrderInfo>activeOrders = Collections.synchronizedList(new LinkedList<>());
    {
        ExchangeSpecification exSpec = new YoBitExchange().getDefaultExchangeSpecification();
        exSpec.setApiKey(Main.botConfig.getProperty(BotConfig.KEYS.YOBIT_API_KEY));
        exSpec.setSecretKey(Main.botConfig.getProperty(BotConfig.KEYS.YOBIT_API_SECRET));
        yobit = ExchangeFactory.INSTANCE.createExchange(exSpec);
        marketDataService = yobit.getMarketDataService();
        tradeService = yobit.getTradeService();
        accountService = yobit.getAccountService();
        ExchangeMetaData metaData = yobit.getExchangeMetaData();
        Map<CurrencyPair, CurrencyPairMetaData> cPairs = metaData.getCurrencyPairs();
    }

    public YobitPriceMonitor() throws IOException
    {
        isFixedTradeStrategy = Main.botConfig.getProperty(BotConfig.KEYS.TRADE_TRADE_STRATEGY,"")
                                                                            .matches(BotConfig.KEYS.fixed_profit);
        maxSellLostPricePercent = new BigDecimal(Main.botConfig.getProperty(
                                    BotConfig.KEYS.TRADE_SELLORDER_ADDITIONAL_LOST_PRICE_PERCENT, "0"));
        MAX_BTC_PERCENT_TO_BUYORDER = new BigDecimal(Main.botConfig.getProperty(
                                    BotConfig.KEYS.TRADE_MAX_BTC_PERCENT_TO_BUYORDER,"0"));
        BUYORDER_ADDITIONAL_PRICE_PERCENT = new BigDecimal(Main.botConfig.getProperty(
                                        BotConfig.KEYS.TRADE_BUYORDER_ADDITIONAL_PRICE_PERCENT,"0"));
        FIXED_PROFIT_PERCENT=new BigDecimal(Main.botConfig.getProperty(BotConfig.KEYS.TRADE_FIXED_PROFIT_PERCENT,"0"));
        PROFIT_MAX_LOSS_PERCENT=new BigDecimal(Main.botConfig.getProperty(BotConfig.KEYS.TRADE_PROFIT_MAX_LOSS_PERCENT,"0"));
        PROFIT_MAX_LOSS_PERCENT = PROFIT_MAX_LOSS_PERCENT.negate();

        TRADE_BUYORDER_MAX_LOSS_PERCENT = new BigDecimal(Main.botConfig.getProperty(BotConfig.KEYS.TRADE_BUYORDER_MAX_LOSS_PERCENT,"0"));
        TRADE_BUYORDER_MAX_LOSS_PERCENT = TRADE_BUYORDER_MAX_LOSS_PERCENT.negate();

        STOP_ON_FALL_IF_PROFIT = Main.botConfig.getProperty(BotConfig.KEYS.TRADE_STOP_ON_FALL_IF_PROFIT,"YES").toUpperCase();

        accInfo = accountService.getAccountInfo();
        wallet = accInfo.getWallet("BTC");
        if (wallet == null) {
            throw new IOException("WALLET NOT FOUND");
        }

        logger.debug("WALLET ID         = " + wallet.getId());
        logger.debug("WALLET NAME       = " + wallet.getName());
        logger.debug("WALLET BALANCE    = " + wallet.getBalance(Currency.BTC).getAvailable().toPlainString());
    }

    public void startPump(String pumpCoin){
        OrderInfo newOrder = new OrderInfo();
        newOrder.pumpStartTimeStamp = newOrder.tradeMonitorTimeStamp = System.currentTimeMillis() - 5 * 60 * 1000;
        newOrder.coinName=pumpCoin;
        synchronized(activeOrders){
            activeOrders.add(newOrder);
        }

    }

    public void enableEmulateDebugMode(){
        DEBUG=true;
    }



    @Override
    public void run()
    {
        long tradeInterval=0;
        AverageTradesOrdersPrice tradePrices=null;
        BigDecimal currentBidPrice = null;

        while (true) {
            try {
                while(true) {
                    synchronized (activeOrders) {
                       for (OrderInfo order : activeOrders) {
                            ////////////////////////////////////////////////////////////////////////////////////////////
                            //
                            // PLACE BUY ORDER
                            //
                            if (order.buyOrderID == null) {
                                if (wallet == null) {
                                    activeOrders.remove(order);
                                    logger.error("PUMP CANCELED. BTC wallet not found");
                                    continue;
                                }

                                logger.debug("PUMP COIN         = " + order.coinName);

                                BigDecimal totalBTC = wallet.getBalance(Currency.BTC).getAvailable();
                                order.totalBTCToBuyOrder = totalBTC.divide(bd100value, 8, RoundingMode.HALF_UP)
                                                                    .multiply(MAX_BTC_PERCENT_TO_BUYORDER);

                                logger.debug("maxUseAmmountPercentForBuyOrder= " + MAX_BTC_PERCENT_TO_BUYORDER.toPlainString());
                                logger.debug("totalBTCToBuyOrder= " + order.totalBTCToBuyOrder.toPlainString());

                                Ticker ticker = getTicker(order.coinName);

                                tradePrices = getAverageTradesOrdersPrice(order.coinName, order.tradeMonitorTimeStamp);
                                BigDecimal buyOrderPumpCoinExchangePrice = ticker.getAsk().divide(
                                        bd100value, 8, RoundingMode.HALF_UP)
                                        .multiply(BUYORDER_ADDITIONAL_PRICE_PERCENT)
                                        .add(ticker.getAsk());

                                logger.debug("maxBuyPricePercent= " + BUYORDER_ADDITIONAL_PRICE_PERCENT.toPlainString());
                                logger.debug("buyOrderPumpCoinExchangePrice= " + buyOrderPumpCoinExchangePrice.toPlainString());


                                order.LastMaxOrderPrice = order.BuyOrderPrice = buyOrderPumpCoinExchangePrice;
                                order.totalCoinsToBuy = order.totalBTCToBuyOrder.divide(buyOrderPumpCoinExchangePrice, 8, RoundingMode.HALF_UP);
                                logger.debug("totalCoinsToBuy= " + order.totalCoinsToBuy.toPlainString());
                                if (order.totalCoinsToBuy.compareTo(new BigDecimal("0.00010000")) == -1) {
                                    activeOrders.remove(order);
                                    logger.error("PUMP CANCELED. totalCoinsToBuy < 0.00010000 ");
                                    continue;
                                }

                                order.totalCoinsToSell = order.totalCoinsToBuy;

                                order.buyOrderID = ""; //TODO:for debug
                                if (!DEBUG) {
                                    try {
                                        order.buyOrderID = placeBuyLimitOrder(order.totalCoinsToBuy, buyOrderPumpCoinExchangePrice, order.coinName);
                                    } catch (IOException ex) {
                                        logger.error("", ex);
                                        activeOrders.remove(order);
                                        continue;
                                    }
                                }
                                logger.debug("BUY ORDER EXECUTED, BUY_ORDER_PRICE: " + order.BuyOrderPrice.toPlainString());
                                logger.debug("BUY ORDER ID: " + order.buyOrderID);

                                tradeInterval = System.currentTimeMillis();
                            }

                            ////////////////////////////////////////////////////////////////////////////////////////////
                            //
                            // BUY ORDER PLACED, MONITOR PRICE
                            //
                            if (order.buyOrderID != null && order.sellOrderID == null) {
                                tradePrices = getAverageTradesOrdersPrice(order.coinName, order.tradeMonitorTimeStamp);

                                if(System.currentTimeMillis() - tradeInterval >= fiveMinutes) {
                                    order.tradeMonitorTimeStamp += fiveMinutes;
                                    tradeInterval = System.currentTimeMillis();
                                }
                                Ticker ticker = getTicker(order.coinName);

                                currentBidPrice = ticker.getBid();

                                BigDecimal CurrentPriceBuyPriceDiffPercent = getPriceDifference(currentBidPrice, order.BuyOrderPrice);
                                logger.debug("Difference Between BuyOrderPrice/currentBidPrice = " +
                                        order.BuyOrderPrice.toPlainString() + "/" +
                                        currentBidPrice.toPlainString() + " = " +
                                        CurrentPriceBuyPriceDiffPercent.toPlainString() + "%");

                                int res = currentBidPrice.compareTo(order.LastMaxOrderPrice);
                                if (res == 1) {
                                    order.trend = TRENDS.GROW;
                                    order.upTrend++;
                                    order.LastMaxOrderPrice = currentBidPrice;
                                    logger.debug("Price Grow - New LastMaxOrderPrice = " + order.LastMaxOrderPrice.toPlainString());

                                    if(order.MaxOrderPrice == null || order.MaxOrderPrice.compareTo(order.LastMaxOrderPrice) < 0){
                                        order.MaxOrderPrice = order.LastMaxOrderPrice;
                                    }

                                    if (isFixedTradeStrategy) {
                                        if (CurrentPriceBuyPriceDiffPercent.compareTo(FIXED_PROFIT_PERCENT) >= 0) {
                                            order.sellOrderID = "";
                                            if (!DEBUG) {
                                                order.sellOrderID = sell(currentBidPrice, order);
                                            }
                                        }
                                    }
                                } else if (res == -1) {
                                    order.downTrend++;
                                    BigDecimal priceDiff = getPriceDifference(currentBidPrice, order.LastMaxOrderPrice);
                                    logger.debug("Prices Falling - New currentBidPrice = " + currentBidPrice);
                                    logger.debug("Difference Between LastMaxOrderPrice/currentBidPrice = " +
                                            order.LastMaxOrderPrice + "/" + currentBidPrice + " = " + priceDiff.toPlainString() + "%");
                                    logger.debug("downTrend/upTrend = " + order.downTrend+"/"+order.upTrend);


                                    //////////////////////////////////////////////////////////////////////////////////
                                    // Price fall but we in profit
                                    int cmpRes=CurrentPriceBuyPriceDiffPercent.compareTo(bdZero);
                                    if (cmpRes >= 0) {
                                        priceDiff = getPriceDifference(currentBidPrice, order.MaxOrderPrice);
                                        logger.debug("Difference Between order.MaxOrderPrice/currentBidPrice = " +
                                                order.MaxOrderPrice + "/" + currentBidPrice + " = " + priceDiff.toPlainString() + "%");
                                        cmpRes = priceDiff.compareTo(PROFIT_MAX_LOSS_PERCENT);
                                        if (cmpRes >= 0 || STOP_ON_FALL_IF_PROFIT.equals("YES")) {
                                            order.sellOrderID = "";
                                            if (!DEBUG) {
                                                order.sellOrderID = sell(currentBidPrice, order);
                                            }
                                        }
                                    } else {
                                        //////////////////////////////////////////////////////////////////////////////////
                                        // Price fall and we reached max loss stop order
                                        int diffRes = CurrentPriceBuyPriceDiffPercent.compareTo(TRADE_BUYORDER_MAX_LOSS_PERCENT);
                                        if (diffRes <= 0) {
                                            logger.debug("REACHED TRADE_PROFIT_MAX_LOSS_PERCENT");
                                            order.sellOrderID = "";
                                            if (!DEBUG) {
                                                order.sellOrderID = sell(currentBidPrice, order);
                                            }
                                        }
                                    }
                                }
                            } else if (order.sellOrderID != null) {
                                ///////////////////////////////////////////////////////////////////////////////////////
                                //
                                // SELL ORDER PLACED
                                //
                                if (!DEBUG) {
                                    Collection<Order> sellOrders = tradeService.getOrder(order.sellOrderID);
                                    for (Order sellOrder : sellOrders) {
                                        Order.OrderStatus sellOrderStatus = sellOrder.getStatus();
                                        if (sellOrderStatus == Order.OrderStatus.FILLED ||
                                                sellOrderStatus == Order.OrderStatus.PARTIALLY_FILLED) {


                                        }
                                    }
                                }

                                activeOrders.remove(order);

                                if(DEBUG){
                                    order.sellOrderPumpCoinExchangePrice = currentBidPrice;
                                }

                                logger.debug("PUMP FOR COIN: " + order.coinName + " FINISHED.");
                                logger.debug("SELL ORDER EXECUTED, SELL_ORDER_PRICE: " + order.sellOrderPumpCoinExchangePrice.toPlainString());
                                logger.debug("totalCoinsToSell = " + order.totalCoinsToSell.toPlainString());
                                logger.debug("Pump duration: " + (System.currentTimeMillis() - order.pumpStartTimeStamp) / 1000L + " sec");

                                BigDecimal profit = getPriceDifference(order.sellOrderPumpCoinExchangePrice, order.BuyOrderPrice);
                                logger.debug("Difference Between BuyOrderPrice/SellOrderPrice = " +
                                        order.BuyOrderPrice.toPlainString() + "/" +
                                        order.sellOrderPumpCoinExchangePrice + " = " +
                                        profit.toPlainString() + "%");

                                BigDecimal btcProfit = order.totalCoinsToSell.multiply(order.sellOrderPumpCoinExchangePrice);
                                logger.debug("BTCProfit = " + btcProfit.toPlainString()+ " - "+order.totalBTCToBuyOrder.toPlainString()+" = "+
                                        btcProfit.subtract(order.totalBTCToBuyOrder).toPlainString());
                                logger.debug("PROFIT % : " + profit.toPlainString() + "%");
                            }
                        }
                    }

                    Thread.sleep(2000);
                }
            } catch (Exception e) {
                e.printStackTrace();
                logger.error("error", e);
            }
        }
    }


    protected String sell(BigDecimal sellPrice,OrderInfo orderInfo) throws IOException
    {
        logger.debug("maxSellLostPricePercent = " + maxSellLostPricePercent);

        orderInfo.sellOrderPumpCoinExchangePrice = sellPrice.subtract(
                                                        sellPrice.divide(
                                                        bd100value, 8, RoundingMode.HALF_UP)
                                                        .multiply(maxSellLostPricePercent)
        );
        logger.debug("sellOrderPumpCoinExchangePrice = " + orderInfo.sellOrderPumpCoinExchangePrice);

        if (!DEBUG) {
             Collection<Order> orders = tradeService.getOrder(orderInfo.buyOrderID);
            for (Order tradeOrder : orders) {
                Order.OrderStatus buyOrderStatus = tradeOrder.getStatus();
                if (buyOrderStatus == Order.OrderStatus.PENDING_NEW ||
                        buyOrderStatus == Order.OrderStatus.NEW) {

                    tradeService.cancelOrder(orderInfo.buyOrderID);
                    activeOrders.remove(orderInfo);
                    throw new IOException("SELL ORDER CANCELED. order has not been executed");
                }
            }
        }

        logger.debug("totalCoinsToSell = " + orderInfo.totalCoinsToSell);

        BigDecimal sellOrderTotalExpectedBtcCoins = orderInfo.totalCoinsToSell.multiply(orderInfo.sellOrderPumpCoinExchangePrice);
        logger.debug("sellOrderTotalExpectedBtcCoins = " + sellOrderTotalExpectedBtcCoins);

        if (sellOrderTotalExpectedBtcCoins.compareTo(new BigDecimal("0.00010000")) == -1) {
            activeOrders.remove(orderInfo);
            throw new IOException("SELL ORDER CANCELED. totalBTCCoins < 0.00010000 ");
        }
        String sellOrderID="";
        if (!DEBUG) {
            sellOrderID = placeSellLimitOrder(orderInfo.totalCoinsToSell, orderInfo.sellOrderPumpCoinExchangePrice,
                    orderInfo.coinName);
            logger.debug("SELL ORDER ID: " + sellOrderID);
            try {
                tradeService.cancelOrder(orderInfo.buyOrderID);
            } catch (Exception e) {
                logger.error("", e);
            }
        }

        return sellOrderID;
    }


    protected Ticker getTicker(String pumpCoin) throws Exception
    {
        Ticker ticker = marketDataService.getTicker(new CurrencyPair(pumpCoin,"BTC"));
        BigDecimal high = ticker.getHigh();
        BigDecimal bid = ticker.getBid();
        BigDecimal last = ticker.getLast();
        BigDecimal volume = ticker.getVolume();
        BigDecimal qvolume = ticker.getQuoteVolume();
        BigDecimal ask = ticker.getAsk();
        BigDecimal low = ticker.getLow();
        BigDecimal open = ticker.getOpen();
        BigDecimal vwap = ticker.getVwap();
        String date = ticker.getTimestamp().toString();

        logger.debug("============================TICKER========================================");
        if(high != null)    logger.debug("High   : "+high.toPlainString());
        if(bid != null)     logger.debug("Bid    : "+bid.toPlainString());
        if(ask != null)     logger.debug("Ask    : "+ask.toPlainString());
        if(last != null)    logger.debug("Last   : "+last.toPlainString());
        if(low != null)     logger.debug("Low    : "+low.toPlainString());
        if(open != null)    logger.debug("Open   : "+open.toPlainString());
        if(volume != null)  logger.debug("Volume : "+volume.toPlainString());
        if(qvolume != null) logger.debug("QVolume: "+qvolume.toPlainString());
        if(vwap != null)    logger.debug("VWap   : "+vwap.toPlainString());
        if(date != null)    logger.debug("Date   : "+date);
        if(bid != null && ask != null)     logger.debug("SPREAD    : "+ask.subtract(bid).toPlainString());

        logger.debug("============================END========================================\n");

        return ticker;
    }


    protected BigDecimal getPriceDifference(BigDecimal price1,BigDecimal price2){
        BigDecimal part1=price1.subtract(price2);
        BigDecimal part2=price1.add(price2).divide(bd2value);
        return part1.divide(part2,8, RoundingMode.HALF_UP).multiply(bd100value);
    }


    protected AverageTradesOrdersPrice getAverageTradesOrdersPrice(String pumpCoin,long minOrderTime) throws IOException
    {
        //LAST FINISHED ORDERS
        Trades trades = marketDataService.getTrades(new CurrencyPair(pumpCoin,"BTC"),2000);
        List<Trade> tradesList = trades.getTrades();
        AverageTradesOrdersPrice avgPrice = new AverageTradesOrdersPrice();

        int bidOrdersCount =0;
        int askOrdersCount = 0;

        for (Trade trade : tradesList) {
            if(trade.getTimestamp().getTime() < minOrderTime){
                continue;
            }
            BigDecimal tradePrice = trade.getPrice();
            switch (trade.getType()) {
                case BID:{
                    avgPrice.totalBidAmmount = avgPrice.totalBidAmmount.add(trade.getOriginalAmount());
                    avgPrice.avgBidPrice = avgPrice.avgBidPrice.add(tradePrice);
                    avgPrice.avgTotalPrice = avgPrice.avgTotalPrice.add(tradePrice);
                    bidOrdersCount++;

                    if(avgPrice.maxBidPrice.compareTo(tradePrice) < 0){
                        avgPrice.maxBidPrice = tradePrice;
                    }
                    else if(avgPrice.minBidPrice.compareTo(bdZero) == 0 || avgPrice.minBidPrice.compareTo(tradePrice) > 0){
                        avgPrice.minBidPrice = tradePrice;
                    }

                    break;
                }
                case ASK:{
                    avgPrice.totalAskAmmount = avgPrice.totalAskAmmount.add(trade.getOriginalAmount());
                    avgPrice.avgAskPrice = avgPrice.avgAskPrice.add(trade.getPrice());
                    avgPrice.avgTotalPrice = avgPrice.avgTotalPrice.add(trade.getPrice());
                    askOrdersCount++;

                    if(avgPrice.maxAskPrice.compareTo(tradePrice) < 0){
                        avgPrice.maxAskPrice = tradePrice;
                    }
                    else if(avgPrice.minAskPrice.compareTo(bdZero) == 0 || avgPrice.minAskPrice.compareTo(tradePrice) > 0){
                        avgPrice.minAskPrice = tradePrice;
                    }
                }
            }
        }

        if(!avgPrice.avgBidPrice.equals(BigDecimal.valueOf(0))) {
            avgPrice.avgBidPrice  = avgPrice.avgBidPrice.divide(new BigDecimal(bidOrdersCount),
                    8, RoundingMode.HALF_UP);
        }

        if(!avgPrice.avgAskPrice.equals(BigDecimal.valueOf(0))) {
            avgPrice.avgAskPrice  = avgPrice.avgAskPrice.divide(new BigDecimal(askOrdersCount),
                    8, RoundingMode.HALF_UP);
        }

        if(!avgPrice.avgBidPrice.equals(BigDecimal.valueOf(0)) || !avgPrice.avgAskPrice.equals(BigDecimal.valueOf(0))) {
            avgPrice.avgTotalPrice  = avgPrice.avgTotalPrice
                    .divide(new BigDecimal(askOrdersCount+bidOrdersCount),
                    8, RoundingMode.HALF_UP);
        }

        avgPrice.spread = avgPrice.avgAskPrice.subtract(avgPrice.avgBidPrice).abs();

        logger.debug("====================AverageTradesOrdersPrice=============================");
        logger.debug("Trades avgAskPrice      : " + avgPrice.avgAskPrice.toPlainString());
        logger.debug("Trades avgBidPrice      : " + avgPrice.avgBidPrice.toPlainString());
        logger.debug("Trades Average Price    : " + avgPrice.avgTotalPrice.toPlainString());
        logger.debug("Trades Total ASK Ammount: " + avgPrice.totalAskAmmount.toPlainString());
        logger.debug("Trades Total BID Ammount: " + avgPrice.totalBidAmmount.toPlainString());
        logger.debug("Trades SPREAD           : " +avgPrice.spread.toPlainString());

        logger.debug("Trades MaxBid Price     : " +avgPrice.maxBidPrice.toPlainString());
        logger.debug("Trades MinBid Price     : " +avgPrice.minBidPrice.toPlainString());
        logger.debug("Trades MaxAsk Price     : " +avgPrice.maxAskPrice.toPlainString());
        logger.debug("Trades MinAsk Price     : " +avgPrice.minAskPrice.toPlainString());
        logger.debug("=========================================================================\n");
        return avgPrice;
    }


    protected AverageBookOrdersPrice getAverageOrderBookPrice(String pumpCoin) throws IOException
    {
        OrderBook oBook=marketDataService.getOrderBook(new CurrencyPair(pumpCoin, "BTC"),2000);
        AverageBookOrdersPrice avgPrice = new AverageBookOrdersPrice();
        avgPrice.avgAskPrice = new BigDecimal(0);
        avgPrice.avgBidPrice = new BigDecimal(0);

        List <LimitOrder> orders;
        orders = oBook.getBids();
        if(orders != null) {
            for (LimitOrder order : orders) {
                avgPrice.avgBidPrice = avgPrice.avgBidPrice.add(order.getLimitPrice());
            }
            avgPrice.avgBidPrice = avgPrice.avgBidPrice.divide(new BigDecimal(orders.size()),
                    8, RoundingMode.HALF_UP);
        }
        orders = oBook.getAsks();
        if(orders != null) {
            for (LimitOrder order : orders) {
                avgPrice.avgAskPrice = avgPrice.avgAskPrice.add(order.getLimitPrice());
            }
            avgPrice.avgAskPrice = avgPrice.avgAskPrice.divide(new BigDecimal(orders.size()),
                    8, RoundingMode.HALF_UP);
        }

        logger.debug("====================================================================");
        logger.debug("Current OrderBook BID Price: " + avgPrice.avgBidPrice);
        logger.debug("Current OrderBook ASK Price: " + avgPrice.avgAskPrice);
        logger.debug("====================================================================");
        return avgPrice;
    }


    protected String placeBuyLimitOrder(BigDecimal totalPumpCoinsToBuyOrder,BigDecimal pumpCoinExchangePrice,
                                        String pumpCoin) throws IOException{
        if(DEBUG){return null;}
        LimitOrder buyOrder = new LimitOrder((Order.OrderType.BID),
                totalPumpCoinsToBuyOrder,
                new CurrencyPair(pumpCoin,"BTC"),
                null,null,
                pumpCoinExchangePrice
        );

        return tradeService.placeLimitOrder(buyOrder);
    }

    protected String placeSellLimitOrder(BigDecimal totalPumpCoinsToSellOrder,BigDecimal pumpCoinExchangePrice,
                                         String pumpCoin) throws IOException{
        if(DEBUG){return null;}
        LimitOrder limitOrder = new LimitOrder((Order.OrderType.ASK),
                totalPumpCoinsToSellOrder,
                new CurrencyPair(pumpCoin,"BTC"),
                null, null,
                pumpCoinExchangePrice);

        return tradeService.placeLimitOrder(limitOrder);
    }







}
