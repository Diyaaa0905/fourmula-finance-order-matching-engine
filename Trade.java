package models;

import java.time.Instant;
import java.util.UUID;
public class Trade {

    public final String  tradeId;
    public final String  ticker;
    public final double  price;
    public final int     quantity;
    public final String  buyOrderId;
    public final String  sellOrderId;
    public final String  buyerId;
    public final String  sellerId;
    public final Instant timestamp;

    public Trade(String ticker, double price, int quantity,
                 String buyOrderId, String sellOrderId,
                 String buyerId,   String sellerId) {
        this.tradeId    = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.ticker     = ticker;
        this.price      = price;
        this.quantity   = quantity;
        this.buyOrderId = buyOrderId;
        this.sellOrderId= sellOrderId;
        this.buyerId    = buyerId;
        this.sellerId   = sellerId;
        this.timestamp  = Instant.now();
    }

    @Override
    public String toString() {
        return String.format("[TRADE %s] %s | qty=%d @ $%.2f | buyer=%s seller=%s",
            tradeId, ticker, quantity, price, buyerId, sellerId);
    }
}
