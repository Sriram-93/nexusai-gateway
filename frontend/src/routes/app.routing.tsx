import { createFileRoute } from "@tanstack/react-router";
import { AnimatePresence, motion } from "motion/react";
import { useEffect, useState } from "react";
import { Check, GitBranch, Layers, Scale, Sparkles, Zap } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { Button } from "@/components/ui/button";
import { Slider } from "@/components/ui/slider";
import { Label } from "@/components/ui/label";
import { dashboardApi, ApiError, type ArmState } from "@/lib/api";

export const Route = createFileRoute("/app/routing")({
  head: () => ({
    meta: [
      { title: "Routing Engine — NexusAI" },
      {
        name: "description",
        content:
          "Configure NexusAI routing strategy: static pinning, rule-based policies, weighted traffic splits, or federated LinUCB bandit learning.",
      },
      { property: "og:title", content: "Routing Engine — NexusAI" },
      {
        property: "og:description",
        content: "Static, rule-based, weighted, and federated LinUCB routing strategies.",
      },
    ],
  }),
  component: RoutingEngine,
});

const STRATEGIES = [
  {
    id: "STATIC",
    name: "Static",
    icon: Layers,
    tone: "indigo",
    desc: "Pin every request to a single upstream model. Deterministic and audit-friendly.",
  },
  {
    id: "RULE_BASED",
    name: "Rule-Based",
    icon: GitBranch,
    tone: "cyan",
    desc: "Route on prompt length, tenant tier, or tool usage with declarative policies.",
  },
  {
    id: "WEIGHTED",
    name: "Weighted",
    icon: Scale,
    tone: "amber",
    desc: "Split traffic by fixed percentages for canaries and cost blending.",
  },
  {
    id: "FEDERATED",
    name: "Federated LinUCB",
    icon: Sparkles,
    tone: "emerald",
    desc: "Contextual bandit learns the optimal provider per request in real time.",
  },
] as const;

type StrategyId = (typeof STRATEGIES)[number]["id"];

