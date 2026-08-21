import { createFileRoute } from "@tanstack/react-router";
import { motion } from "motion/react";
import { useMemo, useState, useEffect } from "react";
import { Search, ScrollText, RefreshCw, Eye } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { adminApi, type RequestLog } from "@/lib/api";
import { useUser } from "@/lib/user-context";
import { Link } from "@tanstack/react-router";

export const Route = createFileRoute("/app/team-logs")({
  head: () => ({
    meta: [
      { title: "Team Logs — NexusAI" },
      { name: "description", content: "View all request logs across your entire organization." },
    ],
  }),
  component: TeamLogs,
});

function TeamLogs() {
  const [rows, setRows] = useState<RequestLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [q, setQ] = useState("");
  const { user } = useUser();

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await adminApi.getTeamLogs();
      setRows(data);
    } catch (err: any) {
      setError(err.message ?? "Failed to load logs");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const filtered = useMemo(
    () =>
      rows.filter((r) =>
        `${r.provider ?? ""} ${r.model ?? ""} ${r.status ?? ""} ${r.userId ?? ""}`.toLowerCase().includes(q.toLowerCase()),
      ),
    [rows, q],
  );

  return (
    <AppShell title="Team Logs" subtitle="Organization-wide request audit trail">
      <div className="glass overflow-hidden rounded-2xl">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b px-5 py-4">
          <div className="flex items-center gap-2">
            <ScrollText className="h-4 w-4 text-cyan" />
            <p className="text-sm font-medium tracking-tight">
              {loading ? "Loading…" : `${filtered.length} of ${rows.length} entries`}
            </p>
          </div>
          <div className="flex items-center gap-2">
            <div className="relative">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" />
              <Input
                value={q}
                onChange={(e) => setQ(e.target.value)}
                placeholder="Filter by user, model…"
                className="h-9 w-64 rounded-lg border-[var(--glass-border)] bg-[var(--glass-bg)] pl-9 text-xs backdrop-blur-md"
              />
            </div>
            <Button
              onClick={load}
              variant="outline"
              size="sm"
              className="glass h-9 rounded-lg text-xs"
            >
              <RefreshCw className={`h-3.5 w-3.5 ${loading ? "animate-spin" : ""}`} />
            </Button>
          </div>
        </div>

        {error && (
          <div className="px-5 py-4 text-xs text-destructive border-b border-destructive/20 bg-destructive/5">
            {error}
          </div>
        )}

        <div className="overflow-x-auto">
          <table className="w-full min-w-[58rem] text-sm">
            <thead>
              <tr className="text-left text-[0.7rem] uppercase tracking-[0.14em] text-muted-foreground">
                {["Timestamp", "User ID", "Provider", "Model", "Cost", "Latency", "Status", ""].map((h) => (
                  <th key={h} className="px-5 py-3 font-medium">
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {loading && Array.from({ length: 6 }).map((_, i) => (
                <tr key={i} className="border-t">
                  {Array.from({ length: 8 }).map((_, j) => (
                    <td key={j} className="px-5 py-3">
                      <div className="h-3 animate-pulse rounded bg-[var(--glass-hover)]" />
                    </td>
                  ))}
                </tr>
              ))}
              {!loading && filtered.length === 0 && (
                <tr>
                  <td colSpan={8} className="px-5 py-8 text-center text-xs text-muted-foreground">
                    No requests found for this organization.
                  </td>
                </tr>
              )}
              {!loading && filtered.map((r, i) => (
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
                  <td className="px-5 py-3 font-mono text-xs text-cyan">{r.userId || "—"}</td>
                  <td className="px-5 py-3 text-xs">{r.provider || "—"}</td>
                  <td className="px-5 py-3 font-mono text-xs text-muted-foreground">{r.model || "—"}</td>
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
                  <td className="px-5 py-3 text-right">
                    {r.userId && (
                      <Link to={`/app/inspect/$userId`} params={{ userId: r.userId }}>
                        <Button variant="ghost" size="icon" className="h-8 w-8 rounded-full hover:bg-[var(--glass-border)]">
                          <Eye className="h-4 w-4 text-muted-foreground" />
                        </Button>
                      </Link>
                    )}
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
