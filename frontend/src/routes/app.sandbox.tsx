import { createFileRoute } from "@tanstack/react-router";
import { motion } from "motion/react";
import { useState } from "react";
import { Brain, Play, Sparkles, Terminal, Zap } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { chatApi, type ChatResponse, type ApiError as ApiErr } from "@/lib/api";

export const Route = createFileRoute("/app/sandbox")({
  head: () => ({
    meta: [
      { title: "Sandbox — NexusAI" },
      {
        name: "description",
        content:
          "Test prompts against the NexusAI gateway and see which upstream provider the router selects, with latency, routing reason, and arm scores.",
      },
      { property: "og:title", content: "Sandbox — NexusAI" },
      {
        property: "og:description",
        content: "Test prompts and inspect the router's provider decision.",
      },
    ],
  }),
  component: Sandbox,
});

function Sandbox() {
  const [prompt, setPrompt] = useState("Summarize the key benefits of federated learning in three concise bullets.");
  const [result, setResult] = useState<ChatResponse | null>(null);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = async () => {
    if (!prompt.trim()) return;
    setRunning(true);
    setResult(null);
    setError(null);

    try {
      const response = await chatApi.chat({
        message: prompt,
        userId: "sandbox-user",
        priority: "HIGH",
      });
      setResult(response);
    } catch (err: any) {
      if (err.status === 401) {
        setError("No API key configured. Go to API Keys and provision a tenant first.");
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
  };

  return (
    <AppShell title="Sandbox" subtitle="Dry-run a prompt through the live routing plane">
      <div className="grid gap-4 lg:grid-cols-2">
        {/* Request panel */}
        <div className="glass rounded-2xl p-6">
          <div className="flex items-center gap-2">
            <Terminal className="h-4 w-4 text-cyan" />
            <p className="text-sm font-medium tracking-tight">Request</p>
          </div>
          <Textarea
            value={prompt}
            onChange={(e) => setPrompt(e.target.value)}
            rows={9}
            placeholder="Enter your prompt here…"
            className="mt-4 resize-none rounded-xl border-[var(--glass-border)] bg-[var(--glass-bg)] font-mono text-xs backdrop-blur-md"
          />
          <motion.div whileHover={{ scale: 1.015 }} whileTap={{ scale: 0.98 }} className="mt-4">
            <Button
              onClick={run}
              disabled={running || !prompt.trim()}
              className="grad-primary h-10 w-full rounded-xl text-sm text-primary-foreground transition-shadow hover:shadow-[0_0_34px_-6px_var(--cyan)]"
            >
              <Play className="mr-1.5 h-4 w-4" />
              {running ? "Routing…" : "Send through gateway"}
            </Button>
          </motion.div>
          {error && (
            <div className="mt-3 rounded-xl border border-destructive/40 bg-destructive/10 px-3 py-2 text-xs text-destructive">
              {error}
            </div>
          )}
        </div>

        {/* Response panel */}
        <div className="glass rounded-2xl p-6 flex flex-col">
          <div className="flex items-center gap-2">
            <Sparkles className="h-4 w-4 text-emerald" />
            <p className="text-sm font-medium tracking-tight">Router decision</p>
          </div>

          {running && (
            <div className="mt-6 space-y-3">
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

          {result && (
            <motion.div
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              className="mt-4 flex flex-col gap-4 flex-1"
            >
              {/* Decision metadata */}
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

              {/* Arm scores */}
              {result.armScores && Object.keys(result.armScores).length > 0 && (
                <div>
                  <p className="mb-2 text-[0.65rem] uppercase tracking-[0.14em] text-muted-foreground flex items-center gap-1">
                    <Brain className="h-3 w-3" /> LinUCB arm scores
                  </p>
                  <div className="space-y-1.5">
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

              {/* Response answer */}
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

          {!running && !result && !error && (
            <p className="mt-6 text-xs text-muted-foreground">
              Send a request to see which provider the adaptive router selects, the LinUCB arm scores, and the full gateway response.
            </p>
          )}
        </div>
      </div>
    </AppShell>
  );
}
