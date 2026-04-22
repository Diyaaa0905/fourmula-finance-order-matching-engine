# Buffer 7.0 | Stock Exchange Order Matching Engine
The themes for Buffer 7.0 are -

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
