import { createFileRoute } from "@tanstack/react-router";
import { motion } from "motion/react";
import { useState, useEffect, useRef } from "react";
import { ShieldCheck, Lock, Unlock, AlertTriangle, Zap, Radio, CircleDollarSign, Save, RefreshCw } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { Button } from "@/components/ui/button";
import { dashboardApi, telemetryApi, type ModelHealth, type AuditLogEntry, type BudgetEntry } from "@/lib/api";

export const Route = createFileRoute("/app/security")({
  head: () => ({ meta: [{ title: "Security & Policy — NexusAI" }] }),
  component: SecurityPolicy,
});

const ACTION_BADGE: Record<string, { label: string; cls: string }> = {
  GATEWAY_REQUEST: { label: "REQUEST", cls: "bg-cyan/10 text-cyan border-cyan/20" },
  ROUTING_DECISION: { label: "ROUTING", cls: "bg-emerald/10 text-emerald border-emerald/20" },
  PROVIDER_FALLBACK: { label: "FALLBACK", cls: "bg-amber/10 text-amber border-amber/20" },
  BUDGET_ENFORCEMENT: { label: "BUDGET", cls: "bg-destructive/10 text-destructive border-destructive/20" },
  PROVIDER_CIRCUIT_OPEN: { label: "CIRCUIT", cls: "bg-amber/10 text-amber border-amber/20" },
  PII_REDACTION_APPLIED: { label: "PII", cls: "bg-indigo/10 text-indigo border-indigo/20" },
  QUALITY_EVALUATION: { label: "QUALITY", cls: "bg-indigo/10 text-indigo border-indigo/20" },
};

