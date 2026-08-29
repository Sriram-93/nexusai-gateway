import { createFileRoute, Link } from "@tanstack/react-router";
import { motion, AnimatePresence } from "motion/react";
import { useState, useEffect, useRef } from "react";
import {
  Brain, Play, Sparkles, Terminal, Zap, AlertTriangle, ArrowRight,
  Radio, ToggleLeft, ToggleRight, CircleDollarSign, ShieldAlert, BookOpen,
} from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { IntegrationGuideModal } from "@/components/nexus/IntegrationGuideModal";
import { chatApi, telemetryApi, providersApi, type ChatResponse, type BudgetStatus } from "@/lib/api";

export const Route = createFileRoute("/app/sandbox")({
  head: () => ({
    meta: [
      { title: "Sandbox — NexusAI" },
      {
        name: "description",
        content:
          "Test prompts against the NexusAI gateway and see which upstream provider the router selects, with latency, routing reason, and arm scores. Supports live SSE streaming.",
      },
    ],
  }),
  component: Sandbox,
});

const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080") as string;

function Sandbox() {
  const [prompt, setPrompt] = useState("Summarize the key benefits of federated learning in three concise bullets.");
  const [result, setResult] = useState<ChatResponse | null>(null);
  const [streamTokens, setStreamTokens] = useState<string[]>([]);
  const [streamDone, setStreamDone] = useState(false);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [streamMode, setStreamMode] = useState(false);
  const [providerStatus, setProviderStatus] = useState<{ readyToChat: boolean; connectedCount: number } | null>(null);
  const [statusLoading, setStatusLoading] = useState(true);
  const [budgetStatus, setBudgetStatus] = useState<BudgetStatus | null>(null);
  const abortRef = useRef<AbortController | null>(null);
  const streamBoxRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    Promise.all([
      providersApi.getStatus().catch(() => ({ readyToChat: false, connectedCount: 0 })),
      telemetryApi.getBudgetStatus("global").catch(() => null),
    ]).then(([ps, budget]) => {
      setProviderStatus(ps);
      setBudgetStatus(budget);
    }).finally(() => setStatusLoading(false));
  }, []);

  // Auto-scroll stream box
  useEffect(() => {
    if (streamBoxRef.current) {
      streamBoxRef.current.scrollTop = streamBoxRef.current.scrollHeight;
    }
  }, [streamTokens]);

  const run = async () => {
    if (!prompt.trim()) return;
    setRunning(true);
    setResult(null);
    setStreamTokens([]);
    setStreamDone(false);
    setError(null);
    abortRef.current = new AbortController();

    if (streamMode) {
      // SSE streaming path — calls /v1/chat/completions with stream:true
      try {
        const token = localStorage.getItem("nexusai_token");
        const resp = await fetch(`${API_BASE}/v1/chat/completions`, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
          },
          body: JSON.stringify({
            model: "auto",
            stream: true,
            messages: [{ role: "user", content: prompt }],
          }),
          signal: abortRef.current.signal,
        });

        if (!resp.ok || !resp.body) {
          throw new Error(`Gateway returned ${resp.status}`);
        }

        const reader = resp.body.getReader();
        const decoder = new TextDecoder();

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          const chunk = decoder.decode(value, { stream: true });
          const lines = chunk.split("\n").filter(l => l.startsWith("data: "));

          for (const line of lines) {
            const data = line.slice(6).trim();
            if (data === "[DONE]") { setStreamDone(true); break; }
            try {
              const parsed = JSON.parse(data);
              const content = parsed?.choices?.[0]?.delta?.content;
              if (content) {
                setStreamTokens(prev => [...prev, content]);
              }
            } catch { /* skip malformed chunks */ }
          }
        }
        setStreamDone(true);
      } catch (err: any) {
        if (err.name !== "AbortError") {
          setError(err.message ?? "Stream connection failed.");
        }
      } finally {
        setRunning(false);
      }
    } else {
      // Buffered path
      try {
        const response = await chatApi.chat({
          message: prompt,
          userId: "sandbox-user",
          priority: "HIGH",
        });
        setResult(response);
      } catch (err: any) {
        if (err.status === 401) {
          setError("Authentication failed. Please refresh and sign in again.");
        } else if (err.status === 402) {
          setError("Budget exhausted for this tenant.");
        } else if (err.status === 429) {
          setError("Rate limit hit. Wait a moment before retrying.");
        } else {
          setError(err.message ?? "Failed to reach the gateway. Is the backend running?");
        }
      } finally {
        setRunning(false);
      }
    }
  };

  const stop = () => {
    abortRef.current?.abort();
    setRunning(false);
    setStreamDone(true);
  };

  const [showGuide, setShowGuide] = useState(false);
  const isBudgetWarning = budgetStatus?.is80PercentWarning;
  const isBudgetBlocked = budgetStatus?.allowed === false;

  return (
    <AppShell title="Sandbox" subtitle="Dry-run prompts through the live routing plane — supports SSE streaming">

      <div className="mb-4 flex items-center justify-between">
        <Button
          onClick={() => setShowGuide(true)}
          variant="outline"
          className="h-8 rounded-xl border-cyan/40 text-cyan hover:bg-cyan/10 text-xs gap-1.5"
        >
          <BookOpen className="h-3.5 w-3.5" />
          Developer SDK Integration Guide
        </Button>
      </div>

      {showGuide && <IntegrationGuideModal onClose={() => setShowGuide(false)} />}

      {/* Status bar: provider + budget */}
      {!statusLoading && (
        <div className="mb-4 flex flex-wrap items-center gap-2">
          {!providerStatus?.readyToChat && (
            <motion.div
              initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }}
              className="flex items-start gap-3 rounded-2xl border border-amber/30 bg-amber/5 p-4 w-full"
            >
              <AlertTriangle className="h-4 w-4 text-amber shrink-0 mt-0.5" />
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium text-amber">No provider connected</p>
                <p className="mt-1 text-xs text-muted-foreground">
                  Connect at least one AI provider in the Provider Hub before running requests.
                </p>
                <Link to="/app/providers" className="mt-2 inline-flex items-center gap-1.5 text-xs font-medium text-cyan hover:underline">
                  Go to Providers Hub <ArrowRight className="h-3 w-3" />
                </Link>
              </div>
            </motion.div>
          )}

          {isBudgetWarning && !isBudgetBlocked && (
            <div className="flex items-center gap-1.5 rounded-xl border border-amber/30 bg-amber/5 px-3 py-1.5 text-xs text-amber">
              <CircleDollarSign className="h-3.5 w-3.5" />
              Budget 80% reached — ${budgetStatus?.currentDailySpendUsd.toFixed(4)} of ${budgetStatus?.dailyCapUsd.toFixed(2)}
            </div>
          )}

          {isBudgetBlocked && (
            <div className="flex items-center gap-1.5 rounded-xl border border-destructive/30 bg-destructive/5 px-3 py-1.5 text-xs text-destructive">
              <ShieldAlert className="h-3.5 w-3.5" />
              Daily budget cap exceeded — requests will be blocked
            </div>
          )}
        </div>
      )}

      <div className="grid gap-4 lg:grid-cols-2">
        {/* Request panel */}
        <div className="section-panel p-6">
          <div className="flex items-center gap-2 mb-4">
            <Terminal className="h-4 w-4 text-cyan" />
            <p className="text-sm font-medium tracking-tight">Request</p>
            <div
              className="ml-auto flex cursor-pointer items-center gap-1.5 text-xs"
              onClick={() => !running && setStreamMode(v => !v)}
            >
              {streamMode
                ? <ToggleRight className="h-4 w-4 text-cyan" />
                : <ToggleLeft className="h-4 w-4 text-muted-foreground" />}
              <span className={streamMode ? "text-cyan font-medium" : "text-muted-foreground"}>
                SSE stream
              </span>
              {streamMode && (
                <span className="flex items-center gap-0.5 rounded-full border border-cyan/30 bg-cyan/10 px-1.5 py-0.5 text-[0.6rem] text-cyan font-mono">
                  <Radio className="h-2 w-2" /> LIVE
                </span>
              )}
            </div>
          </div>

          <Textarea
            value={prompt}
            onChange={(e) => setPrompt(e.target.value)}
            rows={9}
            placeholder="Enter your prompt here…"
            className="resize-none rounded-xl border-[var(--glass-border)] bg-[var(--glass-bg)] font-mono text-xs backdrop-blur-md"
          />

          <div className="mt-4 flex gap-2">
            <motion.div whileHover={{ scale: 1.015 }} whileTap={{ scale: 0.98 }} className="flex-1">
              <Button
                onClick={run}
                disabled={running || !prompt.trim() || !providerStatus?.readyToChat || isBudgetBlocked}
                className="grad-primary h-10 w-full rounded-xl text-sm text-primary-foreground transition-shadow hover:shadow-[0_0_34px_-6px_var(--cyan)]"
              >
                <Play className="mr-1.5 h-4 w-4" />
                {running ? (streamMode ? "Streaming…" : "Routing…") : "Send through gateway"}
              </Button>
            </motion.div>
            {running && streamMode && (
              <Button variant="outline" onClick={stop} className="h-10 rounded-xl text-xs">
                Stop
              </Button>
            )}
          </div>

          {error && (
            <div className="mt-3 rounded-xl border border-destructive/40 bg-destructive/10 px-3 py-2 text-xs text-destructive">
              {error}
            </div>
          )}
        </div>

        {/* Response panel */}
        <div className="section-panel p-6 flex flex-col">
          <div className="flex items-center gap-2 mb-4">
            <Sparkles className="h-4 w-4 text-emerald" />
            <p className="text-sm font-medium tracking-tight">
              {streamMode ? "Live token stream" : "Router decision"}
            </p>
            {streamMode && running && (
              <span className="ml-auto flex items-center gap-1 text-[0.65rem] text-cyan font-mono animate-pulse">
                <Radio className="h-2.5 w-2.5" /> receiving tokens…
              </span>
            )}
          </div>

          {/* SSE streaming output */}
          {streamMode && (streamTokens.length > 0 || running) && (
            <div
              ref={streamBoxRef}
              className="flex-1 rounded-xl border border-[var(--glass-border)] bg-[var(--glass-hover)] p-4 text-xs font-mono leading-relaxed overflow-y-auto max-h-80 whitespace-pre-wrap"
            >
              {streamTokens.join("")}
              {running && !streamDone && (
                <span className="inline-block h-3 w-0.5 bg-cyan align-middle animate-pulse ml-0.5" />
              )}
            </div>
          )}

          {/* Buffered loading skeleton */}
          {!streamMode && running && (
            <div className="mt-2 space-y-3">
              {[0, 1, 2].map((i) => (
                <motion.div
                  key={i}
                  animate={{ opacity: [0.25, 0.7, 0.25] }}
                  transition={{ duration: 1.1, repeat: Infinity, delay: i * 0.15 }}
                  className="h-4 rounded bg-[var(--glass-hover)]"
                />
              ))}
            </div>
          )}

          {/* Buffered result */}
          <AnimatePresence>
            {!streamMode && result && (
              <motion.div
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                className="flex flex-col gap-4 flex-1"
              >
                <div className="space-y-2">
                  {[
                    ["Selected upstream", result.provider],
                    ["Latency", `${result.latencyMs}ms`],
                    ["Active engine", result.activeEngine],
                    ["Routing reason", result.routingReason],
                    ["Reward score", result.rewardScore?.toFixed(4) ?? "—"],
                  ].map(([k, v]) => (
                    <div key={k} className="flex items-start justify-between gap-2 border-b pb-2 text-xs">
                      <span className="text-muted-foreground shrink-0">{k}</span>
                      <span className="font-mono text-cyan text-right break-all">{v}</span>
                    </div>
                  ))}
                </div>

                {result.armScores && Object.keys(result.armScores).length > 0 && (
                  <div>
                    <p className="mb-2 text-[0.65rem] uppercase tracking-[0.14em] text-muted-foreground flex items-center gap-1">
                      <Brain className="h-3 w-3" /> LinUCB arm scores
                    </p>
                    <div className="space-y-1.5 max-h-48 overflow-y-auto pr-2">
                      {Object.entries(result.armScores)
                        .sort(([, a], [, b]) => b - a)
                        .map(([arm, score]) => (
                          <div key={arm} className="flex items-center justify-between text-xs">
                            <span className="font-mono text-muted-foreground">{arm}</span>
                            <span className={`font-mono ${arm === result.provider.split(" ")[0] ? "text-emerald" : "text-muted-foreground"}`}>
                              {score.toFixed(4)}
                            </span>
                          </div>
                        ))}
                    </div>
                  </div>
                )}

                <div className="flex-1">
                  <p className="mb-2 text-[0.65rem] uppercase tracking-[0.14em] text-muted-foreground flex items-center gap-1">
                    <Zap className="h-3 w-3" /> Gateway response
                  </p>
                  <div className="rounded-xl border border-[var(--glass-border)] bg-[var(--glass-hover)] p-4 text-xs leading-relaxed font-mono overflow-y-auto max-h-64">
                    {result.answer}
                  </div>
                </div>
              </motion.div>
            )}
          </AnimatePresence>

          {!running && !result && streamTokens.length === 0 && !error && (
            <p className="mt-6 text-xs text-muted-foreground">
              {streamMode
                ? "Enable stream mode and send a request to see live token output from the gateway."
                : "Send a request to see the routing decision, LinUCB arm scores, and full response."}
            </p>
          )}
        </div>
      </div>
    </AppShell>
  );
}
