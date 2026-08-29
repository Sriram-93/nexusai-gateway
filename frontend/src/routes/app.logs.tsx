import { createFileRoute } from "@tanstack/react-router";
import { motion } from "motion/react";
import { useMemo, useState, useEffect } from "react";
import { Search, ScrollText, RefreshCw, AlertCircle } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { chatApi, type RequestLog } from "@/lib/api";

export const Route = createFileRoute("/app/logs")({
  head: () => ({
    meta: [
      { title: "Request Logs — NexusAI" },
      {
        name: "description",
        content:
          "Searchable audit trail of every request routed through your NexusAI gateway with latency, tokens, and cost per call.",
      },
      { property: "og:title", content: "Request Logs — NexusAI" },
      { property: "og:description", content: "Immutable audit trail of every routed request." },
    ],
  }),
  component: Logs,
});

function Logs() {
  const [rows, setRows] = useState<RequestLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [q, setQ] = useState("");

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await chatApi.getLogs();
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
        `${r.provider ?? ""} ${r.model ?? ""} ${r.status ?? ""} ${r.tenantId ?? ""}`.toLowerCase().includes(q.toLowerCase()),
      ),
    [rows, q],
  );

  const getLatencyColor = (ms: number) => {
    if (ms < 500) return "text-emerald";
    if (ms < 2000) return "text-amber";
    return "text-rose";
  };

  return (
    <AppShell title="Request Logs" subtitle="Full request audit trail from the database">
      <div className="section-panel">
        {/* Toolbar */}
        <div className="section-panel-header">
          <div className="flex items-center gap-2">
            <ScrollText className="h-4 w-4 text-cyan" />
            <p className="text-[0.8125rem] font-semibold tracking-tight">
              {loading ? "Loading…" : `${filtered.length} of ${rows.length} entries`}
            </p>
          </div>
          <div className="flex items-center gap-2">
            <div className="relative">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" />
              <Input
                value={q}
                onChange={(e) => setQ(e.target.value)}
                placeholder="Filter by provider, model, status…"
                className="h-8 w-56 rounded-lg border-border bg-background pl-9 text-xs"
              />
            </div>
            <Button
              onClick={load}
              variant="outline"
              size="sm"
              className="h-8 rounded-lg text-xs border-border gap-1.5"
            >
              <RefreshCw className={`h-3.5 w-3.5 ${loading ? "animate-spin" : ""}`} />
              Refresh
            </Button>
          </div>
        </div>

        {/* Error state */}
        {error && (
          <div className="flex items-start gap-3 px-5 py-4 border-b border-destructive/20 bg-destructive/5">
            <AlertCircle className="h-4 w-4 text-destructive shrink-0 mt-0.5" />
            <div>
              <p className="text-xs font-medium text-destructive">Unable to load request logs</p>
              <p className="text-[0.6875rem] text-muted-foreground mt-0.5">{error}</p>
              <button onClick={load} className="text-[0.6875rem] text-cyan hover:underline mt-1">Retry</button>
            </div>
          </div>
        )}

        {/* Table */}
        <div className="overflow-x-auto">
          <table className="data-table min-w-[52rem]">
            <thead>
              <tr>
                {["Timestamp", "Tenant", "Provider", "Model", "Tokens", "Cost", "Latency", "Status"].map((h) => (
                  <th key={h}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {loading && Array.from({ length: 6 }).map((_, i) => (
                <tr key={i}>
                  {Array.from({ length: 8 }).map((_, j) => (
                    <td key={j}>
                      <div className="skeleton h-3.5 w-full" />
                    </td>
                  ))}
                </tr>
              ))}
              {!loading && filtered.length === 0 && (
                <tr>
                  <td colSpan={8} className="!py-12 text-center">
                    <ScrollText className="mx-auto mb-3 h-8 w-8 text-muted-foreground/20" />
                    <p className="text-[0.8125rem] text-muted-foreground">
                      {rows.length === 0
                        ? "No requests routed yet"
                        : "No results match your filter"}
                    </p>
                    <p className="text-[0.6875rem] text-muted-foreground/60 mt-1">
                      {rows.length === 0
                        ? "Use the Sandbox to send your first request through the gateway."
                        : "Try adjusting your search terms."}
                    </p>
                  </td>
                </tr>
              )}
              {!loading && filtered.map((r, i) => (
                <motion.tr
                  key={r.id}
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  transition={{ delay: Math.min(i * 0.01, 0.3) }}
                >
                  <td className="font-mono text-[0.75rem] text-muted-foreground whitespace-nowrap">
                    {r.timestamp ? new Date(r.timestamp).toLocaleString() : "—"}
                  </td>
                  <td className="font-mono text-[0.75rem] text-muted-foreground">{r.tenantId || "—"}</td>
                  <td className="text-[0.8125rem]">{r.provider || "—"}</td>
                  <td className="font-mono text-[0.75rem] text-cyan">{r.model || "—"}</td>
                  <td className="text-muted-foreground tabular-nums">{r.tokenUsage?.toLocaleString() ?? 0}</td>
                  <td className="font-mono text-[0.75rem] tabular-nums">${(r.costUsd ?? 0).toFixed(5)}</td>
                  <td>
                    <span className={`font-mono text-[0.75rem] font-medium ${getLatencyColor(r.latencyMs ?? 0)}`}>
                      {r.latencyMs ?? 0}ms
                    </span>
                  </td>
                  <td>
                    <span
                      className={`badge ${
                        r.status === "success"
                          ? "badge-success"
                          : "badge-danger"
                      }`}
                    >
                      <span className={`h-1 w-1 rounded-full ${r.status === "success" ? "bg-emerald" : "bg-rose"}`} />
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
