import java.util.*;

enum OrderSide { BUY, SELL }

enum OrderType {
    MARKET, LIMIT, IOC, FOK,
    MARKET_IOC, MARKET_FOK, LIMIT_IOC, LIMIT_FOK,
    STOP_LOSS, STOP_BUY
}

enum OrderStatus { NEW, REJECTED, PARTIALLY_FILLED, FILLED, CANCELLED }

class Position {
    int shares = 0;
    double avgCost = 0.0;
    
    public String toString() {
        return String.format("%d shares @ $%.2f", shares, avgCost);
    }
}

class CircuitBreaker {
    final double thresholdPercentage;
    boolean tripped;

    CircuitBreaker(double thresholdPercentage) {
        this.thresholdPercentage = thresholdPercentage;
        this.tripped = false;
    }

    void check(Stock stock, OrderLog log) {
        if (tripped || stock.openPrice == 0) return;
        
        double deviation = Math.abs(stock.lastTradedPrice - stock.openPrice) / stock.openPrice;
        if (deviation >= (thresholdPercentage / 100.0)) {
            stock.pauseTrading();
            tripped = true;
            log.recordSystemEvent("CIRCUIT BREAKER TRIPPED for " + stock.ticker + " at deviation " + (deviation * 100) + "%");
        }
    }
}

class Stock {
    final String ticker;
    final double tickSize;
    boolean tradingPaused;
    
    // Market Variables
    double lastTradedPrice = 0.0;
    double openPrice = 0.0;
    double highPrice = Double.MIN_VALUE;
    double lowPrice = Double.MAX_VALUE;
    int volume = 0;
    
    final CircuitBreaker circuitBreaker;

    Stock(String ticker, double tickSize) {
        this.ticker          = ticker;
        this.tickSize        = tickSize;
        this.tradingPaused   = false;
        this.circuitBreaker  = new CircuitBreaker(5.0); // 5% circuit breaker
    }

    void updateMarketStats(double price, int qty) {
        if (openPrice == 0.0) openPrice = price; // First trade sets the open price
        lastTradedPrice = price;
        highPrice = Math.max(highPrice, price);
        lowPrice = Math.min(lowPrice, price);
        volume += qty;
    }

    void pauseTrading()  { tradingPaused = true; }
    void resumeTrading() { tradingPaused = false; circuitBreaker.tripped = false; }
}

class Exchange {
    private final Map<String, Stock> stocks = new HashMap<>();
    
    // Revenue tracking
    double totalRevenue = 0.0;
    final double FEE_RATE = 0.001; // 0.1% fee per trade side

    void registerStock(String ticker, double tickSize) {
        stocks.put(ticker, new Stock(ticker, tickSize));
        System.out.println("Registered: " + ticker);
    }

    boolean isValidTicker(String t) { return stocks.containsKey(t); }
    Stock   getStock(String t)      { return stocks.get(t); }
    
    void collectFee(double feeAmount) {
        totalRevenue += feeAmount;
    }
}

class Order {
    final String    id;
    final long      timestamp;
    final OrderSide side;
    final OrderType type;
    final String    traderId;
    final String    ticker;
    final double    price;
    int             quantity;
    OrderStatus     status;

    Order(String id, OrderSide side, double price, int quantity,
          OrderType type, String traderId, String ticker) {
        this.id        = id;
        this.timestamp = System.nanoTime();
        this.side      = side;
        this.price     = price;
        this.quantity  = quantity;
        this.type      = type;
        this.traderId  = traderId;
        this.ticker    = ticker;
        this.status    = OrderStatus.NEW;
    }

    public String toString() {
        return String.format("Order[%s | %s | %s | $%.2f | qty=%d | %s | %s]",
                id, side, type, price, quantity, status, traderId);
    }
}

class TradeRecord {
    final String tradeId;
    final String buyOrderId, sellOrderId, buyTraderId, sellTraderId, ticker;
    final double price;
    final int    quantity;
    final long   timestamp;

