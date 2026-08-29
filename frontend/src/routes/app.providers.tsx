import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { motion, AnimatePresence } from "motion/react";
import { useEffect, useState, useMemo } from "react";
import {
  CircleCheck, CircleSlash, Plus, RefreshCw, Server, X, Key,
  Loader2, Globe, Zap, Check, Cpu, Activity, ShieldCheck, Sparkles,
  Search, SlidersHorizontal, CheckSquare, Square, AlertTriangle, ShieldAlert, BrainCircuit
} from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { Switch } from "@/components/ui/switch";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { providersApi, type ProviderSummary, type ModelSummary } from "@/lib/api";
import { useToast } from "@/lib/toast";
import { useUser } from "@/lib/user-context";
import { useUpgradeRequests } from "@/lib/upgrade-requests";

export const Route = createFileRoute("/app/providers")({
  head: () => ({
    meta: [
      { title: "Provider Hub — NexusAI Gateway" },
      { name: "description", content: "Manage upstream AI providers, BYOK credentials, and model selection." },
    ],
  }),
  component: Providers,
});

type ProviderType = "OPENAI_COMPATIBLE" | "GOOGLE" | "ANTHROPIC" | "AWS_BEDROCK" | "AZURE_OPENAI";

const PROVIDER_CATALOG = [
  {
    type: "OPENAI_COMPATIBLE" as ProviderType,
    label: "OpenAI",
    badge: "OPENAI",
    gradient: "from-emerald-500 to-teal-400",
    color: "#10a37f",
    fields: [{ key: "apiKey", label: "API Key", ph: "sk-proj-••••••••" }],
  },
  {
    type: "ANTHROPIC" as ProviderType,
    label: "Anthropic Claude",
    badge: "CLAUDE",
    gradient: "from-amber-500 to-orange-400",
    color: "#d97706",
    fields: [{ key: "apiKey", label: "API Key", ph: "sk-ant-••••••••" }],
  },
  {
    type: "GOOGLE" as ProviderType,
    label: "Google Gemini",
    badge: "GEMINI",
    gradient: "from-sky-500 to-indigo-500",
    color: "#4285f4",
    fields: [{ key: "apiKey", label: "API Key", ph: "AI••••••••" }],
  },
  {
    type: "AWS_BEDROCK" as ProviderType,
    label: "AWS Bedrock",
    badge: "BEDROCK",
    gradient: "from-amber-400 to-yellow-500",
    color: "#ff9900",
    fields: [
      { key: "apiKey", label: "Access Key ID", ph: "AKIA••••••••" },
      { key: "secretKey", label: "Secret Key", ph: "••••••••••••" },
      { key: "region", label: "Region", ph: "us-east-1" },
    ],
  },
  {
    type: "AZURE_OPENAI" as ProviderType,
    label: "Azure OpenAI",
    badge: "AZURE",
    gradient: "from-blue-600 to-cyan-400",
    color: "#0078d4",
    fields: [
      { key: "baseUrl", label: "Endpoint", ph: "https://your-resource.openai.azure.com" },
      { key: "apiKey", label: "API Key", ph: "••••••••••••" },
    ],
  },
  {
    type: "OPENAI_COMPATIBLE" as ProviderType,
    label: "Groq Cloud",
    badge: "GROQ",
    gradient: "from-orange-500 to-red-500",
    color: "#f97316",
    fields: [
      { key: "baseUrl", label: "Base URL", ph: "https://api.groq.com/openai/v1" },
      { key: "apiKey", label: "API Key", ph: "gsk_••••••••" },
    ],
  },
];

