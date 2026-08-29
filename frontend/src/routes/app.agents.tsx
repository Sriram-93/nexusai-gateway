import { createFileRoute } from "@tanstack/react-router";
import { motion } from "motion/react";
import { useEffect, useState } from "react";
import { Network, Play, Terminal, Database, Loader2, ArrowDown } from "lucide-react";
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
  const [activeIdx, setActiveIdx] = useState<number | null>(null);
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
      // Simulate agent progression
      for (let i = 0; i < agents.length; i++) {
        setActiveIdx(i);
        await new Promise((r) => setTimeout(r, 200));
      }
      const res = await chatApi.agentChat({
        message: prompt,
        userId: "agent-console",
        priority: "HIGH",
      });
      setResult(res);
      setActiveIdx(null);
    } catch (err: any) {
      if (err.status === 401) {
        setRunError("API key required. Provision a tenant first via the API Keys page.");
      } else {
        setRunError(err.message ?? "Failed to run agent pipeline");
      }
      setActiveIdx(null);
    } finally {
      setRunning(false);
    }
  };

  return (
    <AppShell title="Agent Pipelines" subtitle="Multi-stage AEDF execution graph">
      <div className="grid gap-5 lg:grid-cols-[1fr_360px]">

        {/* Pipeline Visualizer */}
        <div className="section-panel">
          <div className="section-panel-header">
            <div className="flex items-center gap-2">
              <Network className="h-4 w-4 text-indigo" />
              <h2 className="text-[0.8125rem] font-semibold tracking-tight">Registered Agent Graph</h2>
            </div>
            <span className="text-[0.6875rem] text-muted-foreground">{agents.length} agents</span>
          </div>

          <div className="p-5">
            {loading && (
              <div className="space-y-3">
                {[0, 1, 2, 3].map((i) => (
                  <div key={i} className="skeleton h-20 w-full" />
                ))}
              </div>
            )}

            {!loading && agents.length === 0 && (
              <div className="flex flex-col items-center py-10 text-center">
                <Network className="h-10 w-10 text-muted-foreground/20 mb-3" />
                <p className="text-[0.8125rem] text-muted-foreground">No agents registered</p>
                <p className="text-[0.6875rem] text-muted-foreground/60 mt-1">Agents will appear when registered in the Spring context.</p>
              </div>
            )}

            <div className="space-y-1">
              {agents.map((agent, i) => {
                const isActive = activeIdx === i;
                const isDone = activeIdx !== null && i < activeIdx;
                return (
                  <div key={agent.name}>
                    <motion.div
                      initial={{ opacity: 0, x: -16 }}
                      animate={{ opacity: 1, x: 0 }}
                      transition={{ delay: i * 0.08 }}
                      className={`flex gap-3.5 items-start p-4 rounded-xl border transition-all duration-200 ${
                        isActive
                          ? "border-indigo/40 bg-indigo/5 shadow-[0_0_20px_-8px_var(--indigo)]"
                          : isDone
                          ? "border-emerald/20 bg-emerald/5"
                          : "border-[var(--glass-border)] bg-[var(--surface)]"
                      }`}
                    >
                      {/* Step number */}
                      <div className={`mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-full border-2 text-xs font-bold transition-colors ${
                        isActive ? "border-indigo bg-indigo/10 text-indigo" :
                        isDone ? "border-emerald bg-emerald/10 text-emerald" :
                        "border-border bg-background text-muted-foreground"
                      }`}>
                        {isDone ? "✓" : agent.order}
                      </div>

                      {/* Agent info */}
                      <div className="flex-1 min-w-0">
                        <h3 className={`text-[0.8125rem] font-semibold ${isActive ? "text-indigo" : "text-foreground"}`}>{agent.name}</h3>

                        <div className="mt-2 grid grid-cols-2 gap-2 text-xs">
                          <div>
                            <p className="text-[0.5625rem] uppercase text-muted-foreground/50 mb-1 tracking-wider">Requires</p>
                            <div className="flex flex-wrap gap-1">
                              {(agent.requiredInputs ?? []).length > 0 ? agent.requiredInputs.map((req) => (
                                <span key={req} className="bg-[var(--surface-subtle)] border border-[var(--glass-border)] rounded px-1.5 py-0.5 text-[0.625rem]">
                                  {req}
                                </span>
                              )) : <span className="text-muted-foreground/40 text-[0.625rem]">none</span>}
                            </div>
                          </div>
                          <div>
                            <p className="text-[0.5625rem] uppercase text-muted-foreground/50 mb-1 tracking-wider">Produces</p>
                            <div className="flex flex-wrap gap-1">
                              {(agent.producedOutputs ?? []).length > 0 ? agent.producedOutputs.map((prod) => (
                                <span key={prod} className="bg-indigo/10 text-indigo rounded px-1.5 py-0.5 text-[0.625rem]">
                                  {prod}
                                </span>
                              )) : <span className="text-muted-foreground/40 text-[0.625rem]">none</span>}
                            </div>
                          </div>
                        </div>

                        {agent.dependencies?.length > 0 && (
                          <p className="mt-1.5 text-[0.625rem] text-muted-foreground/60">
                            Depends on: {agent.dependencies.join(", ")}
                          </p>
                        )}
                      </div>
                    </motion.div>

                    {/* Connector arrow */}
                    {i < agents.length - 1 && (
                      <div className="flex justify-center py-1">
                        <ArrowDown className={`h-3.5 w-3.5 ${
                          activeIdx !== null && i < activeIdx ? "text-emerald" :
                          isActive ? "text-indigo animate-pulse" :
                          "text-muted-foreground/20"
                        }`} />
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        </div>

        {/* Execution Console */}
        <div className="section-panel h-fit sticky top-20">
          <div className="section-panel-header">
            <div className="flex items-center gap-2">
              <Terminal className="h-4 w-4 text-emerald" />
              <h2 className="text-[0.8125rem] font-semibold tracking-tight">Execution Console</h2>
            </div>
          </div>
          <div className="p-5 space-y-4">
            <p className="text-[0.6875rem] text-muted-foreground">
              Run a prompt through the full agent pipeline via POST /api/agent/chat.
              Requires a valid X-API-Key.
            </p>

            <Textarea
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              rows={4}
              className="resize-none rounded-xl border-border bg-[var(--surface-subtle)] font-mono text-xs"
            />

            <Button
              className="grad-primary w-full h-10 rounded-xl text-sm text-primary-foreground gap-2"
              onClick={runPipeline}
              disabled={running || !prompt.trim()}
            >
              {running ? (
                <><Loader2 className="h-4 w-4 animate-spin" /> Running…</>
              ) : (
                <><Play className="h-4 w-4" /> Run Pipeline</>
              )}
            </Button>

            {runError && (
              <div className="rounded-lg border border-destructive/30 bg-destructive/5 px-3 py-2.5 text-xs text-destructive">
                {runError}
              </div>
            )}

            {result && (
              <div className="pt-4 border-t border-[var(--glass-border)] space-y-3">
                <div className="flex items-center gap-2">
                  <Database className="h-3.5 w-3.5 text-muted-foreground" />
                  <h3 className="text-[0.625rem] font-medium text-muted-foreground uppercase tracking-widest">Response</h3>
                </div>
                <div className="font-mono text-[0.65rem] text-muted-foreground bg-[var(--surface-inset)] p-3 rounded-lg overflow-x-auto space-y-1">
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
      </div>
    </AppShell>
  );
}