    TradeRecord(Order buy, Order sell, double price, int qty, String ticker) {
        this.tradeId      = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.buyOrderId   = buy.id;
        this.sellOrderId  = sell.id;
        this.buyTraderId  = buy.traderId;
        this.sellTraderId = sell.traderId;
        this.price        = price;
        this.quantity     = qty;
        this.ticker       = ticker;
        this.timestamp    = System.currentTimeMillis();
    }

    public String toString() {
        return String.format("Trade[%s | %s | buyer=%s | seller=%s | $%.2f | qty=%d]",
                tradeId, ticker, buyTraderId, sellTraderId, price, quantity);
    }
}

class TradeBook {
    // ArrayList — O(1) amortized append, full history preserved
    private final List<TradeRecord> tape = new ArrayList<>();

    void recordTrade(TradeRecord trade) {
        tape.add(trade);
    }

    public List<TradeRecord> getAll() {
        return Collections.unmodifiableList(tape);
    }

    void printFullTape() {
        System.out.println("\n=====FULL TRADE TAPE =====");
        for (TradeRecord t : tape) System.out.println("  " + t);
        System.out.println("============================");
    }
}

class OrderLog {
    private final List<String> log = new ArrayList<>();

    void record(Order order, String note) {
        String entry = "[LOG] " + order.id + " | " + order.status + " | " + note;
        log.add(entry);
        System.out.println(entry);
    }
    
    void recordSystemEvent(String note) {
        String entry = "[SYS] " + note;
        log.add(entry);
        System.out.println(entry);
    }

    void printAll() {
        System.out.println("\n===== ORDER LOG =====");
        for (String e : log) System.out.println(e);
        System.out.println("=====================");
    }
}

class RingBuffer {
    private Order[] buffer;
    private int head, tail, size, capacity;

    RingBuffer(int cap) {
        capacity = cap;
        buffer   = new Order[cap];
    }

    void enqueue(Order o) {
        if (size == capacity) resize();
        buffer[tail] = o;
        tail = (tail + 1) % capacity;
        size++;
    }

    Order dequeue() {
        if (isEmpty()) return null;
        Order o = buffer[head];
        buffer[head] = null;
        head = (head + 1) % capacity;
        size--;
        return o;
    }

    Order peek()      { return isEmpty() ? null : buffer[head]; }
    boolean isEmpty() { return size == 0; }

    List<Order> toList() {
        List<Order> list = new ArrayList<>();
        for (int i = 0; i < size; i++) list.add(buffer[(head + i) % capacity]);
        return list;
    }

    private void resize() {
        Order[] nb = new Order[capacity * 2];
        for (int i = 0; i < size; i++) nb[i] = buffer[(head + i) % capacity];
        buffer   = nb;
        head     = 0;
        tail     = size;
        capacity *= 2;
    }
}

class PriceLevel {
    final double price;
    final RingBuffer queue;
    int totalQuantity;

    PriceLevel(double price) {
        this.price         = price;
        this.queue         = new RingBuffer(64);
        this.totalQuantity = 0;
    }

    void  addOrder(Order o)    { queue.enqueue(o); totalQuantity += o.quantity; }
    Order peekOrder()          { return queue.peek(); }
    Order popOrder() {
        Order o = queue.dequeue();
        if (o != null) totalQuantity -= o.quantity;
        return o;
    }
    void  reduceQuantity(int n) { totalQuantity -= n; }
    boolean isEmpty()           { return queue.isEmpty(); }
}

class OrderBook {
    final TreeMap<Double, PriceLevel> bids = new TreeMap<>(Collections.reverseOrder());
    final TreeMap<Double, PriceLevel> asks = new TreeMap<>();

    void addOrder(Order order) {
        TreeMap<Double, PriceLevel> side = (order.side == OrderSide.BUY) ? bids : asks;
        side.putIfAbsent(order.price, new PriceLevel(order.price));
        side.get(order.price).addOrder(order);
        System.out.println("Added to book: " + order);
    }

