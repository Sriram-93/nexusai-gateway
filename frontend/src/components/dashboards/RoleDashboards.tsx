import { Link } from "@tanstack/react-router";
import { motion, AnimatePresence } from "motion/react";
import {
  Bot, CircleDollarSign, Gauge, Radio, Waves, Brain,
  ArrowUp, Boxes, KeyRound, AlertTriangle, ChevronRight,
  Users, Building2, Server, FlaskConical, Database, Network
} from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { MetricCard } from "@/components/nexus/MetricCard";
import { UpgradeRequestsPanel } from "@/lib/upgrade-requests";
import type { GlobalMetrics, ActivityLog, StreamEvent } from "@/lib/api";

type DashboardProps = {
  metrics: GlobalMetrics | null;
  logs: (ActivityLog | StreamEvent)[];
  live: boolean;
  setLive: (v: boolean | ((prev: boolean) => boolean)) => void;
  tenant: any;
  openModal: () => void;
  session: any;
};

export function LockedState() {
  return (
    <div className="mt-8 flex flex-col items-center justify-center rounded-2xl border border-dashed border-[var(--glass-border)] bg-[var(--glass-bg)] p-12 text-center shadow-sm">
      <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-2xl bg-cyan/10">
        <Boxes className="h-8 w-8 text-cyan" />
      </div>
      <h2 className="text-xl font-semibold tracking-tight">Workspace Locked</h2>
      <p className="mt-2 max-w-md text-sm text-muted-foreground">
        To view live routing metrics and activity streams, you need to connect at least one AI provider first.
      </p>
      <div className="mt-8 grid w-full max-w-2xl gap-3 sm:grid-cols-2">
        {[
          { icon: Boxes, label: "Connect a Provider", sub: "Add your first AI provider", to: "/app/providers", color: "cyan" },
          { icon: KeyRound, label: "Generate an API Key", sub: "Start routing traffic", to: "/app/keys", color: "indigo" },
        ].map((a) => (
          <Link key={a.to} to={a.to as never}>
            <motion.div
              whileHover={{ y: -3, scale: 1.01 }}
              whileTap={{ scale: 0.98 }}
              className="glass flex h-full items-center gap-4 rounded-2xl p-4 transition-all hover:border-[color-mix(in_oklab,var(--foreground)_20%,transparent)]"
            >
              <div className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-${a.color}/10`}>
                <a.icon className={`h-5 w-5 text-${a.color}`} />
              </div>
              <div className="min-w-0 text-left">
                <p className="text-sm font-medium">{a.label}</p>
                <p className="truncate text-xs text-muted-foreground">{a.sub}</p>
              </div>
              <ChevronRight className="ml-auto h-4 w-4 shrink-0 text-muted-foreground" />
            </motion.div>
          </Link>
        ))}
      </div>
    </div>
  );
}

function ActivityStream({ logs, live, setLive }: { logs: any[]; live: boolean; setLive: any }) {
  return (
    <div className="glass mt-6 overflow-hidden rounded-2xl">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b px-5 py-4">
        <div className="flex items-center gap-2">
          <Waves className="h-4 w-4 text-cyan" />
          <p className="text-sm font-medium tracking-tight">Activity Stream</p>
          <span className="text-xs text-muted-foreground">({logs.length} entries)</span>
        </div>
        <button
          onClick={() => setLive((v: any) => !v)}
          className="glass flex items-center gap-2 rounded-full px-3 py-1.5 text-xs transition-colors hover:bg-[var(--glass-hover)]"
        >
          <span className={`h-2 w-2 rounded-full ${live ? "animate-pulse bg-emerald" : "bg-muted-foreground"}`} />
          {live ? "Live" : "Paused"}
        </button>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full min-w-[46rem] text-sm">
          <thead>
            <tr className="text-left text-[0.7rem] uppercase tracking-[0.14em] text-muted-foreground">
              {["Timestamp", "Provider", "Model", "Tokens", "Latency", "Cost", "Status"].map((h) => (
                <th key={h} className="px-5 py-3 font-medium">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            <AnimatePresence initial={false}>
              {logs.length === 0 && (
                <motion.tr key="empty" exit={{ opacity: 0 }}>
                  <td colSpan={7} className="px-5 py-10 text-center text-xs text-muted-foreground">
                    <Brain className="mx-auto mb-3 h-8 w-8 opacity-30" />
                    No requests routed yet. Use the Sandbox to send your first request.
                  </td>
                </motion.tr>
              )}
              {logs.map((row) => (
                <motion.tr
                  key={row.id}
                  layout
                  initial={{ opacity: 0, y: -12, backgroundColor: "var(--glass-hover)" }}
                  animate={{ opacity: 1, y: 0, backgroundColor: "transparent" }}
                  exit={{ opacity: 0 }}
                  transition={{ duration: 0.45 }}
                  className="border-t transition-colors hover:bg-[var(--glass-hover)]"
                >
                  <td className="px-5 py-3 font-mono text-xs text-muted-foreground">
                    {row.timestamp ? new Date(row.timestamp).toLocaleTimeString() : "—"}
                  </td>
                  <td className="px-5 py-3">{row.provider || "—"}</td>
                  <td className="px-5 py-3 font-mono text-xs text-cyan">{row.model || "—"}</td>
                  <td className="px-5 py-3 text-muted-foreground">{row.tokens?.toLocaleString() ?? 0}</td>
                  <td className="px-5 py-3">
                    <span className={row.latencyMs < 500 ? "text-emerald" : "text-amber"}>
                      {row.latencyMs}ms
                    </span>
                  </td>
                  <td className="px-5 py-3 font-mono text-xs">${(row.costUsd ?? 0).toFixed(5)}</td>
                  <td className="px-5 py-3">
                    <span
                      className={`rounded-full border px-2.5 py-0.5 text-[0.7rem] font-medium ${
                        row.status === "success"
                          ? "border-emerald/40 bg-emerald/14 text-emerald"
                          : "border-destructive/40 bg-destructive/14 text-destructive"
                      }`}
                    >
                      {row.status === "success" ? "Success" : row.status || "—"}
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
    <AppShell title="Platform Dashboard" subtitle="Global Command Center">
      <div className="mb-5">
        <UpgradeRequestsPanel />
      </div>
      
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <MetricCard label="Total Organizations" value="1,284" delta={12} icon={Building2} tone="indigo" spark={[800, 900, 1000, 1100, 1200, 1250, 1284]} />
        <MetricCard label="Active Users" value="8,421" delta={4.2} icon={Users} tone="cyan" spark={[7000, 7200, 7500, 7800, 8000, 8200, 8421]} />
        <MetricCard label="Global Requests" value={metrics ? fmtRequests(metrics.totalRequests * 10) : "—"} delta={18} icon={Radio} tone="emerald" spark={[0, 0, 0, 0, 0, 0, 0, metrics?.totalRequests ?? 0]} />
        <MetricCard label="Platform Revenue" value={metrics ? `$${(metrics.totalCostUsd * 2.5).toFixed(2)}` : "—"} delta={8} icon={CircleDollarSign} tone="amber" spark={[0, 0, 0, 0, 0, 0, 0, metrics?.totalCostUsd ?? 0]} />
      </div>

      <div className="mt-6 grid gap-4 md:grid-cols-2">
        <div className="glass rounded-2xl p-5">
          <h3 className="text-sm font-medium mb-4 flex items-center gap-2"><Server className="h-4 w-4 text-cyan" /> Provider Health</h3>
          <div className="space-y-3">
            <div className="flex justify-between items-center text-sm"><span className="text-muted-foreground">OpenAI</span><span className="text-emerald font-medium flex items-center gap-1.5"><span className="h-1.5 w-1.5 rounded-full bg-emerald"></span> Healthy</span></div>
            <div className="flex justify-between items-center text-sm"><span className="text-muted-foreground">Anthropic</span><span className="text-emerald font-medium flex items-center gap-1.5"><span className="h-1.5 w-1.5 rounded-full bg-emerald"></span> Healthy</span></div>
            <div className="flex justify-between items-center text-sm"><span className="text-muted-foreground">Groq</span><span className="text-amber font-medium flex items-center gap-1.5"><span className="h-1.5 w-1.5 rounded-full bg-amber"></span> Degraded</span></div>
          </div>
        </div>
        <div className="glass rounded-2xl p-5">
          <h3 className="text-sm font-medium mb-4 flex items-center gap-2"><AlertTriangle className="h-4 w-4 text-amber" /> System Alerts</h3>
          <div className="space-y-3">
             <div className="flex justify-between items-center text-sm"><span className="text-muted-foreground">Groq latency spike detected</span><span className="text-xs text-amber font-mono">2m ago</span></div>
             <div className="flex justify-between items-center text-sm"><span className="text-muted-foreground">New organization signed up</span><span className="text-xs text-cyan font-mono">15m ago</span></div>
          </div>
        </div>
      </div>
    </AppShell>
  );
}

export function OrgDashboard({ metrics, logs, live, setLive, tenant }: DashboardProps) {

  return (
    <AppShell title="Organization Dashboard" subtitle="Enterprise Overview">
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <MetricCard label="Active Teams" value="8" delta={0} icon={Network} tone="indigo" spark={[8, 8, 8, 8, 8, 8, 8]} />
        <MetricCard label="Org Requests" value={metrics ? fmtRequests(metrics.totalRequests) : "—"} delta={0} icon={Radio} tone="cyan" spark={[0, 0, 0, 0, 0, 0, 0, metrics?.totalRequests ?? 0]} />
        <MetricCard label="Avg Latency" value={metrics ? `${Math.round(metrics.avgLatencyMs)}ms` : "—"} delta={0} icon={Gauge} tone="emerald" spark={[0, 0, 0, 0, 0, 0, 0, metrics?.avgLatencyMs ?? 0]} />
        <MetricCard label="Budget Spent" value={metrics ? `$${(metrics.totalCostUsd).toFixed(2)} / $5,000` : "—"} delta={0} icon={CircleDollarSign} tone="amber" spark={[0, 0, 0, 0, 0, 0, 0, metrics?.totalCostUsd ?? 0]} />
      </div>

      <ActivityStream logs={logs} live={live} setLive={setLive} />
    </AppShell>
  );
}

export function TeamDashboard({ metrics, logs, live, setLive, tenant, openModal }: DashboardProps) {

  const budgetUsed = 82; 
  const isNearLimit = budgetUsed >= 80;

  return (
    <AppShell title="Team Dashboard" subtitle="Team Activity & Budget">
      {isNearLimit && (
        <motion.div initial={{ opacity: 0, y: -8 }} animate={{ opacity: 1, y: 0 }} className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-amber/30 bg-amber/10 px-5 py-4">
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-amber/20 shrink-0">
              <AlertTriangle className="h-4.5 w-4.5 text-amber" />
            </div>
            <div>
              <p className="text-sm font-semibold text-amber">Approaching budget limit</p>
              <p className="text-xs text-muted-foreground mt-0.5">Your team has used <span className="font-mono font-bold text-amber">{budgetUsed}%</span> of its monthly allocation.</p>
            </div>
          </div>
          <motion.button onClick={openModal} whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.98 }} className="flex items-center gap-2 rounded-xl bg-amber px-4 py-2 text-xs font-semibold text-black transition-shadow hover:shadow-[0_0_20px_-6px_var(--amber)]">
            <ArrowUp className="h-3.5 w-3.5" /> Request More Budget
          </motion.button>
        </motion.div>
      )}

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <MetricCard label="Team Members" value="12" delta={0} icon={Users} tone="indigo" spark={[10, 10, 11, 12, 12, 12, 12]} />
        <MetricCard label="Team Requests" value={metrics ? fmtRequests(metrics.totalRequests) : "—"} delta={0} icon={Radio} tone="cyan" spark={[0, 0, 0, 0, 0, 0, 0, metrics?.totalRequests ?? 0]} />
        <MetricCard label="Team Tokens" value={metrics ? fmtRequests((metrics.totalRequests * 142)) : "—"} delta={0} icon={Gauge} tone="emerald" spark={[0, 0, 0, 0, 0, 0, 0, metrics?.totalRequests ?? 0]} />
        <MetricCard label="Team Cost" value={metrics ? `$${(metrics.totalCostUsd).toFixed(2)} / $500` : "—"} delta={0} icon={CircleDollarSign} tone="amber" spark={[0, 0, 0, 0, 0, 0, 0, metrics?.totalCostUsd ?? 0]} />
      </div>

      <ActivityStream logs={logs} live={live} setLive={setLive} />
    </AppShell>
  );
}

export function DeveloperDashboard({ metrics, logs, live, setLive, tenant, session }: DashboardProps) {

  return (
    <AppShell title={`Good Morning, ${session.email?.split('@')[0] || 'Developer'}`} subtitle="Personal Workspace">
      <div className="grid gap-4 sm:grid-cols-3">
        <MetricCard label="My Requests" value={metrics ? fmtRequests(metrics.totalRequests) : "—"} delta={0} icon={Radio} tone="cyan" spark={[0, 0, 0, 0, 0, 0, 0, metrics?.totalRequests ?? 0]} />
        <MetricCard label="My Tokens" value={metrics ? fmtRequests((metrics.totalRequests * 142)) : "—"} delta={0} icon={Gauge} tone="emerald" spark={[0, 0, 0, 0, 0, 0, 0, metrics?.totalRequests ?? 0]} />
        <MetricCard label="My Estimated Cost" value={metrics ? `$${(metrics.totalCostUsd).toFixed(4)}` : "—"} delta={0} icon={CircleDollarSign} tone="amber" spark={[0, 0, 0, 0, 0, 0, 0, metrics?.totalCostUsd ?? 0]} />
      </div>

      <div className="mt-6 flex flex-wrap gap-3">
         <Link to="/app/sandbox" className="glass flex items-center gap-2 rounded-xl px-4 py-2 text-sm font-medium hover:bg-[var(--glass-hover)] transition-colors"><FlaskConical className="h-4 w-4 text-cyan" /> Open Sandbox</Link>
         <Link to="/app/keys" className="glass flex items-center gap-2 rounded-xl px-4 py-2 text-sm font-medium hover:bg-[var(--glass-hover)] transition-colors"><KeyRound className="h-4 w-4 text-indigo" /> Create API Key</Link>
         <Link to="/app/models" className="glass flex items-center gap-2 rounded-xl px-4 py-2 text-sm font-medium hover:bg-[var(--glass-hover)] transition-colors"><Database className="h-4 w-4 text-emerald" /> View Models</Link>
      </div>

      <ActivityStream logs={logs} live={live} setLive={setLive} />
    </AppShell>
  );
}

export function SoloDashboard({ metrics, logs, live, setLive, tenant }: DashboardProps) {
  if (tenant?.hasApiKey === false) return <AppShell title="Solo Workspace"><LockedState /></AppShell>;

  return (
    <AppShell title="Solo Workspace" subtitle="Real-time routing telemetry">
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <MetricCard label="Active Agents" value={metrics ? String(metrics.activeAgents) : "—"} delta={0} icon={Bot} tone="indigo" spark={[0, 0, 0, 0, 0, 0, 0, metrics?.activeAgents ?? 0]} />
        <MetricCard label="Total Requests" value={metrics ? fmtRequests(metrics.totalRequests) : "—"} delta={0} icon={Radio} tone="cyan" spark={[0, 0, 0, 0, 0, 0, 0, metrics?.totalRequests ?? 0]} />
        <MetricCard label="Avg Latency" value={metrics ? `${Math.round(metrics.avgLatencyMs)}ms` : "—"} delta={0} icon={Gauge} tone="emerald" spark={[0, 0, 0, 0, 0, 0, 0, metrics?.avgLatencyMs ?? 0]} />
        <MetricCard label="Total Cost" value={metrics ? `$${(metrics.totalCostUsd).toFixed(4)}` : "—"} delta={0} icon={CircleDollarSign} tone="amber" spark={[0, 0, 0, 0, 0, 0, 0, metrics?.totalCostUsd ?? 0]} />
      </div>

      {metrics && (
        <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} className="glass mt-4 rounded-2xl p-5">
          <div className="flex flex-wrap items-center gap-6 text-xs">
            <div className="flex items-center gap-2">
              <Brain className="h-4 w-4 text-indigo" />
              <span className="text-muted-foreground">Active Strategy</span>
              <span className="font-mono text-cyan">{metrics.activeStrategy}</span>
            </div>
            <div className="flex items-center gap-2">
              <span className="text-muted-foreground">Engine</span>
              <span className="font-mono text-cyan">{metrics.activeEngine}</span>
            </div>
            <div className="flex items-center gap-2">
              <span className="text-muted-foreground">Reward Tier</span>
              <span className="font-mono text-emerald">{metrics.rewardTier}</span>
            </div>
            <div className="flex items-center gap-2">
              <span className="text-muted-foreground">Enabled Arms</span>
              <span className="font-mono text-amber">{metrics.enabledArmCount}</span>
            </div>
          </div>
        </motion.div>
      )}

      <ActivityStream logs={logs} live={live} setLive={setLive} />
    </AppShell>
  );
}
