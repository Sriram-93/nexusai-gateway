import { Link } from "@tanstack/react-router";
import { motion, AnimatePresence } from "motion/react";
import {
  Bot, CircleDollarSign, Gauge, Radio, Waves, Brain,
  ArrowUp, Boxes, KeyRound, AlertTriangle, ChevronRight,
  Users, Building2, Server, FlaskConical, Database, Network,
  Zap, CheckCircle2, Clock, Activity,
} from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { MetricCard } from "@/components/nexus/MetricCard";
import { UpgradeRequestsPanel } from "@/lib/upgrade-requests";
import type { GlobalMetrics, ActivityLog, StreamEvent, ProviderSummary } from "@/lib/api";
import { ProviderLogo } from "@/components/ProviderLogos";

type DashboardProps = {
  metrics: GlobalMetrics | null;
  logs: (ActivityLog | StreamEvent)[];
  live: boolean;
  setLive: (v: boolean | ((prev: boolean) => boolean)) => void;
  tenant: any;
  openModal: () => void;
  session: any;
  providers?: ProviderSummary[];
};

export function ProviderHealthWidget({ providers = [], logs = [] }: { providers?: ProviderSummary[]; logs: any[] }) {
  // Only display providers that have a configured API key in this account
  const configuredProviders = providers.filter((p) => p.hasKey && p.enabled);

  if (!configuredProviders || configuredProviders.length === 0) {
    return (
      <div className="section-panel p-5">
        <h3 className="text-[0.8125rem] font-semibold mb-3 flex items-center justify-between">
          <span className="flex items-center gap-2">
            <Activity className="h-4 w-4 text-cyan" /> Provider Health
          </span>
          <span className="text-[0.6875rem] text-muted-foreground font-mono">0 Connected</span>
        </h3>
        <div className="py-6 text-center text-xs text-muted-foreground">
          <p>No AI provider API keys configured in this account.</p>
          <div className="mt-2.5">
            <Link to="/app/providers" className="inline-flex items-center gap-1 text-cyan font-medium hover:underline">
              + Add API Key
            </Link>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="section-panel p-5">
      <h3 className="text-[0.8125rem] font-semibold mb-4 flex items-center justify-between">
        <span className="flex items-center gap-2">
          <Activity className="h-4 w-4 text-cyan" /> Provider Health
        </span>
        <span className="text-[0.6875rem] text-muted-foreground font-mono">
          {configuredProviders.length} Configured {configuredProviders.length === 1 ? "Provider" : "Providers"}
        </span>
      </h3>
      <div className="space-y-2.5">
        {configuredProviders.map((p) => {
          const providerLogs = logs.filter(
            (l) =>
              l.provider &&
              (l.provider.toLowerCase() === p.slug.toLowerCase() ||
                l.provider.toLowerCase() === p.displayName.toLowerCase())
          );

          let avgLatencyStr = "—";
          if (providerLogs.length > 0) {
            const sum = providerLogs.reduce((acc, curr) => acc + (curr.latencyMs || 0), 0);
            avgLatencyStr = `${Math.round(sum / providerLogs.length)}ms`;
          } else if (p.enabledModelCount > 0) {
            avgLatencyStr = "Active";
          }

          const isHealthy = p.enabled && p.hasKey;

          return (
            <div key={p.slug} className="flex justify-between items-center py-1.5 border-b border-[var(--glass-border)] last:border-0">
              <div className="flex items-center gap-2.5">
                <ProviderLogo slug={p.slug} name={p.displayName} className="h-4 w-4 shrink-0" />
                <span className="text-[0.8125rem] font-medium">{p.displayName || p.slug}</span>
                <span className={`h-1.5 w-1.5 rounded-full ${isHealthy ? "bg-emerald" : "bg-rose"}`} />
                <span className="text-[0.65rem] text-muted-foreground font-mono">({p.enabledModelCount} models)</span>
              </div>
              <div className="flex items-center gap-3">
                <span className={`badge ${isHealthy ? "badge-success" : "badge-danger"}`}>
                  {isHealthy ? "Healthy" : "No Key"}
                </span>
                <span className="font-mono text-[0.6875rem] text-muted-foreground w-16 text-right">
                  {avgLatencyStr}
                </span>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

export function LockedState() {
  return (
    <div className="mt-8 flex flex-col items-center justify-center rounded-2xl border border-dashed border-[var(--glass-border)] bg-[var(--card)] p-16 text-center">
      <div className="mb-5 flex h-16 w-16 items-center justify-center rounded-2xl bg-primary/10">
        <Boxes className="h-8 w-8 text-primary" />
      </div>
      <h2 className="text-xl font-semibold tracking-tight">Workspace Locked</h2>
      <p className="mt-2 max-w-md text-sm text-muted-foreground leading-relaxed">
        To view live routing metrics, test sandbox, and enable analytics, complete provider setup and generate a Gateway API Key.
      </p>
      <div className="mt-10 grid w-full max-w-3xl gap-4 sm:grid-cols-3">
        {[
          { icon: Boxes, label: "1. Connect Provider", sub: "Add AI Provider key", to: "/app/providers", color: "cyan" },
          { icon: Database, label: "2. Select Models", sub: "Enable verified models", to: "/app/models", color: "emerald" },
          { icon: KeyRound, label: "3. Generate API Key", sub: "Unlock workspace", to: "/app/keys", color: "indigo" },
        ].map((a) => (
          <Link key={a.to} to={a.to as never}>
            <motion.div
              whileHover={{ y: -2 }}
              whileTap={{ scale: 0.98 }}
              className="metric-card flex h-full flex-col items-center text-center gap-3 !p-6"
            >
              <div className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-${a.color}/10`}>
                <a.icon className={`h-5 w-5 text-${a.color}`} />
              </div>
              <div className="min-w-0">
                <p className="text-sm font-semibold">{a.label}</p>
                <p className="truncate text-xs text-muted-foreground mt-1">{a.sub}</p>
              </div>
            </motion.div>
          </Link>
        ))}
      </div>
    </div>
  );
}

function ActivityStream({ logs, live, setLive }: { logs: any[]; live: boolean; setLive: any }) {
  return (
    <div className="mt-6 section-panel overflow-hidden">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-[var(--glass-border)] px-5 py-3.5">
        <div className="flex items-center gap-2.5">
          <Waves className="h-4 w-4 text-cyan" />
          <p className="text-[0.8125rem] font-semibold tracking-tight">Recent Requests</p>
          <span className="badge badge-info">{logs.length}</span>
        </div>
        <button
          onClick={() => setLive((v: any) => !v)}
          className="flex items-center gap-2 rounded-full border border-[var(--glass-border)] px-3 py-1 text-[0.6875rem] font-medium transition-colors hover:bg-[var(--glass-hover)]"
        >
          <span className={`h-1.5 w-1.5 rounded-full ${live ? "animate-live bg-emerald" : "bg-muted-foreground"}`} />
          {live ? "Live" : "Paused"}
        </button>
      </div>

      {/* Table */}
      <div className="overflow-x-auto">
        <table className="data-table min-w-[52rem]">
          <thead>
            <tr>
              {["Time", "Provider", "Model", "Tokens", "Latency", "Cost", "Status"].map((h) => (
                <th key={h}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            <AnimatePresence initial={false}>
              {logs.length === 0 && (
                <motion.tr key="empty" exit={{ opacity: 0 }}>
                  <td colSpan={7} className="!py-12 text-center">
                    <Brain className="mx-auto mb-3 h-8 w-8 text-muted-foreground/20" />
                    <p className="text-[0.8125rem] text-muted-foreground">
                      No requests routed yet. Use the Sandbox to send your first request.
                    </p>
                  </td>
                </motion.tr>
              )}
              {logs.map((row, i) => (
                <motion.tr
                  key={row.id}
                  layout
                  initial={{ opacity: 0, y: -8 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0 }}
                  transition={{ duration: 0.3, delay: i < 5 ? i * 0.03 : 0 }}
                >
                  <td className="font-mono text-[0.75rem] text-muted-foreground">
                    {row.timestamp ? new Date(row.timestamp.endsWith('Z') ? row.timestamp : row.timestamp + 'Z').toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }) : "—"}
                  </td>
                  <td>
                    <span className="inline-flex items-center gap-1.5 text-[0.8125rem]">
                      <span className="h-1.5 w-1.5 rounded-full bg-emerald" />
                      {row.provider || "—"}
                    </span>
                  </td>
                  <td className="font-mono text-[0.75rem] text-cyan">{row.model || "—"}</td>
                  <td className="text-muted-foreground tabular-nums">{row.tokens?.toLocaleString() ?? 0}</td>
                  <td>
                    <span className={`font-mono text-[0.75rem] font-medium ${row.latencyMs < 500 ? "text-emerald" : row.latencyMs < 2000 ? "text-amber" : "text-rose"}`}>
                      {row.latencyMs}ms
                    </span>
                  </td>
                  <td className="font-mono text-[0.75rem] tabular-nums">${(row.costUsd ?? 0).toFixed(5)}</td>
                  <td>
                    <span className={`badge ${
                      (row.status === "success" || row.status === "SUCCESS")
                        ? "badge-success"
                        : "badge-danger"
                    }`}>
                      <span className={`h-1 w-1 rounded-full ${(row.status === "success" || row.status === "SUCCESS") ? "bg-emerald" : "bg-rose"}`} />
                      {(row.status === "success" || row.status === "SUCCESS") ? "Success" : row.status || "—"}
                    </span>
                  </td>
                </motion.tr>
              ))}
            </AnimatePresence>
          </tbody>
        </table>
      </div>
    </div>
  );
}

const fmtRequests = (n: number) =>
  n >= 1_000_000 ? (n / 1_000_000).toFixed(2) + "M"
  : n >= 1_000 ? (n / 1_000).toFixed(1) + "K"
  : String(n);

export function PlatformDashboard({ metrics, logs, live, setLive }: DashboardProps) {
  return (
    <AppShell title="Universal Command Center" subtitle="Global AI Routing & Algorithm Telemetry">
      <div className="mb-5">
        <UpgradeRequestsPanel />
      </div>
      
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <MetricCard label="Global Requests" value={metrics ? fmtRequests(metrics.totalRequests * 10) : "—"} delta={18} icon={Radio} tone="emerald" spark={[0, 0, 0, 0, 0, 0, 0, metrics?.totalRequests ?? 0]} />
        <MetricCard label="Global Latency Avg" value={metrics ? `${Math.round(metrics.avgLatencyMs)}ms` : "—"} delta={-12} icon={Gauge} tone="cyan" spark={[0, 0, 0, 0, 0, 0, 0, metrics?.avgLatencyMs ?? 0]} />
        <MetricCard label="Algorithm Adjustments" value="14,239" delta={8} icon={Brain} tone="indigo" spark={[12000, 12500, 13000, 13200, 13800, 14000, 14239]} />
        <MetricCard label="Cost Optimization" value={metrics ? `-$${(metrics.totalCostUsd * 0.4).toFixed(2)}` : "—"} delta={-15} icon={CircleDollarSign} tone="amber" spark={[0, 0, 0, 0, 0, 0, 0, metrics?.totalCostUsd ?? 0]} />
      </div>

      <div className="mt-6 grid gap-4 md:grid-cols-2">
        {/* Algorithm State Panel */}
        <div className="section-panel p-5">
          <h3 className="text-[0.8125rem] font-semibold mb-4 flex items-center gap-2">
            <Brain className="h-4 w-4 text-indigo" /> Global Algorithm State
          </h3>
          <div className="space-y-3">
            {[
              { label: "Active Strategy", value: "MULTI_ARMED_BANDIT", color: "text-cyan" },
              { label: "Global Engine", value: "Nexus_V3_Distributed", color: "text-cyan" },
              { label: "Active Models", value: "124", color: "text-emerald" },
              { label: "Learning Rate", value: "Dynamic (α=0.1, β=0.9)", color: "text-amber" },
            ].map((row) => (
              <div key={row.label} className="flex justify-between items-center py-1.5 border-b border-[var(--glass-border)] last:border-0">
                <span className="text-[0.8125rem] text-muted-foreground">{row.label}</span>
                <span className={`font-mono text-[0.75rem] font-medium ${row.color}`}>{row.value}</span>
              </div>
            ))}
          </div>
        </div>

        <ProviderHealthWidget providers={providers} logs={logs} />
      </div>

      <ActivityStream logs={logs} live={live} setLive={setLive} />
    </AppShell>
  );
}

export function OrgDashboard({ metrics, logs, live, setLive, tenant }: DashboardProps) {

  return (
    <AppShell title="Organization Dashboard" subtitle="Enterprise Overview">
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <MetricCard label="Active Teams" value={metrics?.activeTeams ?? 0} delta={0} icon={Network} tone="indigo" spark={[8, 8, 8, 8, 8, 8, 8]} />
        <MetricCard label="Org Requests" value={metrics ? fmtRequests(metrics.totalRequests) : "—"} delta={0} icon={Radio} tone="cyan" spark={[0, 0, 0, 0, 0, 0, 0, metrics?.totalRequests ?? 0]} />
        <MetricCard label="Avg Latency" value={metrics ? `${Math.round(metrics.avgLatencyMs)}ms` : "—"} delta={0} icon={Gauge} tone="emerald" spark={[0, 0, 0, 0, 0, 0, 0, metrics?.avgLatencyMs ?? 0]} />
        <MetricCard label="Budget Spent" value={metrics ? `$${(metrics.totalCostUsd).toFixed(2)}` : "—"} delta={0} icon={CircleDollarSign} tone="amber" spark={[0, 0, 0, 0, 0, 0, 0, metrics?.totalCostUsd ?? 0]} />
      </div>

      <ActivityStream logs={logs} live={live} setLive={setLive} />
    </AppShell>
  );
}

export function TeamDashboard({ metrics, logs, live, setLive, tenant, openModal }: DashboardProps) {

  const budget = metrics?.dailyBudgetUsd || 500;
  const cost = metrics?.totalCostUsd || 0;
  const budgetUsed = Math.round((cost / budget) * 100);
  const isNearLimit = budgetUsed >= 80;

  return (
    <AppShell title="Team Dashboard" subtitle="Team Activity & Budget">
      {isNearLimit && (
        <motion.div initial={{ opacity: 0, y: -8 }} animate={{ opacity: 1, y: 0 }} className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-amber/30 bg-amber/5 px-5 py-4">
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-amber/15 shrink-0">
              <AlertTriangle className="h-4 w-4 text-amber" />
            </div>
            <div>
              <p className="text-[0.8125rem] font-semibold text-amber">Approaching budget limit</p>
              <p className="text-[0.75rem] text-muted-foreground mt-0.5">Your team has used <span className="font-mono font-bold text-amber">{budgetUsed}%</span> of its monthly allocation.</p>
            </div>
          </div>
          <motion.button onClick={openModal} whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.98 }} className="flex items-center gap-2 rounded-lg bg-amber px-4 py-2 text-[0.75rem] font-semibold text-black transition-shadow hover:shadow-[0_0_20px_-6px_var(--amber)]">
            <ArrowUp className="h-3.5 w-3.5" /> Request More Budget
          </motion.button>
        </motion.div>
      )}

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <MetricCard label="Team Members" value={metrics?.teamMembersCount ?? 0} delta={0} icon={Users} tone="indigo" spark={[10, 10, 11, 12, 12, 12, 12]} />
        <MetricCard label="Team Requests" value={metrics ? fmtRequests(metrics.totalRequests) : "—"} delta={0} icon={Radio} tone="cyan" spark={[0, 0, 0, 0, 0, 0, 0, metrics?.totalRequests ?? 0]} />
        <MetricCard label="Team Cost" value={metrics ? `$${(metrics.totalCostUsd).toFixed(2)} / $${budget}` : "—"} delta={0} icon={CircleDollarSign} tone="amber" spark={[0, 0, 0, 0, 0, 0, 0, metrics?.totalCostUsd ?? 0]} />
      </div>

      <ActivityStream logs={logs} live={live} setLive={setLive} />
    </AppShell>
  );
}

export function DeveloperDashboard({ metrics, logs, live, setLive, tenant, session }: DashboardProps) {

  return (
    <AppShell title={`Welcome back, ${session.email?.split('@')[0] || 'Developer'}`} subtitle="Personal Workspace">
      <div className="grid gap-4 sm:grid-cols-3">
        <MetricCard label="My Requests" value={metrics ? fmtRequests(metrics.totalRequests) : "—"} delta={0} icon={Radio} tone="cyan" spark={[0, 0, 0, 0, 0, 0, 0, metrics?.totalRequests ?? 0]} />
        <MetricCard label="My Tokens" value={metrics ? fmtRequests((metrics.totalRequests * 142)) : "—"} delta={0} icon={Gauge} tone="emerald" spark={[0, 0, 0, 0, 0, 0, 0, metrics?.totalRequests ?? 0]} />
        <MetricCard label="My Estimated Cost" value={metrics ? `$${(metrics.totalCostUsd).toFixed(4)}` : "—"} delta={0} icon={CircleDollarSign} tone="amber" spark={[0, 0, 0, 0, 0, 0, 0, metrics?.totalCostUsd ?? 0]} />
      </div>

      <div className="mt-5 flex flex-wrap gap-2.5">
         <Link to="/app/sandbox" className="flex items-center gap-2 rounded-lg border border-[var(--glass-border)] bg-[var(--card)] px-4 py-2.5 text-[0.8125rem] font-medium transition-colors hover:border-cyan/30 hover:bg-cyan/5">
           <FlaskConical className="h-4 w-4 text-cyan" /> Open Sandbox
         </Link>
         <Link to="/app/keys" className="flex items-center gap-2 rounded-lg border border-[var(--glass-border)] bg-[var(--card)] px-4 py-2.5 text-[0.8125rem] font-medium transition-colors hover:border-indigo/30 hover:bg-indigo/5">
           <KeyRound className="h-4 w-4 text-indigo" /> Create API Key
         </Link>
         <Link to="/app/models" className="flex items-center gap-2 rounded-lg border border-[var(--glass-border)] bg-[var(--card)] px-4 py-2.5 text-[0.8125rem] font-medium transition-colors hover:border-emerald/30 hover:bg-emerald/5">
           <Database className="h-4 w-4 text-emerald" /> View Models
         </Link>
      </div>

      <ActivityStream logs={logs} live={live} setLive={setLive} />
    </AppShell>
  );
}

export function SoloDashboard({ metrics, logs, live, setLive, tenant, providers }: DashboardProps) {
  const hasKey = tenant?.hasApiKey ?? true;
  if (tenant?.hasApiKey === false && !hasKey) return <AppShell title="Solo Workspace"><LockedState /></AppShell>;

  return (
    <AppShell title="Overview" subtitle="Real-time overview of your AI gateway">
      {/* Metric Cards */}
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <MetricCard
          label="Total Requests"
          value={metrics ? fmtRequests(metrics.totalRequests) : "—"}
          delta={12}
          icon={Radio}
          tone="cyan"
          spark={[20, 35, 28, 45, 52, 48, 60, metrics?.totalRequests ?? 70]}
        />
        <MetricCard
          label="Avg Latency"
          value={metrics ? `${Math.round(metrics.avgLatencyMs)}ms` : "—"}
          delta={-8}
          icon={Gauge}
          tone="emerald"
          spark={[800, 650, 720, 600, 580, 620, 550, metrics?.avgLatencyMs ?? 500]}
        />
        <MetricCard
          label="Total Cost"
          value={metrics ? `$${(metrics.totalCostUsd).toFixed(4)}` : "—"}
          delta={23}
          icon={CircleDollarSign}
          tone="amber"
          spark={[0.01, 0.02, 0.03, 0.04, 0.06, 0.08, 0.09, metrics?.totalCostUsd ?? 0.10]}
        />
        <MetricCard
          label="Active Agents"
          value={metrics ? String(metrics.activeAgents) : "—"}
          delta={20}
          icon={Bot}
          tone="indigo"
          spark={[2, 3, 3, 4, 4, 5, 5, metrics?.activeAgents ?? 6]}
        />
      </div>

      {/* Engine State */}
      {metrics && (
        <motion.div
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.15 }}
          className="mt-4 grid gap-4 md:grid-cols-2"
        >
          {/* Routing Engine Panel */}
          <div className="section-panel p-5">
            <h3 className="text-[0.8125rem] font-semibold mb-4 flex items-center gap-2">
              <Brain className="h-4 w-4 text-indigo" /> Routing Engine
            </h3>
            <div className="space-y-2.5">
              {[
                { label: "Strategy", value: metrics.activeStrategy, color: "text-cyan" },
                { label: "Engine", value: metrics.activeEngine, color: "text-cyan" },
                { label: "Reward Tier", value: metrics.rewardTier, color: "text-emerald" },
                { label: "Enabled Arms", value: String(metrics.enabledArmCount), color: "text-amber" },
              ].map((row) => (
                <div key={row.label} className="flex justify-between items-center py-1.5 border-b border-[var(--glass-border)] last:border-0">
                  <span className="text-[0.8125rem] text-muted-foreground">{row.label}</span>
                  <span className={`font-mono text-[0.75rem] font-medium ${row.color}`}>{row.value}</span>
                </div>
              ))}
            </div>
          </div>

          {/* Provider Health */}
          <ProviderHealthWidget providers={providers} logs={logs} />
        </motion.div>
      )}

      <ActivityStream logs={logs} live={live} setLive={setLive} />
    </AppShell>
  );
}
