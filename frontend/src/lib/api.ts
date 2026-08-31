/**
 * NexusAI Gateway — Centralized API Client
 *
 * All HTTP calls go through this module.
 * Base URL is picked from VITE_API_BASE env or defaults to the Vite proxy (/api → localhost:8080).
 */

// ─── Base HTTP ───────────────────────────────────────────────────────────────

export function getBaseUrl(): string {
  if (typeof import.meta !== "undefined" && (import.meta as any).env?.VITE_API_BASE) {
    return (import.meta as any).env.VITE_API_BASE;
  }
  if (typeof import.meta !== "undefined" && (import.meta as any).env?.PROD) {
    return "";
  }
  return "http://localhost:8080";
}

export function getJwt(): string | null {
  if (typeof window === "undefined") return null;
  return sessionStorage.getItem("nexus_jwt");
}

// We no longer store the API key in the frontend session for security reasons.
// Sandbox endpoints rely entirely on the JWT.

export function getTenantId(): string | null {
  if (typeof window === "undefined") return null;
  return sessionStorage.getItem("nexus_tenant_id");
}

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
    this.name = "ApiError";
  }
}

export async function authFetch(path: string, options: RequestInit = {}): Promise<Response> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...((options.headers as Record<string, string>) ?? {}),
  };
  const jwt = getJwt();
  if (jwt) headers["Authorization"] = `Bearer ${jwt}`;
  const gatewayKey = typeof window !== "undefined" ? sessionStorage.getItem("nexus_api_key") : null;
  if (gatewayKey && !headers["X-API-Key"] && (path.startsWith("/v1/") || path.startsWith("/api/chat"))) {
    headers["X-API-Key"] = gatewayKey;
  }
  const url = path.startsWith("http") ? path : `${getBaseUrl()}${path}`;
  return fetch(url, { ...options, headers });
}

async function request<T>(
  path: string,
  options: RequestInit = {}
): Promise<T> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...((options.headers as Record<string, string>) ?? {}),
  };

  const jwt = getJwt();
  if (jwt) headers["Authorization"] = `Bearer ${jwt}`;

  const gatewayKey = typeof window !== "undefined" ? sessionStorage.getItem("nexus_api_key") : null;
  if (gatewayKey && !headers["X-API-Key"] && (path.startsWith("/v1/") || path.startsWith("/api/chat"))) {
    headers["X-API-Key"] = gatewayKey;
  }



  const url = `${getBaseUrl()}${path}`;
  const res = await fetch(url, { ...options, headers });

  if (!res.ok) {
    let message = `HTTP ${res.status}`;
    try {
      const body = await res.json();
      message = body.error ?? body.message ?? message;
    } catch {
      /* ignore */
    }
    if (res.status === 429) throw new ApiError(429, "Rate limit exceeded. Please wait before retrying.");
    if (res.status === 402) throw new ApiError(402, "Budget exhausted. Upgrade your plan or top-up.");
    if (res.status === 401 && path.startsWith("/api/auth/me")) {
      if (typeof window !== "undefined") {
        sessionStorage.removeItem("nexus_jwt");
        sessionStorage.removeItem("nexus_api_key");
        sessionStorage.removeItem("nexus_tenant_id");
        window.location.href = "/";
      }
      throw new ApiError(401, "Session expired. Please log in again.");
    }
    throw new ApiError(res.status, message);
  }

  const text = await res.text();
  if (!text) return undefined as unknown as T;
  return JSON.parse(text) as T;
}

// ─── TypeScript Types (derived from backend Java DTOs) ───────────────────────

export interface GlobalMetrics {
  totalRequests: number;
  totalCostUsd: number;
  avgLatencyMs: number;
  activeAgents: number;
  activeStrategy: string;
  activeEngine: string;
  rewardTier: string;
  enabledArmCount: number;
  activeTeams?: number;
  teamMembersCount?: number;
  dailyBudgetUsd?: number;
}

export interface ModelHealth {
  armKey: string;
  provider: string;
  model: string;
  cbState: string; // CLOSED | OPEN | HALF_OPEN | UNKNOWN
  healthScore: number | null;
  avgQuality: number | null;
  avgLatencyMs: number | null;
  availability: number | null;
  failureRate: number | null;
  totalRequests: number;
  hasData: boolean;
}

