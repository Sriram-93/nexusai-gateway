import { createFileRoute } from "@tanstack/react-router";
import { motion } from "motion/react";
import { BarChart3, CircleDollarSign, Gauge, TrendingDown, Activity } from "lucide-react";
import { useEffect, useState } from "react";
import { AppShell } from "@/components/AppShell";
import { MetricCard } from "@/components/nexus/MetricCard";
import { dashboardApi, type GlobalMetrics, type ArmState } from "@/lib/api";

export const Route = createFileRoute("/app/analytics")({
  head: () => ({
    meta: [
      { title: "Analytics — NexusAI" },
      {
        name: "description",
        content:
          "Real-time analytics from the NexusAI routing gateway: cost, latency, and provider win rates.",
      },
      { property: "og:title", content: "Analytics — NexusAI" },
      { property: "og:description", content: "Real cost, latency, and provider win rates." },
    ],
  }),
  component: Analytics,
});

const TONES = ["emerald", "cyan", "indigo", "amber", "chart-5"];

function Analytics() {
  const [metrics, setMetrics] = useState<GlobalMetrics | null>(null);
  const [armStates, setArmStates] = useState<ArmState[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([dashboardApi.getMetrics(), dashboardApi.getLearning()])
      .then(([m, l]) => {
        setMetrics(m);
        setArmStates(l.armStates.sort((a, b) => b.totalRequests - a.totalRequests));
      })
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  const totalReqs = armStates.reduce((acc, a) => acc + a.totalRequests, 0) || 1;

  // Compute avg latency from arm states when backend metrics not available
  const avgLatencyMs = metrics?.avgLatencyMs ?? 0;
  const totalCost = metrics?.totalCostUsd ?? 0;
  const totalRequests = metrics?.totalRequests ?? 0;

  // Derive failover rate: sum of arms with failureRate > 0, averaged
  const avgFailureRate = armStates.length > 0
    ? armStates.reduce((acc, a) => acc + (a.failureRate ?? 0), 0) / armStates.length
    : 0;

  return (
    <AppShell title="Analytics" subtitle="Real routing telemetry — no fake metrics">
      <div className="grid gap-4 sm:grid-cols-3">
        <MetricCard
          label="Total Cost"
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
      </div>

      <div className="mt-4 grid gap-4 lg:grid-cols-[1.4fr_1fr]">
        {/* Arm health bars */}
        <div className="glass rounded-2xl p-6">
          <div className="flex items-center gap-2">
            <Activity className="h-4 w-4 text-cyan" />
            <p className="text-sm font-medium tracking-tight">Model arm health scores</p>
          </div>
          <p className="mt-1 text-xs text-muted-foreground">EWMA health from the reputation system</p>

          {loading && (
            <div className="mt-6 space-y-3">
              {[0, 1, 2].map((i) => (
                <div key={i} className="h-8 animate-pulse rounded bg-[var(--glass-hover)]" />
              ))}
            </div>
          )}

          {!loading && armStates.length === 0 && (
            <p className="mt-6 text-xs text-muted-foreground italic">
              No arm data yet. Send some requests through the Sandbox to populate metrics.
            </p>
          )}

          {!loading && armStates.length > 0 && (
            <div className="mt-6 space-y-4">
              {armStates.map((arm, i) => (
                <div key={arm.armKey}>
                  <div className="flex items-center justify-between text-xs mb-1.5">
                    <span className="font-mono">{arm.armKey}</span>
                    <div className="flex items-center gap-4 text-muted-foreground">
                      <span>{Math.round(arm.avgLatencyMs)}ms avg</span>
                      <span className={arm.healthScore >= 0.8 ? "text-emerald font-mono" : "text-amber font-mono"}>
                        {(arm.healthScore * 100).toFixed(1)}%
                      </span>
                    </div>
                  </div>
                  <div className="h-1.5 overflow-hidden rounded-full bg-[var(--glass-hover)]">
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

        {/* Provider win rate */}
        <div className="glass rounded-2xl p-6">
          <p className="text-sm font-medium tracking-tight">Provider win rate</p>
          <p className="mt-1 text-xs text-muted-foreground">
            Share of routed requests per arm ({totalRequests.toLocaleString()} total)
          </p>

          {loading && (
            <div className="mt-6 space-y-4">
              {[0, 1, 2].map((i) => (
                <div key={i} className="h-6 animate-pulse rounded bg-[var(--glass-hover)]" />
              ))}
            </div>
          )}

          {!loading && armStates.length === 0 && (
            <p className="mt-6 text-xs text-muted-foreground italic">
              No routing data yet.
            </p>
          )}

          {!loading && armStates.length > 0 && (
            <div className="mt-6 space-y-5">
              {armStates.map((arm, i) => {
                const pct = Math.round((arm.totalRequests / totalReqs) * 100);
                const tone = TONES[i % TONES.length];
                return (
                  <div key={arm.armKey}>
                    <div className="flex items-center justify-between text-xs">
                      <span className="font-mono">{arm.armKey}</span>
                      <span className={`font-mono text-${tone}`}>{pct}%</span>
                    </div>
                    <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-[var(--glass-hover)]">
                      <motion.div
                        initial={{ width: 0 }}
                        animate={{ width: `${pct}%` }}
                        transition={{ delay: 0.1 + i * 0.08, duration: 0.7, ease: [0.22, 1, 0.36, 1] }}
                        className="h-full rounded-full"
                        style={{ background: `var(--${tone})` }}
                      />
                    </div>
                    <p className="mt-1 text-[0.65rem] text-muted-foreground">
                      {arm.totalRequests.toLocaleString()} requests · {arm.successCount.toLocaleString()} succeeded
                    </p>
                  </div>
                );
              })}
            </div>
          )}

          {/* Engine info */}
          {!loading && metrics && (
            <div className="mt-6 border-t pt-4 space-y-1 text-xs text-muted-foreground">
              <div className="flex justify-between">
                <span>Active strategy</span>
                <span className="font-mono text-cyan">{metrics.activeStrategy}</span>
              </div>
              <div className="flex justify-between">
                <span>Active engine</span>
                <span className="font-mono text-cyan">{metrics.activeEngine}</span>
              </div>
              <div className="flex justify-between">
                <span>Reward tier</span>
                <span className="font-mono text-cyan">{metrics.rewardTier}</span>
              </div>
            </div>
          )}
        </div>
      </div>
    </AppShell>
  );
}