    int availableQuantity(Order incoming) {
        TreeMap<Double, PriceLevel> opposite = (incoming.side == OrderSide.BUY) ? asks : bids;
        boolean isMarket = (incoming.type == OrderType.MARKET_FOK || incoming.type == OrderType.MARKET);
        int total = 0;
        for (Map.Entry<Double, PriceLevel> entry : opposite.entrySet()) {
            double lvlPrice = entry.getKey();
            if (!isMarket) {
                boolean ok = (incoming.side == OrderSide.BUY)
                        ? lvlPrice <= incoming.price
                        : lvlPrice >= incoming.price;
                if (!ok) break;
            }
            for (Order r : entry.getValue().queue.toList()) {
                if (!r.traderId.equals(incoming.traderId)) total += r.quantity;
            }
            if (total >= incoming.quantity) return total;
        }
        return total;
    }

    void printBook() {
        System.out.println("\n===== ORDER BOOK =====");
        System.out.println("-- ASKS (lowest first) --");
        for (Map.Entry<Double, PriceLevel> e : asks.entrySet())
            System.out.printf("  $%.2f x %d%n", e.getKey(), e.getValue().totalQuantity);
        System.out.println("-- BIDS (highest first) --");
        for (Map.Entry<Double, PriceLevel> e : bids.entrySet())
            System.out.printf("  $%.2f x %d%n", e.getKey(), e.getValue().totalQuantity);
        System.out.println("======================");
    }
}

class StopOrderBook {
    final TreeMap<Double, List<Order>> stopLoss = new TreeMap<>(Collections.reverseOrder());
    final TreeMap<Double, List<Order>> stopBuy  = new TreeMap<>();

    void addStopOrder(Order order) {
        if (order.type == OrderType.STOP_LOSS) {
            stopLoss.putIfAbsent(order.price, new ArrayList<>());
            stopLoss.get(order.price).add(order);
        } else {
            stopBuy.putIfAbsent(order.price, new ArrayList<>());
            stopBuy.get(order.price).add(order);
        }
        System.out.println("Stop order stored: " + order);
    }

    List<Order> getTriggeredOrders(double marketPrice) {
        List<Order> triggered = new ArrayList<>();
        for (Map.Entry<Double, List<Order>> e : stopLoss.entrySet())
            if (marketPrice <= e.getKey()) triggered.addAll(e.getValue());
        for (Map.Entry<Double, List<Order>> e : stopBuy.entrySet())
            if (marketPrice >= e.getKey()) triggered.addAll(e.getValue());
            
        stopLoss.entrySet().removeIf(e -> triggered.containsAll(e.getValue()));
        stopBuy.entrySet().removeIf(e -> triggered.containsAll(e.getValue()));
        return triggered;
    }
}

class MatchingEngine {
    final Exchange           exchange;
    final OrderBook          orderBook;
    final StopOrderBook      stopOrderBook;
    final TradeBook          tradeBook;
    final OrderLog           orderLog;
    final UserProfileManager um;

    MatchingEngine(Exchange exchange, OrderBook ob, StopOrderBook sob, TradeBook tb, OrderLog ol, UserProfileManager um) {
        this.exchange      = exchange;
        this.orderBook     = ob;
        this.stopOrderBook = sob;
        this.tradeBook     = tb;
        this.orderLog      = ol;
        this.um            = um;
    }

    void process(Order order, Stock stock) {
        if (stock.tradingPaused && order.type != OrderType.STOP_LOSS && order.type != OrderType.STOP_BUY) {
             order.status = OrderStatus.REJECTED;
             orderLog.record(order, "REJECTED: Circuit Breaker active. Trading is paused.");
             return;
        }
    
        System.out.println("\nProcessing: " + order);
        switch (order.type) {
            case STOP_LOSS: case STOP_BUY: handleStop(order); break;
            case MARKET: handleMarketNormal(order, stock); break;
            case MARKET_IOC: handleMarketIOC(order, stock); break;
            case MARKET_FOK: handleMarketFOK(order, stock); break;
            case LIMIT: handleLimitNormal(order, stock); break;
            case LIMIT_IOC: handleLimitIOC(order, stock); break;
            case LIMIT_FOK: handleLimitFOK(order, stock); break;
            default:
                order.status = OrderStatus.REJECTED;
                orderLog.record(order, "Unknown order type");
        }
    }