export interface ActivityLog {
  id: number;
  timestamp: string | null;
  provider: string;
  model: string;
  strategy: string;
  latencyMs: number;
  costUsd: number;
  tokens: number;
  status: string;
}

export interface StreamEvent {
  id: number;
  timestamp: string | null;
  provider: string;
  model: string;
  latencyMs: number;
  costUsd: number;
  status: string;
  promptSnippet: string;
}

export interface ArmState {
  armKey: string;
  healthScore: number;
  avgQuality: number;
  avgLatencyMs: number;
  availability: number;
  failureRate: number;
  totalRequests: number;
  successCount: number;
  lastUpdatedMs: number;
}

export interface LearningState {
  activeStrategy: string;
  activeEngine: string;
  rewardTier: string;
  armStates: ArmState[];
  totalArmsTracked: number;
}

export interface AgentInfo {
  name: string;
  order: number;
  dependencies: string[];
  requiredInputs: string[];
  producedOutputs: string[];
}

export interface ProviderSummary {
  id: number;
  displayName: string;
  slug: string;
  type: string;
  enabled: boolean;
  lastDiscoveredAt: string | null;
  enabledModelCount: number;
  hasKey: boolean;
}

export interface ProviderStatus {
  hasProviders: boolean;
  readyToChat: boolean;
  connectedCount: number;
}

export interface ModelSummary {
  modelId: string;
  armKey: string;
  displayName: string;
  enabled: boolean;
  inputPricePer1M: number;
  outputPricePer1M: number;
  contextWindowTokens: number;
  estimatedLatencyMs: number;
  pricingVerified: boolean;
}

export interface ChatRequest {
  message: string;
  userId?: string;
  tenantId?: string;
  priority?: "HIGH" | "MEDIUM" | "LOW";
  provider?: string;
  model?: string;
  pipelineName?: string;
}

export interface ChatResponse {
  answer: string;
  provider: string;
  latencyMs: number;
  activeEngine: string;
  routingReason: string;
  rewardScore: number;
  armScores: Record<string, number> | null;
}

export interface RequestLog {
  id: number;
  tenantId: string;
  userId: string;
  prompt: string;
  response: string;
  provider: string;
  model: string;
  priority: string;
  latencyMs: number;
  tokenUsage: number;
  costUsd: number;
  timestamp: string;
  status: string;
}

export interface ProviderRegistrationRequest {
  displayName: string;
  slug: string;
  type: "OPENAI_COMPATIBLE" | "GOOGLE" | "ANTHROPIC" | "AWS_BEDROCK" | "AZURE_OPENAI";
  baseUrl?: string;
  apiKey?: string;
  region?: string;
}

export interface UserContextValue {
  token: string | null;
  role: "SOLO" | "ORG_ADMIN" | "TEAM_LEAD" | "TEAM_MEMBER" | null;
  tenantId: string | null;
  email: string | null;
  orgId: string | null;
  setAuth: (
    token: string,
    role: string,
    tenantId: string,
    email: string,
    orgId: string,
  ) => void;
  logout: () => void;
}

export interface TeamMember {
  userId: string;
  email: string;
  role: string;
  joinedAt: string;
  totalRequests?: number;
}

export interface TeamSummary {
  id: string;
  name: string;
  description: string;
  leadEmail: string;
  leadUserId: string;
  active: boolean;
  createdAt: string;
  memberCount: number;
  tenantId: string;
  hasKey: boolean;
  keyActive: boolean;
  dailyBudgetUsd?: number;
  members?: TeamMember[];
  totalRequests?: number;
  totalCostUsd?: number;
  avgLatencyMs?: number;
}

export interface SignupRequest {
  tier: "SOLO" | "ADMINISTRATION";
  organizationName?: string;
  email: string;
  password: string;
}

export interface SignupResponse {
  message: string;
  token: string;
  tenantId: string;
  apiKey: string;
}

