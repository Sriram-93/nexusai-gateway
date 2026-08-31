import { createFileRoute } from "@tanstack/react-router";
import { motion, AnimatePresence } from "motion/react";
import { useEffect, useState, useMemo } from "react";
import { AppShell } from "@/components/AppShell";
import {
  Activity, ShieldCheck, AlertTriangle, RefreshCw,
  Search, Server, Zap, CheckCircle2, XCircle, Clock, Play, Loader2, Layers, Globe,
  Gauge, WifiOff
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useToast } from "@/lib/toast";
import { PageLoadingSkeleton } from "@/components/nexus/PageLoadingSkeleton";
import { authFetch } from "@/lib/api";

export const Route = createFileRoute("/app/health")({
  head: () => ({
    meta: [
      { title: "Model Health Review — NexusAI" },
      { name: "description", content: "Real-time automated model health diagnostic review dashboard." },
    ],
  }),
  component: ModelHealthPage,
});

interface RegisteredModelHealth {
  id: number;
  armKey: string;
  providerSlug: string;
  modelId: string;
  displayName: string;
  enabled: boolean;
  healthStatus: "HEALTHY" | "DEGRADED" | "UNREACHABLE" | "RATE_LIMITED" | "UNKNOWN";
  lastHealthCheck: string | null;
  lastHealthError: string | null;
  lastHealthLatencyMs: number | null;
  estimatedLatencyMs: number;
  inputPricePer1M: number;
  outputPricePer1M: number;
}

interface HealthOverviewData {
  totalModels: number;
  healthyCount: number;
  degradedCount: number;
  unreachableCount: number;
  averageLatencyMs: number;
  models: RegisteredModelHealth[];
}

interface HealthLogEntry {
  armKey: string;
  provider: string;
  modelId: string;
  status: string;
  healthy: boolean;
  latencyMs?: number;
  timestamp: string;
  error?: string;
  message?: string;
}

const STATUS_CONFIG: Record<string, { label: string; icon: typeof CheckCircle2; color: string; bgClass: string; borderClass: string }> = {
  HEALTHY: { label: "Healthy", icon: CheckCircle2, color: "text-emerald-500", bgClass: "bg-emerald-500/8", borderClass: "border-emerald-500/20" },
  RATE_LIMITED: { label: "Rate Limited", icon: AlertTriangle, color: "text-amber-500", bgClass: "bg-amber-500/8", borderClass: "border-amber-500/20" },
  DEGRADED: { label: "Degraded", icon: AlertTriangle, color: "text-amber-500", bgClass: "bg-amber-500/8", borderClass: "border-amber-500/20" },
  UNREACHABLE: { label: "Unreachable", icon: XCircle, color: "text-rose-500", bgClass: "bg-rose-500/8", borderClass: "border-rose-500/20" },
  UNKNOWN: { label: "Pending", icon: Clock, color: "text-muted-foreground", bgClass: "bg-muted/20", borderClass: "border-border" },
};