    private void handleStop(Order order) {
        stopOrderBook.addStopOrder(order);
        orderLog.record(order, "Stored in stop book at threshold: " + order.price);
    }

    private void handleMarketNormal(Order order, Stock stock) {
        match(order, stock, true);
        if (order.quantity > 0) orderLog.record(order, "Partial fill - remainder added to book");
        else orderLog.record(order, "Fully filled");
    }

    private void handleMarketIOC(Order order, Stock stock) {
        match(order, stock, false);
        if (order.quantity > 0) {
            order.status = OrderStatus.CANCELLED;
            orderLog.record(order, "IOC - unfilled remainder cancelled");
        } else orderLog.record(order, "IOC - fully filled");
    }

    private void handleMarketFOK(Order order, Stock stock) {
        int available = orderBook.availableQuantity(order);
        if (available < order.quantity) {
            order.status = OrderStatus.CANCELLED;
            orderLog.record(order, "FOK failed - only " + available + " available, needed " + order.quantity);
            return;
        }
        match(order, stock, false);
        orderLog.record(order, "FOK - fully filled");
    }

    private void handleLimitNormal(Order order, Stock stock) {
        match(order, stock, true);
        if (order.quantity > 0) orderLog.record(order, "Partial/no fill - remainder added to book");
        else orderLog.record(order, "Fully filled");
    }

    private void handleLimitIOC(Order order, Stock stock) {
        match(order, stock, false);
        if (order.quantity > 0) {
            order.status = OrderStatus.CANCELLED;
            orderLog.record(order, "Limit IOC - unfilled remainder cancelled");
        } else orderLog.record(order, "Limit IOC - fully filled");
    }

    private void handleLimitFOK(Order order, Stock stock) {
        int available = orderBook.availableQuantity(order);
        if (available < order.quantity) {
            order.status = OrderStatus.CANCELLED;
            orderLog.record(order, "Limit FOK failed - only " + available + " available at limit, needed " + order.quantity);
            return;
        }
        match(order, stock, false);
        orderLog.record(order, "Limit FOK - fully filled");
    }

    private void match(Order incoming, Stock stock, boolean addRemainder) {
        TreeMap<Double, PriceLevel> opposite =
                (incoming.side == OrderSide.BUY) ? orderBook.asks : orderBook.bids;

        boolean isLimitType = (incoming.type == OrderType.LIMIT
                || incoming.type == OrderType.LIMIT_IOC
                || incoming.type == OrderType.LIMIT_FOK);
                
        boolean tradeExecuted = false;

        while (incoming.quantity > 0 && !opposite.isEmpty()) {
            Map.Entry<Double, PriceLevel> bestEntry = opposite.firstEntry();
            double bestPrice = bestEntry.getKey();
            PriceLevel level = bestEntry.getValue();

            if (isLimitType) {
                boolean priceOk = (incoming.side == OrderSide.BUY)
                        ? bestPrice <= incoming.price
                        : bestPrice >= incoming.price;
                if (!priceOk) break;
            }

            List<Order> skipped = new ArrayList<>();
            boolean matchedAtLevel = false;

            while (!level.isEmpty() && incoming.quantity > 0) {
                Order resting = level.peekOrder();

                if (resting.traderId.equals(incoming.traderId)) {
                    skipped.add(level.popOrder());
                    continue;
                }

                int tradeQty = Math.min(incoming.quantity, resting.quantity);
                incoming.quantity -= tradeQty;
                resting.quantity  -= tradeQty;
                level.reduceQuantity(tradeQty);

                incoming.status = (incoming.quantity == 0) ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;
                resting.status  = (resting.quantity == 0) ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;

                Order buy  = (incoming.side == OrderSide.BUY) ? incoming : resting;
                Order sell = (incoming.side == OrderSide.SELL) ? incoming : resting;
                
                // 1. Create Trade Record
                TradeRecord trade = new TradeRecord(buy, sell, bestPrice, tradeQty, stock.ticker);
                tradeBook.recordTrade(trade);
                
                // 2. Settlement & PnL & Revenue
                UserProfile buyerProfile = um.getUser(buy.traderId);
                UserProfile sellerProfile = um.getUser(sell.traderId);
                
                double tradeValue = bestPrice * tradeQty;
                double fee = tradeValue * exchange.FEE_RATE;
                exchange.collectFee(fee * 2); // Collect from both
                
                buyerProfile.settleBuy(stock.ticker, tradeQty, bestPrice, fee);
                sellerProfile.settleSell(stock.ticker, tradeQty, bestPrice, fee);
                
                // 3. Update Market Variables
                stock.updateMarketStats(bestPrice, tradeQty);
                tradeExecuted = true;
                matchedAtLevel = true;

                if (resting.quantity == 0) level.popOrder(); 
                else break; 
            }

            for (Order s : skipped) level.addOrder(s);
            if (level.isEmpty()) opposite.remove(bestPrice);
            if (!matchedAtLevel && !skipped.isEmpty()) break;
        }

        if (addRemainder && incoming.quantity > 0) {
            orderBook.addOrder(incoming);
        }

        // 4. Post-Trade Validations (Stop Triggers & Circuit Breaker)
        if (tradeExecuted) {
            stock.circuitBreaker.check(stock, orderLog);
            if (!stock.tradingPaused) {
                checkStopTriggers(stock);
            }
        }
    }