export interface TenantProvisionResponse {
  message: string;
  tenantId: string;
  apiKey: string;
}

export interface PipelineDefinitions {
  DEFAULT: string[];
  GREETING: string[];
  SECURITY_FAST_PATH: string[];
  CODING: string[];
}

// ─── Dashboard API ────────────────────────────────────────────────────────────

export const dashboardApi = {
  getMetrics: () => request<GlobalMetrics>("/api/dashboard/metrics"),
  getModels: () => request<ModelHealth[]>("/api/dashboard/models"),
  getActivity: () => request<ActivityLog[]>("/api/dashboard/activity"),
  getLearning: () => request<LearningState>("/api/dashboard/learning"),
  getAgents: () => request<AgentInfo[]>("/api/dashboard/agents"),
  switchRoutingStrategy: (strategy: string, weights?: Record<string, number>) =>
    request<{ status: string; activeStrategy: string; activeEngine: string }>(
      "/api/dashboard/settings/routing",
      {
        method: "PATCH",
        body: JSON.stringify({ strategy, ...(weights ? { weights } : {}) }),
      },
    ),
  updateBanditHyperparameters: (alpha: number) =>
    request<{ updated: boolean; alpha: number; activeEngine: string; message: string }>(
      "/api/dashboard/settings/bandit",
      {
        method: "PATCH",
        body: JSON.stringify({ alpha }),
      },
    ),
  tripCircuitBreaker: (provider: string) =>
    request<{ provider: string; cbState: string; message: string }>(
      `/api/dashboard/circuit-breaker/${provider}/trip`,
      { method: "POST" },
    ),
  resetCircuitBreaker: (provider: string) =>
    request<{ provider: string; cbState: string; message: string }>(
      `/api/dashboard/circuit-breaker/${provider}/reset`,
      { method: "POST" },
    ),
};

// ─── Providers API ────────────────────────────────────────────────────────────

export const providersApi = {
  listProviders: () => request<ProviderSummary[]>("/api/providers"),
  registerProvider: (req: ProviderRegistrationRequest) =>
    request<{ providerId: number; slug: string; status: string; modelsDiscovered: number; message: string }>(
      "/api/providers",
      { method: "POST", body: JSON.stringify(req) },
    ),
  triggerDiscovery: (slug: string) =>
    request<{ slug: string; newModelsFound: number; message: string }>(
      `/api/providers/${slug}/discover`,
      { method: "POST" },
    ),
  setEnabled: (slug: string, enabled: boolean) =>
    request<{ slug: string; enabled: boolean }>(
      `/api/providers/${slug}/enabled`,
      { method: "PATCH", body: JSON.stringify({ enabled }) },
    ),
  updateCredentials: (slug: string, apiKey: string) =>
    request<{ message: string }>(
      `/api/providers/${slug}/credentials`,
      { method: "PATCH", body: JSON.stringify({ apiKey }) },
    ),
  listModels: (slug: string) =>
    request<ModelSummary[]>(`/api/providers/${slug}/models`),
  enableModel: (slug: string, modelId: string) =>
    request<{ armKey: string; enabled: boolean; message: string }>(
      `/api/providers/${slug}/models/${encodeURIComponent(modelId)}/enable`,
      { method: "PATCH" },
    ),
  disableModel: (slug: string, modelId: string) =>
    request<{ armKey: string; enabled: boolean }>(
      `/api/providers/${slug}/models/${encodeURIComponent(modelId)}/disable`,
      { method: "PATCH" },
    ),
  registerModel: (slug: string, model: {
    modelId: string;
    displayName?: string;
    inputPricePer1M?: number;
    outputPricePer1M?: number;
    contextWindowTokens?: number;
    estimatedLatencyMs?: number;
    enabled?: boolean;
  }) =>
    request<{ armKey: string; enabled: boolean }>(
      `/api/providers/${slug}/models`,
      { method: "POST", body: JSON.stringify(model) },
    ),
  syncPricing: () =>
    request<{ message: string; unpricedModels: string[] }>(
      "/api/providers/pricing/sync",
      { method: "POST" },
    ),
  listUnpricedModels: () => request<string[]>("/api/providers/pricing/unverified"),
  getStatus: () => request<ProviderStatus>("/api/providers/status"),
  discoverAll: () =>
    request<{ message: string }>("/api/providers/discover-all", { method: "POST" }),
  deleteProvider: (slug: string) =>
    request<{ message: string; modelsDisabled: number }>(
      `/api/providers/${slug}`,
      { method: "DELETE" },
    ),
};

