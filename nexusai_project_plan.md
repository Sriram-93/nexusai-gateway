# NexusAI Gateway — Complete 2-Phase Master Plan

**Student:** Sriram (Final Year AIDS, Kongu Engineering College)  
**Goal:** Google SDE role (Backend focused, Spring Boot stack)  
**GitHub:** [Sriram-93](https://github.com/Sriram-93) | **LinkedIn:** [linkedin.com/in/sriram-m-710116311/](https://linkedin.com/in/sriram-m-710116311/)

---

## Vision

> NexusAI Gateway is a production-grade AI infrastructure platform — a single intelligent abstraction layer over all AI providers that governs traffic, routes intelligently, ensures reliability, evaluates quality, detects anomalies, optimizes cost, and evolves into an autonomous agentic control plane.

---

# PHASE 1 — Intelligent LLM Gateway (Sem 7)

**Goal:** Production-ready, observable, resilient gateway any application can use to route requests across multiple LLM providers automatically.

---

## 1. Provider Abstraction Layer

### Providers
| Provider | Status |
| :--- | :--- |
| Google Gemini 2.5 Flash | ✅ Implemented |
| Groq Llama 3.3 70B | ✅ Implemented |
| Groq Llama 3.1 8B | ✅ Implemented |
| OpenAI GPT-4o | ⚠️ Stub — to implement |
| Anthropic Claude | ⚠️ Stub — to implement |
| Ollama (local, zero-cost) | 🔜 To implement |
| Mistral | 🔜 Phase 2 |
| DeepSeek | 🔜 Phase 2 |
| Azure OpenAI | 🔜 Phase 2 |
| AWS Bedrock | 🔜 Phase 2 |

### Design
- `LlmProvider` interface: `Mono<String> chat(String message, String model)` ✅
- `ProviderRegistry` bean: Spring DI list → Map<providerName, LlmProvider> 🔜
- Applications never communicate directly with providers ✅

---

## 2. Intelligent Routing Engine

### Content-Based Auto Classification ✅
- HIGH complexity → Gemini 2.5 Flash (code, algorithms, math, long prompts)
- MEDIUM complexity → Groq Llama 3.3 70B (explanations, questions, jokes)
- LOW complexity → Groq Llama 3.1 8B (greetings, simple QA)

### Routing Dimensions (Phase 1 Rule-Based)
- [ ] **Cost-aware routing** — cheapest provider for simple requests
- [ ] **Latency-aware routing** — track rolling avg latency per provider, prefer fastest
- [ ] **Context length routing** — detect long prompts (>4k tokens) → route to long-context model
- [ ] **Availability-aware routing** — dynamic provider weight based on circuit breaker state
- Configured via `application.properties` (routing.model.high/medium/low.*) ✅

---

## 3. Fallback Management

| Feature | Status |
| :--- | :--- |
| Gemini → Groq 70B fallback | ✅ Implemented |
| Groq 70B → Groq 8B fallback | ✅ Implemented |
| Circuit Breaker OPEN → Instant Fallback | ✅ Implemented |
| Multi-hop fallback chain (3+ providers) | 🔜 To implement |
| Exponential backoff retry (currently flat `.retry(2)`) | 🔜 To implement |
| Graceful degradation (Redis cached response if all fail) | 🔜 To implement |

---

## 4. Rate Limiting

- [ ] **User-level rate limiting** — max N requests per minute per userId
- [ ] **Tenant-level rate limiting** — max N requests per minute per tenantId
- [ ] **Provider-level rate limiting** — respect Gemini (15 RPM), Groq (30 RPM) limits
- [ ] **Token Bucket algorithm** — Bucket4j + Redis
- [ ] **Sliding Window algorithm** — Redis sorted set implementation
- [ ] **Leaky Bucket algorithm** — constant output rate, absorbs burst traffic
- Returns HTTP `429 Too Many Requests` with `Retry-After` header

---

## 5. Backpressure Management

| Feature | Status |
| :--- | :--- |
| Resilience4j Circuit Breakers (per provider) | ✅ Implemented |
| AIMD Backpressure Engine | 🔜 To implement |
| Request Queueing (in-memory priority queue) | 🔜 To implement |
| Priority Scheduling (HIGH before MEDIUM before LOW) | 🔜 To implement |
| Traffic Shaping (token bucket on egress) | 🔜 To implement |
| Load Shedding (drop LOW priority if overloaded) | 🔜 To implement |
| Admission Control (reject at entry if system > threshold) | 🔜 To implement |

**AIMD Logic:**
- Concurrency limit starts at N
- Every successful response → limit += 1 (Additive Increase)
- Every error/timeout → limit = limit / 2 (Multiplicative Decrease)

---

## 6. Observability

| Feature | Status |
| :--- | :--- |
| Custom UI Visualizer Dashboard | ✅ Implemented |
| PostgreSQL Audit Logging (`RequestLog` entity) | 🔜 To implement |
| Prometheus metrics (`/actuator/prometheus`) | 🔜 To implement |
| Grafana dashboards | 🔜 To implement |
| Token usage tracking per request | 🔜 To implement |
| Cost tracking per request (provider pricing table) | 🔜 To implement |
| OpenTelemetry distributed tracing | 🔜 Phase 2 |

**Custom Prometheus Metrics to Expose:**
- `nexusai_requests_total` (by provider, model, priority)
- `nexusai_latency_ms` (histogram by provider)
- `nexusai_circuit_breaker_state` (gauge: CLOSED=0, OPEN=1)
- `nexusai_tokens_used_total` (counter by provider)
- `nexusai_cost_usd_total` (counter by provider)
- `nexusai_fallbacks_total` (counter by source→target)

---

## 7. Caching

- [ ] **Redis Response Cache** — cache identical prompt+model responses (TTL: 1 hour)
- [ ] **Cache invalidation strategy** — LRU eviction + manual flush endpoint
- Semantic cache → Phase 2

---

## 8. Security (Phase 1 Basics)

- [ ] **API Key validation header** (`X-API-Key`) — simple gateway-level auth
- Full JWT + RBAC → Phase 2

---

## Phase 1 Tech Stack

| Layer | Technology |
| :--- | :--- |
| Language | Java 21 |
| Framework | Spring Boot 3 + WebFlux (Netty) |
| Resilience | Resilience4j 2.x |
| Rate Limiting | Bucket4j + Redis |
| Database | PostgreSQL + Spring Data JPA |
| Caching | Redis |
| Metrics | Prometheus + Micrometer |
| Visualization | Grafana + Custom HTML Dashboard |
| Load Testing | k6 |

---

## Phase 1 — Complete Build Checklist

### ✅ Already Built
- [x] Spring Boot 3 + WebFlux project setup
- [x] `ChatRequest` & `ChatResponse` models
- [x] `LlmProvider` interface (Strategy Pattern)
- [x] `GeminiProvider` (WebClient + retry)
- [x] `GroqProvider` (WebClient + retry)
- [x] `ChatController` POST `/api/chat`
- [x] `RoutingService` — content classifier (HIGH/MEDIUM/LOW)
- [x] `Priority` enum + `RouteDecision` record
- [x] Gemini → Groq fallback chain
- [x] Resilience4j Circuit Breaker (per-provider)
- [x] Glassmorphic UI Visualizer (auto-updating routing path)
- [x] **C. PostgreSQL Audit Logging** — `RequestLog` JPA entity + `LoggingService`
- [x] **D. Token & Cost Tracking** — estimate tokens, store cost per request
- [x] **A. Complete Provider Stubs** — OpenAI, Claude, Ollama implementing `LlmProvider`
- [x] **B. Provider Registry** — `ProviderRegistry.java` bean

### 🔜 To Build
- [ ] **E. Redis Response Cache** — cache + invalidation + TTL
- [ ] **F. Redis Rate Limiter** — Bucket4j, user/tenant/provider levels, all 3 algorithms
- [ ] **G. AIMD Backpressure Engine** — `BackpressureService` with AIMD logic
- [ ] **H. Load Shedding + Admission Control** — reject LOW priority when system overloaded
- [ ] **I. Multi-hop Fallback + Exponential Backoff** — 3+ provider chain with backoff
- [ ] **J. Graceful Degradation** — serve stale cached response if all providers fail
- [ ] **K. Cost + Latency Routing** — `RoutingService` extension for cost/latency weights
- [ ] **L. Prometheus + Grafana** — all 6 custom metrics + Grafana dashboard JSON
- [ ] **M. k6 Load Test Script** — 100–1000 VUs firing at `/api/chat`
- [ ] **N. API Key Header Auth** — basic `X-API-Key` gateway auth

---

# PHASE 2 — Agentic Control Plane (Sem 8)

**Goal:** Evolve from rule-based routing to an AI-driven autonomous control plane with agents, async processing, full security, and Kubernetes deployment.

---

## 1. Expanded Provider Layer

- [ ] **Mistral Provider** — via Mistral API
- [ ] **DeepSeek Provider** — via DeepSeek API
- [ ] **Azure OpenAI Provider** — via Azure REST API
- [ ] **AWS Bedrock Provider** — via AWS SDK

---

## 2. Context Optimization Engine

- [ ] **Prompt Summarization** — summarize long system prompts before sending to provider
- [ ] **Semantic Chunking** — split large documents into meaningful chunks
- [ ] **Memory Pruning** — remove irrelevant conversation history turns
- [ ] **Context Window Optimization** — dynamically trim to fit model's max context
- [ ] **Token Compression** — LLMLingua-style compression to reduce token count

---

## 3. Intelligent Routing Agent (LangChain4j ReAct)

Replaces static rule-based routing with AI-driven reasoning:

```
Routing Agent Loop:
  THOUGHT: Analyze prompt type, length, complexity
  ACTION: Query provider health from Redis
  OBSERVATION: Gemini latency 2s avg, Groq 400ms avg
  THOUGHT: User budget is medium, quality required is high
  ACTION: Select Gemini (quality > cost for this request)
  FINAL: Execute on Gemini
```

- [ ] **ReAct Routing Agent** — LangChain4j tool-calling loop
- [ ] **Provider Health Tool** — Redis-backed real-time health scores
- [ ] **Cost Estimation Tool** — estimate cost before execution
- [ ] **Quality History Tool** — query past quality scores from PostgreSQL
- [ ] **Auto Tool Selection** — agent decides whether to use Web Search, RAG, Code Execution, or DB query
- [ ] **Quality-aware routing prediction** — predict expected quality before selecting provider

---

## 4. Quality Evaluation Agent

- [ ] **LLM-as-Judge scoring** — use a fast model to evaluate response quality
- [ ] **Relevance scoring** — does the response answer the question?
- [ ] **Hallucination detection** — cross-reference factual claims
- [ ] **Toxicity detection** — flag harmful content
- [ ] **Format Compliance check** — validate structured output (JSON, markdown)
- [ ] **Auto-retry on low quality** — if score < threshold, retry with different provider
- [ ] **RAGAS-style evaluation framework** — faithfulness, answer relevancy, context recall

---

## 5. Anomaly Detection Agent

- [ ] **Moving average Z-score** — detect latency spikes per provider
- [ ] **Cost spike detection** — alert when cost/hour exceeds baseline × 2σ
- [ ] **Prompt injection detection** — regex + LLM-based detection
- [ ] **Token abuse detection** — flag abnormally large requests
- [ ] **Provider instability detection** — error rate spike triggers alert
- [ ] **Webhook/Slack alerts** — automated notifications on anomaly

---

## 6. Security Agent + Full Security Layer

- [ ] **JWT Authentication** — stateless bearer token on all `/api/*` endpoints
- [ ] **RBAC** — roles: `ADMIN`, `TENANT`, `USER`, method-level security
- [ ] **Multi-Tenancy** — tenant-scoped routing policies, rate limits, audit logs
- [ ] **Full Audit Logs** — every request, agent decision, quality score, alert with userId + tenantId
- [ ] **Encryption at rest** — encrypted PostgreSQL columns for sensitive fields
- [ ] **Encryption in transit** — TLS/HTTPS enforced
- [ ] **API Key Management** — generate, rotate, revoke per-tenant API keys
- [ ] **Prompt Injection Detection** — Security Agent monitors all inputs

---

## 7. Optimization Agent

- [ ] **Cost Reduction** — identify equivalent cheaper providers for repeated request patterns
- [ ] **Cache Optimization** — promote frequently requested prompts to warm cache
- [ ] **Semantic Cache** — Redis + vector embeddings, serve semantically similar cached responses
- [ ] **Cache invalidation strategy** — TTL + LRU + manual flush

---

## 8. Observability Agent + Full Observability

- [ ] **OpenTelemetry distributed tracing** — trace every request end-to-end
- [ ] **Jaeger integration** — visualize traces
- [ ] **Observability Agent** — automated incident analysis from metrics anomalies
- [ ] **Grafana Phase 2 dashboards** — quality scores, anomaly alerts, agent decisions, cost per tenant

---

## 9. Coordinator Agent

- [ ] Orchestrates Routing, Quality, Anomaly, Security, and Optimization agents
- [ ] Resolves conflicts between agent recommendations
- [ ] Implements priority order: Security → Quality → Cost → Latency

---

## 10. Self-Learning Layer

- [ ] **Adaptive routing policies** — update provider weights based on historical performance
- [ ] **Feedback-driven model selection** — learn from user thumbs up/down signals
- [ ] **Cost/latency/quality history** — PostgreSQL + time-series analysis
- [ ] **Auto-tuning circuit breaker thresholds** — adjust failure-rate-threshold per provider dynamically

---

## 11. Kafka Async Pipeline

| Topic | Purpose |
| :--- | :--- |
| `llm-requests` | Ingest all chat requests as events |
| `llm-responses` | Publish completed AI responses |
| `llm-quality-scores` | Quality eval results |
| `llm-anomalies` | Anomaly detection alerts |
| `llm-failed` | Dead letter queue for failed requests |

- [ ] Controller returns `202 Accepted + jobId` immediately
- [ ] Consumer group `nexusai-workers` throttled to provider rate limits
- [ ] Client receives answer via WebSocket / SSE push

---

## 12. Architecture Deliverables

- [ ] High-Level System Architecture diagram
- [ ] Microservice Architecture diagram
- [ ] Database Schema Design (PostgreSQL tables + Redis key structure)
- [ ] Kafka Event Architecture diagram
- [ ] Agent Architecture + ReAct loop sequence diagram
- [ ] Routing Algorithm documentation (decision tree + weight matrix)
- [ ] Backpressure Design document (AIMD state machine)
- [ ] Quality Evaluation Framework documentation
- [ ] Anomaly Detection Architecture (Z-score thresholds, alert rules)
- [ ] Security Architecture (JWT flow, RBAC matrix, encryption strategy)
- [ ] Kubernetes Deployment Design (pod topology, HPA config)
- [ ] Scalability Strategy (horizontal scaling, partitioning, sharding)
- [ ] Sequence Diagrams (request flow, fallback flow, agent ReAct loop)
- [ ] Future Research Roadmap (federated learning, RL routing, edge deployment)

---

## 13. Deployment

- [ ] **Docker** — containerize Spring Boot gateway
- [ ] **Docker Compose** — local dev: gateway + PostgreSQL + Redis + Kafka + Prometheus + Grafana
- [ ] **Kubernetes manifests** — Deployment, Service, ConfigMap, Secret, HPA
- [ ] **Helm Chart** — parameterized K8s deployment
- [ ] **GitHub Actions CI/CD** — auto build + test + push Docker image on PR merge

---

## Phase 2 Tech Stack (additions)

| Layer | Technology |
| :--- | :--- |
| AI Orchestration | LangChain4j (ReAct Agents) |
| Async Messaging | Apache Kafka |
| Security | Spring Security 6 + JWT (JJWT) |
| Tracing | OpenTelemetry + Jaeger |
| Containerization | Docker + Docker Compose |
| Orchestration | Kubernetes + Helm |
| CI/CD | GitHub Actions |
| Additional Providers | Mistral, DeepSeek, Azure OpenAI, AWS Bedrock |

---

## Complete Build Timeline

```
PHASE 1 — Sem 7
├── ✅ GeminiProvider + GroqProvider
├── ✅ Content-based Smart Router
├── ✅ Fallback Chain
├── ✅ Circuit Breaker (Resilience4j)
├── ✅ UI Visualizer Dashboard
├── 🔜 Provider Stubs + Registry
├── 🔜 PostgreSQL Audit Logging + Token/Cost Tracking
├── 🔜 Redis Cache + Rate Limiter (3 algorithms)
├── 🔜 AIMD Backpressure + Load Shedding + Admission Control
├── 🔜 Multi-hop Fallback + Exponential Backoff + Graceful Degradation
├── 🔜 Cost + Latency Aware Routing
├── 🔜 Prometheus + Grafana (6 custom metrics)
└── 🔜 k6 Load Testing

PHASE 2 — Sem 8
├── 🔜 Mistral + DeepSeek + Azure OpenAI + AWS Bedrock Providers
├── 🔜 Context Optimization Engine
├── 🔜 LangChain4j Routing Agent (ReAct)
├── 🔜 Quality Evaluation Agent (LLM-as-Judge, RAGAS)
├── 🔜 Anomaly Detection Agent (Z-score, Alerts)
├── 🔜 Security Agent (Prompt Injection)
├── 🔜 Optimization Agent (Cost + Semantic Cache)
├── 🔜 Observability Agent (OpenTelemetry + Jaeger)
├── 🔜 Coordinator Agent
├── 🔜 Self-Learning Layer
├── 🔜 Kafka Async Pipeline (5 topics)
├── 🔜 JWT + RBAC + Multi-Tenancy + Encryption + API Key Mgmt
├── 🔜 Architecture Deliverables (14 docs/diagrams)
└── 🔜 Docker + Kubernetes + Helm + GitHub Actions CI/CD
```

---

## Interview Story (Google SDE)

> "I built NexusAI Gateway — a production-grade LLM router in Spring Boot WebFlux that handles thousands of concurrent requests non-blocking on Netty event loops.
>
> In Phase 1: I built a content classifier that auto-routes to Gemini for complex tasks, Groq 70B for reasoning, and Groq 8B for simple queries. I integrated Resilience4j circuit breakers that trip within 3 failures and instantly cascade to fallback providers. I implemented AIMD backpressure and Redis-backed rate limiting to prevent provider overload. Everything is instrumented with Prometheus and visualized in Grafana.
>
> In Phase 2: I replaced static routing rules with a LangChain4j ReAct agent that reasons over provider health, cost, latency, and quality history before each routing decision. I added a Quality Evaluation Agent using LLM-as-Judge scoring that auto-retries with a different provider if quality falls below threshold. I built an Anomaly Detection Agent using Z-score analysis on rolling latency windows. I deployed the full system on Kubernetes with horizontal auto-scaling via HPA."
