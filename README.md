<h1 align="center">NexusAI Gateway</h1>

<p align="center">
  <strong>The Ultimate Enterprise Cognitive Control Plane & Federated AI Router</strong>
</p>

<p align="center">
  <img alt="License" src="https://img.shields.io/badge/License-Apache_2.0-blue.svg">
  <img alt="Java" src="https://img.shields.io/badge/Java-21-orange.svg">
  <img alt="Spring Boot" src="https://img.shields.io/badge/Spring_Boot-3.3.2-brightgreen.svg">
  <img alt="React" src="https://img.shields.io/badge/React-18-61dafb.svg">
  <img alt="Redis" src="https://img.shields.io/badge/Redis-Reactive-red.svg">
</p>

Welcome to the **NexusAI Gateway** — an advanced, multi-agent AI orchestration proxy and intelligent routing engine designed for enterprise-grade generative AI deployments. 

Unlike traditional static API proxies that use hardcoded `if/else` logic to route AI requests, NexusAI acts as a dynamic **Cognitive Control Plane**. It intercepts LLM requests, analyzes their semantic intent using local neural networks, and routes them across a federated network of upstream providers (OpenAI, Gemini, Groq, Anthropic, Ollama, VertexAI) using **Contextual Bandit Reinforcement Learning (LinUCB)**.

NexusAI guarantees optimal performance by balancing **Cost**, **Quality**, and **Latency** in real-time, completely eliminating vendor lock-in and minimizing AI infrastructure spend.

---