// ─── Chat API ─────────────────────────────────────────────────────────────────

export const chatApi = {
  chat: (req: ChatRequest) =>
    request<ChatResponse>("/api/chat", {
      method: "POST",
      body: JSON.stringify(req),
    }),
  agentChat: (req: ChatRequest) =>
    request<{
      answer: string;
      latencyMs: number;
      intent: any;
      context: any;
      policy: any;
      routing: any;
      quality: any;
    }>("/api/agent/chat", {
      method: "POST",
      body: JSON.stringify(req),
    }),
  getLogs: () => request<RequestLog[]>("/api/logs"),
  getReputations: () => request<Record<string, unknown>>("/api/reputations"),
  clearCache: () => request<void>("/api/cache/clear", { method: "POST" }),
};

// ─── Auth API ─────────────────────────────────────────────────────────────────

export const authApi = {
  signup: (req: SignupRequest) =>
    request<SignupResponse>("/api/auth/signup", {
      method: "POST",
      body: JSON.stringify(req),
    }),
  login: (req: Omit<SignupRequest, "tier" | "organizationName">) =>
    request<SignupResponse>("/api/auth/login", {
      method: "POST",
      body: JSON.stringify(req),
    }),
};

// ─── Tenant API ───────────────────────────────────────────────────────────────

export const tenantApi = {
  getTenant: (tenantId: string) =>
    request<any>(`/api/tenants/${tenantId}`),
  generateKey: (tenantId: string) =>
    request<{ apiKey: string }>(`/api/tenant/${tenantId}/generate-key`, {
      method: "POST",
    }),
  revokeKey: (tenantId: string) =>
    request<{ message: string }>(`/api/tenant/${tenantId}/key`, {
      method: "DELETE",
    }),
  provision: (config: {
    tenantId: string;
    tenantName?: string;
    dailyBudgetUsd?: number;
    maxRequestsPerMinute?: number;
  }) =>
    request<TenantProvisionResponse>("/api/tenant/signup", {
      method: "POST",
      body: JSON.stringify(config),
    }),
  updatePolicy: (tenantId: string, rewardWeights: number[]) =>
    request<string>(`/api/tenant/${tenantId}/policy`, {
      method: "PUT",
      body: JSON.stringify(rewardWeights),
    }),
  updateCredentials: (tenantId: string, providerKeys: Record<string, string>) =>
    request<string>(`/api/tenant/${tenantId}/credentials`, {
      method: "POST",
      body: JSON.stringify(providerKeys),
    }),
};

// ─── Pipeline API ─────────────────────────────────────────────────────────────

export const pipelineApi = {
  getAgents: () => request<AgentInfo[]>("/api/pipeline/agents"),
  getDefinitions: () => request<PipelineDefinitions>("/api/pipeline/definitions"),
};

// ─── Admin API ────────────────────────────────────────────────────────────────

export const adminApi = {
  getTeamLogs: () => request<RequestLog[]>("/api/admin/logs"),
  getMembers: () => request<Array<{ id: string; email: string; role: string; teamId?: string; teamName?: string }>>("/api/admin/members"),
  getMemberLogs: (userId: string) => request<RequestLog[]>(`/api/admin/members/${userId}/logs`),
  getMemberSummary: (userId: string) => request<any>(`/api/admin/members/${userId}/summary`),
  updateMemberRole: (userId: string, role: "TEAM_LEAD" | "TEAM_MEMBER") => 
    request<{ userId: string; role: string; message: string }>(`/api/admin/members/${userId}/role`, {
      method: "PATCH",
      body: JSON.stringify({ role })
    }),
  removeMember: (userId: string) => request<{ message: string }>(`/api/admin/members/${userId}`, { method: "DELETE" }),
  inviteMember: (email: string, password?: string, role?: string, teamId?: string) => 
    request<any>("/api/admin/members/invite", {
      method: "POST",
      body: JSON.stringify({ email, password, role, teamId })
    })
};