function SecurityPolicy() {
  const [models, setModels] = useState<ModelHealth[]>([]);
  const [auditLogs, setAuditLogs] = useState<AuditLogEntry[]>([]);
  const [budgetList, setBudgetList] = useState<BudgetEntry[]>([]);
  const [loading, setLoading] = useState(true);

  const [editingTarget, setEditingTarget] = useState({
    targetType: "ORGANIZATION",
    targetId: "global",
    dailyCapUsd: 100,
    monthlyCapUsd: 2500,
    actionOnExceeded: "BLOCK"
  });
  const [savingBudget, setSavingBudget] = useState(false);

  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const fetchData = () => {
    Promise.all([
      dashboardApi.getModels(),
      telemetryApi.getAuditLogs(30).catch(() => [] as AuditLogEntry[]),
      telemetryApi.getAllBudgets().catch(() => [] as BudgetEntry[]),
    ]).then(([m, logs, bList]) => {
      setModels(m);
      setAuditLogs(logs);
      setBudgetList(bList);
    }).catch(console.error).finally(() => setLoading(false));
  };

  useEffect(() => {
    fetchData();
    intervalRef.current = setInterval(fetchData, 8000);
    return () => { if (intervalRef.current) clearInterval(intervalRef.current); };
  }, []);

  const handleSaveBudget = async (e: React.FormEvent) => {
    e.preventDefault();
    setSavingBudget(true);
    try {
      await telemetryApi.upsertBudget(editingTarget);
      fetchData();
    } catch (err) {
      console.error("Failed to update budget", err);
    } finally {
      setSavingBudget(false);
    }
  };

  const openCircuitBreakers = models.filter((m) => m.cbState === "OPEN");
  const halfOpenCircuitBreakers = models.filter((m) => m.cbState === "HALF_OPEN");

  return (
    <AppShell title="Security & Policy" subtitle="Circuit breaker guardrails, PII-safe audit trail, and zero-trust budget governance">

      {/* Metric Cards */}
      <div className="grid gap-4 sm:grid-cols-4 mb-4">
        {[
          { label: "Provider Credentials", value: "AES-256-GCM", cls: "text-emerald" },
          { label: "API Key Hashing", value: "SHA-256", cls: "text-cyan" },
          { label: "PII Auto-Redaction", value: "ACTIVE", cls: "text-emerald" },
          { label: "Circuit Breakers", value: openCircuitBreakers.length === 0 ? "ALL CLOSED" : `${openCircuitBreakers.length} OPEN`, cls: openCircuitBreakers.length > 0 ? "text-destructive" : "text-emerald" },
        ].map((item) => (
          <div key={item.label} className="glass rounded-2xl p-4">
            <p className="text-[0.65rem] uppercase tracking-widest text-muted-foreground">{item.label}</p>
            <p className={`mt-1.5 text-sm font-mono font-bold ${item.cls}`}>{item.value}</p>
          </div>
        ))}
      </div>

      {/* Budget & Governance Manager Panel */}
      <div className="glass rounded-2xl p-6 mb-4">
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-2">
            <CircleDollarSign className="h-4 w-4 text-cyan" />
            <p className="text-sm font-medium tracking-tight">Hard-Stop Budget Governance & Cap Allocation</p>
          </div>
          <span className="text-[0.7rem] font-mono text-emerald bg-emerald/10 border border-emerald/20 px-2 py-0.5 rounded-full">
            Real-Time Redis Enforcement Active
          </span>
        </div>

        <div className="grid gap-6 md:grid-cols-2">
          {/* Active Tenant Budgets */}
          <div className="space-y-3">
            <p className="text-xs text-muted-foreground font-mono flex items-center gap-1">
              Configured Target Caps
            </p>
            {budgetList.length === 0 ? (
              <div className="p-4 rounded-xl border border-[var(--glass-border)] bg-[var(--glass-hover)] text-xs text-muted-foreground italic">
                No custom budget limits configured yet. Global system default ($1,000/day) applied.
              </div>
            ) : (
              <div className="space-y-2 max-h-[220px] overflow-y-auto pr-1">
                {budgetList.map((b) => {
                  const pct = Math.min(100, ((b.currentDailySpendUsd || 0) / (b.dailyCapUsd || 1)) * 100);
                  const isWarning = pct >= 80;
                  return (
                    <div key={b.id || b.targetId} className="p-3 rounded-xl border border-[var(--glass-border)] bg-[var(--glass-hover)] text-xs space-y-1.5">
                      <div className="flex justify-between items-center">
                        <span className="font-mono text-cyan font-bold">{b.targetType}: {b.targetId}</span>
                        <span className={`font-mono font-semibold ${isWarning ? "text-amber" : "text-emerald"}`}>
                          ${(b.currentDailySpendUsd || 0).toFixed(2)} / ${(b.dailyCapUsd || 0).toFixed(2)} daily
                        </span>
                      </div>
                      <div className="w-full bg-black/40 rounded-full h-1.5 overflow-hidden">
                        <div
                          className={`h-full transition-all ${isWarning ? "bg-amber" : "bg-cyan"}`}
                          style={{ width: `${pct}%` }}
                        />
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          {/* Upsert Policy Form */}
          <form onSubmit={handleSaveBudget} className="p-4 rounded-xl border border-[var(--glass-border)] bg-black/40 space-y-3 text-xs">
            <p className="font-medium text-foreground text-xs">Update Target Spend Policy</p>
            <div className="grid grid-cols-2 gap-2">
              <div>
                <label className="text-[0.65rem] text-muted-foreground">Scope</label>
                <select
                  value={editingTarget.targetType}
                  onChange={(e) => setEditingTarget({ ...editingTarget, targetType: e.target.value })}
                  className="w-full mt-1 bg-black/60 border border-[var(--glass-border)] rounded-lg p-1.5 text-xs text-foreground"
                >
                  <option value="ORGANIZATION">ORGANIZATION</option>
                  <option value="WORKSPACE">WORKSPACE</option>
                  <option value="PROJECT">PROJECT</option>
                  <option value="TEAM">TEAM</option>
                </select>
              </div>
              <div>
                <label className="text-[0.65rem] text-muted-foreground">Target Identifier</label>
                <input
                  type="text"
                  value={editingTarget.targetId}
                  onChange={(e) => setEditingTarget({ ...editingTarget, targetId: e.target.value })}
                  className="w-full mt-1 bg-black/60 border border-[var(--glass-border)] rounded-lg p-1.5 text-xs text-foreground font-mono"
                  placeholder="global"
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-2">
              <div>
                <label className="text-[0.65rem] text-muted-foreground">Daily Limit ($)</label>
                <input
                  type="number"
                  value={editingTarget.dailyCapUsd}
                  onChange={(e) => setEditingTarget({ ...editingTarget, dailyCapUsd: Number(e.target.value) })}
                  className="w-full mt-1 bg-black/60 border border-[var(--glass-border)] rounded-lg p-1.5 text-xs text-foreground font-mono"
                />
              </div>
              <div>
                <label className="text-[0.65rem] text-muted-foreground">Monthly Limit ($)</label>
                <input
                  type="number"
                  value={editingTarget.monthlyCapUsd}
                  onChange={(e) => setEditingTarget({ ...editingTarget, monthlyCapUsd: Number(e.target.value) })}
                  className="w-full mt-1 bg-black/60 border border-[var(--glass-border)] rounded-lg p-1.5 text-xs text-foreground font-mono"
                />
              </div>
            </div>

            <Button
              type="submit"
              disabled={savingBudget}
              size="sm"
              className="w-full h-8 rounded-lg grad-primary font-medium text-xs gap-1.5"
            >
              {savingBudget ? <RefreshCw className="h-3 w-3 animate-spin" /> : <Save className="h-3 w-3" />}
              {savingBudget ? "Updating Policy..." : "Save Budget Allocation"}
            </Button>
          </form>
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-[1.2fr_1fr]">
        {/* Circuit Breaker Panel */}
        <div className="glass rounded-2xl p-6">
          <div className="flex items-center gap-2 mb-4">
            <ShieldCheck className="h-4 w-4 text-indigo" />
            <p className="text-sm font-medium tracking-tight">Circuit Breaker Guardrails</p>
          </div>

          {loading && (
            <div className="space-y-3">
              {[0, 1, 2].map((i) => (
                <div key={i} className="h-10 animate-pulse rounded-lg bg-[var(--glass-hover)]" />
              ))}
            </div>
          )}

          {!loading && models.length === 0 && (
            <p className="text-xs text-muted-foreground italic">No models registered. Add providers and enable models first.</p>
          )}

          {!loading && openCircuitBreakers.length > 0 && (
            <div className="mb-4 rounded-xl border border-destructive/40 bg-destructive/10 px-4 py-3 text-xs">
              <div className="flex items-center gap-2 text-destructive font-medium mb-2">
                <AlertTriangle className="h-3.5 w-3.5" />
                {openCircuitBreakers.length} circuit breaker{openCircuitBreakers.length !== 1 ? "s" : ""} OPEN
              </div>
              {openCircuitBreakers.map((m) => (
                <p key={m.armKey} className="font-mono text-destructive">{m.armKey}</p>
              ))}
            </div>
          )}

          {!loading && halfOpenCircuitBreakers.length > 0 && (
            <div className="mb-4 rounded-xl border border-amber/40 bg-amber/10 px-4 py-3 text-xs text-amber">
              <div className="flex items-center gap-2 font-medium mb-1">
                <AlertTriangle className="h-3.5 w-3.5" />
                {halfOpenCircuitBreakers.length} half-open (recovering…)
              </div>
              {halfOpenCircuitBreakers.map((m) => (
                <p key={m.armKey} className="font-mono">{m.armKey}</p>
              ))}
            </div>
          )}

          <div className="space-y-2">
            {!loading && models.map((m) => {
              const providerSlug = m.armKey.split(":")[0];
              return (
                <div key={m.armKey} className="flex items-center justify-between p-3 rounded-lg border border-[var(--glass-border)] bg-[var(--glass-hover)]">
                  <div>
                    <p className="font-mono text-xs text-cyan">{m.armKey}</p>
                    {m.hasData && (
                      <p className="text-[0.65rem] text-muted-foreground mt-0.5">
                        health: {((m.healthScore ?? 0) * 100).toFixed(1)}% · fail: {((m.failureRate ?? 0) * 100).toFixed(2)}%
                      </p>
                    )}
                  </div>
                  <div className="flex items-center gap-2">
                    <span className={`flex items-center gap-1 text-xs font-medium ${
                      m.cbState === "CLOSED" ? "text-emerald" :
                      m.cbState === "OPEN" ? "text-destructive" :
                      m.cbState === "HALF_OPEN" ? "text-amber" : "text-muted-foreground"
                    }`}>
                      {m.cbState === "CLOSED" ? <Unlock className="h-3.5 w-3.5" /> : <Lock className="h-3.5 w-3.5" />}
                      {m.cbState}
                    </span>
                    {m.cbState === "CLOSED" ? (
                      <Button
                        onClick={async () => {
                          try {
                            await dashboardApi.tripCircuitBreaker(providerSlug);
                            fetchData();
                          } catch (e) {
                            console.error("Failed to trip breaker", e);
                          }
                        }}
                        variant="outline"
                        size="sm"
                        className="h-6 text-[0.65rem] px-2 text-destructive border-destructive/40 hover:bg-destructive/10"
                      >
                        Trip Breaker
                      </Button>
                    ) : (
                      <Button
                        onClick={async () => {
                          try {
                            await dashboardApi.resetCircuitBreaker(providerSlug);
                            fetchData();
                          } catch (e) {
                            console.error("Failed to reset breaker", e);
                          }
                        }}
                        variant="outline"
                        size="sm"
                        className="h-6 text-[0.65rem] px-2 text-emerald border-emerald/40 hover:bg-emerald/10"
                      >
                        Reset Circuit
                      </Button>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Live Audit Trail */}
        <div className="glass rounded-2xl p-6 flex flex-col">
          <div className="flex items-center gap-2 mb-4">
            <div className="flex items-center gap-1.5">
              <Radio className="h-4 w-4 text-emerald" />
              <p className="text-sm font-medium tracking-tight">Live Audit Trail</p>
            </div>
            <span className="ml-auto flex items-center gap-1 text-[0.65rem] text-muted-foreground">
              <Zap className="h-2.5 w-2.5 text-cyan" />
              Auto-refreshes · PII-redacted at write-time
            </span>
          </div>

          {loading && (
            <div className="space-y-2">
              {[0, 1, 2, 3].map((i) => (
                <div key={i} className="h-7 animate-pulse rounded bg-[var(--glass-hover)]" />
              ))}
            </div>
          )}

          {!loading && auditLogs.length === 0 && (
            <p className="text-xs text-muted-foreground italic mt-2">
              No audit events yet. Events populate as requests flow through the gateway.
            </p>
          )}

          {!loading && auditLogs.length > 0 && (
            <div className="flex-1 space-y-2 overflow-y-auto max-h-[520px] pr-1">
              {auditLogs.map((entry, i) => {
                const badge = ACTION_BADGE[entry.action] ?? { label: entry.action, cls: "bg-muted/20 text-foreground border-border" };
                return (
                  <motion.div
                    key={entry.id}
                    initial={{ opacity: 0, x: -8 }}
                    animate={{ opacity: 1, x: 0 }}
                    transition={{ delay: i * 0.025 }}
                    className="flex items-start gap-2 p-2.5 rounded-lg border border-[var(--glass-border)] bg-[var(--glass-hover)] text-xs"
                  >
                    <span className={`shrink-0 rounded border px-1.5 py-0.5 text-[0.6rem] font-mono font-semibold tracking-wider ${badge.cls}`}>
                      {badge.label}
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="font-mono text-foreground truncate">{entry.resource}</p>
                      <p className="text-muted-foreground text-[0.6rem] mt-0.5">
                        {entry.actorEmail} · {new Date(entry.timestamp).toLocaleTimeString()}
                      </p>
                    </div>
                  </motion.div>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </AppShell>
  );
}
