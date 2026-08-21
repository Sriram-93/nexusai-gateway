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

function getJwt(): string | null {
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
};

// ─── Chat API ─────────────────────────────────────────────────────────────────

export const chatApi = {
  chat: (req: ChatRequest) =>
    request<ChatResponse>("/api/chat", {
      method: "POST",
      body: JSON.stringify(req),
    }),
  agentChat: (req: ChatRequest) =>
    request<{ answer: string; provider: string; latencyMs: number }>(
      "/api/agent/chat",
      {
        method: "POST",
        body: JSON.stringify(req),
      },
    ),
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
  getMembers: () => request<Array<{ id: string; email: string; role: string }>>("/api/admin/members"),
  getMemberLogs: (userId: string) => request<RequestLog[]>(`/api/admin/members/${userId}/logs`),
  getMemberSummary: (userId: string) => request<any>(`/api/admin/members/${userId}/summary`),
  updateMemberRole: (userId: string, role: "TEAM_LEAD" | "TEAM_MEMBER") => 
    request<{ userId: string; role: string; message: string }>(`/api/admin/members/${userId}/role`, {
      method: "PATCH",
      body: JSON.stringify({ role })
    }),
  removeMember: (userId: string) => request<{ message: string }>(`/api/admin/members/${userId}`, { method: "DELETE" }),
  inviteMember: (email: string, password: string, role: "TEAM_LEAD" | "TEAM_MEMBER") => 
    request<any>("/api/admin/members/invite", {
      method: "POST",
      body: JSON.stringify({ email, password, role })
    })
};