    private void checkStopTriggers(Stock stock) {
        List<Order> triggered = stopOrderBook.getTriggeredOrders(stock.lastTradedPrice);
        for (Order stopOrder : triggered) {
            System.out.println("Stop triggered: " + stopOrder.id + " at price: " + stock.lastTradedPrice);
            Order marketOrder = new Order(
                    stopOrder.id + "_MKT", stopOrder.side, 0, stopOrder.quantity,
                    OrderType.MARKET, stopOrder.traderId, stopOrder.ticker
            );
            process(marketOrder, stock); // Route triggered order back into engine
        }
    }
}

class UserProfile {
    final String traderId;
    final String name;
    double balance;
    double realizedPnL = 0.0;
    private final Map<String, Position> portfolio = new HashMap<>();

    UserProfile(String traderId, String name, double balance) {
        this.traderId = traderId;
        this.name     = name;
        this.balance  = balance;
    }

    void deposit(double a) { balance += a; }
    
    // Settlement execution
    void settleBuy(String ticker, int qty, double price, double fee) {
        double totalCost = (qty * price) + fee;
        balance -= totalCost;
        
        Position p = portfolio.computeIfAbsent(ticker, k -> new Position());
        double cumulativeCost = (p.shares * p.avgCost) + (qty * price);
        p.shares += qty;
        p.avgCost = cumulativeCost / p.shares;
    }
    
    void settleSell(String ticker, int qty, double price, double fee) {
        double totalRevenue = (qty * price) - fee;
        balance += totalRevenue;
        
        Position p = portfolio.get(ticker);
        if (p != null) {
            p.shares -= qty;
            // Calculate Realized P&L: (Exit Price - Entry Price) * Qty - Fees
            double pnl = ((price - p.avgCost) * qty) - fee;
            realizedPnL += pnl;
            
            if (p.shares == 0) portfolio.remove(ticker);
        }
    }
    
    // For direct manual initialization
    void addShares(String t, int q, double initialCost) {
        Position p = portfolio.computeIfAbsent(t, k -> new Position());
        p.shares += q;
        p.avgCost = initialCost;
    }
    
    int getShares(String t) { 
        return portfolio.containsKey(t) ? portfolio.get(t).shares : 0; 
    }

    public String toString() {
        return String.format("User[%s | %s | Bal: $%.2f | P&L: $%.2f | Portfolio: %s]", 
                traderId, name, balance, realizedPnL, portfolio);
    }
}

class UserProfileManager {
    private final Map<String, UserProfile> users = new HashMap<>();
    void        addUser(UserProfile u)    { users.put(u.traderId, u); }
    UserProfile getUser(String id)        { return users.get(id); }
    boolean     userExists(String id)     { return users.containsKey(id); }
    
    void printAllProfiles() {
        System.out.println("\n===== TRADER PROFILES =====");
        for (UserProfile u : users.values()) System.out.println(u);
    }
}

