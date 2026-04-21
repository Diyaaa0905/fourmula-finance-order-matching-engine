package core;

import datastructures.RingBuffer;
import models.Order;

import java.util.*;
public class OrderBook {

    public  final String ticker;

    

    // Bids: highest price first  (reverseOrder comparator)
    private final TreeMap<Double, RingBuffer<Order>> bids =
        new TreeMap<>(Collections.reverseOrder());

    // Asks: lowest price first   (natural order)
    private final TreeMap<Double, RingBuffer<Order>> asks =
        new TreeMap<>();

    // ── Stop orders — sorted list per stock ──────────────────────────────────
    // Key = stop price, Value = list of orders at that stop price
    private final TreeMap<Double, List<Order>> stopOrders = new TreeMap<>();

   

    public  final MarketStats    stats;
    public  final CircuitBreaker circuitBreaker;

    public OrderBook(String ticker) {
        this.ticker         = ticker;
        this.stats          = new MarketStats();
        this.circuitBreaker = new CircuitBreaker(ticker, 5.0, 10_000);
    }

    // ── Add order to book ─────────────────────────────────────────────────────

    
    public void addOrder(Order order) {
        if (order.type == Order.Type.STOP_LOSS) {
            addStopOrder(order);
            return;
        }

        TreeMap<Double, RingBuffer<Order>> side =
            order.side == Order.Side.BUY ? bids : asks;

        side.computeIfAbsent(order.price, p -> new RingBuffer<>()).enqueue(order);

        System.out.printf("[ORDER BOOK] %s added to %s side: %s%n",
            ticker, order.side, order);
    }

    // ── Cancel order from book ────────────────────────────────────────────────

    /**
     * Remove a resting order by direct object reference.
     * The MatchingEngine holds the reference via HashMap, so this is fast.
     *
     * NOTE: RingBuffer doesn't support arbitrary removal — we mark the order
     * CANCELLED and skip it during matching (lazy deletion pattern).
     */
    public void cancelOrder(Order order) {
        order.status = Order.Status.CANCELLED;
        System.out.printf("[CANCEL] %s order %s cancelled%n", ticker, order.orderId);
    }

    // ── Best prices ───────────────────────────────────────────────────────────

    
    public Double bestBidPrice() {
        return bids.isEmpty() ? null : bids.firstKey();
    }

    
    public Double bestAskPrice() {
        return asks.isEmpty() ? null : asks.firstKey();
    }

   
    public Order peekBestAsk() {
        return peekFront(asks);
    }

    public Order peekBestBid() {
        return peekFront(bids);
    }

    
    public Order pollBestAsk() {
        return pollFront(asks);
    }

    public Order pollBestBid() {
        return pollFront(bids);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private Order peekFront(TreeMap<Double, RingBuffer<Order>> side) {
        while (!side.isEmpty()) {
            RingBuffer<Order> queue = side.firstEntry().getValue();
            while (!queue.isEmpty()) {
                Order o = queue.peek();
                if (o.status == Order.Status.CANCELLED) { queue.dequeue(); continue; }
                return o;
            }
            side.pollFirstEntry();
        }
        return null;
    }

    private Order pollFront(TreeMap<Double, RingBuffer<Order>> side) {
        while (!side.isEmpty()) {
            Map.Entry<Double, RingBuffer<Order>> entry = side.firstEntry();
            RingBuffer<Order> queue = entry.getValue();
            while (!queue.isEmpty()) {
                Order o = queue.dequeue();
                if (o.status == Order.Status.CANCELLED) continue;
                if (queue.isEmpty()) side.pollFirstEntry(); // clean empty level
                return o;
            }
            side.pollFirstEntry();
        }
        return null;
    }

    // ── Stop orders ───────────────────────────────────────────────────────────

    private void addStopOrder(Order order) {
        stopOrders.computeIfAbsent(order.stopPrice, p -> new ArrayList<>()).add(order);
        System.out.printf("[STOP] %s stop order registered at $%.2f%n", ticker, order.stopPrice);
    }

   
    public List<Order> checkStopOrders(double lastTradedPrice) {
        List<Order> triggered = new ArrayList<>();

        
        NavigableMap<Double, List<Order>> crossed =
            stopOrders.headMap(lastTradedPrice, true);

        for (List<Order> orders : crossed.values())
            triggered.addAll(orders);

        crossed.clear();
        return triggered;
    }

    // ── Market data feeds ─────────────────────────────────────────────────────

    /** Level 1: best bid and best ask only */
    public void printLevel1() {
        System.out.printf("[L1 %s] best bid=$%.2f  best ask=$%.2f%n",
            ticker,
            bestBidPrice() == null ? 0 : bestBidPrice(),
            bestAskPrice() == null ? 0 : bestAskPrice());
    }

    /** Level 2: top N price levels on each side with quantities */
    public void printLevel2(int depth) {
        System.out.println("\n[L2 " + ticker + "] ── Order Book Depth ──");
        System.out.println("  ASKS (lowest first):");
        int count = 0;
        for (Map.Entry<Double, RingBuffer<Order>> e : asks.entrySet()) {
            if (count++ >= depth) break;
            System.out.printf("    $%.2f  x  %d orders%n", e.getKey(), e.getValue().size());
        }
        System.out.println("  BIDS (highest first):");
        count = 0;
        for (Map.Entry<Double, RingBuffer<Order>> e : bids.entrySet()) {
            if (count++ >= depth) break;
            System.out.printf("    $%.2f  x  %d orders%n", e.getKey(), e.getValue().size());
        }
        System.out.println("───────────────────────────────────");
    }

    public boolean isEmpty() {
        return bids.isEmpty() && asks.isEmpty();
    }
}
