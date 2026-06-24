# NexusAI Gateway: Full Project Documentation

## 1. Problem Statement
When modern enterprises build applications powered by Large Language Models (LLMs), they face four critical architectural challenges:
1. **Single-Provider Lock-in & Reliability**: If a primary LLM provider (like OpenAI or Google Gemini) experiences an outage or rate-limits the application, the entire dependent system crashes.
2. **Sub-Optimal Financial Costs**: Sending simple, repetitive prompts (e.g., "Hello") to premium, expensive models wastes money. Conversely, sending complex logic requests to cheap models yields poor quality. 
3. **High Latency & Scalability Bottlenecks**: Standard HTTP API calls block server threads. Under massive concurrent user loads, servers exhaust their thread pools and crash.
4. **Lack of Auditable Telemetry**: There is no centralized infrastructure to track real-time latency, precise token usage, exact USD costs, and provider health metrics.

## 2. What We Implemented
We built the **NexusAI Gateway**, an intelligent, fully reactive, non-blocking API Gateway built on **Spring Boot WebFlux**. It acts as a smart proxy between client applications and downstream LLM providers (Gemini, Groq, OpenAI, Claude, Ollama). 

It governs AI traffic by auto-classifying requests to optimize costs, guarantees system resilience via cascading circuit-breaker fallbacks, and logs deep financial and performance metrics asynchronously.

---

## 3. How to Start the Project (Offline / Local Mode)

Follow these exact steps to start the complete system locally:

### Step 1: Start the Redis Database
Redis is used for distributed caching and rate limiting.
```bash
docker start redis-nexus
```
*(If the container is missing, create it using: `docker run --name redis-nexus -p 6379:6379 -d redis:alpine`)*

### Step 2: Start the Observability Stack
This starts Prometheus (for time-series metric collection) and Grafana (for the visual dashboard).
```bash
docker compose -f observability/docker-compose.yml up -d
```

### Step 3: Start the Main Spring Boot Gateway
Run the reactive Java backend.
```bash
./mvnw spring-boot:run
```

### Access Points:
* **Frontend Dashboard / Playground**: `http://localhost:8080/`
* **Real-time API Logs**: `http://localhost:8080/api/logs`
* **Grafana Command Center**: `http://localhost:3000/` *(Login: admin/admin)*
* **Prometheus Metrics**: `http://localhost:9090/`

---

## 4. System Modules

### A. Core Routing Module
Analyzes the incoming prompt payload, determines its complexity, and routes it to the most cost-efficient LLM provider.

### B. Resilience & Fallback Module
Monitors network calls using a state machine (Circuit Breaker). If a provider fails, it instantly catches the failure and cascades the request to a secondary provider without breaking the user experience.

### C. Caching & Request Collapsing Module
Interfaces with Redis to store identical responses. If a cache miss occurs and 100 users ask the exact same question simultaneously, it collapses the traffic into a single upstream request to save API costs.

### D. Audit & Telemetry Module
Scrapes precise token usage (`input_tokens`, `output_tokens`) from the upstream JSON responses, calculates the USD cost based on official pricing, and publishes high-resolution metrics to Prometheus.

---

## 5. Core Algorithms & Patterns Used

### 1. Heuristic Classification Algorithm
* **Purpose**: Optimize costs by sending simple requests to cheap models and hard requests to premium models.
* **How it works**: The algorithm calculates a complexity score in **O(N)** time by inspecting prompt length and matching a dictionary of high-complexity keywords (e.g., `code`, `algorithm`, `optimize`, `debug`). 
* **Result**: `HIGH` priority routes to Gemini 2.5 Flash, `MEDIUM/LOW` routes to Groq (Llama 3 models).

### 2. Request Collapsing (Cache Stampede Protection)
* **Purpose**: Prevent cost-multiplication when a viral prompt is submitted by thousands of users at the exact same millisecond before the Redis cache can populate.
* **How it works**: Uses a thread-safe `ConcurrentHashMap`. When a request arrives, the algorithm computes a unique `SHA-256` hash of the prompt. If a network call for that hash is already "in-flight", subsequent requests do not spawn new network calls. Instead, they use Project Reactor's `.share()` operator to subscribe to the exact same memory pointer of the active request.
* **Result**: 1,000 concurrent identical requests = 1 single billed LLM network call.

### 3. Cascading Circuit Breaker Pattern (State Machine)
* **Purpose**: Guarantee 100% uptime even if external APIs crash.
* **How it works**: Implemented using Resilience4j. The algorithm wraps every network call in a state machine (`CLOSED`, `OPEN`, `HALF_OPEN`). If the failure rate of a provider crosses 50%, the circuit trips `OPEN`.
* **Result**: The gateway immediately halts traffic to the dead provider (preventing timeout hangs) and cascades the request down a fallback chain (e.g., Gemini &rarr; Groq Llama 70B &rarr; Groq Llama 8B).

### 4. Distributed Token-Bucket Rate Limiting
* **Purpose**: Protect the gateway from DDoS attacks and abuse.
* **How it works**: An algorithm that stores a "bucket" of allowed requests in Redis per user IP. Every request removes a token. If the bucket is empty, it returns an HTTP 429 error. The bucket refills at a constant mathematical rate over time.

### 5. Reactive Non-Blocking Event Loop (Netty)
* **Purpose**: Massive concurrency scaling.
* **How it works**: Standard Java servers (Tomcat) map 1 network request to 1 OS thread. If 500 requests wait for LLMs to reply, 500 threads block, and the server crashes. Our gateway uses the **Reactor Pattern**. A single event-loop delegates I/O calls to the OS kernel. 
* **Result**: The gateway can handle tens of thousands of concurrent connections using just 4-8 CPU threads and minimal RAM.