function Providers() {
  const [providers, setProviders] = useState<ProviderSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [testingGemini, setTestingGemini] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showAddModal, setShowAddModal] = useState(false);
  const [selectedProviderSlug, setSelectedProviderSlug] = useState<string | null>(null);
  const [editingKeySlug, setEditingKeySlug] = useState<string | null>(null);

  const { success, error: toastError, info } = useToast();
  const { session } = useUser();
  const navigate = useNavigate();
  const { openModal: openUpgradeModal } = useUpgradeRequests();
  const isTeamHead = session.role === "TEAM_HEAD";
  const role = session.role ?? "SOLO";

  const [testingProviderSlug, setTestingProviderSlug] = useState<string | null>(null);

  const handleTestAndLoadProvider = async (slug: string, displayName: string) => {
    setTestingProviderSlug(slug);
    info(`Testing ${displayName}...`, `Pinging candidate models to verify working endpoints...`);
    try {
      const res = await fetch(`http://localhost:8080/api/providers/${slug}/test-and-load`, { method: "POST" });
      const data = await res.json();
      if (data.status === "SUCCESS") {
        success(`${displayName} Verified`, `Loaded ${data.totalActive} active working models (${data.verifiedWorkingModels?.join(", ") || "none"}).`);
      } else if (data.status === "MISSING_KEY") {
        toastError("API Key Missing", `No valid API key configured for ${displayName}. Please add an API key first.`);
      } else {
        toastError("Verification Failed", data.message || data.error || "Failed to load models");
      }
      await load();
    } catch (err: any) {
      toastError("Execution Error", err.message);
    } finally {
      setTestingProviderSlug(null);
    }
  };

  const handleTestAndLoadAll = async () => {
    setTestingProviderSlug("ALL");
    info("Testing All Providers...", "Verifying models across all configured AI providers...");
    try {
      const res = await fetch("http://localhost:8080/api/providers/test-and-load-all", { method: "POST" });
      const data = await res.json();
      if (data.status === "SUCCESS") {
        success("All Providers Tested", "Completed live model verification across all upstream providers.");
      } else {
        toastError("Verification Warning", data.message || "Partial provider verification");
      }
      await load();
    } catch (err: any) {
      toastError("Execution Error", err.message);
    } finally {
      setTestingProviderSlug(null);
    }
  };

  useEffect(() => {
    if (role !== "ORG_ADMIN" && role !== "SOLO" && role !== "SUPER_ADMIN" && role !== "OWNER") {
      navigate({ to: "/app" });
    }
  }, [role, navigate]);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await providersApi.listProviders();
      setProviders(data);
    } catch (err: any) {
      setError(err.message ?? "Failed to load providers");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const toggleEnabled = async (slug: string, newEnabled: boolean) => {
    try {
      await providersApi.setEnabled(slug, newEnabled);
      setProviders((prev) => prev.map((p) => (p.slug === slug ? { ...p, enabled: newEnabled } : p)));
      success(newEnabled ? "Provider Enabled" : "Provider Disabled", `Routing policy updated for ${slug}.`);
    } catch (err: any) {
      toastError("Update Failed", err.message);
    }
  };

  const triggerDiscovery = async (slug: string) => {
    try {
      info("Discovering Models...", `Scanning ${slug} API endpoints...`);
      const res = await providersApi.triggerDiscovery(slug);
      success("Discovery Complete", `${res.newModelsFound} new models cataloged for ${slug}.`);
      load();
    } catch (err: any) {
      toastError("Discovery Failed", err.message);
    }
  };

  const handleUpdateKey = async (slug: string, apiKey: string) => {
    try {
      await providersApi.updateCredentials(slug, apiKey);
      success("Credentials Saved", `${slug} is now live and ready for traffic.`);
      setEditingKeySlug(null);
      load();
    } catch (err: any) {
      toastError("Update Failed", err.message);
    }
  };

  // Compute stats
  const totalModelsActive = providers.reduce((acc, p) => acc + (p.hasKey && p.enabled ? p.enabledModelCount : 0), 0);
  const activeProvidersCount = providers.filter((p) => p.enabled && p.hasKey).length;
  const activeProvider = providers.find((p) => p.slug === selectedProviderSlug);

  return (
    <AppShell title="Provider Hub" subtitle="Enterprise AI Gateway Upstream Connectivity & Model Management">
      {/* Top Banner Stats Ribbon */}
      <div className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-3">
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          className="section-panel p-5"
        >
          <div className="flex items-center justify-between">
            <div>
              <p className="text-[0.7rem] uppercase tracking-widest text-sky-600 dark:text-sky-400 font-bold">Active Providers</p>
              <h4 className="mt-1 text-2xl font-extrabold text-foreground">{activeProvidersCount} <span className="text-xs font-normal text-muted-foreground">/ {providers.length} registered</span></h4>
            </div>
            <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-sky-500/10 border border-sky-500/30 text-sky-500">
              <Server className="h-5 w-5" />
            </div>
          </div>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.05 }}
          className="section-panel p-5"
        >
          <div className="flex items-center justify-between">
            <div>
              <p className="text-[0.7rem] uppercase tracking-widest text-emerald-600 dark:text-emerald-400 font-bold">Active Models in Routing</p>
              <h4 className="mt-1 text-2xl font-extrabold text-foreground">{totalModelsActive} <span className="text-xs font-normal text-muted-foreground">ready for requests</span></h4>
            </div>
            <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-emerald-500/10 border border-emerald-500/30 text-emerald-500">
              <Cpu className="h-5 w-5" />
            </div>
          </div>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.1 }}
          className="section-panel p-5"
        >
          <div className="flex items-center justify-between">
            <div>
              <p className="text-[0.7rem] uppercase tracking-widest text-indigo-600 dark:text-indigo-400 font-bold">Gateway Health Monitor</p>
              <h4 className="mt-1 text-2xl font-extrabold text-emerald-600 dark:text-emerald-400 flex items-center gap-1.5 text-lg">
                <ShieldCheck className="h-5 w-5" /> System Operational
              </h4>
            </div>
            <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-indigo-500/10 border border-indigo-500/30 text-indigo-500">
              <Activity className="h-5 w-5 animate-pulse" />
            </div>
          </div>
        </motion.div>
      </div>

      {/* Action Header */}
      <div className="mb-6 flex flex-wrap items-center justify-between gap-3 section-panel p-4">
        <div>
          <h3 className="text-sm font-bold tracking-tight text-foreground flex items-center gap-2">
            <Sparkles className="h-4 w-4 text-cyan" /> Upstream AI Providers
          </h3>
          <p className="text-xs text-muted-foreground">Add your API keys to enable routing. Click 'Select Models' on any provider to pick models.</p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            onClick={handleTestAndLoadAll}
            disabled={testingProviderSlug === "ALL"}
            variant="outline"
            size="sm"
            className="h-9 rounded-xl text-xs gap-1.5 border-amber-500/40 text-amber-600 dark:text-amber-400 bg-amber-500/10 hover:bg-amber-500/20 font-bold shadow-sm"
          >
            {testingProviderSlug === "ALL" ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <BrainCircuit className="h-3.5 w-3.5 text-amber-500" />}
            Test & Load All Providers
          </Button>
          <Button onClick={load} variant="outline" size="sm" className="h-9 rounded-xl text-xs gap-1.5 border-border hover:bg-accent">
            <RefreshCw className={`h-3.5 w-3.5 ${loading ? "animate-spin" : ""}`} /> Sync Catalog
          </Button>
          {isTeamHead ? (
            <Button onClick={openUpgradeModal} className="h-9 rounded-xl text-xs bg-amber-500 text-black font-bold hover:bg-amber-400 shadow-md">
              Request Access
            </Button>
          ) : (
            <Button onClick={() => setShowAddModal(true)} className="grad-primary h-9 rounded-xl text-xs text-white font-bold shadow-md hover:brightness-110">
              <Plus className="mr-1 h-4 w-4" /> Add AI Provider
            </Button>
          )}
        </div>
      </div>

      {error && (
        <div className="mb-4 rounded-xl border border-destructive/40 bg-destructive/10 px-4 py-3 text-xs text-destructive flex items-center gap-2">
          <CircleSlash className="h-4 w-4 shrink-0" />
          {error}
        </div>
      )}

      {loading && (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {[0, 1, 2, 3, 4, 5].map((i) => (
            <div key={i} className="h-52 animate-pulse rounded-2xl bg-muted" />
          ))}
        </div>
      )}

      {!loading && providers.length === 0 && !error && (
        <div className="section-panel p-16 text-center border-dashed">
          <Server className="mx-auto mb-4 h-14 w-14 text-cyan opacity-50 animate-bounce" />
          <h3 className="text-base font-bold text-foreground">No Providers Configured</h3>
          <p className="mt-1 text-xs text-muted-foreground max-w-sm mx-auto">
            Connect your AI provider API key to start routing requests.
          </p>
          <Button onClick={() => setShowAddModal(true)} className="grad-primary mt-6 h-10 rounded-xl text-xs font-bold text-white shadow-lg">
            <Plus className="mr-1.5 h-4 w-4" /> Connect AI Provider
          </Button>
        </div>
      )}

      {/* Provider Grid */}
      <div className="grid gap-5 md:grid-cols-2 lg:grid-cols-3">
        {providers.map((p, i) => {
          const catalogItem = PROVIDER_CATALOG.find((c) => c.label.toLowerCase().includes(p.displayName.toLowerCase()) || p.slug.includes(c.label.toLowerCase())) || PROVIDER_CATALOG[i % PROVIDER_CATALOG.length];
          const activeModels = p.hasKey && p.enabled ? p.enabledModelCount : 0;

          return (
            <motion.div
              key={p.slug}
              initial={{ opacity: 0, y: 16 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: i * 0.05, duration: 0.3 }}
              whileHover={{ y: -3 }}
              className="section-panel relative overflow-hidden p-5 transition-all duration-200 hover:border-sky-500/50 hover:shadow-md cursor-pointer group"
              onClick={() => setSelectedProviderSlug(p.slug)}
            >
              {/* Top Accent Bar */}
              <div className={`absolute top-0 left-0 right-0 h-1.5 bg-gradient-to-r ${catalogItem?.gradient ?? "from-sky-500 to-indigo-500"}`} />

              <div className="flex items-start justify-between gap-3">
                <div className="flex items-center gap-3">
                  <div
                    className="flex h-11 w-11 items-center justify-center rounded-xl font-extrabold text-white text-xs shadow-sm transition-transform group-hover:scale-105"
                    style={{ background: catalogItem?.color ?? "#6366f1" }}
                  >
                    {catalogItem?.badge || p.displayName.slice(0, 2).toUpperCase()}
                  </div>
                  <div>
                    <h4 className="text-sm font-bold tracking-tight text-foreground group-hover:text-cyan transition-colors">{p.displayName}</h4>
                    <p className="font-mono text-[0.68rem] text-muted-foreground flex items-center gap-1.5 mt-0.5">
                      <span className="inline-block h-1.5 w-1.5 rounded-full bg-cyan" /> {p.slug}
                    </p>
                  </div>
                </div>
                <div onClick={(e) => e.stopPropagation()}>
                  <Switch disabled={!p.hasKey} checked={p.enabled && p.hasKey} onCheckedChange={(val) => toggleEnabled(p.slug, val)} />
                </div>
              </div>

              {/* Status Box */}
              <div className="mt-4 grid grid-cols-2 gap-2 p-3 rounded-xl bg-muted/60 border border-border text-xs">
                <div>
                  <p className="text-[0.6rem] uppercase tracking-wider text-muted-foreground font-mono font-bold">Active Models</p>
                  <p className={`mt-0.5 font-mono text-xs font-extrabold ${activeModels > 0 ? "text-emerald-600 dark:text-emerald-400" : "text-muted-foreground"}`}>
                    {activeModels} Active
                  </p>
                </div>
                <div>
                  <p className="text-[0.6rem] uppercase tracking-wider text-muted-foreground font-mono font-bold">API Key Status</p>
                  <p className={`mt-0.5 font-mono text-xs font-extrabold ${p.hasKey ? "text-emerald-600 dark:text-emerald-400" : "text-destructive"}`}>
                    {p.hasKey ? "Configured" : "Missing Key"}
                  </p>
                </div>
              </div>

              {/* Status & Action Bar */}
              <div className="mt-4 flex items-center justify-between border-t border-border pt-3 text-xs" onClick={(e) => e.stopPropagation()}>
                <div className="flex items-center gap-1.5">
                  {p.hasKey && p.enabled ? (
                    <>
                      <CircleCheck className="h-3.5 w-3.5 text-emerald-500" />
                      <span className="text-[0.7rem] font-bold text-emerald-600 dark:text-emerald-400">Live</span>
                    </>
                  ) : (
                    <>
                      <CircleSlash className="h-3.5 w-3.5 text-muted-foreground" />
                      <span className="text-[0.7rem] font-semibold text-muted-foreground">{!p.hasKey ? "Key Required" : "Disabled"}</span>
                    </>
                  )}
                </div>

                <div className="flex gap-1.5">
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      handleTestAndLoadProvider(p.slug, p.displayName);
                    }}
                    disabled={testingProviderSlug === p.slug}
                    className="px-2.5 py-1 rounded-lg bg-amber-500/10 border border-amber-500/30 text-[0.7rem] font-bold text-amber-600 dark:text-amber-400 hover:bg-amber-500/20 transition-colors flex items-center gap-1"
                  >
                    {testingProviderSlug === p.slug ? <Loader2 className="h-3 w-3 animate-spin" /> : <BrainCircuit className="h-3 w-3 text-amber-500" />}
                    Test & Load
                  </button>
                  <button
                    onClick={() => setEditingKeySlug(p.slug)}
                    className="px-2.5 py-1 rounded-lg bg-sky-500/10 text-[0.7rem] font-bold text-sky-600 dark:text-sky-400 hover:bg-sky-500/20 transition-colors"
                  >
                    {p.hasKey ? "Update Key" : "+ Add Key"}
                  </button>
                  <button
                    onClick={() => setSelectedProviderSlug(p.slug)}
                    className="px-3 py-1 rounded-lg grad-primary text-[0.7rem] font-bold text-white shadow-sm hover:brightness-110 transition-all flex items-center gap-1"
                  >
                    <SlidersHorizontal className="h-3 w-3" /> Select Models
                  </button>
                </div>
              </div>
            </motion.div>
          );
        })}
      </div>

      {/* Granular Individual Model Selector Modal */}
      {selectedProviderSlug && activeProvider && (
        <IndividualModelSelectorModal
          provider={activeProvider}
          onClose={() => setSelectedProviderSlug(null)}
          onRefreshProviders={load}
          onUpdateKey={() => {
            const slug = selectedProviderSlug;
            setSelectedProviderSlug(null);
            setEditingKeySlug(slug);
          }}
        />
      )}

      {/* Add Provider Modal */}
      {showAddModal && (
        <AddProviderModal
          onClose={() => setShowAddModal(false)}
          onSuccess={() => { setShowAddModal(false); load(); }}
        />
      )}

      {/* Update Key Modal */}
      {editingKeySlug && (
        <UpdateKeyModal
          slug={editingKeySlug}
          onClose={() => setEditingKeySlug(null)}
          onSubmit={(key) => handleUpdateKey(editingKeySlug, key)}
        />
      )}
    </AppShell>
  );
}

