import { createFileRoute } from "@tanstack/react-router";
import { motion } from "motion/react";
import { useState } from "react";
import { TestTubeDiagonal, Zap, Shield, GitBranch, RefreshCcw, AlertTriangle } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { Button } from "@/components/ui/button";
import { chatApi } from "@/lib/api";

export const Route = createFileRoute("/app/labs")({
  head: () => ({ meta: [{ title: "Benchmarking Labs — NexusAI" }] }),
  component: Labs,
});

/**
 * Benchmarking Labs — sends real requests to /api/chat to stress-test routing.
 *
 * Note: There is no /api/benchmark endpoint in the backend. These tests work by sending
 * real chat requests through the gateway and measuring actual latency/routing behavior.
 * Results are real gateway responses, not simulated.
 */
function Labs() {
  const [running, setRunning] = useState<string | null>(null);
  const [logs, setLogs] = useState<{ text: string; type: "info" | "success" | "error" | "data" }[]>([]);

  const addLog = (text: string, type: "info" | "success" | "error" | "data" = "info") => {
    setLogs((prev) => [...prev, { text, type }]);
  };

  const clearLogs = () => setLogs([]);

  const runLatencyTest = async () => {
    setRunning("latency");
    clearLogs();
    addLog("Starting latency benchmark — sending 5 sequential requests to /api/chat");
    addLog("Note: Requires a valid API key configured in session. Go to API Keys if needed.", "info");

    const prompts = [
      "What is 2+2?",
      "Name three primary colors.",
      "What is the capital of France?",
      "How many continents are there?",
      "What is machine learning in one sentence?",
    ];

    const timings: number[] = [];
    for (let i = 0; i < prompts.length; i++) {
      addLog(`[${i + 1}/${prompts.length}] Sending: "${prompts[i]}"`);
      const start = Date.now();
      try {
        const res = await chatApi.chat({ message: prompts[i]!, userId: "benchmark", priority: "HIGH" });
        const elapsed = Date.now() - start;
        timings.push(elapsed);
        addLog(`  ✓ Provider: ${res.provider} | Latency: ${res.latencyMs}ms | Measured: ${elapsed}ms | Engine: ${res.activeEngine}`, "success");
      } catch (err: any) {
        const elapsed = Date.now() - start;
        addLog(`  ✗ Error: ${err.message} (${elapsed}ms)`, "error");
        if (err.status === 401) {
          addLog("  API key missing or invalid. Configure your key in the API Keys page.", "error");
          break;
        }
      }
    }

    if (timings.length > 0) {
      const avg = timings.reduce((a, b) => a + b, 0) / timings.length;
      const min = Math.min(...timings);
      const max = Math.max(...timings);
      addLog("─────────────────────────────────────────", "info");
      addLog(`Results: avg=${Math.round(avg)}ms, min=${min}ms, max=${max}ms over ${timings.length} requests`, "data");
    }

    setRunning(null);
  };

  const runRoutingConvergenceTest = async () => {
    setRunning("routing");
    clearLogs();
    addLog("Routing convergence test — 10 requests to observe provider selection pattern");
    addLog("This shows how the FADE/LinUCB bandit selects providers across requests.");

    const providerCounts: Record<string, number> = {};
    const engineCounts: Record<string, number> = {};

    for (let i = 0; i < 10; i++) {
      addLog(`[${i + 1}/10] Routing request...`);
      try {
        const res = await chatApi.chat({
          message: `Benchmark probe ${i + 1}: What is ${i + 1} squared?`,
          userId: "benchmark-routing",
          priority: "HIGH",
        });
        providerCounts[res.provider] = (providerCounts[res.provider] ?? 0) + 1;
        engineCounts[res.activeEngine] = (engineCounts[res.activeEngine] ?? 0) + 1;
        addLog(`  → ${res.provider} | ${res.latencyMs}ms | score: ${res.rewardScore?.toFixed(3) ?? "—"}`, "success");
      } catch (err: any) {
        addLog(`  ✗ ${err.message}`, "error");
        if (err.status === 401) {
          addLog("  Configure your API key first.", "error");
          break;
        }
      }
    }

    addLog("─────────────────────────────────────────", "info");
    Object.entries(providerCounts).forEach(([p, c]) => {
      addLog(`  ${p}: ${c}/10 selections (${(c / 10 * 100).toFixed(0)}%)`, "data");
    });
    Object.entries(engineCounts).forEach(([e, c]) => {
      addLog(`  Engine: ${e} used ${c} time(s)`, "data");
    });

    setRunning(null);
  };

  const BENCHMARKS = [
    {
      id: "latency",
      title: "Latency Benchmark",
      desc: "Send 5 real requests through the gateway and measure end-to-end latency per provider.",
      icon: Zap,
      action: runLatencyTest,
    },
    {
      id: "routing",
      title: "Routing Convergence Test",
      desc: "Send 10 requests and observe how the FADE/LinUCB bandit distributes selections.",
      icon: GitBranch,
      action: runRoutingConvergenceTest,
    },
  ];

  return (
    <AppShell title="Benchmarking Labs" subtitle="Real gateway stress tests — actual API calls, no simulation">
      <div className="mb-4 rounded-xl border border-amber/40 bg-amber/10 px-4 py-3 text-xs text-amber flex items-start gap-2">
        <AlertTriangle className="h-3.5 w-3.5 mt-0.5 shrink-0" />
        <span>
          These tests send real requests to the live gateway. They consume your tenant budget and rate limit quota.
          Ensure a valid API key is configured in <strong>API Keys</strong> before running.
        </span>
      </div>

      <div className="grid gap-6 lg:grid-cols-[1fr_400px]">
        <div className="space-y-4">
          {BENCHMARKS.map((bench, i) => (
            <motion.div
              key={bench.id}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: i * 0.1 }}
              className="glass p-6 rounded-2xl flex items-center justify-between transition-colors hover:border-[color-mix(in_oklab,var(--foreground)_20%,transparent)]"
            >
              <div className="flex items-center gap-4">
                <span className="flex h-12 w-12 items-center justify-center rounded-xl bg-amber/10">
                  <bench.icon className="h-5 w-5 text-amber" />
                </span>
                <div>
                  <h3 className="text-sm font-semibold tracking-tight">{bench.title}</h3>
                  <p className="text-xs text-muted-foreground mt-1">{bench.desc}</p>
                </div>
              </div>
              <Button
                disabled={running !== null}
                onClick={bench.action}
                className="glass h-9 text-xs rounded-lg hover:bg-[var(--glass-hover)]"
              >
                {running === bench.id ? <RefreshCcw className="h-3.5 w-3.5 animate-spin" /> : "Run Test"}
              </Button>
            </motion.div>
          ))}
        </div>

        <div className="glass rounded-2xl p-0 overflow-hidden flex flex-col h-[500px]">
          <div className="bg-[var(--glass-bg)] px-4 py-3 border-b border-[var(--glass-border)] flex items-center justify-between">
            <div className="flex items-center gap-2">
              <TestTubeDiagonal className="h-4 w-4 text-cyan" />
              <span className="text-xs font-semibold tracking-wider uppercase text-muted-foreground">Live Output</span>
            </div>
            {logs.length > 0 && (
              <button
                onClick={clearLogs}
                className="text-[0.65rem] text-muted-foreground hover:text-foreground"
              >
                Clear
              </button>
            )}
          </div>

          <div className="p-4 flex-1 overflow-y-auto bg-black/40 font-mono text-[0.7rem] leading-relaxed space-y-0.5">
            {logs.map((log, i) => (
              <div
                key={i}
                className={
                  log.type === "error" ? "text-destructive" :
                  log.type === "success" ? "text-emerald" :
                  log.type === "data" ? "text-cyan" :
                  "text-muted-foreground"
                }
              >
                <span className="opacity-50 select-none mr-2">{">"}</span>{log.text}
              </div>
            ))}
            {logs.length === 0 && (
              <div className="opacity-50">Select a benchmark suite to begin execution.</div>
            )}
            {running && (
              <div className="text-amber animate-pulse">
                <span className="opacity-50 select-none mr-2">{">"}</span>Running…
              </div>
            )}
          </div>
        </div>
      </div>
    </AppShell>
  );
}
