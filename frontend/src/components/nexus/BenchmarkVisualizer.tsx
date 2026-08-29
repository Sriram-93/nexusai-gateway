import { useState } from "react";
import { motion } from "motion/react";
import { Gauge, Play, RefreshCw, CheckCircle2, AlertTriangle, Layers, Cpu } from "lucide-react";
import { Button } from "@/components/ui/button";
import { telemetryApi, type BenchmarkResult } from "@/lib/api";

export function BenchmarkVisualizer() {
  const [running, setRunning] = useState(false);
  const [result, setResult] = useState<BenchmarkResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  const runBenchmark = async () => {
    setRunning(true);
    setError(null);
    try {
      const res = await telemetryApi.runBenchmark(12);
      setResult(res);
    } catch (err: any) {
      setError(err.message || "Failed to execute synthetic benchmark");
    } finally {
      setRunning(false);
    }
  };

  return (
    <div className="glass rounded-2xl p-6">
      <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
        <div className="flex items-center gap-2">
          <Gauge className="h-4 w-4 text-cyan" />
          <div>
            <p className="text-sm font-medium tracking-tight">Model SLA & Latency Benchmark</p>
            <p className="text-xs text-muted-foreground">Synthetic multi-model load test across orchestration pipeline</p>
          </div>
        </div>
        <Button
          onClick={runBenchmark}
          disabled={running}
          size="sm"
          className="grad-primary h-8 rounded-lg text-xs gap-1.5 font-medium text-foreground"
        >
          {running ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <Play className="h-3.5 w-3.5" />}
          {running ? "Executing Load Test..." : "Run SLA Benchmark"}
        </Button>
      </div>

      {error && (
        <div className="mb-4 rounded-xl border border-destructive/40 bg-destructive/10 px-4 py-2.5 text-xs text-destructive">
          {error}
        </div>
      )}

      {!result && !running && (
        <div className="p-8 text-center border border-dashed border-[var(--glass-border)] rounded-xl bg-[var(--surface-subtle)] text-xs text-muted-foreground">
          <Cpu className="mx-auto mb-2 h-6 w-6 opacity-40" />
          Click <strong>Run SLA Benchmark</strong> to evaluate multi-model latency, bandit distribution, and cache performance under load.
        </div>
      )}

      {running && (
        <div className="p-8 text-center space-y-3">
          <RefreshCw className="mx-auto h-7 w-7 animate-spin text-cyan" />
          <p className="text-xs text-muted-foreground animate-pulse font-mono">
            Dispatching 12 concurrent requests to multi-model routing pipeline...
          </p>
        </div>
      )}

      {result && !running && (
        <div className="space-y-4">
          {/* Summary Metric Strip */}
          <div className="grid grid-cols-4 gap-3 text-xs">
            <div className="glass p-3 rounded-xl">
              <span className="text-muted-foreground block text-[0.65rem] uppercase">Total Requests</span>
              <span className="font-mono font-bold text-foreground text-sm">{result.totalRequests}</span>
            </div>
            <div className="glass p-3 rounded-xl">
              <span className="text-muted-foreground block text-[0.65rem] uppercase">Success Count</span>
              <span className="font-mono font-bold text-emerald text-sm">{result.successfulRequests}</span>
            </div>
            <div className="glass p-3 rounded-xl">
              <span className="text-muted-foreground block text-[0.65rem] uppercase">Avg Latency</span>
              <span className="font-mono font-bold text-cyan text-sm">{Math.round(result.avgLatencyMs)}ms</span>
            </div>
            <div className="glass p-3 rounded-xl">
              <span className="text-muted-foreground block text-[0.65rem] uppercase">Min / Max</span>
              <span className="font-mono text-muted-foreground">{result.minLatencyMs}ms / {result.maxLatencyMs}ms</span>
            </div>
          </div>

          {/* SLA Compliance Indicator */}
          <div className="flex items-center justify-between p-3 rounded-xl border border-[var(--glass-border)] bg-[var(--surface-subtle)] text-xs">
            <div className="flex items-center gap-2">
              {result.avgLatencyMs <= 2000 ? (
                <CheckCircle2 className="h-4 w-4 text-emerald" />
              ) : (
                <AlertTriangle className="h-4 w-4 text-amber" />
              )}
              <span>
                SLA Status: <strong className={result.avgLatencyMs <= 2000 ? "text-emerald" : "text-amber"}>
                  {result.avgLatencyMs <= 2000 ? "SLA COMPLIANT (<2000ms target)" : "LATENCY DEGRADED"}
                </strong>
              </span>
            </div>
            <span className="font-mono text-[0.65rem] text-muted-foreground">LinUCB Bandit Active</span>
          </div>

          {/* Model Selection Distribution */}
          <div>
            <p className="text-xs font-mono text-muted-foreground mb-2 flex items-center gap-1">
              <Layers className="h-3 w-3 text-cyan" /> Model Traffic Distribution
            </p>
            <div className="space-y-2">
              {Object.entries(result.modelDistribution || {}).map(([model, count]) => {
                const pct = Math.round((count / result.totalRequests) * 100);
                return (
                  <div key={model} className="space-y-1">
                    <div className="flex justify-between text-xs font-mono">
                      <span className="text-cyan">{model}</span>
                      <span className="text-muted-foreground">{count} reqs ({pct}%)</span>
                    </div>
                    <div className="h-1.5 bg-border rounded-full overflow-hidden">
                      <motion.div
                        initial={{ width: 0 }}
                        animate={{ width: `${pct}%` }}
                        transition={{ duration: 0.5 }}
                        className="bg-cyan h-full"
                      />
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