// ─── Teams API ────────────────────────────────────────────────────────────────

export const teamsApi = {
  createTeam: (name: string, description: string) =>
    request<TeamSummary>("/api/admin/teams", {
      method: "POST",
      body: JSON.stringify({ name, description }),
    }),
  listTeams: () => request<TeamSummary[]>("/api/admin/teams"),
  getTeam: (teamId: string) => request<TeamSummary>(`/api/admin/teams/${teamId}`),
  deleteTeam: (teamId: string) => request<{ message: string }>(`/api/admin/teams/${teamId}`, { method: "DELETE" }),
  assignLead: (teamId: string, email: string) =>
    request<{ message: string; userId: string; isNewUser: boolean }>(
      `/api/admin/teams/${teamId}/lead`,
      { method: "POST", body: JSON.stringify({ email }) },
    ),
  addMember: (teamId: string, email: string, role: string) =>
    request<{ message: string; userId: string; role: string; isNewUser: boolean }>(
      `/api/admin/teams/${teamId}/members`,
      { method: "POST", body: JSON.stringify({ email, role }) },
    ),
  removeMember: (teamId: string, userId: string) =>
    request<{ message: string }>(`/api/admin/teams/${teamId}/members/${userId}`, {
      method: "DELETE",
    }),
  generateKey: (teamId: string) =>
    request<{ rawKey: string; tenantId: string; emailedTo: string }>(
      `/api/admin/teams/${teamId}/generate-key`,
      { method: "POST" },
    ),
  enableKey: (teamId: string) =>
    request<{ keyActive: boolean }>(`/api/admin/teams/${teamId}/key/enable`, {
      method: "PATCH",
    }),
  disableKey: (teamId: string) =>
    request<{ keyActive: boolean }>(`/api/admin/teams/${teamId}/key/disable`, {
      method: "PATCH",
    }),
  resendKeyEmail: (teamId: string, rawKey?: string) =>
    request<{ message: string }>(`/api/admin/teams/${teamId}/key/email`, {
      method: "POST",
      ...(rawKey ? { body: JSON.stringify({ rawKey }) } : {}),
    }),
  updateStatus: (teamId: string, active: boolean) =>
    request<{ active: boolean }>(`/api/admin/teams/${teamId}/status`, {
      method: "PATCH",
      body: JSON.stringify({ active }),
    }),
  getAnalytics: () => request<TeamSummary[]>("/api/admin/teams/analytics"),
  getMyTeam: () => request<TeamSummary>("/api/my-team"),
  updateTeamBudget: (teamId: string, dailyBudgetUsd: number | null) =>
    request<TeamSummary>(`/api/admin/teams/${teamId}/budget`, {
      method: "PATCH",
      body: JSON.stringify({ dailyBudgetUsd })
    })
};

// ─── Routing Simulation API ──────────────────────────────────────────────────

export interface SimulationRequest {
  prompt: string;
  taskCategory: string;
  qualityWeight: number;
  costWeight: number;
  latencyWeight: number;
  reliabilityWeight: number;
}

export interface CandidateEvaluation {
  armKey: string;
  providerSlug: string;
  modelId: string;
  displayName: string;
  qualityScore: number;
  costScore: number;
  latencyScore: number;
  reliabilityScore: number;
  healthScore: number;
  finalScore: number;
  estimatedCostUsd: number;
  estimatedLatencyMs: number;
  isWinner: boolean;
  statusReason: string;
}

export interface SimulationResult {
  selectedArmKey: string;
  selectedModelDisplayName: string;
  explanationReason: string;
  policyWeights: Record<string, number>;
  candidates: CandidateEvaluation[];
}

export const routingApi = {
  simulate: (req: SimulationRequest) =>
    request<SimulationResult>("/api/routing/simulate", {
      method: "POST",
      body: JSON.stringify(req),
    }),
};

// ─── Telemetry & Governance API ──────────────────────────────────────────────