class OrderValidator {
    private static final double EPSILON = 1e-9;

    static String validate(Order order, UserProfileManager um, Exchange ex) {
        if (order == null)                      return "Order is null.";
        if (!um.userExists(order.traderId))     return "Invalid trader: " + order.traderId;
        if (!ex.isValidTicker(order.ticker))    return "Invalid ticker: " + order.ticker;
        Stock stock = ex.getStock(order.ticker);
        if (stock.tradingPaused && order.type != OrderType.STOP_LOSS && order.type != OrderType.STOP_BUY)               
                                                return "Trading paused for: " + order.ticker;
        if (order.quantity <= 0)                return "Quantity must be > 0.";

        boolean needsPrice = (order.type == OrderType.LIMIT || order.type == OrderType.LIMIT_IOC
                || order.type == OrderType.LIMIT_FOK || order.type == OrderType.STOP_LOSS
                || order.type == OrderType.STOP_BUY);

        if (needsPrice) {
            if (order.price <= 0)               return "Price must be > 0.";
            double rem = order.price % stock.tickSize;
            if (rem > EPSILON && Math.abs(rem - stock.tickSize) > EPSILON)
                return "Price violates tick size: " + stock.tickSize;
        }

        UserProfile user = um.getUser(order.traderId);
        if (order.side == OrderSide.BUY && needsPrice) {
            double required = (order.price * order.quantity) * (1 + ex.FEE_RATE); // Check with fees buffer
            if (user.balance < required)
                return "Insufficient balance. Need: " + required + " Have: " + user.balance;
        }
        if (order.side == OrderSide.SELL) {
            if (user.getShares(order.ticker) < order.quantity)
                return "Insufficient shares.";
        }
        return "VALID";
    }
}

class IncomingOrderQueue {
    private final Queue<Order> queue = new LinkedList<>();
    void  enqueue(Order o)  { queue.offer(o); }
    Order dequeue()         { return queue.poll(); }
    boolean isEmpty()       { return queue.isEmpty(); }
}