/** Granular Individual Model Selector Modal */
function IndividualModelSelectorModal({
  provider,
  onClose,
  onRefreshProviders,
  onUpdateKey,
}: {
  provider: ProviderSummary;
  onClose: () => void;
  onRefreshProviders: () => void;
  onUpdateKey: () => void;
}) {
  const [models, setModels] = useState<ModelSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [testingModal, setTestingModal] = useState(false);
  const [search, setSearch] = useState("");
  const [showAddCustom, setShowAddCustom] = useState(false);
  const [customModelId, setCustomModelId] = useState("");
  const [customDisplayName, setCustomDisplayName] = useState("");
  const [customSubmitting, setCustomSubmitting] = useState(false);

  const { success, error: toastError, info } = useToast();

  const handleTestAndLoadModal = async () => {
    setTestingModal(true);
    info(`Testing ${provider.displayName}...`, `Pinging candidate models to verify working endpoints...`);
    try {
      const res = await fetch(`http://localhost:8080/api/providers/${provider.slug}/test-and-load`, { method: "POST" });
      const data = await res.json();
      if (data.status === "SUCCESS") {
        success(`${provider.displayName} Verified`, `Loaded ${data.totalActive} active working models (${data.verifiedWorkingModels?.join(", ") || "none"}).`);
      } else if (data.status === "MISSING_KEY") {
        toastError("API Key Missing", `No valid API key configured for ${provider.displayName}. Please add an API key first.`);
      } else {
        toastError("Verification Failed", data.message || data.error || "Failed to load models");
      }
      await loadModels();
      onRefreshProviders();
    } catch (err: any) {
      toastError("Execution Error", err.message);
    } finally {
      setTestingModal(false);
    }
  };

  const loadModels = async () => {
    setLoading(true);
    try {
      const data = await providersApi.listModels(provider.slug);
      setModels(data);
    } catch (err: any) {
      toastError("Failed to Load Models", err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadModels(); }, [provider.slug]);

  const toggleSingleModel = async (modelId: string, enable: boolean) => {
    if (!provider.hasKey && enable) {
      toastError("API Key Required", `Please add an API key for ${provider.displayName} before enabling models.`);
      return;
    }
    if (enable) {
      info("Validating Endpoint...", `Pinging live endpoint for ${modelId}...`);
      try {
        const pingRes = await fetch(`http://localhost:8080/api/models/health/verify-single?providerSlug=${provider.slug}&modelId=${encodeURIComponent(modelId)}`, { method: "POST" });
        const pingData = await pingRes.json();
        if (!pingData.healthy) {
          const confirmForce = window.confirm(
            `⚠️ LIVE PING WARNING for '${modelId}':\n\n` +
            `Reason: ${pingData.message || pingData.error || "Endpoint verification failed (404/401)."}\n\n` +
            `Do you still want to force-enable this model anyway?`
          );
          if (!confirmForce) return;
        } else {
          success("Model Health Verified", `HTTP 200 OK (${pingData.latencyMs}ms) — ${modelId} is responsive!`);
        }
      } catch (pingErr: any) {
        console.warn("Model health ping failed:", pingErr);
      }
    }
    try {
      if (enable) await providersApi.enableModel(provider.slug, modelId);
      else await providersApi.disableModel(provider.slug, modelId);

      setModels((prev) => prev.map((m) => (m.modelId === modelId ? { ...m, enabled: enable } : m)));
      success(
        enable ? "Model Active" : "Model Disabled",
        `${modelId} is now ${enable ? "enabled for" : "excluded from"} AI routing.`
      );
      onRefreshProviders();
    } catch (err: any) {
      toastError("Toggle Failed", err.message);
    }
  };

  const handleSelectAll = async (enable: boolean) => {
    if (!provider.hasKey && enable) {
      toastError("API Key Required", `Please add an API key for ${provider.displayName} first.`);
      return;
    }
    try {
      await Promise.all(
        models.map((m) =>
          enable
            ? providersApi.enableModel(provider.slug, m.modelId)
            : providersApi.disableModel(provider.slug, m.modelId)
        )
      );
      setModels((prev) => prev.map((m) => ({ ...m, enabled: enable })));
      success(enable ? "All Models Enabled" : "All Models Disabled", `Updated routing state for ${provider.displayName}.`);
      onRefreshProviders();
    } catch (err: any) {
      toastError("Batch Update Failed", err.message);
    }
  };

  const handleRegisterCustomModel = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!customModelId.trim()) return;
    setCustomSubmitting(true);
    try {
      await providersApi.registerModel(provider.slug, {
        modelId: customModelId.trim(),
        displayName: customDisplayName.trim() || customModelId.trim(),
        enabled: provider.hasKey,
      });
      success("Custom Model Added", `${customModelId} added to ${provider.displayName}.`);
      setCustomModelId("");
      setCustomDisplayName("");
      setShowAddCustom(false);
      loadModels();
      onRefreshProviders();
    } catch (err: any) {
      toastError("Registration Failed", err.message);
    } finally {
      setCustomSubmitting(false);
    }
  };

  const filteredModels = useMemo(() => {
    return models.filter(
      (m) =>
        m.modelId.toLowerCase().includes(search.toLowerCase()) ||
        m.displayName.toLowerCase().includes(search.toLowerCase())
    );
  }, [models, search]);

  const enabledCount = models.filter((m) => m.enabled && provider.hasKey).length;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/70 backdrop-blur-md"
      onClick={(e) => e.target === e.currentTarget && onClose()}
    >
      <motion.div
        initial={{ opacity: 0, scale: 0.96, y: 10 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.96, y: 10 }}
        className="section-panel w-full max-w-2xl overflow-hidden max-h-[90vh] flex flex-col shadow-2xl"
      >
        {/* Header */}
        <div className="flex items-center justify-between border-b border-border px-6 py-5 bg-muted/30">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl grad-primary font-bold text-white text-xs">
              <Cpu className="h-5 w-5" />
            </div>
            <div>
              <h3 className="text-base font-bold text-foreground flex items-center gap-2">
                Select Models for <span className="text-cyan">{provider.displayName}</span>
              </h3>
              <p className="text-xs text-muted-foreground">
                Select specific model arms to enable or disable them in the gateway's AI routing engine.
              </p>
            </div>
          </div>
          <button onClick={onClose} className="flex h-8 w-8 items-center justify-center rounded-xl text-muted-foreground hover:text-foreground hover:bg-muted transition-colors">
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* API Key Missing Warning Banner */}
        {!provider.hasKey && (
          <div className="bg-amber-500/10 border-b border-amber-500/20 px-6 py-3 flex items-center justify-between gap-3 text-xs text-amber-700 dark:text-amber-300">
            <div className="flex items-center gap-2">
              <AlertTriangle className="h-4 w-4 shrink-0 text-amber-500" />
              <span><strong>API Key Missing:</strong> Models cannot process traffic until you add a provider API key.</span>
            </div>
            <Button onClick={onUpdateKey} size="sm" className="h-7 px-3 text-xs bg-amber-500 text-black font-bold hover:bg-amber-400">
              Add Key Now
            </Button>
          </div>
        )}

        {/* Toolbar Controls */}
        <div className="p-4 border-b border-border bg-muted/20 flex flex-wrap items-center justify-between gap-3">
          <div className="relative flex-1 min-w-[180px]">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
            <Input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Filter model ID or name..."
              className="h-9 text-xs pl-9 rounded-xl border-border bg-background text-foreground"
            />
          </div>

          <div className="flex items-center gap-2">
            <Button
              onClick={handleTestAndLoadModal}
              disabled={testingModal}
              size="sm"
              className="h-9 rounded-xl text-xs bg-amber-500/10 text-amber-600 dark:text-amber-400 border border-amber-500/30 hover:bg-amber-500/20 gap-1.5 font-bold"
            >
              {testingModal ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <BrainCircuit className="h-3.5 w-3.5 text-amber-500" />}
              Test & Load Models
            </Button>

            <Button
              onClick={() => handleSelectAll(true)}
              variant="outline"
              size="sm"
              className="h-9 rounded-xl text-xs text-emerald-600 dark:text-emerald-400 border-emerald-500/30 hover:bg-emerald-500/10 gap-1.5 font-bold"
            >
              <CheckSquare className="h-3.5 w-3.5" /> Enable All
            </Button>

            <Button
              onClick={() => handleSelectAll(false)}
              variant="outline"
              size="sm"
              className="h-9 rounded-xl text-xs text-destructive border-destructive/30 hover:bg-destructive/10 gap-1.5 font-bold"
            >
              <Square className="h-3.5 w-3.5" /> Disable All
            </Button>

            <Button
              onClick={() => setShowAddCustom((v) => !v)}
              size="sm"
              className="grad-primary h-9 rounded-xl text-xs text-white gap-1.5 font-bold shadow-sm"
            >
              <Plus className="h-3.5 w-3.5" /> Custom Model
            </Button>
          </div>
        </div>

        {/* Custom Model Form */}
        <AnimatePresence>
          {showAddCustom && (
            <motion.form
              initial={{ height: 0, opacity: 0 }}
              animate={{ height: "auto", opacity: 1 }}
              exit={{ height: 0, opacity: 0 }}
              onSubmit={handleRegisterCustomModel}
              className="p-4 border-b border-border bg-sky-500/5 space-y-3"
            >
              <p className="text-xs font-bold text-sky-600 dark:text-sky-400 flex items-center gap-1.5">
                <Plus className="h-3.5 w-3.5" /> Register Custom Model ID
              </p>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                <Input
                  value={customModelId}
                  onChange={(e) => setCustomModelId(e.target.value)}
                  placeholder="e.g. llama-3.3-70b-versatile"
                  className="h-8 text-xs font-mono rounded-lg border-border bg-background"
                  required
                />
                <Input
                  value={customDisplayName}
                  onChange={(e) => setCustomDisplayName(e.target.value)}
                  placeholder="Display Name (optional)"
                  className="h-8 text-xs rounded-lg border-border bg-background"
                />
              </div>
              <div className="flex justify-end gap-2">
                <Button type="button" onClick={() => setShowAddCustom(false)} variant="outline" size="sm" className="h-7 text-xs rounded-lg">Cancel</Button>
                <Button type="submit" disabled={customSubmitting} size="sm" className="grad-primary h-7 text-xs text-white rounded-lg font-bold">
                  {customSubmitting ? "Adding..." : "Add Model"}
                </Button>
              </div>
            </motion.form>
          )}
        </AnimatePresence>

        {/* Models List */}
        <div className="p-5 overflow-y-auto space-y-3 flex-1 bg-background/50">
          {loading && (
            <div className="space-y-3">
              {[0, 1, 2, 3].map((i) => (
                <div key={i} className="h-16 animate-pulse rounded-2xl bg-muted" />
              ))}
            </div>
          )}

          {!loading && filteredModels.length === 0 && (
            <div className="py-12 text-center text-xs text-muted-foreground border border-dashed border-border rounded-2xl">
              No models cataloged. Click <span className="text-cyan font-bold">Custom Model</span> above to manually add a model ID.
            </div>
          )}

          {!loading &&
            filteredModels.map((m) => {
              const isActive = m.enabled && provider.hasKey;
              return (
                <motion.div
                  key={m.modelId}
                  initial={{ opacity: 0, y: 6 }}
                  animate={{ opacity: 1, y: 0 }}
                  className={`flex items-center justify-between p-4 rounded-2xl border transition-all duration-200 ${
                    isActive
                      ? "bg-sky-500/10 border-sky-500/40 shadow-sm"
                      : "bg-card border-border opacity-70 hover:opacity-100"
                  }`}
                >
                  <div className="flex items-center gap-3">
                    <div className={`flex h-10 w-10 items-center justify-center rounded-xl font-mono text-xs font-bold ${isActive ? "bg-sky-500/20 text-sky-600 dark:text-sky-400 border border-sky-500/40" : "bg-muted text-muted-foreground"}`}>
                      {m.modelId.slice(0, 3).toUpperCase()}
                    </div>

                    <div>
                      <div className="flex items-center gap-2">
                        <h4 className="text-sm font-bold font-mono text-foreground">{m.modelId}</h4>
                        {isActive ? (
                          <span className="rounded-full bg-emerald-500/20 px-2.5 py-0.5 text-[0.65rem] font-extrabold text-emerald-600 dark:text-emerald-400 border border-emerald-500/30">
                            Active in Routing
                          </span>
                        ) : (
                          <span className="rounded-full bg-muted px-2.5 py-0.5 text-[0.65rem] font-semibold text-muted-foreground">
                            {!provider.hasKey ? "Key Missing" : "Disabled"}
                          </span>
                        )}
                      </div>

                      <div className="mt-1 flex flex-wrap items-center gap-3 text-[0.7rem] text-muted-foreground font-mono">
                        <span>Arm Key: <strong className="text-cyan font-semibold">{m.armKey}</strong></span>
                        {m.pricingVerified && (
                          <span>${m.inputPricePer1M.toFixed(2)} in / ${m.outputPricePer1M.toFixed(2)} out per 1M</span>
                        )}
                        {m.contextWindowTokens > 0 && (
                          <span>{(m.contextWindowTokens / 1000).toFixed(0)}K context</span>
                        )}
                      </div>
                    </div>
                  </div>

                  {/* Individual Model Toggle Switch */}
                  <div className="flex items-center gap-3">
                    <Switch
                      disabled={!provider.hasKey}
                      checked={isActive}
                      onCheckedChange={(val) => toggleSingleModel(m.modelId, val)}
                    />
                  </div>
                </motion.div>
              );
            })}
        </div>

        {/* Modal Footer */}
        <div className="border-t border-border px-6 py-4 bg-muted/30 flex items-center justify-between text-xs text-muted-foreground">
          <p>
            <strong className="text-sky-600 dark:text-sky-400 font-bold">{enabledCount}</strong> of <strong className="text-foreground">{models.length}</strong> models active for {provider.displayName}.
          </p>
          <Button onClick={onClose} className="grad-primary h-9 rounded-xl text-xs text-white font-bold">
            Done Selecting
          </Button>
        </div>
      </motion.div>
    </div>
  );
}

