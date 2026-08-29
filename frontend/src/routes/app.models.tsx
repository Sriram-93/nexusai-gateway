import { createFileRoute } from "@tanstack/react-router";
import { motion, AnimatePresence } from "motion/react";
import { useEffect, useState, useMemo } from "react";
import { AppShell } from "@/components/AppShell";
import {
  Search, Server, CircleCheck, CircleSlash, RefreshCw, X,
  BrainCircuit, Activity, Coins, Crown, Zap, FlaskConical,
  Settings, Plus, Loader2, ChevronRight, Lock,
} from "lucide-react";
import { providersApi, dashboardApi, type ProviderSummary, type ModelSummary, type ModelHealth } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import { useToast } from "@/lib/toast";
import { useUser } from "@/lib/user-context";
import { useUpgradeRequests } from "@/lib/upgrade-requests";
import { Authorize } from "@/components/Authorize";
import { useNavigate } from "@tanstack/react-router";

export const Route = createFileRoute("/app/models")({
  head: () => ({
    meta: [
      { title: "Model Hub — NexusAI" },
      { name: "description", content: "Browse and manage AI models across all connected providers." },
    ],
  }),
  component: ModelHub,
});

type EnrichedModel = ModelSummary & { providerSlug: string; providerName: string; health?: ModelHealth | undefined; category: Category };
type Category = "premium" | "balanced" | "specialized" | "custom";

const CATEGORY_META = {
  premium: { label: "High Intelligence", icon: Crown, color: "indigo", sub: "Most capable models for complex tasks" },
  balanced: { label: "Cost-Effective", icon: Zap, color: "cyan", sub: "Best performance per dollar" },
  specialized: { label: "Specialized", icon: FlaskConical, color: "emerald", sub: "Embeddings, vision, audio" },
  custom: { label: "Custom / Registered", icon: Settings, color: "amber", sub: "Manually registered models" },
};

function categorize(m: ModelSummary): Category {
  const id = m.modelId.toLowerCase();
  if (id.includes("embed") || id.includes("whisper") || id.includes("vision") || id.includes("dall")) return "specialized";
  if (m.inputPricePer1M > 5 || id.includes("gpt-4") || id.includes("claude-3-5") || id.includes("gemini-1.5-pro")) return "premium";
  if (m.inputPricePer1M < 1 || id.includes("haiku") || id.includes("3.5-turbo") || id.includes("llama") || id.includes("groq")) return "balanced";
  return "custom";
}

