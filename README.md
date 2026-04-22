# Buffer 7.0 | Stock Exchange Order Matching Engine
> PROJECT DEMO: https://youtu.be/wKG1oMNybmU
> The themes for Buffer 7.0 are -

## Themes
This project was developed under the banner of **Buffer 7.0**, which focuses on the following core themes:
1. **Enterprise Systems & Process Optimization** *(Target Theme)*
2. **GreenTech**
3. **Cybersecurity and Digital Defense**
4. **Open Innovation**

---

## Problem Statement
> *“Design and implement a stock exchange order matching engine that processes buy and sell orders from multiple traders.”*

---

## Project Overview

This project implements a robust **Stock Exchange Order Matching System** designed to simulate how real-world trading platforms seamlessly process buy and sell orders. 

Traders can submit orders containing precise details—such as **price, quantity, and stock ticker**. The system is built to ensure fair, efficient, and rapid order handling, forming the core foundation for trade matching and execution.

### How It Works
* **Strict Validation:** Before processing, the system validates every order against strict market rules (e.g., tick size compliance, sufficient available balance, and verified user holdings).
* **Queuing & Routing:** Validated orders are placed into an incoming queue and efficiently processed by the exchange, which routes them directly to the appropriate stock’s Order Book.
* **Multi-Asset & Profile Management:** The engine is inherently designed to handle multiple stock tickers simultaneously while actively maintaining up-to-date user profiles, positions, and balances.

---

# Data Structures Used — Buffer 7.0 Stock Exchange Engine

---

## 1. HashMap — `order_id → Order`

**Where used:** `Exchange.java`

**Why:**
Every cancel or modify request comes in with just an order ID.
Instead of scanning the entire book to find the order, the HashMap
jumps directly to it in one step.

**Operations:**
| Operation | Cost |
|-----------|------|
| Lookup by order ID | O(1) |
| Insert new order | O(1) |
| Delete on cancel | O(1) |

**Example:**
```java
HashMap<String, Order> orderMap = new HashMap<>();
orderMap.put(order.orderId, order);       // register
Order found = orderMap.get("ORD-001");    // instant lookup
orderMap.remove("ORD-001");              // cancel
```

---

## 2. HashMap — `ticker → OrderBook`

**Where used:** `Exchange.java`

**Why:**
Every incoming order has a ticker field (e.g. "AAPL", "TSLA").
This HashMap routes the order to the correct stock's book in O(1).
Without this, you would loop through all books to find the right one — O(N).

**Operations:**
| Operation | Cost |
|-----------|------|
| Route order to correct book | O(1) |

**Example:**
```java
HashMap<String, OrderBook> books = new HashMap<>();
books.put("AAPL", new OrderBook("AAPL"));
OrderBook book = books.get(order.ticker);  // instant routing
```

---

## 3. HashMap — `traderId → TraderAccount`

**Where used:** `Exchange.java`, `Validator.java`, `SettlementEngine.java`

**Why:**
Pre-trade validation needs to check a trader's cash balance and share
holdings before every order. Settlement needs to update them after
every trade. Both operations must be O(1) to not slow down matching.

**Operations:**
| Operation | Cost |
|-----------|------|
| Balance check (validation) | O(1) |
| Update after trade (settlement) | O(1) |

---

## 4. HashMap — `traderId → PnL`

**Where used:** `PnLTracker.java`

**Why:**
Real-time profit and loss must be updated on every single fill.
HashMap gives instant access to any trader's running P&L figure.

**Operations:**
| Operation | Cost |
|-----------|------|
| Update P&L on fill | O(1) |
| Read P&L | O(1) |

**Formula kept live as:**
```
sum_pv += price × quantity
sum_v  += quantity
vwap    = sum_pv / sum_v
```

---

## 5. TreeMap — `price → RingBuffer<Order>` (Bids)

**Where used:** `OrderBook.java`

**Why:**
Bids must always be sorted highest price first so the best buyer
is always at the top. TreeMap maintains sorted order automatically.
Buyers competing to pay the most sit at the top. The best buyer
is always instantly visible.

**Comparator:** `Collections.reverseOrder()` — highest first

**Operations:**
| Operation | Cost |
|-----------|------|
| Peek best bid price | O(1) |
| Insert new price level | O(log P) |
| Remove price level | O(log P) |

*P = number of distinct price levels (small in practice — hundreds, not millions)*

---

## 6. TreeMap — `price → RingBuffer<Order>` (Asks)

**Where used:** `OrderBook.java`

**Why:**
Asks must always be sorted lowest price first so the cheapest
seller is always at the top. Sellers competing to charge the least
sit at the top. The best seller is always instantly visible.

**Comparator:** Natural order — lowest first

**Operations:**
| Operation | Cost |
|-----------|------|
| Peek best ask price | O(1) |
| Insert new price level | O(log P) |
| Remove price level | O(log P) |

---

## 7. RingBuffer (Circular Array) — orders at each price level

**Where used:** `OrderBook.java` — one RingBuffer per price level