## 📖 Comprehensive Table of Contents
1. [The Enterprise AI Dilemma](#1-the-enterprise-ai-dilemma)
2. [The NexusAI Paradigm Shift](#2-the-nexusai-paradigm-shift)
3. [Algorithmic Intelligence & Reinforcement Learning](#3-algorithmic-intelligence--reinforcement-learning)
4. [Enterprise Security & Cyber-Threat Defense](#4-enterprise-security--cyber-threat-defense)
5. [Multi-Agent Directed Acyclic Graph (DAG) Pipeline](#5-multi-agent-directed-acyclic-graph-dag-pipeline)
6. [Dynamic Vector Semantic Caching](#6-dynamic-vector-semantic-caching)
7. [In-Memory RAG & Knowledge Indexing](#7-in-memory-rag--knowledge-indexing)
8. [Comprehensive Backend Architecture (Folder-by-Folder)](#8-comprehensive-backend-architecture-folder-by-folder)
9. [Getting Started & Installation](#9-getting-started--installation)

---

## 1. The Enterprise AI Dilemma
As enterprises transition from GenAI prototypes to massive production workloads, they face severe operational and architectural bottlenecks:

1. **Catastrophic Vendor Lock-in & Downtime:** Relying entirely on a single LLM provider (e.g., OpenAI) means that when their API goes down, your enterprise application goes down.
2. **Massive Cost Overruns:** Large language models (like GPT-4) are frequently invoked for trivial factual queries that a faster, highly optimized open-source model (like Llama-3-8B via Groq) could answer for 99% less cost.
3. **Severe Data Leaks (PII):** Employees and applications routinely leak sensitive customer data (Emails, SSNs, Credit Cards) to third-party LLM providers, violating HIPAA, GDPR, and enterprise compliance.
4. **Static Routing Limitations:** Hardcoded fallback logic (`if provider A fails, try B`) cannot dynamically adjust to fluctuating API latencies, rate limits, or degraded model quality during peak traffic hours.

---

## 2. The NexusAI Paradigm Shift
NexusAI is built around a **Closed-Loop Feedback Orchestrator**. It does not just forward requests; it *learns* from them.

* **Zero Hardcoded Logic:** The gateway contains absolutely no static string-matching or hardcoded model lists. It dynamically discovers active models via real network endpoints.
* **On-the-Fly Intent Detection:** Incoming prompts are embedded into vectors using a local ONNX model (`all-MiniLM-L6-v2`) in under 10 milliseconds to determine if the task requires deep reasoning, creative writing, or factual retrieval.
* **Continuous Reinforcement Learning:** NexusAI evaluates the output of every single LLM execution and updates its own neural routing weights, constantly optimizing for the cheapest, fastest, and most accurate models.

---

## 3. Algorithmic Intelligence & Reinforcement Learning

### The Federated LinUCB Contextual Bandit Engine
NexusAI replaces static heuristics with a sophisticated multi-armed contextual bandit algorithm. 

1. **Context Feature Extraction:** Every prompt is mapped into a feature vector array representing its characteristics (e.g., Complexity Score [0-1], Token Count, Task Category).
2. **Upper Confidence Bound (UCB) Calculation:** For every available model "arm" (e.g., `gemini-1.5-flash`, `groq-llama-3`), the engine calculates a UCB score representing the expected reward of using that model, combined with an exploration bonus to discover new efficiencies.
3. **Reward Function:**
   After execution, the system calculates a normalized reward metric:
   ```math
   \text{Reward} = (W_q \times \text{Quality}) + (W_l \times \text{LatencyNorm}) + (W_c \times \text{CostNorm})
   ```
4. **Covariance Matrix Update:** The algorithm uses Sherman-Morrison matrix inversions to update the ridge regression weights of the selected model arm. This means if a model suddenly becomes slow or degraded, the engine instantly down-weights it for future requests.

### LLM-as-a-Judge Quality Evaluation
To ensure the bandit learns correctly, NexusAI employs **LLM-as-a-Judge**:
* A background asynchronous thread takes the generated response and sends it to the fastest available secondary LLM in your tenant.
* The Judge grades the response on **Completeness**, **Relevance**, and **Format Compliance**, returning a precise decimal score (e.g., `0.92`).
* If no secondary LLM is available, the system seamlessly falls back to a fast, Java-native **Heuristic Quality Evaluator** that analyzes syntax, verbosity, and structural coherence.

---

## 4. Enterprise Security & Cyber-Threat Defense
NexusAI operates as an impenetrable firewall between your internal microservices and external LLM networks. It deploys **6 Layers of Security Defense**:

| Defense Layer | Implementation Detail | Threat Prevented |
| :--- | :--- | :--- |
| **Layer 1: Edge Authentication** | Validates JWT/HMAC and SHA-256 hashed Gateway API keys at the network edge. Rejects unauthenticated requests instantly. | Unauthorized Access, Session Hijacking |
| **Layer 2: Reactive Rate Limiting** | Implements a strict Token-Bucket algorithm per tenant and IP address powered by Redis. | DDoS Attacks, API Key Scraping, Runaway Billing |
| **Layer 3: PII Data Redaction** | A real-time regex pipeline intercepts the payload and redacts Emails (`[REDACTED_EMAIL]`), Credit Cards, SSNs, and IP addresses. | Data Exfiltration, GDPR/HIPAA Violations |
| **Layer 4: Policy Filtering** | Validates requests against organizational boundary policies. Tenant A cannot use Tenant B's credentials. | Cross-Tenant Data Contamination |
| **Layer 5: Key Encryption at Rest** | Upstream provider API keys (OpenAI, Gemini) are NEVER stored in plaintext. Encrypted via **AES-256-GCM**. | Database Compromise, Insider Threats |
| **Layer 6: Cryptographic Auditing** | Immutable, cryptographically secure logging of all administrative actions and API usage. | Compliance Evasion |

---

## 5. Multi-Agent Directed Acyclic Graph (DAG) Pipeline

NexusAI executes requests asynchronously using a non-blocking WebFlux DAG workflow engine. Agents are resolved into parallel execution tiers.

```mermaid
graph TD
    %% Tier 1
    subgraph Tier 1: Feature Extraction
    C[Context Agent] 
    I[Intent Agent]
    end
    
    %% Tier 2
    subgraph Tier 2: Governance
    P[Policy Agent]
    end
    
    %% Tier 3
    subgraph Tier 3: AI Routing
    R[Routing Agent]
    end
    
    %% Tier 4
    subgraph Tier 4: Evaluation
    Q[Quality Agent]
    end
    
    %% Tier 5
    subgraph Tier 5: Learning
    F[Feedback Agent]
    end
    
    Start((Client Request)) --> C
    Start --> I
    C --> P
    I --> P
    P --> R
    R --> LLM((Upstream Provider Execution))
    LLM --> Q
    Q --> F
    F --> End((Client Response))
```
* **Context Agent:** Estimates prompt complexity and token limits.
* **Intent Agent:** Computes ONNX embeddings to categorize the query.
* **Policy Agent:** Enforces security, rate limits, and budget caps.
* **Routing Agent:** Executes the Bandit Algorithm to select the LLM.
* **Quality Agent:** Runs LLM-as-a-Judge or heuristic evaluation.
* **Feedback Agent:** Closes the loop by updating LinUCB weights.

---

## 6. Dynamic Vector Semantic Caching

To reduce API spend to zero for duplicate queries, NexusAI implements a **100% Dynamic Vector Semantic Cache**. 
Instead of relying on naive string matching or hardcoded arrays (e.g., stripping `"who is"` or `"what is"`), NexusAI approaches caching purely mathematically:

1. The prompt is passed into the `all-MiniLM-L6-v2` neural network to produce a **384-dimensional semantic embedding**.
2. NexusAI calculates the **Cosine Similarity** between the incoming query and all previously cached queries.
3. If the distance threshold is extremely close (e.g., Cosine Similarity $\ge 0.88$), a cache hit is triggered.

**Example Scenario:**
* Prompt A: *"Who is the father of economics?"*
* Prompt B: *"father of economics"*
* Prompt C: *"Can you explain the father of economics to me?"*

Through neural embedding, all three prompts generate nearly identical semantic vectors. Prompt B and Prompt C will automatically trigger a 0-millisecond Cache Hit from Prompt A, saving 100% of the API cost and drastically reducing latency.

---

## 7. In-Memory RAG & Knowledge Indexing

NexusAI provides an out-of-the-box Retrieval-Augmented Generation (RAG) engine.
* **TF-IDF & Cosine Search:** The `InMemoryVectorStore` tokenizes enterprise documents, filters out stop-words, and calculates term-frequency vectors.
* **Context Injection:** When a prompt arrives, the RAG engine searches the enterprise knowledge base, retrieves the most relevant `KnowledgeChunk`s, and injects them directly into the prompt context window before it reaches the external LLM.

---

## 8. Comprehensive Backend Architecture (Folder-by-Folder)

The backend (`src/main/java/com/llm/nexusai_gateway`) consists of **23 architectural packages** and **106 domain-driven files**:

### 🧠 Core Intelligence
* 📁 **`Agent/`**: Multi-Agent System orchestration. Manages the execution lifecycle of autonomous agents.
* 📁 **`Context/`**: Semantic feature extraction. Houses `ContextExtractor` which utilizes the DJL ONNX runtime.
* 📁 **`Decision/`**: The brain of the router. Contains `FederatedLinUcbEngine`, `WeightedDecisionEngine`, and the `RoutingEngineManager`.
* 📁 **`Evaluation/`**: Output verification. Houses `LlmAsAJudgeEvaluator` and `HeuristicQualityEvaluator`.
* 📁 **`Reward/`**: Normalization math. Transforms raw latency, cost, and quality metrics into a bounded `[0.0, 1.0]` reward function.

### 🔌 External Provider Integration
* 📁 **`Provider/`**: Implementations for `OpenAiProvider`, `GeminiProvider`, `GroqProvider`, `ClaudeProvider`, and `OllamaProvider`. Includes `ModelDiscoveryService` for active network endpoint verification.
* 📁 **`Provider/Auth/`**: Custom HTTP interceptors implementing `BearerAuthStrategy`, `AwsSigV4AuthStrategy`, and `GeminiAuthStrategy`.

### 🛡️ Security & Governance
* 📁 **`Security/`**: AES-256-GCM `SecretEncryptionService`, JWT utilities, `PiiRedactionService`, and API Key management.
* 📁 **`Policy/`**: Enforces strict `PolicyFilter` rules to isolate multi-tenant operations.
* 📁 **`Governance/`**: Daily budget tracking (`BudgetService`) to prevent financial runaway.
* 📁 **`Health/`**: Circuit breaking mechanisms (`ProviderHealthMonitor`) that temporarily quarantine failing models.

### ⚙️ Performance & Persistence
* 📁 **`Service/`**: The core orchestration layer. Houses the `ChatOrchestrationService`, `ResponseCacheService`, and `StreamingOrchestrationService` (SSE).
* 📁 **`Rag/`**: Vector similarity search components (`InMemoryVectorStore`).
* 📁 **`Repository/`**: Spring Data JPA interfaces bridging domain entities to the SQL database.

### 📡 APIs & Telemetry
* 📁 **`Controller/`**: WebFlux reactive REST controllers exposing `/api/chat`, `/v1/chat/completions` (OpenAI proxy), and `/api/cache/stats`.
* 📁 **`Telemetry/`**: Request tracing and the `TrafficBroadcaster`, which sinks live operational metrics to the React frontend dashboard via Server-Sent Events.
* 📁 **`Benchmark/`**: Built-in synthetic load testing suites to benchmark model concurrency.

---

## 9. Getting Started & Installation

### Prerequisites
* **Java 21+** (Optimized for ZGC/G1GC)
* **Maven 3.9+**
* **Node.js 18+** (For the React/Vite dashboard)
* **Redis** (Optional: Used for distributed rate limiting. Gateway falls back to `ConcurrentHashMap` automatically if offline).

### 1. Build and Run the Gateway Backend
```bash
# Clone the repository
git clone https://github.com/your-org/nexusai-gateway.git
cd nexusai-gateway

# Set up environment variables (Or use the UI later)
export OPENAI_API_KEY="sk-..."
export GROQ_API_KEY="gsk_..."

# Run the Spring Boot WebFlux Application
mvn spring-boot:run
```
*The backend proxy will start on `http://localhost:8080`.*

### 2. Launch the Control Plane Dashboard
```bash
cd frontend
npm install
npm run dev
```
*Access the enterprise dashboard at `http://localhost:5173` to view live telemetry, configure LinUCB parameters, and manage API keys.*

### 3. Test the Proxy (OpenAI Compatible)
You can point any standard OpenAI SDK directly to the NexusAI Gateway!
```bash
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer nx_live_YOUR_GATEWAY_KEY" \
  -d '{
    "messages": [{"role": "user", "content": "Explain quantum computing in one sentence."}]
  }'
```

---

<p align="center">
  Built with ❤️ by the open-source GenAI community. 
</p>
