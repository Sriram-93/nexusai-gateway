import { createFileRoute } from "@tanstack/react-router";
import { motion } from "motion/react";
import { useState, useEffect } from "react";
import { ShieldCheck, Lock, Unlock, AlertTriangle } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { dashboardApi, type ModelHealth } from "@/lib/api";

export const Route = createFileRoute("/app/security")({
  head: () => ({ meta: [{ title: "Security & Policy — NexusAI" }] }),
  component: SecurityPolicy,
});

function SecurityPolicy() {
  const [models, setModels] = useState<ModelHealth[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    dashboardApi.getModels()
      .then(setModels)
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  // Derive security signals from real model health data
  const openCircuitBreakers = models.filter((m) => m.cbState === "OPEN");
  const halfOpenCircuitBreakers = models.filter((m) => m.cbState === "HALF_OPEN");
  const healthyModels = models.filter((m) => m.cbState === "CLOSED" || m.cbState === "UNKNOWN");

  return (
    <AppShell title="Security & Policy" subtitle="Circuit breaker status and model health guardrails">
      <div className="grid gap-6 lg:grid-cols-2">

        {/* Circuit Breaker Status */}
        <div className="glass rounded-2xl p-6">
          <div className="flex items-center gap-2 mb-6">
            <ShieldCheck className="h-5 w-5 text-indigo" />
            <h2 className="text-sm font-semibold tracking-tight">Circuit Breaker Status</h2>
          </div>

          {loading && (
            <div className="space-y-3">
              {[0, 1, 2].map((i) => (
                <div key={i} className="h-10 animate-pulse rounded-lg bg-[var(--glass-hover)]" />
              ))}
            </div>
          )}

          {!loading && models.length === 0 && (
            <p className="text-xs text-muted-foreground italic">
              No models registered. Add providers and enable models first.
            </p>
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
                {halfOpenCircuitBreakers.length} half-open (recovering)
              </div>
              {halfOpenCircuitBreakers.map((m) => (
                <p key={m.armKey} className="font-mono">{m.armKey}</p>
              ))}
            </div>
          )}

          <div className="space-y-2">
            {!loading && models.map((m) => (
              <div key={m.armKey} className="flex items-center justify-between p-3 rounded-lg border border-[var(--glass-border)] bg-[var(--glass-hover)]">
                <div>
                  <p className="font-mono text-xs text-cyan">{m.armKey}</p>
                  {m.hasData && (
                    <p className="text-[0.65rem] text-muted-foreground mt-0.5">
                      health: {((m.healthScore ?? 0) * 100).toFixed(1)}% · fail: {((m.failureRate ?? 0) * 100).toFixed(2)}%
                    </p>
                  )}
                </div>
                <span className={`flex items-center gap-1 text-xs font-medium ${
                  m.cbState === "CLOSED" ? "text-emerald" :
                  m.cbState === "OPEN" ? "text-destructive" :
                  m.cbState === "HALF_OPEN" ? "text-amber" :
                  "text-muted-foreground"
                }`}>
                  {m.cbState === "CLOSED" ? <Unlock className="h-3.5 w-3.5" /> : <Lock className="h-3.5 w-3.5" />}
                  {m.cbState}
                </span>
              </div>
            ))}
          </div>
        </div>

        {/* Model Availability */}
        <div className="glass rounded-2xl p-6">
          <div className="flex items-center gap-2 mb-6">
            <Lock className="h-5 w-5 text-amber" />
            <h2 className="text-sm font-semibold tracking-tight">Model Availability</h2>
          </div>
          <p className="text-xs text-muted-foreground mb-4">
            Models currently enabled for routing. Manage in the Providers page.
          </p>

          {loading && (
            <div className="space-y-2">
              {[0, 1, 2].map((i) => (
                <div key={i} className="h-10 animate-pulse rounded-lg bg-[var(--glass-hover)]" />
              ))}
            </div>
          )}

          {!loading && models.length === 0 && (
            <p className="text-xs text-muted-foreground italic">No models registered.</p>
          )}

          <div className="space-y-2">
            {!loading && models.map((m) => (
              <div key={m.armKey} className="flex items-center justify-between p-3 rounded-lg border border-[var(--glass-border)] bg-[var(--glass-hover)]">
                <div>
                  <span className="font-mono text-xs text-cyan">{m.armKey}</span>
                  {m.hasData && (
                    <p className="text-[0.65rem] text-muted-foreground mt-0.5">
                      {m.totalRequests.toLocaleString()} requests · {Math.round(m.avgLatencyMs ?? 0)}ms avg
                    </p>
                  )}
                  {!m.hasData && (
                    <p className="text-[0.65rem] text-muted-foreground mt-0.5">No traffic yet</p>
                  )}
                </div>
                {m.cbState === "CLOSED" || m.cbState === "UNKNOWN" ? (
                  <Unlock className="h-4 w-4 text-emerald" />
                ) : (
                  <Lock className="h-4 w-4 text-destructive" />
                )}
              </div>
            ))}
          </div>

          {/* Security architecture note */}
          <div className="mt-6 border-t pt-4 text-xs text-muted-foreground space-y-1">
            <p className="font-medium text-foreground text-xs mb-2">Gateway security architecture</p>
            <p>• API key authentication required for /api/chat and /v1/chat</p>
            <p>• Per-tenant rate limiting via Redis</p>
            <p>• Per-tenant daily budget enforcement</p>
            <p>• Circuit breakers per provider (Resilience4j)</p>
            <p>• BYOK credential encryption</p>
          </div>
        </div>
      </div>
    </AppShell>
  );
}