function ModelHealthPage() {
  const [data, setData] = useState<HealthOverviewData | null>(null);
  const [logs, setLogs] = useState<HealthLogEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [runningScan, setRunningScan] = useState(false);
  const [testingModelId, setTestingModelId] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<string>("ALL");
  const [viewTab, setViewTab] = useState<"ACTIVE" | "ALL_DISCOVERED">("ACTIVE");

  const { success, error: toastError, info } = useToast();

  const loadHealthData = async () => {
    setLoading(true);
    try {
      const [resHealth, resLogs] = await Promise.all([
        authFetch("/api/models/health").then(r => r.json()),
        authFetch("/api/models/health/logs").then(r => r.json())
      ]);
      setData(resHealth);
      setLogs(resLogs || []);
    } catch (err: any) {
      toastError("Failed to Load Health Data", err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadHealthData();
    const interval = setInterval(loadHealthData, 30000);
    return () => clearInterval(interval);
  }, []);

  const handleRunFullScan = async () => {
    setRunningScan(true);
    info("Executing Health Scan...", "Pinging all candidate model endpoints across providers...");
    try {
      const res = await authFetch("/api/models/health/run", {
        method: "POST",
      });
      const result = await res.json();
      success(
        "Health Scan Completed",
        `Verified ${result.healthyCount} healthy, ${result.degradedCount} degraded, ${result.unreachableCount} unreachable models.`
      );
      await loadHealthData();
    } catch (err: any) {
      toastError("Health Scan Failed", err.message);
    } finally {
      setRunningScan(false);
    }
  };

  const handleTestSingleModel = async (model: RegisteredModelHealth) => {
    setTestingModelId(model.armKey);
    info(`Testing ${model.modelId}...`, `Pinging ${model.providerSlug} live API endpoint...`);
    try {
      const res = await authFetch(
        `/api/models/health/verify-single?providerSlug=${model.providerSlug}&modelId=${encodeURIComponent(model.modelId)}`,
        { method: "POST" }
      );
      const pingData = await res.json();
      if (pingData.healthy) {
        success("Model Verified", pingData.message || `HTTP 200 OK (${pingData.latencyMs}ms) for ${model.modelId}.`);
      } else {
        toastError("Endpoint Warning", pingData.message || pingData.error || "Model ping failed.");
      }
      await loadHealthData();
    } catch (err: any) {
      toastError("Ping Error", err.message);
    } finally {
      setTestingModelId(null);
    }
  };

  const handleToggleModelEnabled = async (model: RegisteredModelHealth) => {
    const action = model.enabled ? "disable" : "enable";
    try {
      const res = await authFetch(`/api/providers/${model.providerSlug}/models/${encodeURIComponent(model.modelId)}/${action}`, {
        method: "PATCH",
      });
      if (res.ok) {
        success(`Model ${action === "enable" ? "Enabled" : "Disabled"}`, `${model.modelId} is now ${action === "enable" ? "active for routing" : "disabled"}.`);
        await loadHealthData();
      } else {
        const errJson = await res.json();
        toastError("Action Failed", errJson.error || "Could not update model state.");
      }
    } catch (err: any) {
      toastError("Toggle Error", err.message);
    }
  };

  const tabModels = useMemo(() => {
    if (!data?.models) return [];
    return data.models.filter(m => viewTab === "ACTIVE" ? m.enabled : true);
  }, [data, viewTab]);

  const healthyCount = useMemo(() => tabModels.filter(m => m.healthStatus === "HEALTHY").length, [tabModels]);
  const rateLimitedCount = useMemo(() => tabModels.filter(m => m.healthStatus === "RATE_LIMITED").length, [tabModels]);
  const degradedCount = useMemo(() => tabModels.filter(m => m.healthStatus === "DEGRADED").length, [tabModels]);
  const unreachableCount = useMemo(() => tabModels.filter(m => m.healthStatus === "UNREACHABLE" || m.healthStatus === "UNKNOWN").length, [tabModels]);
  const totalCount = useMemo(() => viewTab === "ACTIVE" ? tabModels.length : (data?.totalModels ?? tabModels.length), [tabModels, viewTab, data]);

  const avgLatency = useMemo(() => {
    const tested = tabModels.filter(m => m.lastHealthLatencyMs != null && m.lastHealthLatencyMs > 0);
    if (tested.length === 0) return 0;
    const sum = tested.reduce((acc, m) => acc + (m.lastHealthLatencyMs || 0), 0);
    return Math.round(sum / tested.length);
  }, [tabModels]);

  const filteredModels = useMemo(() => {
    return tabModels.filter(m => {
      const matchesSearch =
        m.modelId.toLowerCase().includes(search.toLowerCase()) ||
        m.providerSlug.toLowerCase().includes(search.toLowerCase()) ||
        (m.displayName && m.displayName.toLowerCase().includes(search.toLowerCase()));
      const matchesStatus = statusFilter === "ALL" || m.healthStatus === statusFilter;
      return matchesSearch && matchesStatus;
    });
  }, [tabModels, search, statusFilter]);

  const getLatencyColor = (ms: number | null) => {
    if (ms == null) return "text-muted-foreground";
    if (ms < 500) return "text-emerald-500";
    if (ms < 1500) return "text-amber-500";
    return "text-rose-500";
  };

  const formatErrorMsg = (err: string | undefined | null) => {
    if (!err) return "";
    const lower = err.toLowerCase();
    if (lower.includes("401")) return "Unauthorized (401)";
    if (lower.includes("403")) return "Access Denied (403)";
    if (lower.includes("404")) return "Model Not Found (404)";
    if (lower.includes("429")) return "Rate Limited (429)";
    if (lower.includes("400")) return "Bad Request (400) - Unsupported Model";
    if (lower.includes("500") || lower.includes("502") || lower.includes("503")) return "Provider API Offline";
    return "Endpoint Unreachable";
  };

  if (loading) {
    return (
      <AppShell title="Model Health Review" subtitle="Real-time automated model health diagnostic review dashboard">
        <PageLoadingSkeleton
          title="Gathering Real-Time Health Diagnostics..."
          subtitle="Querying model ping statistics, latency traces, and circuit breaker telemetry."
          cardsCount={4}
        />
      </AppShell>
    );
  }

  return (
    <AppShell
      title="Model Health Governance"
      subtitle="Real-time automated diagnostic review, latency metrics, and health trace audit logs."
    >
      {/* Floating High-Tech Health Scan Banner */}
      <AnimatePresence>
        {runningScan && (
          <motion.div
            initial={{ opacity: 0, y: -30, scale: 0.95 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -20, scale: 0.95 }}
            className="fixed top-16 left-1/2 -translate-x-1/2 z-50 flex items-center gap-4 rounded-2xl border border-cyan-500/50 bg-background/95 px-6 py-3.5 shadow-[0_0_40px_rgba(6,182,212,0.4)] backdrop-blur-xl"
          >
            <div className="relative flex h-10 w-10 items-center justify-center rounded-xl bg-cyan-500/20 text-cyan-400 border border-cyan-500/40">
              <motion.div
                animate={{ scale: [1, 1.5, 1], opacity: [0.7, 0, 0.7] }}
                transition={{ repeat: Infinity, duration: 1.3 }}
                className="absolute inset-0 rounded-xl bg-cyan-500/30"
              />
              <motion.div animate={{ rotate: 360 }} transition={{ repeat: Infinity, duration: 1.8, ease: "linear" }}>
                <RefreshCw className="h-5 w-5 text-cyan-400" />
              </motion.div>
            </div>

            <div>
              <div className="flex items-center gap-2">
                <p className="text-xs font-bold text-foreground">Executing Health Diagnostic Scan...</p>
                <span className="flex h-2.5 w-2.5 rounded-full bg-cyan-400 animate-ping" />
              </div>
              <p className="text-[0.7rem] text-muted-foreground">Pinging candidate endpoints across all configured upstream AI providers.</p>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      <div className="space-y-5">
        {/* View Mode Tabs + Scan Button */}
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-1.5 rounded-xl border border-border bg-[var(--surface-subtle)] p-1">
            <button
              onClick={() => setViewTab("ACTIVE")}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[0.75rem] font-medium transition-all ${
                viewTab === "ACTIVE"
                  ? "bg-[var(--surface)] text-foreground shadow-sm"
                  : "text-muted-foreground hover:text-foreground"
              }`}
            >
              <Layers className="h-3.5 w-3.5" /> Active ({data?.models?.filter(m => m.enabled).length ?? 0})
            </button>
            <button
              onClick={() => setViewTab("ALL_DISCOVERED")}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[0.75rem] font-medium transition-all ${
                viewTab === "ALL_DISCOVERED"
                  ? "bg-[var(--surface)] text-foreground shadow-sm"
                  : "text-muted-foreground hover:text-foreground"
              }`}
            >
              <Globe className="h-3.5 w-3.5" /> All Candidates ({data?.totalModels ?? 0})
            </button>
          </div>

          <Button
            onClick={handleRunFullScan}
            disabled={runningScan}
            size="sm"
            className="h-9 rounded-xl text-xs grad-primary text-primary-foreground font-medium gap-1.5 shadow-sm"
          >
            {runningScan ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Activity className="h-3.5 w-3.5" />}
            Run System Health Scan Now
          </Button>
        </div>

        {/* KPI Row */}
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
          {[
            { label: "Total Monitored", value: totalCount, icon: Server, color: "text-foreground", sub: viewTab === "ACTIVE" ? "Active Gateway Arms" : "All Candidates" },
            { label: "Healthy", value: healthyCount, icon: CheckCircle2, color: "text-emerald-500", sub: "Operational 200 OK" },
            { label: "Rate Limited", value: rateLimitedCount, icon: AlertTriangle, color: "text-amber-500", sub: "429 Throttled" },
            { label: "Degraded", value: degradedCount, icon: Gauge, color: "text-amber-600", sub: "Slow Response" },
            { label: "Unreachable", value: unreachableCount, icon: WifiOff, color: "text-rose-500", sub: "404/401/Pending" },
            { label: "Avg Latency", value: `${avgLatency}ms`, icon: Zap, color: "text-cyan", sub: "Live Response" },
          ].map((kpi, i) => (
            <motion.div
              key={kpi.label}
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: i * 0.04 }}
              className="section-panel p-3.5"
            >
              <div className="flex items-center justify-between mb-2">
                <span className="text-[0.625rem] font-medium uppercase tracking-wider text-muted-foreground">{kpi.label}</span>
                <kpi.icon className={`h-3.5 w-3.5 ${kpi.color}`} />
              </div>
              <p className={`text-xl font-bold ${kpi.color}`}>{kpi.value}</p>
              <p className="text-[0.625rem] text-muted-foreground mt-0.5">{kpi.sub}</p>
            </motion.div>
          ))}
        </div>

        {/* Filter Bar */}
        <div className="section-panel p-3.5 flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-2">
            <div className="relative min-w-[200px]">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
              <Input
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Filter models..."
                className="h-8 text-xs pl-9 rounded-lg border-border bg-background"
              />
            </div>
            <div className="flex items-center rounded-lg border border-border bg-background p-0.5 text-xs">
              {["ALL", "HEALTHY", "RATE_LIMITED", "DEGRADED", "UNREACHABLE"].map((st) => (
                <button
                  key={st}
                  onClick={() => setStatusFilter(st)}
                  className={`px-2.5 py-1 rounded-md font-medium transition-colors text-[0.6875rem] ${
                    statusFilter === st
                      ? "bg-primary text-primary-foreground shadow-xs"
                      : "text-muted-foreground hover:text-foreground"
                  }`}
                >
                  {st === "RATE_LIMITED" ? "Rate Limited" : st.charAt(0) + st.slice(1).toLowerCase()}
                </button>
              ))}
            </div>
          </div>

          <Button onClick={loadHealthData} variant="outline" size="sm" className="h-8 rounded-lg text-xs gap-1.5 border-border">
            <RefreshCw className={`h-3.5 w-3.5 ${loading ? "animate-spin" : ""}`} /> Refresh
          </Button>
        </div>

        {/* Models Table */}
        <div className="section-panel">
          <div className="section-panel-header">
            <h3 className="text-[0.8125rem] font-semibold tracking-tight text-foreground flex items-center gap-2">
              <ShieldCheck className="h-4 w-4 text-cyan" />
              {viewTab === "ACTIVE" ? "Active Gateway Models" : "All Provider Candidate Models"}
            </h3>
            <span className="text-[0.6875rem] text-muted-foreground">{filteredModels.length} models</span>
          </div>

          <div className="overflow-x-auto">
            <table className="data-table min-w-[56rem]">
              <thead>
                <tr>
                  <th>Provider</th>
                  <th>Model ID</th>
                  <th>Status</th>
                  <th>Routing</th>
                  <th>Last Tested</th>
                  <th>Latency</th>
                  <th>Diagnostic</th>
                  <th className="text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {loading && Array.from({ length: 4 }).map((_, i) => (
                  <tr key={i}>
                    {Array.from({ length: 8 }).map((_, j) => (
                      <td key={j}><div className="skeleton h-4 w-full" /></td>
                    ))}
                  </tr>
                ))}
                {!loading && filteredModels.length === 0 && (
                  <tr>
                    <td colSpan={8} className="!py-12 text-center">
                      <Search className="mx-auto mb-3 h-8 w-8 text-muted-foreground/20" />
                      <p className="text-[0.8125rem] text-muted-foreground">No models matching current filters.</p>
                      <p className="text-[0.6875rem] text-muted-foreground/60 mt-1">Try adjusting your search or status filter.</p>
                    </td>
                  </tr>
                )}
                {!loading && filteredModels.map((m) => {
                    const cfg = STATUS_CONFIG[m.healthStatus] ?? STATUS_CONFIG["UNKNOWN"]!;
                  const StatusIcon = cfg.icon;
                  return (
                    <tr key={m.armKey}>
                      <td className="font-semibold uppercase text-[0.75rem] text-cyan">{m.providerSlug}</td>
                      <td className="font-mono text-[0.75rem] font-medium text-foreground">{m.modelId}</td>
                      <td>
                        <span className={`inline-flex items-center gap-1 rounded-full ${cfg.bgClass} border ${cfg.borderClass} px-2 py-0.5 text-[0.65rem] font-medium ${cfg.color}`}>
                          <StatusIcon className="h-3 w-3" /> {cfg.label}
                        </span>
                      </td>
                      <td>
                        {m.enabled ? (
                          <span className="inline-flex items-center gap-1 text-emerald-500 font-medium text-[0.65rem]">
                            <CheckCircle2 className="h-3 w-3" /> Active
                          </span>
                        ) : (
                          <span className="text-muted-foreground text-[0.65rem]">Disabled</span>
                        )}
                      </td>
                      <td className="text-muted-foreground text-[0.75rem]">
                        {m.lastHealthCheck ? new Date(m.lastHealthCheck).toLocaleTimeString() : "—"}
                      </td>
                      <td className={`font-mono text-[0.75rem] ${getLatencyColor(m.lastHealthLatencyMs)}`}>
                        {m.lastHealthLatencyMs != null ? `${m.lastHealthLatencyMs}ms` : "—"}
                      </td>
                      <td className="max-w-[200px] truncate text-[0.75rem]">
                        {m.lastHealthError ? (
                          <span className="text-rose-400">{formatErrorMsg(m.lastHealthError)}</span>
                        ) : m.healthStatus === "RATE_LIMITED" ? (
                          <span className="text-amber-400">Operational — rate limited (429)</span>
                        ) : m.healthStatus === "HEALTHY" ? (
                          <span className="text-emerald-400">Endpoint responsive</span>
                        ) : (
                          <span className="text-muted-foreground">—</span>
                        )}
                      </td>
                      <td className="text-right">
                        <div className="flex items-center justify-end gap-1.5">
                          <Button
                            onClick={() => handleToggleModelEnabled(m)}
                            variant="ghost"
                            size="sm"
                            className={`h-7 text-[0.6875rem] rounded-lg px-2 font-medium ${
                              m.enabled ? "text-rose-400 hover:text-rose-300 hover:bg-rose-500/10" : "text-emerald-400 hover:text-emerald-300 hover:bg-emerald-500/10"
                            }`}
                          >
                            {m.enabled ? "Disable" : "Enable"}
                          </Button>
                          <Button
                            onClick={() => handleTestSingleModel(m)}
                            disabled={testingModelId === m.armKey}
                            variant="outline"
                            size="sm"
                            className="h-7 text-[0.6875rem] rounded-lg px-2.5 border-border hover:bg-accent gap-1"
                          >
                            {testingModelId === m.armKey ? <Loader2 className="h-3 w-3 animate-spin" /> : <Play className="h-3 w-3 text-cyan" />}
                            Test
                          </Button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>

        {/* Diagnostic Logs */}
        <div className="section-panel">
          <div className="section-panel-header">
            <h3 className="text-[0.8125rem] font-semibold tracking-tight text-foreground flex items-center gap-2">
              <Activity className="h-4 w-4 text-cyan" /> Health Diagnostic Trace
            </h3>
            <span className="text-[0.6875rem] text-muted-foreground">{logs.length} entries</span>
          </div>
          <div className="p-4 space-y-1.5 max-h-60 overflow-y-auto font-mono text-[0.7rem]">
            {logs.length === 0 ? (
              <div className="flex flex-col items-center py-8 text-center">
                <Activity className="h-8 w-8 text-muted-foreground/20 mb-3" />
                <p className="text-[0.8125rem] text-muted-foreground">No diagnostic logs recorded yet</p>
                <p className="text-[0.6875rem] text-muted-foreground/60 mt-1">Run a health scan to populate diagnostic traces.</p>
              </div>
            ) : (
              logs.map((lg, i) => (
                <div
                  key={i}
                  className="flex flex-wrap items-center justify-between gap-2 p-2 rounded-lg bg-[var(--surface-subtle)] border border-[var(--glass-border)]"
                >
                  <div className="flex items-center gap-2">
                    <span className="text-muted-foreground">{new Date(lg.timestamp).toLocaleTimeString()}</span>
                    <span className="font-semibold uppercase text-cyan">[{lg.provider}]</span>
                    <span className="text-foreground">{lg.modelId}</span>
                  </div>
                  <div className="flex items-center gap-3">
                    {lg.healthy ? (
                      <span className="text-emerald-400 font-medium">200 OK ({lg.latencyMs}ms)</span>
                    ) : (
                      <span className="text-rose-400 font-medium">{formatErrorMsg(lg.error || lg.message)}</span>
                    )}
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </AppShell>
  );
}