function ModelHub() {
  const [providers, setProviders] = useState<ProviderSummary[]>([]);
  const [models, setModels] = useState<EnrichedModel[]>([]);
  const [healthData, setHealthData] = useState<ModelHealth[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [showActiveOnly, setShowActiveOnly] = useState(false);
  const [selectedModel, setSelectedModel] = useState<EnrichedModel | null>(null);

  const { success, error: toastError } = useToast();
  const { session } = useUser();
  const { openModal: openUpgrade } = useUpgradeRequests();
  const navigate = useNavigate();
  const role = session.role ?? "SOLO";
  const canToggle = role === "SOLO" || role === "SUPER_ADMIN" || role === "ORG_ADMIN";

  const loadAll = async () => {
    setLoading(true);
    setError(null);
    try {
      const [provs, healths] = await Promise.all([
        providersApi.listProviders(),
        dashboardApi.getModels().catch(() => [] as ModelHealth[]),
      ]);
      setProviders(provs);
      setHealthData(healths);

      const allModelsArrays = await Promise.all(
        provs.map(async (p) => {
          try {
            const pModels = await providersApi.listModels(p.slug);
            return pModels.map((m) => ({
              ...m,
              providerSlug: p.slug,
              providerName: p.displayName,
              health: healths.find((h) => h.armKey === m.armKey),
              category: categorize(m),
            }));
          } catch { return []; }
        })
      );
      setModels(allModelsArrays.flat());
    } catch (err: any) {
      setError(err.message ?? "Failed to load model catalog");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadAll(); }, []);

  const toggleModel = async (model: EnrichedModel, enable: boolean) => {
    if (!canToggle) { openUpgrade(); return; }
    try {
      if (enable) await providersApi.enableModel(model.providerSlug, model.modelId);
      else await providersApi.disableModel(model.providerSlug, model.modelId);
      setModels((prev) => prev.map((m) => m.armKey === model.armKey ? { ...m, enabled: enable } : m));
      if (selectedModel?.armKey === model.armKey) setSelectedModel((p) => p ? { ...p, enabled: enable } : null);
      success(enable ? "Model enabled" : "Model disabled", model.displayName);
    } catch (err: any) {
      toastError("Update failed", err.message);
    }
  };

  const filtered = useMemo(() =>
    models.filter((m) => {
      return (!showActiveOnly || m.enabled) &&
      (!search ||
      m.displayName.toLowerCase().includes(search.toLowerCase()) ||
      m.modelId.toLowerCase().includes(search.toLowerCase()));
    }), [models, search, showActiveOnly]);

  const hasData = providers.length > 0 || models.length > 0;

  return (
    <AppShell title="Model Hub" subtitle="Browse, configure, and assign model access across your workspace">
      {/* No providers guard */}
      {!loading && !hasData && (
        <div className="section-panel p-16 text-center border border-[var(--glass-border)] bg-[var(--glass-bg)]">
          <Lock className="mx-auto mb-4 h-12 w-12 text-muted-foreground opacity-50" />
          <p className="text-base font-semibold text-foreground">Model Hub is locked</p>
          <p className="mt-2 text-sm text-muted-foreground max-w-sm mx-auto">
            Connect at least one AI provider to unlock the Model Hub and discover available models.
          </p>
          <Button onClick={() => navigate({ to: '/app/providers' })} className="grad-primary mt-6 h-10 rounded-xl text-sm font-medium text-white">
            <ChevronRight className="mr-1 h-4 w-4" /> Go to Provider Hub
          </Button>
        </div>
      )}

      {hasData && (
        <>
          {/* Controls */}
          <div className="mb-6 flex flex-wrap items-center gap-3">
            <div className="relative flex-1 min-w-52">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
              <Input
                placeholder="Search models..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="glass-input h-9 pl-9 text-xs rounded-xl"
              />
            </div>
            <div className="flex items-center gap-2 border-l border-[var(--glass-border)] pl-3">
              <Switch checked={showActiveOnly} onCheckedChange={setShowActiveOnly} id="active-only" />
              <label htmlFor="active-only" className="text-xs text-muted-foreground cursor-pointer">Enabled Only</label>
            </div>
            <p className="text-xs text-muted-foreground ml-auto">{models.length} models across {providers.length} providers</p>
            <Button
              onClick={async () => {
                try {
                  await providersApi.discoverAll();
                  success("Discovery complete", "Updated model catalog from connected provider APIs.");
                  loadAll();
                } catch (err: any) {
                  toastError("Discovery failed", err.message);
                }
              }}
              variant="outline"
              size="sm"
              className="glass h-9 rounded-lg text-xs gap-1.5 text-cyan border-cyan/40 hover:bg-cyan/10"
            >
              <Zap className="h-3.5 w-3.5" /> Re-Discover Models
            </Button>
            <Button
              onClick={async () => {
                setLoading(true);
                try {
                  const res = await fetch("http://localhost:8080/api/providers/gemini/test-and-load-reasoning", { method: "POST" });
                  const data = await res.json();
                  if (data.status === "SUCCESS") {
                    success("Reasoning Models Verified", `Loaded ${data.totalActive} working Gemini reasoning models into gateway.`);
                  } else {
                    toastError("Verification Failed", data.message || "Failed to verify models");
                  }
                  await loadAll();
                } catch (err: any) {
                  toastError("Execution Error", err.message);
                  setLoading(false);
                }
              }}
              variant="outline"
              size="sm"
              className="glass h-9 rounded-lg text-xs gap-1.5 text-amber border-amber/40 hover:bg-amber/10"
            >
              <BrainCircuit className="h-3.5 w-3.5" /> Test & Load Working Reasoning Models
            </Button>
            <Button onClick={loadAll} variant="outline" size="sm" className="glass h-9 rounded-lg text-xs gap-1.5">
              <RefreshCw className="h-3.5 w-3.5" /> Sync
            </Button>
          </div>

          {error && (
            <div className="mb-4 rounded-xl border border-destructive/40 bg-destructive/10 px-4 py-3 text-xs text-destructive">{error}</div>
          )}

          {loading && (
            <div className="space-y-6">
              {[0, 1].map((i) => <div key={i} className="space-y-3"><div className="h-5 w-40 animate-pulse rounded-lg bg-[var(--glass-hover)]" /><div className="grid grid-cols-2 gap-3 md:grid-cols-3">{[0,1,2].map((j) => <div key={j} className="glass h-28 animate-pulse rounded-2xl" />)}</div></div>)}
            </div>
          )}

          {/* Category carousels */}
          {!loading && (
            <div className="flex flex-col lg:flex-row gap-6">
              <div className="flex-1 space-y-8">
                {(Object.keys(CATEGORY_META) as Category[]).map((cat) => {
                  const catModels = filtered.filter((m) => m.category === cat);
                  if (catModels.length === 0) return null;
                  const meta = CATEGORY_META[cat];
                  const Icon = meta.icon;
                  return (
                    <div key={cat}>
                      <div className="mb-3 flex items-center gap-2">
                        <Icon className={`h-4 w-4 text-${meta.color}`} />
                        <p className="text-sm font-semibold">{meta.label}</p>
                        <span className="rounded-full bg-[var(--glass-hover)] px-2 py-0.5 text-[0.65rem] text-muted-foreground">{catModels.length}</span>
                        <p className="ml-1 text-xs text-muted-foreground hidden sm:block">{meta.sub}</p>
                      </div>
                      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
                        {catModels.map((m) => (
                          <motion.div
                            key={m.armKey}
                            onClick={() => setSelectedModel(m)}
                            whileHover={{ y: -3, scale: 1.01 }}
                            whileTap={{ scale: 0.99 }}
                            className={`section-panel cursor-pointer p-4 transition-all duration-200 hover:border-[color-mix(in_oklab,var(--${meta.color})_30%,transparent)] ${
                              selectedModel?.armKey === m.armKey
                                ? `border-${meta.color}/40 shadow-[0_0_20px_-8px_var(--${meta.color})]`
                                : ""
                            } ${m.enabled ? "" : "opacity-60"}`}
                          >
                            <div className="flex items-start justify-between gap-2 mb-3">
                              <div className="min-w-0">
                                <p className="text-sm font-semibold truncate">{m.displayName}</p>
                                <p className="font-mono text-[0.65rem] text-muted-foreground truncate mt-0.5">{m.modelId}</p>
                              </div>
                              {m.enabled ? (
                                <div className="flex items-center gap-1.5 shrink-0">
                                  <span className="flex items-center gap-1 rounded-full border border-emerald/50 bg-emerald/10 px-2.5 py-0.5 text-[0.6rem] font-bold text-emerald shadow-[0_0_14px_rgba(16,185,129,0.45)]">
                                    <span className="relative flex h-1.5 w-1.5">
                                      <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald opacity-75" />
                                      <span className="relative inline-flex h-1.5 w-1.5 rounded-full bg-emerald" />
                                    </span>
                                    LIVE & VERIFIED
                                  </span>
                                </div>
                              ) : (
                                <CircleSlash className="h-4 w-4 shrink-0 text-muted-foreground opacity-50" />
                              )}
                            </div>
                            <div className="flex flex-wrap gap-1.5 text-[0.65rem]">
                              <span className="flex items-center gap-1 rounded-md bg-[var(--glass-bg)] border border-[var(--glass-border)] px-2 py-1">
                                <Server className="h-3 w-3 text-muted-foreground" /> {m.providerName}
                              </span>
                              {m.contextWindowTokens > 0 && (
                                <span className="rounded-md bg-[var(--glass-bg)] border border-[var(--glass-border)] px-2 py-1">
                                  {(m.contextWindowTokens / 1000).toFixed(0)}K ctx
                                </span>
                              )}
                              {m.pricingVerified && (
                                <span className="rounded-md bg-[var(--glass-bg)] border border-[var(--glass-border)] px-2 py-1 font-mono text-emerald">
                                  ${m.inputPricePer1M.toFixed(2)}/1M
                                </span>
                              )}
                            </div>
                          </motion.div>
                        ))}
                      </div>
                    </div>
                  );
                })}

                {filtered.length === 0 && !loading && (
                  <div className="glass rounded-2xl p-12 text-center">
                    <Search className="mx-auto mb-3 h-8 w-8 opacity-30" />
                    <p className="text-sm font-medium">No models match your search</p>
                  </div>
                )}
              </div>

              {/* Inspector panel */}
              <AnimatePresence mode="wait">
                {selectedModel ? (
                  <motion.div
                    key={selectedModel.armKey}
                    initial={{ opacity: 0, x: 20 }}
                    animate={{ opacity: 1, x: 0 }}
                    exit={{ opacity: 0, x: 20 }}
                    className="w-full lg:w-[320px] shrink-0"
                  >
                    <div className="section-panel p-5 sticky top-24">
                      <div className="flex items-start justify-between mb-4">
                        <div className="min-w-0">
                          <h3 className="font-semibold text-base leading-tight truncate">{selectedModel.displayName}</h3>
                          <p className="font-mono text-xs text-muted-foreground mt-1 truncate">{selectedModel.armKey}</p>
                        </div>
                        <button onClick={() => setSelectedModel(null)} className="glass ml-2 flex h-7 w-7 shrink-0 items-center justify-center rounded-lg hover:bg-[var(--glass-hover)]">
                          <X className="h-3.5 w-3.5" />
                        </button>
                      </div>

                      {/* Toggle */}
                      <div className="flex items-center justify-between p-3 rounded-xl bg-[var(--glass-bg)] border border-[var(--glass-border)] mb-4">
                        <div className="text-xs">
                          <span className="block font-semibold uppercase tracking-wider text-[0.65rem] text-muted-foreground mb-0.5">Routing Status</span>
                          {selectedModel.enabled
                            ? <span className="text-emerald font-medium flex items-center gap-1.5"><CircleCheck className="h-3.5 w-3.5" /> Eligible</span>
                            : <span className="text-muted-foreground font-medium flex items-center gap-1.5"><CircleSlash className="h-3.5 w-3.5" /> Excluded</span>
                          }
                        </div>
                        {canToggle
                          ? <Switch checked={selectedModel.enabled} onCheckedChange={(v) => toggleModel(selectedModel, v)} />
                          : <button onClick={openUpgrade} className="text-xs text-amber underline">Request access</button>
                        }
                      </div>

                      <div className="space-y-4">
                        {/* Pricing */}
                        <div>
                          <h4 className="flex items-center gap-1.5 text-xs font-bold uppercase tracking-wider text-muted-foreground mb-2">
                            <Coins className="h-3.5 w-3.5 text-amber" /> Pricing / 1M tokens
                          </h4>
                          {selectedModel.pricingVerified ? (
                            <div className="grid grid-cols-2 gap-2">
                              {[["Input", selectedModel.inputPricePer1M, "emerald"], ["Output", selectedModel.outputPricePer1M, "amber"]].map(([label, val, color]) => (
                                <div key={label as string} className="glass p-2 rounded-lg text-center">
                                  <span className="block text-[0.65rem] text-muted-foreground mb-1 uppercase">{label}</span>
                                  <span className={`font-mono font-semibold text-${color}`}>${(val as number).toFixed(2)}</span>
                                </div>
                              ))}
                            </div>
                          ) : (
                            <p className="text-xs text-muted-foreground italic bg-[var(--glass-bg)] p-3 rounded-lg border border-dashed border-[var(--glass-border)] text-center">Pricing unverified</p>
                          )}
                        </div>

                        {/* Capabilities */}
                        <div>
                          <h4 className="flex items-center gap-1.5 text-xs font-bold uppercase tracking-wider text-muted-foreground mb-2">
                            <BrainCircuit className="h-3.5 w-3.5 text-indigo" /> Capabilities
                          </h4>
                          <div className="flex flex-wrap gap-1.5">
                            <span className="text-[0.7rem] bg-[var(--glass-bg)] border border-[var(--glass-border)] px-2 py-1 rounded-md">
                              Ctx: <strong>{selectedModel.contextWindowTokens > 0 ? `${(selectedModel.contextWindowTokens/1000).toFixed(0)}K` : "Unknown"}</strong>
                            </span>
                            <span className="text-[0.7rem] bg-[var(--glass-bg)] border border-[var(--glass-border)] px-2 py-1 rounded-md">
                              ~{selectedModel.estimatedLatencyMs}ms est.
                            </span>
                          </div>
                        </div>

                        {/* Telemetry */}
                        {selectedModel.health && (
                          <div>
                            <h4 className="flex items-center gap-1.5 text-xs font-bold uppercase tracking-wider text-muted-foreground mb-2">
                              <Activity className="h-3.5 w-3.5 text-cyan" /> Live Telemetry
                            </h4>
                            <div className="grid grid-cols-2 gap-2 text-xs">
                              {[
                                ["Requests", selectedModel.health.totalRequests, ""],
                                ["Failure Rate", selectedModel.health.failureRate !== null ? `${(selectedModel.health.failureRate * 100).toFixed(1)}%` : "0%", "text-destructive"],
                                ["Circuit", selectedModel.health.cbState, selectedModel.health.cbState === "OPEN" ? "text-destructive" : "text-emerald"],
                                ["Avg Latency", selectedModel.health.avgLatencyMs !== null ? `${selectedModel.health.avgLatencyMs.toFixed(0)}ms` : "—", ""],
                              ].map(([label, val, cls]) => (
                                <div key={label as string} className="flex justify-between p-2 rounded-lg bg-[var(--glass-bg)] border border-[var(--glass-border)]">
                                  <span className="text-muted-foreground">{label}</span>
                                  <span className={`font-mono ${cls}`}>{val}</span>
                                </div>
                              ))}
                            </div>
                          </div>
                        )}
                      </div>

                      <Authorize roles={["TEAM_LEAD", "TEAM_MEMBER", "SOLO", "ORG_ADMIN"]}>
                        <Button 
                          onClick={() => navigate({ 
                            to: '/app/sandbox', 
                            search: { model: selectedModel.modelId, provider: selectedModel.providerSlug } 
                          })}
                          className="w-full mt-4 grad-primary h-9 text-xs rounded-xl text-primary-foreground"
                        >
                          <FlaskConical className="h-3.5 w-3.5 mr-1.5" /> Try in Sandbox
                        </Button>
                      </Authorize>
                    </div>
                  </motion.div>
                ) : (
                  <motion.div
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    exit={{ opacity: 0 }}
                    className="section-panel hidden lg:flex w-[320px] shrink-0 items-center justify-center p-6 text-center border-dashed text-muted-foreground"
                  >
                    <div>
                      <BrainCircuit className="mx-auto mb-3 h-8 w-8 opacity-40" />
                      <p className="text-sm font-medium">Select a Model</p>
                      <p className="text-xs mt-1">Click any model card to view details, pricing, and live telemetry.</p>
                    </div>
                  </motion.div>
                )}
              </AnimatePresence>
            </div>
          )}
        </>
      )}
    </AppShell>
  );
}
