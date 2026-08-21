import { createFileRoute } from "@tanstack/react-router";
import { motion } from "motion/react";
import { useEffect, useState } from "react";
import { Network, ArrowRight, Play, Terminal, Database, Loader2 } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { dashboardApi, pipelineApi, chatApi, type AgentInfo } from "@/lib/api";

export const Route = createFileRoute("/app/agents")({
  head: () => ({ meta: [{ title: "Agent Pipelines — NexusAI" }] }),
  component: Agents,
});

function Agents() {
  const [agents, setAgents] = useState<AgentInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [prompt, setPrompt] = useState("Analyze the routing decisions from this week.");
  const [running, setRunning] = useState(false);
  const [result, setResult] = useState<any | null>(null);
  const [runError, setRunError] = useState<string | null>(null);

  useEffect(() => {
    dashboardApi.getAgents()
      .then(setAgents)
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  const runPipeline = async () => {
    if (!prompt.trim()) return;
    setRunning(true);
    setResult(null);
    setRunError(null);
    try {
      const res = await chatApi.agentChat({
        message: prompt,
        userId: "agent-console",
        priority: "HIGH",
      });
      setResult(res);
    } catch (err: any) {
      if (err.status === 401) {
        setRunError("API key required. Provision a tenant first via the API Keys page.");
      } else {
        setRunError(err.message ?? "Failed to run agent pipeline");
      }
    } finally {
      setRunning(false);
    }
  };

  return (
    <AppShell title="Agent Pipelines" subtitle="Multi-stage AEDF execution graph">
      <div className="grid gap-6 lg:grid-cols-[1fr_360px]">

        {/* Pipeline Visualizer */}
        <div className="glass rounded-2xl p-6">
          <div className="flex items-center gap-2 mb-6">
            <Network className="h-5 w-5 text-indigo" />
            <h2 className="text-sm font-semibold tracking-tight">Registered Agent Graph</h2>
          </div>

          {loading && (
            <div className="space-y-4 pl-14">
              {[0, 1, 2, 3].map((i) => (
                <div key={i} className="h-20 animate-pulse rounded-xl bg-[var(--glass-hover)]" />
              ))}
            </div>
          )}

          {!loading && agents.length === 0 && (
            <p className="text-sm text-muted-foreground pl-4">No agents registered in the Spring context.</p>
          )}

          <div className="relative mt-8 space-y-4">
            {agents.length > 0 && (
              <div className="absolute left-6 top-8 bottom-8 w-[2px] bg-[var(--glass-border)] z-0" />
            )}

            {agents.map((agent, i) => (
              <motion.div
                key={agent.name}
                initial={{ opacity: 0, x: -20 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ delay: i * 0.1 }}
                className="relative z-10 flex gap-4"
              >
                <div className="mt-2 flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-background border-2 border-indigo">
                  <span className="text-xs font-bold text-indigo">{agent.order}</span>
                </div>
                <div className="flex-1 glass rounded-xl p-4 transition-colors hover:bg-[var(--glass-hover)]">
                  <h3 className="text-sm font-semibold text-cyan">{agent.name}</h3>

                  <div className="mt-3 grid grid-cols-2 gap-2 text-xs">
                    <div>
                      <p className="text-[0.65rem] uppercase text-muted-foreground mb-1">Requires</p>
                      <div className="flex flex-wrap gap-1">
                        {(agent.requiredInputs ?? []).length > 0 ? agent.requiredInputs.map((req) => (
                          <span key={req} className="bg-background/50 border border-[var(--glass-border)] rounded px-1.5 py-0.5">
                            {req}
                          </span>
                        )) : <span className="text-muted-foreground italic">none</span>}
                      </div>
                    </div>
                    <div>
                      <p className="text-[0.65rem] uppercase text-muted-foreground mb-1">Produces</p>
                      <div className="flex flex-wrap gap-1">
                        {(agent.producedOutputs ?? []).length > 0 ? agent.producedOutputs.map((prod) => (
                          <span key={prod} className="bg-indigo/20 text-indigo rounded px-1.5 py-0.5">
                            {prod}
                          </span>
                        )) : <span className="text-muted-foreground italic">none</span>}
                      </div>
                    </div>
                  </div>

                  {agent.dependencies?.length > 0 && (
                    <p className="mt-2 text-[0.65rem] text-muted-foreground">
                      Depends on: {agent.dependencies.join(", ")}
                    </p>
                  )}
                </div>
              </motion.div>
            ))}
          </div>
        </div>

        {/* Execution Console */}
        <div className="glass rounded-2xl p-6 h-fit">
          <div className="flex items-center gap-2 mb-4">
            <Terminal className="h-5 w-5 text-emerald" />
            <h2 className="text-sm font-semibold tracking-tight">Execution Console</h2>
          </div>
          <p className="text-xs text-muted-foreground mb-4">
            Run a prompt through the full agent pipeline via POST /api/agent/chat.
            Requires a valid X-API-Key.
          </p>

          <Textarea
            value={prompt}
            onChange={(e) => setPrompt(e.target.value)}
            rows={4}
            className="resize-none rounded-xl border-[var(--glass-border)] bg-[var(--glass-bg)] font-mono text-xs backdrop-blur-md"
          />

          <motion.div whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.98 }} className="mt-4">
            <Button
              className="grad-primary w-full h-10 rounded-xl text-sm text-primary-foreground"
              onClick={runPipeline}
              disabled={running || !prompt.trim()}
            >
              {running ? (
                <><Loader2 className="mr-2 h-4 w-4 animate-spin" /> Running…</>
              ) : (
                <><Play className="mr-2 h-4 w-4" /> Run Pipeline</>
              )}
            </Button>
          </motion.div>

          {runError && (
            <div className="mt-3 rounded-xl border border-destructive/40 bg-destructive/10 px-3 py-2 text-xs text-destructive">
              {runError}
            </div>
          )}

          {result && (
            <div className="mt-6 pt-6 border-t border-[var(--glass-border)]">
              <div className="flex items-center gap-2 mb-3">
                <Database className="h-4 w-4 text-muted-foreground" />
                <h3 className="text-xs font-medium text-muted-foreground uppercase tracking-widest">Response</h3>
              </div>
              <div className="font-mono text-[0.65rem] text-muted-foreground bg-[var(--glass-hover)] p-3 rounded-lg overflow-x-auto space-y-1">
                <div><span className="text-cyan">provider</span>: {result.provider}</div>
                <div><span className="text-emerald">latencyMs</span>: {result.latencyMs}ms</div>
                <div className="border-t border-[var(--glass-border)] pt-1 mt-1">
                  <span className="text-amber">answer</span>: {result.answer?.slice(0, 200)}{result.answer?.length > 200 ? "…" : ""}
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </AppShell>
  );
}
