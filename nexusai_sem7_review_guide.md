# NexusAI Gateway — Semester 7 Presentation Guide & Technical Report

This document serves as your complete guide to explaining the design decisions, architecture, and core implementations of the **NexusAI Gateway** during your final Semester 7 project review.

---

## 1. Project Context & Problem Statement

### The Problem
When enterprises build LLM-powered applications, they face four critical challenges:
1. **Single-Provider Lock-in & Reliability**: If a provider (like Google Gemini or Groq) goes down or hits rate limits, the entire dependent app crashes.
2. **Sub-Optimal Costs**: Sending simple prompts (like "Hello") to premium models (like Gemini) wastes money, while sending complex requests (like coding) to cheap models yields poor quality.
3. **High Latency**: Standard HTTP calls block thread execution, meaning servers cannot scale under massive concurrent user loads.
4. **Lack of Auditable Data**: No centralized place to track latency, token usage, cost, and provider reliability metrics.

### The Solution: NexusAI Gateway
An intelligent LLM gateway built on a **fully reactive, non-blocking asynchronous stack** that governs AI traffic, auto-classifies requests to optimize cost, guarantees resilience via cascading circuit-breaker fallbacks, and logs transactions asynchronously.

---

## 2. Core Architectural Highlights (The Tech Stack)

When examiners ask about your technology choices, highlight these core points:

| Component | Technology | Rationale |
| :--- | :--- | :--- |
| **Runtime** | Java 21 | Leverages modern LTS features and native optimization. |
| **Framework** | Spring Boot 3 + WebFlux | Built on **Reactor (Netty)**. Uses an asynchronous event-loop model instead of a "one-thread-per-request" model, enabling it to scale to thousands of concurrent connections with minimal memory. |
| **Resilience** | Resilience4j Reactor | Non-blocking circuit breaker implementations per provider that intercept network calls. |
| **Database** | JPA + Hibernate (H2 / PostgreSQL) | Persistent storage for audit logs, optimized for non-blocking writes using worker scheduling. |

---

## 3. Explaining Your Key Implementations

Explain each feature using the SDE-level terminology below:

### A. Intelligent Heuristic Routing (`RoutingService.java`)
* **How to explain it:** "We built a real-time heuristic content classifier. Instead of making manual routing decisions, the gateway inspects the payload size and key terms (e.g., `'code'`, `'algorithm'`, `'solve'`)."
* **The Mapping:**
  * `HIGH` Priority &rarr; Routed to `Gemini 2.5 Flash` (Advanced reasoning, long context).
  * `MEDIUM` Priority &rarr; Routed to `Llama 3.3 70B` on Groq (Fast reasoning, explanations).
  * `LOW` Priority &rarr; Routed to `Llama 3.1 8B` on Groq (Instant greetings, simple QA).

### B. Reactive Fault Tolerance (`ChatController.java` + `Resilience4j`)
* **How to explain it:** "We wrap each provider's WebClient call inside a Resilience4j `CircuitBreaker`. If a provider's failure rate exceeds **50%** (out of a sliding window of 5 calls), the circuit state trips to **OPEN**. Subsequent requests bypass that provider instantly, trigger a **cascading fallback** chain, and degrade gracefully without network timeouts:
  `Gemini` &rarr; `Groq Llama 3.3 70B` &rarr; `Groq Llama 3.1 8B`."

### C. Non-Blocking Audit Logging (`LoggingService.java`)
* **How to explain it:** "JPA/Hibernate uses blocking JDBC connections. To protect our WebFlux event loop from being blocked by database writes, we offloaded the database persistence tasks onto a dedicated worker thread pool using `.subscribeOn(Schedulers.boundedElastic())`. This guarantees that database latency never blocks active gateway traffic."
* **Cost & Token tracking:** "Before writing the log to PostgreSQL/H2, the service dynamically estimates token usage (~4 characters per token) and references our local provider pricing matrix to log the transaction cost in USD."

---

## 4. Anticipated Examiner Q&A (Be Prepared)

### Q1: Why did you choose Spring WebFlux over standard Spring Web MVC?
* **Answer:** "Standard Web MVC uses a blocking 'Thread-Per-Request' model. Under load (e.g., 500 concurrent connections waiting on slow LLM responses), the server exhausts its thread pool and crashes. WebFlux uses a single-digit pool of Netty event-loop threads. It handles input/output events asynchronously, allowing the server to handle tens of thousands of concurrent requests using very low RAM."

### Q2: JPA is blocking. Doesn't that ruin your reactive architecture?
* **Answer:** "Yes, if called directly. We resolved this critical performance bottleneck by encapsulating our `JpaRepository.save()` inside a `Mono.fromCallable()` wrapper and subscribing it to `Schedulers.boundedElastic()`. This schedules the blocking JDBC call onto a separate worker thread pool designed specifically for blocking I/O, keeping the main Netty event loop free to route traffic."

### Q3: How do you estimate token count and cost without calling provider APIs?
* **Answer:** "We use a standard SDE heuristic: 1 token is approximately 4 characters of English text. We estimate input tokens from the user prompt and output tokens from the model response. We multiply these counts by the provider's official pricing rates (e.g., `$0.075 / 1M` input for Gemini) to compute and store the transaction cost on-the-fly."

---

## 5. Step-by-Step Live Demo Script

Follow this sequence during your review to show a flawless live execution:

1. **Open the Visualizer Dashboard** (`http://localhost:8080/index.html`) in your browser to showcase the sleek glassmorphic UI.
2. **Send a simple greeting** (e.g., `"Hello!"`):
   * *Point out:* The dashboard evaluates complexity as `LOW` and instantly routes it to `Llama 3.1 8B` (Groq).
3. **Send a coding prompt** (e.g., `"Write a Python binary search algorithm"`):
   * *Point out:* Complexity is dynamically classified as `HIGH` and routes to `Gemini 2.5 Flash`.
4. **Trigger the fallback logs**:
   * Open `http://localhost:8080/api/logs` in another tab to show the examiners the real-time JSON audit trail of the database, showing calculated token counts, latency in milliseconds, and the exact USD transaction cost logged.
