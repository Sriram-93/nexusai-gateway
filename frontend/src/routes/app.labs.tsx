import { createFileRoute } from "@tanstack/react-router";
import { motion } from "motion/react";
import { useState, useRef, useEffect } from "react";
import { TestTubeDiagonal, Zap, GitBranch, RefreshCcw, AlertTriangle, Terminal } from "lucide-react";
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
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (scrollRef.current) scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
  }, [logs]);

  const addLog = (text: string, type: "info" | "success" | "error" | "data" = "info") => {
    setLogs((prev) => [...prev, { text, type }]);
  };

  const clearLogs = () => setLogs([]);

  const runLatencyTest = async () => {
    setRunning("latency");
    clearLogs();
    addLog("Starting latency benchmark — sending 5 sequential requests to /api/chat");
    addLog("Note: Active Gateway LinUCB Bandit Orchestrator engaged.", "info");

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
        addLog(`  ✗ Error: ${err?.message || "Request failed"} (${elapsed}ms)`, "error");
        if (err?.status === 401) {
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
        addLog(`  ✗ ${err?.message || "Routing error"}`, "error");
        if (err?.status === 401) {
          addLog("  Configure your API key first.", "error");
          break;
        }
      }
    }

    addLog("─────────────────────────────────────────", "info");
    Object.entries(providerCounts).forEach(([p, c]) => {
      addLog(`  ${p}: ${c}/10 selections (${((c / 10) * 100).toFixed(0)}%)`, "data");
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
      detail: "5 requests · measures avg/min/max · real API calls",
      icon: Zap,
      action: runLatencyTest,
      color: "cyan",
    },
    {
      id: "routing",
      title: "Routing Convergence Test",
      desc: "Send 10 requests and observe how the FADE/LinUCB bandit distributes selections.",
      detail: "10 requests · tracks provider distribution · real routing",
      icon: GitBranch,
      action: runRoutingConvergenceTest,
      color: "indigo",
    },
  ];

  const LOG_COLORS: Record<string, string> = {
    error: "text-destructive",
    success: "text-emerald",
    data: "text-cyan",
    info: "text-muted-foreground",
  };

  return (
    <AppShell title="Benchmarking Labs" subtitle="Real gateway stress tests — actual API calls, no simulation">
      <div className="rounded-lg border border-amber/30 bg-amber/5 px-4 py-3 text-xs text-amber flex items-start gap-2 mb-5">
        <AlertTriangle className="h-3.5 w-3.5 mt-0.5 shrink-0" />
        <span>
          These tests send real requests to the live gateway. They consume your tenant budget and rate limit quota.
          Ensure a valid API key is configured in <strong>API Keys</strong> before running.
        </span>
      </div>

      <div className="grid gap-5 lg:grid-cols-[1fr_400px]">
        {/* Benchmark suites */}
        <div className="space-y-3">
          {BENCHMARKS.map((bench, i) => (
            <motion.div
              key={bench.id}
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: i * 0.08 }}
              className="section-panel p-5 flex items-center justify-between gap-4"
            >
              <div className="flex items-center gap-4">
                <span className={`flex h-11 w-11 items-center justify-center rounded-xl bg-${bench.color}/10`}>
                  <bench.icon className={`h-5 w-5 text-${bench.color}`} />
                </span>
                <div>
                  <h3 className="text-[0.8125rem] font-semibold tracking-tight">{bench.title}</h3>
                  <p className="text-[0.6875rem] text-muted-foreground mt-0.5">{bench.desc}</p>
                  <p className="text-[0.625rem] text-muted-foreground/50 font-mono mt-1">{bench.detail}</p>
                </div>
              </div>
              <Button
                disabled={running !== null}
                onClick={bench.action}
                variant="outline"
                className="h-9 text-xs rounded-lg border-border gap-1.5 shrink-0"
              >
                {running === bench.id ? <RefreshCcw className="h-3.5 w-3.5 animate-spin" /> : "Run Test"}
              </Button>
            </motion.div>
          ))}
        </div>

        {/* Terminal Output */}
        <div className="section-panel flex flex-col h-[500px]">
          <div className="section-panel-header">
            <div className="flex items-center gap-2">
              <Terminal className="h-4 w-4 text-cyan" />
              <span className="text-[0.8125rem] font-semibold tracking-tight">Live Output</span>
            </div>
            {logs.length > 0 && (
              <button
                onClick={clearLogs}
                className="text-[0.6875rem] text-muted-foreground hover:text-foreground transition-colors"
              >
                Clear
              </button>
            )}
          </div>

          <div
            ref={scrollRef}
            className="p-4 flex-1 overflow-y-auto bg-[var(--surface-inset)] font-mono text-[0.7rem] leading-relaxed space-y-0.5"
          >
            {logs.map((log, i) => (
              <div key={i} className={LOG_COLORS[log.type]}>
                <span className="opacity-40 select-none mr-2">{">"}</span>{log.text}
              </div>
            ))}
            {logs.length === 0 && (
              <div className="flex flex-col items-center justify-center h-full text-center">
                <TestTubeDiagonal className="h-8 w-8 text-muted-foreground/15 mb-3" />
                <p className="text-[0.75rem] text-muted-foreground">Ready to run benchmark</p>
                <p className="text-[0.625rem] text-muted-foreground/50 mt-1">Select a suite to begin.</p>
              </div>
            )}
            {running && (
              <div className="text-amber animate-pulse">
                <span className="opacity-40 select-none mr-2">{">"}</span>Running…
              </div>
            )}
          </div>
        </div>
      </div>
    </AppShell>
  );
}
