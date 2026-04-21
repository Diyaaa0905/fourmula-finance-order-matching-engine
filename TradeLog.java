package core;

import models.Trade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TradeLog {

    // ArrayList — O(1) amortized append, full history preserved
    private final List<Trade> tape = new ArrayList<>();

  
    
    public void record(Trade trade) {
        tape.add(trade);
        System.out.println("[TRADE LOG] " + trade);
    }

    

    
    public List<Trade> getAll() {
        return Collections.unmodifiableList(tape);
    }

    
    public List<Trade> getLastN(int n) {
        int from = Math.max(0, tape.size() - n);
        List<Trade> slice = new ArrayList<>(tape.subList(from, tape.size()));
        Collections.reverse(slice);
        return slice;
    }

    
    public List<Trade> getByTicker(String ticker) {
        List<Trade> result = new ArrayList<>();
        for (Trade t : tape)
            if (t.ticker.equals(ticker)) result.add(t);
        return result;
    }

    /** All trades involving a specific trader (as buyer or seller) */
    public List<Trade> getByTrader(String traderId) {
        List<Trade> result = new ArrayList<>();
        for (Trade t : tape)
            if (t.buyerId.equals(traderId) || t.sellerId.equals(traderId))
                result.add(t);
        return result;
    }

    public int size() { return tape.size(); }

    
    public void printTape(String ticker) {
        System.out.println("\n── Trade Tape: " + ticker + " ──────────────────────────");
        tape.stream()
            .filter(t -> t.ticker.equals(ticker))
            .forEach(t -> System.out.printf(
                "  %s  qty=%-4d @ $%-8.2f  buyer=%-6s seller=%s%n",
                t.timestamp, t.quantity, t.price, t.buyerId, t.sellerId));
        System.out.println("───────────────────────────────────────────────────");
    }

    public void printFullTape() {
        System.out.println("\n══ FULL TRADE TAPE ═══════════════════════════════");
        for (Trade t : tape)
            System.out.println("  " + t);
        System.out.println("══════════════════════════════════════════════════");
    }
}