**Why:**
At each price level, multiple orders can exist (e.g. 5 traders all
want to buy at $100). They must be served in FIFO order — whoever
arrived first gets matched first. This is the price-time priority rule.

A RingBuffer gives O(1) at both ends with no memory waste and no
shifting — far better than a LinkedList (cache unfriendly) or
ArrayList (O(N) removal from front).

**Operations:**
| Operation | Cost |
|-----------|------|
| Add new order to back | O(1) |
| Remove matched order from front | O(1) |
| Peek front order | O(1) |
| Grow when full | O(N) — rare, doubles in size |

**Example:**
```
Price $100 → [Order-A, Order-B, Order-C]
              ↑ front                ↑ back
              matched first          added last
```

---

## 8. LinkedList — incoming order queue

**Where used:** `Exchange.java`

**Why:**
Orders arrive from traders and must be processed in the order they
came in. A LinkedList-backed Queue gives O(1) enqueue and dequeue.
No shifting, no resizing.

**Operations:**
| Operation | Cost |
|-----------|------|
| Enqueue incoming order | O(1) |
| Dequeue for processing | O(1) |

```java
Queue<Order> incomingQueue = new LinkedList<>();
incomingQueue.add(order);       // O(1) enqueue
Order next = incomingQueue.poll(); // O(1) dequeue
```

---

## 9. ArrayList — trade tape (TradeLog)

**Where used:** `TradeLog.java`

**Why:**
Every completed trade must be appended to a running history in
chronological order. ArrayList gives O(1) amortized append and
preserves insertion order. The full trade tape is always available
for reporting and audit.

**Operations:**
| Operation | Cost |
|-----------|------|
| Append trade | O(1) amortized |
| Read full history | O(N) |
| Get last N trades | O(N) |

---

## 10. TreeMap — stop orders `stopPrice → List<Order>`

**Where used:** `OrderBook.java`

**Why:**
Stop-loss orders must be checked after every trade to see if any
stop prices have been crossed. A TreeMap sorted by stop price lets
us use `headMap(lastTradedPrice)` to instantly find all triggered
stops in one call, without scanning every stop order.

**Operations:**
| Operation | Cost |
|-----------|------|
| Insert stop order | O(log S) |
| Find all triggered stops | O(log S) |

*S = number of active stop orders — always small*

---

## 11. ArrayDeque — OHLCV candle rolling window

**Where used:** `MarketStats.java`

**Why:**
OHLCV candles (Open, High, Low, Close, Volume) are generated per
time interval (e.g. 1 minute). Old buckets are pushed to history,
new ones are created. ArrayDeque gives O(1) append and O(1) evict
from either end — perfect for a rolling time window.

**Operations:**
| Operation | Cost |
|-----------|------|
| Complete candle → history | O(1) |
| Start new candle | O(1) |
| Update current candle on trade | O(1) |

---

## 12. Two plain variables — Circuit Breaker

**Where used:** `CircuitBreaker.java`

**Why:**
No data structure needed. Just three fields per stock:
`ref_price`, `ref_timestamp`, and `paused` boolean.
On every trade, one comparison is made. If price deviated beyond
threshold within the time window, flip `paused = true`.

**Operations:**
| Operation | Cost |
|-----------|------|
| Check on every trade | O(1) |
| Flip halt flag | O(1) |
| Resume trading | O(1) |

---

## 13. Two plain variables — VWAP

**Where used:** `MarketStats.java`

**Why:**
VWAP (Volume Weighted Average Price) is maintained as two running
floats. No history of individual trades needed. Just multiply and
add on every fill.

```
sum_pv += price × quantity     ← one multiplication + one addition
sum_v  += quantity             ← one addition
vwap    = sum_pv / sum_v       ← one division, only when read
```

**Operations:**
| Operation | Cost |
|-----------|------|
| Update on every trade | O(1) |
| Read current VWAP | O(1) |

---

## Summary Table

| Component | Data Structure | Key Operation Cost |
|-----------|---------------|-------------------|
| Order lookup | HashMap | O(1) |
| Stock routing | HashMap | O(1) |
| Trader accounts | HashMap | O(1) |
| P&L tracking | HashMap | O(1) |
| Best bid price | TreeMap | O(1) peek |
| Best ask price | TreeMap | O(1) peek |
| Insert/remove price level | TreeMap | O(log P) |
| Orders at a price level | RingBuffer | O(1) both ends |
| Incoming order queue | LinkedList | O(1) both ends |
| Trade tape | ArrayList | O(1) append |
| Stop orders | TreeMap | O(log S) insert |
| OHLCV candles | ArrayDeque | O(1) |
| Circuit breaker | 2 variables + boolean | O(1) |
| VWAP | 2 running floats | O(1) |

---

> The only operations that are not O(1) are TreeMap insert/delete
> at O(log P) — but P (distinct price levels) is typically tiny
> (hundreds, not millions), so in practice this is negligible.
> Stop order insertion at O(log S) is similarly negligible as the
> number of active stop orders is always small.