function AddProviderModal({ onClose, onSuccess }: { onClose: () => void; onSuccess: () => void }) {
  const [step, setStep] = useState<1 | 2>(1);
  const [selectedCatalog, setSelectedCatalog] = useState<typeof PROVIDER_CATALOG[0] | null>(null);
  const [form, setForm] = useState({ displayName: "", slug: "", fields: {} as Record<string, string> });
  const [loading, setLoading] = useState(false);
  const [validated, setValidated] = useState(false);
  const { success, error: toastError } = useToast();

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedCatalog) return;
    setLoading(true);
    try {
      const regPayload: Parameters<typeof providersApi.registerProvider>[0] = {
        displayName: form.displayName || selectedCatalog.label,
        slug: form.slug || selectedCatalog.label.toLowerCase().replace(/\s+/g, "-"),
        type: selectedCatalog.type,
      };
      if (form.fields["baseUrl"]) regPayload.baseUrl = form.fields["baseUrl"];
      if (form.fields["apiKey"]) regPayload.apiKey = form.fields["apiKey"];
      const res = await providersApi.registerProvider(regPayload);
      setValidated(true);
      setTimeout(() => {
        success("Provider Connected!", `${res.modelsDiscovered} models cataloged.`);
        onSuccess();
      }, 800);
    } catch (err: any) {
      toastError("Connection Failed", err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/70 backdrop-blur-md" onClick={(e) => e.target === e.currentTarget && onClose()}>
      <motion.div
        initial={{ opacity: 0, scale: 0.96 }}
        animate={{ opacity: 1, scale: 1 }}
        className="w-full max-w-lg rounded-3xl bg-card text-card-foreground border border-border shadow-2xl overflow-hidden"
      >
        <div className="flex items-center justify-between border-b border-border px-6 py-4 bg-muted/30">
          <p className="text-sm font-bold flex items-center gap-2 text-foreground">
            <Globe className="h-4 w-4 text-cyan" />
            {step === 1 ? "Select AI Provider Type" : `Configure ${selectedCatalog?.label}`}
          </p>
          <button onClick={onClose} className="text-muted-foreground hover:text-foreground"><X className="h-4 w-4" /></button>
        </div>

        <div className="p-6">
          {step === 1 && (
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
              {PROVIDER_CATALOG.map((p) => (
                <motion.button
                  key={p.label}
                  onClick={() => { setSelectedCatalog(p); setStep(2); }}
                  whileHover={{ scale: 1.03, y: -2 }}
                  whileTap={{ scale: 0.97 }}
                  className="flex flex-col items-center gap-2.5 rounded-2xl border border-border bg-card p-4 text-xs font-bold transition-all hover:border-cyan-500/50 hover:shadow-md"
                >
                  <div className="h-11 w-11 rounded-xl flex items-center justify-center font-bold text-white text-xs shadow-md" style={{ background: p.color }}>
                    {p.badge}
                  </div>
                  <span className="text-center font-bold text-foreground">{p.label}</span>
                </motion.button>
              ))}
            </div>
          )}

          {step === 2 && selectedCatalog && (
            <form onSubmit={submit} className="space-y-4">
              <div className="flex items-center gap-3 p-3.5 rounded-2xl bg-muted/40 border border-border">
                <div className="h-10 w-10 rounded-xl flex items-center justify-center font-bold text-white text-xs shrink-0" style={{ background: selectedCatalog.color }}>
                  {selectedCatalog.badge}
                </div>
                <div>
                  <p className="text-sm font-bold text-foreground">{selectedCatalog.label}</p>
                  <p className="text-xs text-muted-foreground">{selectedCatalog.type}</p>
                </div>
                <button type="button" onClick={() => setStep(1)} className="ml-auto text-xs text-cyan hover:underline font-bold">Change</button>
              </div>

              <div className="grid gap-3 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <Label className="text-xs text-muted-foreground font-bold">Display Name</Label>
                  <Input value={form.displayName} onChange={(e) => setForm({ ...form, displayName: e.target.value })} placeholder={selectedCatalog.label} className="h-9 text-xs rounded-xl border-border bg-background" />
                </div>
                <div className="space-y-1.5">
                  <Label className="text-xs text-muted-foreground font-bold">Slug Identifier</Label>
                  <Input value={form.slug} onChange={(e) => setForm({ ...form, slug: e.target.value.toLowerCase().replace(/\s+/g, "-") })} placeholder={selectedCatalog.label.toLowerCase().replace(/\s+/g, "-")} className="h-9 text-xs rounded-xl border-border bg-background" />
                </div>
              </div>

              {selectedCatalog.fields.map((f) => (
                <div key={f.key} className="space-y-1.5">
                  <Label className="text-xs text-muted-foreground font-bold">{f.label}</Label>
                  <Input
                    type={f.key === "apiKey" || f.key === "secretKey" ? "password" : "text"}
                    placeholder={f.ph}
                    value={form.fields[f.key] ?? ""}
                    onChange={(e) => setForm({ ...form, fields: { ...form.fields, [f.key]: e.target.value } })}
                    className="h-9 text-xs rounded-xl font-mono border-border bg-background"
                    required={f.key === "apiKey"}
                  />
                </div>
              ))}

              <div className="flex gap-2 pt-2">
                <Button type="submit" disabled={loading || validated} className="flex-1 grad-primary h-10 rounded-xl text-xs text-white font-bold shadow-md">
                  {validated ? (
                    <span className="flex items-center gap-2 text-emerald-200"><Check className="h-4 w-4" /> Connected!</span>
                  ) : loading ? (
                    <span className="flex items-center gap-2"><Loader2 className="h-4 w-4 animate-spin" /> Connecting...</span>
                  ) : (
                    <span className="flex items-center gap-2"><Zap className="h-4 w-4" /> Connect & Save Key</span>
                  )}
                </Button>
                <Button type="button" onClick={onClose} variant="outline" className="h-10 rounded-xl text-xs">Cancel</Button>
              </div>
            </form>
          )}
        </div>
      </motion.div>
    </div>
  );
}

