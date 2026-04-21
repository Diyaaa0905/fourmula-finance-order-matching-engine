package models;

import java.time.Instant;
import java.util.UUID;

public class Order {

   
    public enum Side   { BUY, SELL }
    public enum Type   { LIMIT, MARKET, IOC, FOK, STOP_LOSS }
    public enum Status { NEW, PARTIALLY_FILLED, FILLED, CANCELLED }

    // ── Immutable fields ─────────────────────────────────────────────────────

    public final String  orderId;
    public final String  traderId;
    public final String  ticker;
    public final Side    side;
    public final Type    type;
    public final double  price;        // 0 for MARKET orders
    public final double  stopPrice;    // only for STOP_LOSS
    public final Instant timestamp;

    // ── Mutable fields (updated by engine) ───────────────────────────────────

    public int    quantity;
    public int    filledQty;
    public Status status;

   
    public Order(String traderId, String ticker, Side side, Type type,
                 double price, int quantity, double stopPrice) {
        this.orderId   = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.traderId  = traderId;
        this.ticker    = ticker;
        this.side      = side;
        this.type      = type;
        this.price     = price;
        this.quantity  = quantity;
        this.stopPrice = stopPrice;
        this.filledQty = 0;
        this.status    = Status.NEW;
        this.timestamp = Instant.now();
    }

    // Convenience constructor without stop price
    public Order(String traderId, String ticker, Side side, Type type,
                 double price, int quantity) {
        this(traderId, ticker, side, type, price, quantity, 0.0);
    }

    public int remainingQty() {
        return quantity - filledQty;
    }

    public boolean isActive() {
        return status == Status.NEW || status == Status.PARTIALLY_FILLED;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s %s %s %s qty=%d filled=%d @ $%.2f status=%s",
            orderId, traderId, ticker, side, type, quantity, filledQty, price, status);
    }
}
