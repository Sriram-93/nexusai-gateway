import { createFileRoute } from "@tanstack/react-router";
import { motion } from "motion/react";
import { useState, useEffect } from "react";
import { User, Activity, DollarSign, Clock, ScrollText, ArrowLeft } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { adminApi, type RequestLog } from "@/lib/api";
import { Link } from "@tanstack/react-router";
import { Button } from "@/components/ui/button";

export const Route = createFileRoute("/app/inspect/$userId")({
  component: InspectMember,
});

function InspectMember() {
  const { userId } = Route.useParams();
  const [summary, setSummary] = useState<any>(null);
  const [logs, setLogs] = useState<RequestLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      try {
        const [sumData, logData] = await Promise.all([
          adminApi.getMemberSummary(userId),
          adminApi.getMemberLogs(userId)
        ]);
        setSummary(sumData);
        setLogs(logData);
      } catch (err: any) {
        setError(err.message ?? "Failed to load member data");
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [userId]);

  if (loading) {
    return (
      <AppShell title="Inspector" subtitle="Loading member data...">
        <div className="flex h-64 items-center justify-center">
          <div className="h-8 w-8 animate-spin rounded-full border-4 border-cyan border-t-transparent" />
        </div>
      </AppShell>
    );
  }

  if (error || !summary) {
    return (
      <AppShell title="Inspector" subtitle="Error loading data">
        <div className="glass rounded-xl p-6 text-destructive">{error || "Member not found"}</div>
      </AppShell>
    );
  }

  return (
    <AppShell title="Member Inspector" subtitle={`Analyzing usage for ${summary.email}`}>
      <div className="mb-6 flex items-center gap-4">
        <Link to="/app/members">
          <Button variant="outline" size="sm" className="glass h-9 rounded-lg">
            <ArrowLeft className="mr-2 h-4 w-4" />
            Back to Team
          </Button>
        </Link>
        <div className="flex items-center gap-2 rounded-full border border-[var(--glass-border)] bg-[var(--glass-bg)] px-3 py-1">
          <User className="h-4 w-4 text-cyan" />
          <span className="text-xs font-medium">{summary.role.replace('_', ' ')}</span>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
        <div className="glass flex flex-col gap-1 rounded-2xl p-5">
          <div className="flex items-center gap-2 text-muted-foreground">
            <Activity className="h-4 w-4" />
            <span className="text-xs uppercase tracking-widest font-semibold">Total Requests</span>
          </div>
          <span className="mt-1 text-3xl font-light tracking-tight">{summary.totalRequests}</span>
        </div>
        <div className="glass flex flex-col gap-1 rounded-2xl p-5">
          <div className="flex items-center gap-2 text-muted-foreground">
            <DollarSign className="h-4 w-4" />
            <span className="text-xs uppercase tracking-widest font-semibold">Total Cost</span>
          </div>
          <span className="mt-1 text-3xl font-light tracking-tight">${summary.totalCostUsd.toFixed(5)}</span>
        </div>
        <div className="glass flex flex-col gap-1 rounded-2xl p-5">
          <div className="flex items-center gap-2 text-muted-foreground">
            <Clock className="h-4 w-4" />
            <span className="text-xs uppercase tracking-widest font-semibold">Avg Latency</span>
          </div>
          <span className="mt-1 text-3xl font-light tracking-tight">{summary.avgLatencyMs}ms</span>
        </div>
      </div>

      <div className="glass overflow-hidden rounded-2xl">
        <div className="flex items-center gap-2 border-b px-5 py-4">
          <ScrollText className="h-4 w-4 text-indigo" />
          <p className="text-sm font-medium tracking-tight">Recent Activity</p>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full min-w-[58rem] text-sm">
            <thead>
              <tr className="text-left text-[0.7rem] uppercase tracking-[0.14em] text-muted-foreground">
                <th className="px-5 py-3 font-medium">Timestamp</th>
                <th className="px-5 py-3 font-medium">Provider</th>
                <th className="px-5 py-3 font-medium">Model</th>
                <th className="px-5 py-3 font-medium">Cost</th>
                <th className="px-5 py-3 font-medium">Latency</th>
                <th className="px-5 py-3 font-medium">Status</th>
              </tr>
            </thead>
            <tbody>
              {logs.length === 0 && (
                <tr>
                  <td colSpan={6} className="px-5 py-8 text-center text-xs text-muted-foreground">
                    No requests found for this user.
                  </td>
                </tr>
              )}
              {logs.map((r, i) => (
                <motion.tr
                  key={r.id}
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  transition={{ delay: Math.min(i * 0.01, 0.3) }}
                  className="border-t transition-colors hover:bg-[var(--glass-hover)]"
                >
                  <td className="px-5 py-3 font-mono text-xs text-muted-foreground">
                    {r.timestamp ? new Date(r.timestamp).toLocaleString() : "—"}
                  </td>
                  <td className="px-5 py-3 text-xs">{r.provider || "—"}</td>
                  <td className="px-5 py-3 font-mono text-xs text-cyan">{r.model || "—"}</td>
                  <td className="px-5 py-3 font-mono text-xs">${(r.costUsd ?? 0).toFixed(5)}</td>
                  <td className="px-5 py-3 text-xs">
                    <span className={(r.latencyMs ?? 0) < 500 ? "text-emerald" : "text-amber"}>
                      {r.latencyMs ?? 0}ms
                    </span>
                  </td>
                  <td className="px-5 py-3">
                    <span
                      className={`rounded-full border px-2.5 py-0.5 text-[0.7rem] font-medium ${
                        r.status === "success"
                          ? "border-[color-mix(in_oklab,var(--emerald)_40%,transparent)] bg-[color-mix(in_oklab,var(--emerald)_14%,transparent)] text-emerald"
                          : "border-[color-mix(in_oklab,var(--destructive)_40%,transparent)] bg-[color-mix(in_oklab,var(--destructive)_14%,transparent)] text-destructive"
                      }`}
                    >
                      {r.status === "success" ? "Success" : r.status ?? "—"}
                    </span>
                  </td>
                </motion.tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </AppShell>
  );
}