function UpdateKeyModal({ slug, onClose, onSubmit }: { slug: string; onClose: () => void; onSubmit: (key: string) => Promise<void> }) {
  const [key, setKey] = useState("");
  const [loading, setLoading] = useState(false);
  const [show, setShow] = useState(false);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/70 backdrop-blur-md" onClick={(e) => e.target === e.currentTarget && onClose()}>
      <motion.div
        initial={{ opacity: 0, scale: 0.96 }}
        animate={{ opacity: 1, scale: 1 }}
        className="w-full max-w-sm rounded-3xl bg-card text-card-foreground border border-border shadow-2xl p-6"
      >
        <div className="mb-4 flex items-center justify-between">
          <p className="text-sm font-bold flex items-center gap-2 text-foreground">
            <Key className="h-4 w-4 text-cyan" /> Add API Key — <span className="font-mono text-xs text-cyan">{slug}</span>
          </p>
          <button onClick={onClose}><X className="h-4 w-4 text-muted-foreground" /></button>
        </div>
        <form onSubmit={async (e) => { e.preventDefault(); if (!key.trim()) return; setLoading(true); await onSubmit(key.trim()); setLoading(false); }} className="space-y-4">
          <div className="space-y-1.5 relative">
            <Label className="text-xs text-muted-foreground font-bold">API Key for {slug}</Label>
            <Input type={show ? "text" : "password"} value={key} onChange={(e) => setKey(e.target.value)} placeholder="sk-..." required className="h-10 text-xs pr-12 font-mono rounded-xl border-border bg-background" autoFocus />
            <button type="button" onClick={() => setShow((v) => !v)} className="absolute right-3 top-[calc(50%+6px)] -translate-y-1/2 text-muted-foreground hover:text-foreground text-[0.7rem] font-bold">
              {show ? "Hide" : "Show"}
            </button>
          </div>
          <Button type="submit" disabled={loading} className="grad-primary h-10 w-full rounded-xl text-xs text-white font-bold shadow-md">
            {loading ? <span className="flex items-center gap-2"><Loader2 className="h-4 w-4 animate-spin" /> Saving...</span> : "Save Provider Key"}
          </Button>
        </form>
      </motion.div>
    </div>
  );
}
