import { createFileRoute } from "@tanstack/react-router";
import { motion } from "motion/react";
import {
  CircleDollarSign, Gauge, TrendingDown, Activity, Lock, List,
  ShieldCheck, Zap, AlertTriangle, BarChart3,
} from "lucide-react";
import { useEffect, useState } from "react";
import { AppShell } from "@/components/AppShell";
import { MetricCard } from "@/components/nexus/MetricCard";
import { TrafficFlowVisualizer } from "@/components/nexus/TrafficFlowVisualizer";
import { BenchmarkVisualizer } from "@/components/nexus/BenchmarkVisualizer";
import {
  dashboardApi, telemetryApi, type GlobalMetrics, type ArmState,
  type AuditLogEntry, type BudgetStatus,
} from "@/lib/api";

export const Route = createFileRoute("/app/analytics")({
  head: () => ({
    meta: [
      { title: "Analytics — NexusAI" },
      {
        name: "description",
        content:
          "Real-time analytics from the NexusAI routing gateway: cost, latency, provider win rates, budget governance, and zero-trust audit trail.",
      },
      { property: "og:title", content: "Analytics — NexusAI" },
      { property: "og:description", content: "Cost, latency, win rates, governance, PII audit trail." },
    ],
  }),
  component: Analytics,
});

const TONES = ["emerald", "cyan", "indigo", "amber", "chart-5"];

const ACTION_COLORS: Record<string, string> = {
  GATEWAY_REQUEST: "text-cyan",
  ROUTING_DECISION: "text-emerald",
  PROVIDER_FALLBACK: "text-amber",
  BUDGET_ENFORCEMENT: "text-destructive",
  PROVIDER_CIRCUIT_OPEN: "text-amber",
  PII_REDACTION_APPLIED: "text-indigo",
  QUALITY_EVALUATION: "text-indigo",
};