function RoutingEngine() {
  const [strategy, setStrategy] = useState<StrategyId>("FEDERATED");
  const [isSaving, setIsSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Learning state from backend
  const [learningState, setLearningState] = useState<{
    activeStrategy: string;
    activeEngine: string;
    rewardTier: string;
    armStates: ArmState[];
  } | null>(null);

  // Weighted strategy: build weights map from real arm keys
  const [weights, setWeights] = useState<Record<string, number>>({});

  useEffect(() => {
    dashboardApi.getLearning()
      .then((data) => {
        setLearningState(data);
        // Sync UI to current backend strategy
        const strat = data.activeStrategy as StrategyId;
        if (STRATEGIES.some((s) => s.id === strat)) setStrategy(strat);

        // Initialize weights evenly from real arm keys
        if (data.armStates.length > 0) {
          const evenWeight = Math.round(100 / data.armStates.length);
          const w: Record<string, number> = {};
          data.armStates.forEach((arm, i) => {
            w[arm.armKey] = i === 0 ? 100 - evenWeight * (data.armStates.length - 1) : evenWeight;
          });
          setWeights(w);
        }
      })
      .catch(console.error);
  }, []);

  const deploy = async () => {
    setIsSaving(true);
    setError(null);
    try {
      const payload =
        strategy === "WEIGHTED" && Object.keys(weights).length > 0
          ? { strategy, weights }
          : { strategy };
      await dashboardApi.switchRoutingStrategy(payload.strategy, (payload as any).weights);
      setSaved(true);
      window.setTimeout(() => setSaved(false), 2500);
      // Refresh learning state
      const updated = await dashboardApi.getLearning();
      setLearningState(updated);
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : "Network error";
      setError(msg);
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <AppShell title="Routing Engine" subtitle="Decide how every inference request finds its model">
      <div className="grid gap-4 lg:grid-cols-2 xl:grid-cols-4">
        {STRATEGIES.map((s) => {
          const active = strategy === s.id;
          return (
            <motion.button
              key={s.id}
              onClick={() => setStrategy(s.id)}
              whileHover={{ y: -5 }}
              whileTap={{ scale: 0.985 }}
              transition={{ type: "spring", stiffness: 320, damping: 24 }}
              className={`glass relative overflow-hidden rounded-2xl p-5 text-left transition-colors duration-300 ${
                active
                  ? "border-[color-mix(in_oklab,var(--cyan)_55%,transparent)]"
                  : "hover:border-[color-mix(in_oklab,var(--foreground)_20%,transparent)]"
              }`}
            >
              {active && (
                <motion.span
                  layoutId="strategy-glow"
                  className="pointer-events-none absolute -right-14 -top-14 h-36 w-36 rounded-full blur-3xl"
                  style={{ background: `radial-gradient(circle, var(--${s.tone}), transparent 70%)` }}
                />
              )}
              <div className="relative flex items-center justify-between">
                <span
                  className="flex h-9 w-9 items-center justify-center rounded-xl"
                  style={{ background: `color-mix(in oklab, var(--${s.tone}) 16%, transparent)` }}
                >
                  <s.icon className={`h-4 w-4 text-${s.tone}`} />
                </span>
                <span
                  className={`flex h-5 w-5 items-center justify-center rounded-full border transition-all ${
                    active ? "grad-primary border-transparent" : "border-[var(--glass-border)]"
                  }`}
                >
                  {active && <Check className="h-3 w-3 text-primary-foreground" />}
                </span>
              </div>
              <p className="relative mt-4 text-sm font-semibold tracking-tight">{s.name}</p>
              <p className="relative mt-1.5 text-xs leading-relaxed text-muted-foreground">
                {s.desc}
              </p>
              {learningState?.activeStrategy === s.id && (
                <span className="mt-2 inline-block rounded-full bg-emerald/20 px-2 py-0.5 text-[0.65rem] text-emerald">
                  Active
                </span>
              )}
            </motion.button>
          );
        })}
      </div>

      <AnimatePresence mode="popLayout">
        {strategy === "WEIGHTED" && (
          <motion.div
            key="weighted"
            initial={{ opacity: 0, height: 0, y: -10 }}
            animate={{ opacity: 1, height: "auto", y: 0 }}
            exit={{ opacity: 0, height: 0, y: -10 }}
            transition={{ duration: 0.4, ease: [0.22, 1, 0.36, 1] }}
            className="overflow-hidden"
          >
            <div className="glass mt-4 rounded-2xl p-6">
              <p className="text-sm font-medium tracking-tight">Traffic distribution</p>
              <p className="mt-1 text-xs text-muted-foreground">
                Weights are applied across active model arms. Values normalized to 100% on dispatch.
              </p>
              {Object.keys(weights).length === 0 ? (
                <p className="mt-4 text-xs text-muted-foreground italic">
                  No model arms discovered yet. Register and enable providers first.
                </p>
              ) : (
                <div className="mt-6 space-y-7">
                  {Object.entries(weights).map(([armKey, pct]) => (
                    <WeightRow
                      key={armKey}
                      label={armKey}
                      hint={`Arm key: ${armKey}`}
                      value={pct}
                      onChange={(v) => setWeights((prev) => ({ ...prev, [armKey]: v }))}
                      tone="cyan"
                    />
                  ))}
                </div>
              )}
            </div>
          </motion.div>
        )}

        {strategy === "FEDERATED" && (
          <motion.div
            key="linucb"
            initial={{ opacity: 0, height: 0, y: -10 }}
            animate={{ opacity: 1, height: "auto", y: 0 }}
            exit={{ opacity: 0, height: 0, y: -10 }}
            transition={{ duration: 0.4, ease: [0.22, 1, 0.36, 1] }}
            className="overflow-hidden"
          >
            <div className="glass mt-4 rounded-2xl p-6">
              <p className="text-sm font-medium tracking-tight">Federated Adaptive Decision Engine (FADE)</p>
              <p className="mt-1 text-xs text-muted-foreground">
                LinUCB bandit state from the backend — real-time per-arm scores.
              </p>

              {learningState ? (
                <div className="mt-6 grid gap-3 sm:grid-cols-3">
                  {[
                    ["Active Engine", learningState.activeEngine],
                    ["Reward Tier", learningState.rewardTier],
                    ["Arms Tracked", String(learningState.totalArmsTracked)],
                  ].map(([k, v]) => (
                    <div key={k} className="glass rounded-xl p-4">
                      <p className="text-[0.68rem] uppercase tracking-[0.14em] text-muted-foreground">{k}</p>
                      <p className="mt-1.5 text-sm font-mono">{v}</p>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="mt-4 text-xs text-muted-foreground">Loading bandit state…</div>
              )}

              {learningState && learningState.armStates.length > 0 && (
                <div className="mt-6">
                  <p className="mb-3 text-xs uppercase tracking-[0.14em] text-muted-foreground">
                    Per-arm reputation (EWMA)
                  </p>
                  <div className="space-y-3 overflow-x-auto">
                    <table className="w-full text-xs">
                      <thead>
                        <tr className="text-left text-[0.68rem] uppercase text-muted-foreground">
                          {["Arm", "Health", "Quality", "Latency", "Avail", "Requests"].map((h) => (
                            <th key={h} className="pb-2 pr-4 font-medium">{h}</th>
                          ))}
                        </tr>
                      </thead>
                      <tbody>
                        {learningState.armStates.map((arm) => (
                          <tr key={arm.armKey} className="border-t">
                            <td className="py-2 pr-4 font-mono text-cyan">{arm.armKey}</td>
                            <td className="py-2 pr-4">
                              <span className={arm.healthScore >= 0.8 ? "text-emerald" : arm.healthScore >= 0.5 ? "text-amber" : "text-destructive"}>
                                {(arm.healthScore * 100).toFixed(1)}%
                              </span>
                            </td>
                            <td className="py-2 pr-4 text-muted-foreground">{(arm.avgQuality * 100).toFixed(1)}%</td>
                            <td className="py-2 pr-4">{Math.round(arm.avgLatencyMs)}ms</td>
                            <td className="py-2 pr-4 text-muted-foreground">{(arm.availability * 100).toFixed(1)}%</td>
                            <td className="py-2 pr-4 font-mono">{arm.totalRequests.toLocaleString()}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {error && (
        <div className="mt-4 rounded-xl border border-destructive/40 bg-destructive/10 px-4 py-3 text-xs text-destructive">
          {error}
        </div>
      )}

      <div className="glass mt-4 flex flex-wrap items-center justify-between gap-4 rounded-2xl p-6">
        <div className="flex items-center gap-3">
          <Zap className="h-4 w-4 text-amber" />
          <div>
            <Label className="text-sm font-medium">Deploy routing strategy</Label>
            <p className="text-xs text-muted-foreground">
              Switches the active engine at runtime with zero restart.
            </p>
          </div>
        </div>
        <motion.div whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.98 }}>
          <Button
            disabled={isSaving}
            onClick={deploy}
            className="grad-primary h-10 rounded-xl text-sm text-primary-foreground transition-shadow hover:shadow-[0_0_34px_-6px_var(--cyan)]"
          >
            {saved ? (
              <span className="flex items-center gap-1.5">
                <Check className="h-4 w-4" /> Deployed
              </span>
            ) : isSaving ? "Deploying..." : (
              "Deploy configuration"
            )}
          </Button>
        </motion.div>
      </div>
    </AppShell>
  );
}

function WeightRow({
  label,
  hint,
  value,
  onChange,
  tone,
}: {
  label: string;
  hint: string;
  value: number;
  onChange: (v: number) => void;
  tone: string;
}) {
  return (
    <div>
      <div className="flex items-end justify-between">
        <div>
          <p className="text-sm font-medium font-mono">{label}</p>
          <p className="text-xs text-muted-foreground">{hint}</p>
        </div>
        <span className={`font-mono text-sm text-${tone}`}>{value}%</span>
      </div>
      <Slider
        value={[value]}
        onValueChange={(v) => onChange(v[0] ?? 0)}
        max={100}
        step={1}
        className="mt-3"
      />
    </div>
  );
}