public class Main {
    public static void main(String[] args) {

        // 1. Setup Exchange & Predefined Stock
        Exchange exchange = new Exchange();
        exchange.registerStock("AAPL", 0.01);

        // 2. Setup Predefined Users
        UserProfileManager um = new UserProfileManager();
        um.addUser(new UserProfile("T001", "Alice", 100000));
        um.addUser(new UserProfile("T002", "Bob",   100000));
        um.addUser(new UserProfile("T003", "Carol", 100000));

        // Adding initial shares at an avg entry cost of $95.00
        um.getUser("T002").addShares("AAPL", 500, 95.00);
        um.getUser("T003").addShares("AAPL", 500, 95.00);

        // 3. Initialize Engine Components
        OrderBook          orderBook     = new OrderBook();
        StopOrderBook      stopOrderBook = new StopOrderBook();
        TradeBook          tradeBook     = new TradeBook();
        OrderLog           orderLog      = new OrderLog();
        IncomingOrderQueue queue         = new IncomingOrderQueue();
        
        MatchingEngine engine = new MatchingEngine(exchange, orderBook, stopOrderBook, tradeBook, orderLog, um);
        Stock stock = exchange.getStock("AAPL");

        // 4. Predefined Test Orders (Initial State)
        Order[] testOrders = {
            new Order("O001", OrderSide.SELL, 100.00, 50, OrderType.LIMIT,      "T002", "AAPL"),
            new Order("O002", OrderSide.SELL, 101.00, 30, OrderType.LIMIT,      "T003", "AAPL"),
            new Order("O003", OrderSide.SELL, 102.00, 20, OrderType.LIMIT,      "T002", "AAPL"),
            new Order("O004", OrderSide.SELL, 99.00,  20, OrderType.STOP_LOSS,  "T002", "AAPL"),
            new Order("O005", OrderSide.BUY,  100.00, 80, OrderType.LIMIT,      "T001", "AAPL"),
        };

        System.out.println("=== LOADING PREDEFINED ORDERS ===");
        for (Order o : testOrders) {
            if (OrderValidator.validate(o, um, exchange).equals("VALID")) {
                engine.process(o, stock);
            }
        }
        System.out.println("=== INITIALIZATION COMPLETE ===\n");

        // 5. Interactive Console Loop
        Scanner scanner = new Scanner(System.in);
        System.out.println("Type 'HELP' to see available commands. Type 'EXIT' to quit.");

        while (true) {
            System.out.print("\nTradingEngine> ");
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) continue;

            String[] parts = input.split("\\s+");
            String cmd = parts[0].toUpperCase();

            if (cmd.equals("EXIT") || cmd.equals("QUIT")) {
                System.out.println("Shutting down engine...");
                break;
            }

            try {
                switch (cmd) {
                    case "HELP":
                        System.out.println("Commands:");
                        System.out.println("  BUY <traderId> <ticker> <qty> <price> <type>  - Submit a BUY order");
                        System.out.println("  SELL <traderId> <ticker> <qty> <price> <type> - Submit a SELL order");
                        System.out.println("      (Available Types: LIMIT, MARKET, LIMIT_IOC, MARKET_IOC, STOP_LOSS, etc.)");
                        System.out.println("      (Note: For MARKET orders, set price to 0)");
                        System.out.println("  BOOK      - Show the Order Book");
                        System.out.println("  TAPE      - Show the Trade Tape (History)");
                        System.out.println("  PROFILES  - Show all User Profiles and P&L");
                        System.out.println("  STATS     - Show Market Variables & Revenue");
                        System.out.println("  RESUME    - Clear Circuit Breaker and resume trading");
                        System.out.println("  EXIT      - Quit system");
                        break;

                    case "BUY":
                    case "SELL":
                        if (parts.length < 6) {
                            System.out.println("Usage: " + cmd + " <traderId> <ticker> <qty> <price> <type>");
                            System.out.println("Example: BUY T001 AAPL 10 101.50 LIMIT");
                            break;
                        }
                        
                        OrderSide side = OrderSide.valueOf(cmd);
                        String traderId = parts[1];
                        String ticker = parts[2];
                        int qty = Integer.parseInt(parts[3]);
                        double price = Double.parseDouble(parts[4]);
                        OrderType type = OrderType.valueOf(parts[5].toUpperCase());

                        // Generate a unique ID for the manual order
                        String manualOrderId = "M" + (System.currentTimeMillis() % 10000);
                        Order newOrder = new Order(manualOrderId, side, price, qty, type, traderId, ticker);

                        // Validate and Process
                        String validationResult = OrderValidator.validate(newOrder, um, exchange);
                        if (validationResult.equals("VALID")) {
                            engine.process(newOrder, exchange.getStock(ticker));
                        } else {
                            System.out.println("ORDER REJECTED: " + validationResult);
                        }
                        break;

                    case "BOOK":
                        orderBook.printBook();
                        break;

                    case "TAPE":
                        tradeBook.printFullTape();
                        break;

                    case "PROFILES":
                        um.printAllProfiles();
                        break;

                    case "STATS":
                        Stock s = exchange.getStock("AAPL");
                        System.out.println("\n===== MARKET STATS [AAPL] =====");
                        System.out.printf("Open: $%.2f | Last: $%.2f | High: $%.2f | Low: $%.2f | Vol: %d%n",
                            s.openPrice, s.lastTradedPrice, s.highPrice, s.lowPrice, s.volume);
                        System.out.printf("Trading Paused (Circuit Breaker): %b%n", s.tradingPaused);
                        System.out.printf("Exchange Revenue Collected: $%.2f%n", exchange.totalRevenue);
                        break;
                        
                    case "RESUME":
                        exchange.getStock("AAPL").resumeTrading();
                        System.out.println("Trading resumed for AAPL. Circuit Breaker reset.");
                        break;

                    default:
                        System.out.println("Unknown command. Type 'HELP' for a list of commands.");
                }
            } catch (IllegalArgumentException e) {
                System.out.println("Input Error: Invalid order type or parameter. Please check your spelling.");
            } catch (Exception e) {
                System.out.println("Error processing command: " + e.getMessage());
            }
        }
        scanner.close();
    }
}