function Analytics() {
  const [metrics, setMetrics] = useState<GlobalMetrics | null>(null);
  const [armStates, setArmStates] = useState<ArmState[]>([]);
  const [auditLogs, setAuditLogs] = useState<AuditLogEntry[]>([]);
  const [budgetStatus, setBudgetStatus] = useState<BudgetStatus | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([
      dashboardApi.getMetrics(),
      dashboardApi.getLearning(),
      telemetryApi.getAuditLogs(20).catch(() => [] as AuditLogEntry[]),
      telemetryApi.getBudgetStatus("global").catch(() => null),
    ])
      .then(([m, l, logs, budget]) => {
        setMetrics(m);
        setArmStates(l.armStates.sort((a, b) => b.totalRequests - a.totalRequests));
        setAuditLogs(logs);
        setBudgetStatus(budget);
      })
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  const avgLatencyMs = metrics?.avgLatencyMs ?? 0;
  const totalCost = metrics?.totalCostUsd ?? 0;
  const avgFailureRate =
    armStates.length > 0
      ? armStates.reduce((acc, a) => acc + (a.failureRate ?? 0), 0) / armStates.length
      : 0;

  return (
    <AppShell title="Analytics & Telemetry" subtitle="Real routing telemetry, budget governance & zero-trust audit trail">
      {/* Top Metrics */}
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <MetricCard
          label="Total Cost Ledger"
          value={loading ? "—" : `$${totalCost.toFixed(4)}`}
          delta={0}
          icon={CircleDollarSign}
          tone="amber"
          spark={[0, 0, 0, 0, 0, 0, 0, totalCost * 100]}
        />
        <MetricCard
          label="Avg Latency"
          value={loading ? "—" : `${Math.round(avgLatencyMs)}ms`}
          delta={0}
          icon={Gauge}
          tone="cyan"
          spark={[0, 0, 0, 0, 0, 0, 0, avgLatencyMs]}
        />
        <MetricCard
          label="Avg Failure Rate"
          value={loading ? "—" : `${(avgFailureRate * 100).toFixed(2)}%`}
          delta={0}
          icon={TrendingDown}
          tone="emerald"
          spark={[0, 0, 0, 0, 0, 0, 0, avgFailureRate * 100]}
        />
        <MetricCard
          label="Zero-Trust Shield"
          value="ACTIVE"
          delta={0}
          icon={ShieldCheck}
          tone="indigo"
          spark={[100, 100, 100, 100, 100]}
        />
      </div>

      {/* Real-time SSE Traffic Flow Visualizer */}
      <div className="mt-5">
        <TrafficFlowVisualizer />
      </div>

      {/* Latency SLA & Synthetic Load Benchmark Visualizer */}
      <div className="mt-5">
        <BenchmarkVisualizer />
      </div>

      {/* Budget + Arm Health */}
      <div className="mt-5 grid gap-4 lg:grid-cols-[1.4fr_1fr]">
        {/* Arm health bars */}
        <div className="section-panel">
          <div className="section-panel-header">
            <div className="flex items-center gap-2">
              <Activity className="h-4 w-4 text-cyan" />
              <div>
                <p className="text-[0.8125rem] font-semibold tracking-tight">Model Arm Health</p>
                <p className="text-[0.625rem] text-muted-foreground">EWMA health from the reputation system</p>
              </div>
            </div>
          </div>
          <div className="p-5">
            {loading && (
              <div className="space-y-3">
                {[0, 1, 2].map((i) => (
                  <div key={i} className="skeleton h-10 w-full" />
                ))}
              </div>
            )}
            {!loading && armStates.length === 0 && (
              <div className="flex flex-col items-center py-8 text-center">
                <BarChart3 className="h-8 w-8 text-muted-foreground/20 mb-3" />
                <p className="text-[0.8125rem] text-muted-foreground">No arm data yet</p>
                <p className="text-[0.6875rem] text-muted-foreground/60 mt-1">Send requests through the Sandbox to populate metrics.</p>
              </div>
            )}
            {!loading && armStates.length > 0 && (
              <div className="space-y-4">
                {armStates.map((arm, i) => (
                  <div key={arm.armKey}>
                    <div className="flex items-center justify-between text-xs mb-1.5">
                      <span className="font-mono text-[0.75rem]">{arm.armKey}</span>
                      <div className="flex items-center gap-4 text-muted-foreground text-[0.6875rem]">
                        <span>{Math.round(arm.avgLatencyMs)}ms avg</span>
                        <span className={arm.healthScore >= 0.8 ? "text-emerald font-mono font-medium" : "text-amber font-mono font-medium"}>
                          {(arm.healthScore * 100).toFixed(1)}%
                        </span>
                      </div>
                    </div>
                    <div className="h-1.5 overflow-hidden rounded-full bg-[var(--surface-subtle)]">
                      <motion.div
                        initial={{ width: 0 }}
                        animate={{ width: `${arm.healthScore * 100}%` }}
                        transition={{ delay: 0.1 + i * 0.08, duration: 0.7, ease: [0.22, 1, 0.36, 1] }}
                        className="h-full rounded-full"
                        style={{ background: `var(--${TONES[i % TONES.length]})` }}
                      />
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* Right column: governance */}
        <div className="space-y-4">
          {/* Budget Utilization */}
          <div className="section-panel">
            <div className="section-panel-header">
              <div className="flex items-center gap-2">
                <Zap className="h-4 w-4 text-amber" />
                <p className="text-[0.8125rem] font-semibold tracking-tight">Budget Governance</p>
              </div>
            </div>
            <div className="p-5">
              {budgetStatus ? (
                <div className="space-y-3">
                  <div className="flex items-center justify-between text-xs">
                    <span className="text-muted-foreground">Daily spend</span>
                    <span className={`font-mono font-medium ${budgetStatus.is80PercentWarning ? "text-amber" : "text-emerald"}`}>
                      ${budgetStatus.currentDailySpendUsd.toFixed(4)} / ${budgetStatus.dailyCapUsd.toFixed(2)}
                    </span>
                  </div>
                  <div className="h-2 overflow-hidden rounded-full bg-[var(--surface-subtle)]">
                    <motion.div
                      initial={{ width: 0 }}
                      animate={{ width: `${Math.min(100, budgetStatus.dailyUtilizationPct)}%` }}
                      transition={{ duration: 0.8, ease: [0.22, 1, 0.36, 1] }}
                      className="h-full rounded-full"
                      style={{
                        background: budgetStatus.dailyUtilizationPct >= 80
                          ? "var(--amber)" : "var(--emerald)"
                      }}
                    />
                  </div>
                  {budgetStatus.is80PercentWarning && (
                    <div className="flex items-center gap-1.5 text-xs text-amber">
                      <AlertTriangle className="h-3 w-3" />
                      <span>80% threshold reached — approaching daily cap</span>
                    </div>
                  )}
                  <div className="flex justify-between text-xs text-muted-foreground pt-1 border-t border-[var(--glass-border)]">
                    <span>Monthly spend</span>
                    <span className="font-mono">${budgetStatus.currentMonthlySpendUsd.toFixed(4)} / ${budgetStatus.monthlyCapUsd.toFixed(2)}</span>
                  </div>
                </div>
              ) : (
                <p className="text-xs text-muted-foreground">No budget cap configured — unlimited.</p>
              )}
            </div>
          </div>

          {/* Security Status */}
          <div className="section-panel">
            <div className="section-panel-header">
              <div className="flex items-center gap-2">
                <Lock className="h-4 w-4 text-emerald" />
                <p className="text-[0.8125rem] font-semibold tracking-tight">Zero-Trust Security</p>
              </div>
            </div>
            <div className="p-5 space-y-2.5">
              {[
                ["Provider Credentials", "AES-256-GCM", "text-emerald"],
                ["API Key Hashing", "SHA-256", "text-cyan"],
                ["PII Auto-Redaction", "Enabled", "text-emerald"],
                ["Audit Trail", "Async Tracing", "text-indigo"],
              ].map(([label, value, color]) => (
                <div key={label} className="flex justify-between items-center text-xs py-1 border-b border-[var(--glass-border)] last:border-0">
                  <span className="text-muted-foreground">{label}</span>
                  <span className={`font-mono font-medium ${color}`}>{value}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* Live Audit Trail */}
      <div className="section-panel mt-5">
        <div className="section-panel-header">
          <div className="flex items-center gap-2">
            <List className="h-4 w-4 text-cyan" />
            <p className="text-[0.8125rem] font-semibold tracking-tight">Live Audit Trail</p>
          </div>
          <span className="text-[0.6875rem] text-muted-foreground">Last 20 events · PII-redacted</span>
        </div>
        <div className="p-5">
          {loading && (
            <div className="space-y-2">
              {[0, 1, 2, 3].map((i) => (
                <div key={i} className="skeleton h-7 w-full" />
              ))}
            </div>
          )}

          {!loading && auditLogs.length === 0 && (
            <div className="flex flex-col items-center py-8 text-center">
              <List className="h-8 w-8 text-muted-foreground/20 mb-3" />
              <p className="text-[0.8125rem] text-muted-foreground">No audit events yet</p>
              <p className="text-[0.6875rem] text-muted-foreground/60 mt-1">Events are written asynchronously as requests flow through the gateway.</p>
            </div>
          )}

          {!loading && auditLogs.length > 0 && (
            <div className="overflow-x-auto">
              <table className="data-table min-w-[40rem]">
                <thead>
                  <tr>
                    <th>Time</th>
                    <th>Action</th>
                    <th>Actor</th>
                    <th>Resource</th>
                  </tr>
                </thead>
                <tbody>
                  {auditLogs.map((entry) => (
                    <tr key={entry.id}>
                      <td className="font-mono text-[0.75rem] text-muted-foreground whitespace-nowrap">
                        {new Date(entry.timestamp).toLocaleTimeString()}
                      </td>
                      <td>
                        <span className={`font-mono text-[0.75rem] font-medium ${ACTION_COLORS[entry.action] ?? "text-foreground"}`}>
                          {entry.action}
                        </span>
                      </td>
                      <td className="text-muted-foreground text-[0.75rem] truncate max-w-[120px]">
                        {entry.actorEmail}
                      </td>
                      <td className="font-mono text-[0.75rem] text-muted-foreground truncate max-w-[200px]">
                        {entry.resource}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </AppShell>
  );
}