export interface AuditLogEntry {
  id: string;
  actorEmail: string;
  action: string;
  resource: string;
  organizationId: string | null;
  metadataJson: string | null;
  timestamp: string;
}

export interface BudgetEntry {
  id: string;
  targetType: string;
  targetId: string;
  dailyCapUsd: number;
  monthlyCapUsd: number;
  currentDailySpendUsd: number;
  currentMonthlySpendUsd: number;
  actionOnExceeded: string;
  lastResetAt: string;
}

export interface BudgetStatus {
  targetType: string;
  targetId: string;
  allowed: boolean;
  dailyCapUsd: number;
  currentDailySpendUsd: number;
  monthlyCapUsd: number;
  currentMonthlySpendUsd: number;
  is80PercentWarning: boolean;
  dailyUtilizationPct: number;
  message: string;
}

export const telemetryApi = {
  getAuditLogs: (limit = 50) =>
    request<AuditLogEntry[]>(`/api/telemetry/audit-logs?limit=${limit}`),
  getAllBudgets: () => request<BudgetEntry[]>("/api/telemetry/budget"),
  upsertBudget: (body: {
    targetType: string;
    targetId: string;
    dailyCapUsd: number;
    monthlyCapUsd: number;
    actionOnExceeded?: string;
  }) =>
    request<BudgetEntry>("/api/telemetry/budget", {
      method: "POST",
      body: JSON.stringify(body),
    }),
  getBudgetStatus: (targetId: string, targetType = "ORGANIZATION") =>
    request<BudgetStatus>(
      `/api/telemetry/budget/${encodeURIComponent(targetId)}?targetType=${targetType}`
    ),
  runBenchmark: (requests = 10) =>
    request<{
      totalRequests: number;
      successfulRequests: number;
      avgLatencyMs: number;
      cacheHits: number;
      cacheHitRatioPct: number;
      modelDistribution: Record<String, number>;
    }>(`/api/telemetry/benchmark?requests=${requests}`, { method: "POST" }),
};

export interface ApiKeyRecord {
  id: string;
  name: string;
  keyPrefix: string;
  environment: string;
  status: string;
  createdAt: string;
  lastUsedAt?: string;
  projectId?: string;
  rawSecretKey?: string;
}

export const keysApi = {
  getKeys: (projectId?: string) =>
    request<ApiKeyRecord[]>(`/api/keys${projectId ? `?projectId=${projectId}` : ""}`),
  createKey: (data: { name: string; environment?: string; projectId?: string }) =>
    request<ApiKeyRecord>("/api/keys", {
      method: "POST",
      body: JSON.stringify(data),
    }),
  revokeKey: (id: string) =>
    request<{ id: string; status: string; message: string }>(`/api/keys/${id}`, {
      method: "DELETE",
    }),
};

export interface CacheStats {
  hits: number;
  misses: number;
  hitRatio: number;
  costSavedUsd: number;
  latencySavedMs: number;
  redisActive?: boolean;
}

export const cacheApi = {
  getStats: () => request<CacheStats>("/api/cache/stats"),
  flushCache: () =>
    request<{ status: string; message: string }>("/api/cache/flush", {
      method: "POST",
    }),
};

export interface KnowledgeChunk {
  id: string;
  documentName: string;
  content: string;
  similarityScore: number;
  metadata: Record<string, String>;
}

export const ragApi = {
  getChunks: () => request<KnowledgeChunk[]>("/api/rag/chunks"),
  ingestChunk: (chunk: { documentName: string; content: string; metadata?: Record<string, string> }) =>
    request<{ message: string; documentName: string; totalChunks: number }>("/api/rag/chunks", {
      method: "POST",
      body: JSON.stringify(chunk),
    }),
  search: (query: string, topK = 5) =>
    request<KnowledgeChunk[]>(`/api/rag/search?topK=${topK}`, {
      method: "POST",
      body: JSON.stringify({ query }),
    }),
  deleteChunk: (id: string) =>
    request<{ id: string; deleted: boolean }>(`/api/rag/chunks/${id}`, {
      method: "DELETE",
    }),
};


