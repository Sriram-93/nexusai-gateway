import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { motion, AnimatePresence } from "motion/react";
import { useEffect, useState } from "react";
import {
  CircleCheck, CircleSlash, Plus, RefreshCw, Server, X, Key,
  Loader2, ChevronRight, Globe, Zap, Check,
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
      { title: "Provider Hub — NexusAI" },
      { name: "description", content: "Manage upstream AI providers: health, models, and credentials." },
    ],
  }),
  component: Providers,
});

type ProviderType = "OPENAI_COMPATIBLE" | "GOOGLE" | "ANTHROPIC" | "AWS_BEDROCK" | "AZURE_OPENAI";

const PROVIDER_CATALOG = [
  { type: "OPENAI_COMPATIBLE" as ProviderType, label: "OpenAI", color: "#10a37f", fields: [{ key: "apiKey", label: "API Key", ph: "sk-proj-••••••••" }] },
  { type: "ANTHROPIC" as ProviderType, label: "Anthropic", color: "#d97706", fields: [{ key: "apiKey", label: "API Key", ph: "sk-ant-••••••••" }] },
  { type: "GOOGLE" as ProviderType, label: "Google AI", color: "#4285f4", fields: [{ key: "apiKey", label: "API Key", ph: "AI••••••••" }] },
  { type: "AWS_BEDROCK" as ProviderType, label: "AWS Bedrock", color: "#ff9900", fields: [
    { key: "apiKey", label: "Access Key ID", ph: "AKIA••••••••" },
    { key: "secretKey", label: "Secret Key", ph: "••••••••••••" },
    { key: "region", label: "Region", ph: "us-east-1" },
  ]},
  { type: "AZURE_OPENAI" as ProviderType, label: "Azure OpenAI", color: "#0078d4", fields: [
    { key: "baseUrl", label: "Endpoint", ph: "https://your-resource.openai.azure.com" },
    { key: "apiKey", label: "API Key", ph: "••••••••••••" },
  ]},
  { type: "OPENAI_COMPATIBLE" as ProviderType, label: "Groq", color: "#f97316", fields: [
    { key: "baseUrl", label: "Base URL", ph: "https://api.groq.com/openai/v1" },
    { key: "apiKey", label: "API Key", ph: "gsk_••••••••" },
  ]},
];

const TONE_CLASSES = ["cyan", "emerald", "indigo", "amber", "indigo", "cyan"];

function Providers() {
  const [providers, setProviders] = useState<ProviderSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showAddModal, setShowAddModal] = useState(false);
  const [selectedSlug, setSelectedSlug] = useState<string | null>(null);
  const [editingKeySlug, setEditingKeySlug] = useState<string | null>(null);
  const [models, setModels] = useState<ModelSummary[]>([]);
  const [modelsLoading, setModelsLoading] = useState(false);

  const { success, error: toastError, info } = useToast();
  const { session } = useUser();
  const navigate = useNavigate();
  const { openModal: openUpgradeModal } = useUpgradeRequests();
  const isTeamHead = session.role === "TEAM_HEAD";

  const role = session.role ?? "SOLO";

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
      success(newEnabled ? "Provider enabled" : "Provider disabled", `Routing updated for ${slug}.`);
    } catch (err: any) {
      toastError("Update failed", err.message);
    }
  };

  const triggerDiscovery = async (slug: string) => {
    try {
      info("Discovering models...", `Scanning ${slug} for available models.`);
      const res = await providersApi.triggerDiscovery(slug);
      success("Discovery complete", `${res.newModelsFound} new models found for ${slug}.`);
      load();
    } catch (err: any) {
      toastError("Discovery failed", err.message);
    }
  };

  const openModels = async (slug: string) => {
    setSelectedSlug(slug);
    setModelsLoading(true);
    setModels([]);
    try {
      const data = await providersApi.listModels(slug);
      setModels(data);
    } catch (err: any) {
      toastError("Failed to load models", err.message);
    } finally {
      setModelsLoading(false);
    }
  };

  const handleUpdateKey = async (slug: string, apiKey: string) => {
    try {
      await providersApi.updateCredentials(slug, apiKey);
      success("Credentials updated", `${slug} is now using the new API key.`);
      setEditingKeySlug(null);
      load();
    } catch (err: any) {
      toastError("Update failed", err.message);
    }
  };

  const toggleModel = async (slug: string, modelId: string, enable: boolean) => {
    try {
      if (enable) await providersApi.enableModel(slug, modelId);
      else await providersApi.disableModel(slug, modelId);
      setModels((prev) => prev.map((m) => (m.modelId === modelId ? { ...m, enabled: enable } : m)));
      setProviders((prev) => prev.map((p) => {
        if (p.slug === slug) {
          return { ...p, enabledModelCount: p.enabledModelCount + (enable ? 1 : -1) };
        }
        return p;
      }));
    } catch (err: any) {
      toastError("Model update failed", err.message);
    }
  };

  return (
    <AppShell title="Provider Hub" subtitle="Infrastructure-level AI provider connections and model discovery">
      <div className="mb-5 flex items-center justify-between">
        <p className="text-xs text-muted-foreground">
          {providers.length} provider{providers.length !== 1 ? "s" : ""} registered
        </p>
        <div className="flex gap-2">
          <Button onClick={load} variant="outline" size="sm" className="glass h-9 rounded-lg text-xs gap-1.5">
            <RefreshCw className="h-3.5 w-3.5" /> Refresh
          </Button>
          {isTeamHead ? (
            <Button
              onClick={openUpgradeModal}
              className="h-9 rounded-lg text-xs bg-amber text-black font-semibold hover:bg-amber/90"
            >
              Request Provider Access
            </Button>
          ) : (
            <Button
              onClick={() => setShowAddModal(true)}
              className="grad-primary h-9 rounded-lg text-xs text-primary-foreground"
            >
              <Plus className="mr-1 h-3.5 w-3.5" /> Add Provider
            </Button>
          )}
        </div>
      </div>

      {error && (
        <div className="mb-4 rounded-xl border border-destructive/40 bg-destructive/10 px-4 py-3 text-xs text-destructive">
          {error}
        </div>
      )}

      {loading && (
        <div className="grid gap-4 md:grid-cols-2">
          {[0, 1, 2, 3].map((i) => (
            <div key={i} className="glass h-48 animate-pulse rounded-2xl" />
          ))}
        </div>
      )}

      {!loading && providers.length === 0 && !error && (
        <div className="glass rounded-2xl p-16 text-center">
          <Server className="mx-auto mb-4 h-12 w-12 text-muted-foreground opacity-40" />
          <p className="text-sm font-semibold">No providers registered</p>
          <p className="mt-1 text-xs text-muted-foreground max-w-sm mx-auto">
            Add your first AI provider to unlock the Model Hub and start routing traffic.
          </p>
          <Button onClick={() => setShowAddModal(true)} className="grad-primary mt-6 h-10 rounded-xl text-sm text-primary-foreground">
            <Plus className="mr-1.5 h-4 w-4" /> Add Your First Provider
          </Button>
        </div>
      )}

      <div className="grid gap-4 md:grid-cols-2">
        {providers.map((p, i) => (
          <motion.div
            key={p.slug}
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: i * 0.06, duration: 0.45 }}
            whileHover={{ y: -3 }}
            className="glass rounded-2xl p-5 transition-colors hover:border-[color-mix(in_oklab,var(--foreground)_20%,transparent)]"
          >
            <div className="flex items-start justify-between">
              <div className="flex items-center gap-3">
                <span
                  className="flex h-11 w-11 items-center justify-center rounded-xl font-bold text-white text-sm"
                  style={{ background: PROVIDER_CATALOG[i % PROVIDER_CATALOG.length]?.color ?? "#6366f1" }}
                >
                  {p.displayName.slice(0, 2).toUpperCase()}
                </span>
                <div>
                  <p className="text-sm font-semibold tracking-tight">{p.displayName}</p>
                  <p className="font-mono text-xs text-muted-foreground">{p.slug} · {p.type}</p>
                </div>
              </div>
              <div className="flex items-center gap-2">
                <Switch disabled={!p.hasKey} checked={p.enabled} onCheckedChange={(val) => toggleEnabled(p.slug, val)} />
              </div>
            </div>

            <div className="mt-5 grid grid-cols-3 gap-3 text-xs">
              {[
                ["Models", String(p.enabledModelCount)],
                ["API Key", p.hasKey ? "Configured" : "Missing"],
                ["Discovered", p.lastDiscoveredAt ? new Date(p.lastDiscoveredAt).toLocaleDateString() : "Never"],
              ].map(([k, v]) => (
                <div key={k}>
                  <p className="text-[0.65rem] uppercase tracking-[0.14em] text-muted-foreground">{k}</p>
                  <p className={`mt-1 font-mono ${k === "API Key" && v === "Missing" ? "text-destructive" : k === "API Key" && v === "Configured" ? "text-emerald" : ""}`}>{v}</p>
                </div>
              ))}
            </div>

            <div className="mt-4 flex items-center justify-between">
              <p className="flex items-center gap-1.5 text-xs">
                {p.enabled ? (
                  <><CircleCheck className="h-3.5 w-3.5 text-emerald" /><span className="text-emerald">Enabled for routing</span></>
                ) : (
                  <><CircleSlash className="h-3.5 w-3.5 text-muted-foreground" /><span className="text-muted-foreground">Disabled</span></>
                )}
              </p>
              <div className="flex gap-3">
                <button onClick={() => setEditingKeySlug(p.slug)} className="text-xs text-muted-foreground hover:text-cyan transition-colors">
                  Update Key
                </button>
                <button onClick={() => triggerDiscovery(p.slug)} className="text-xs text-muted-foreground hover:text-cyan transition-colors">
                  Discover
                </button>
                <button onClick={() => openModels(p.slug)} className="text-xs text-muted-foreground hover:text-cyan transition-colors">
                  Models ({p.enabledModelCount})
                </button>
              </div>
            </div>
          </motion.div>
        ))}
      </div>

      {/* Models panel */}
      <AnimatePresence>
        {selectedSlug && (() => {
          const providerHasKey = providers.find(p => p.slug === selectedSlug)?.hasKey ?? false;
          return (
          <motion.div
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 12 }}
            className="glass mt-6 rounded-2xl p-6"
          >
            <div className="flex items-center justify-between mb-5">
              <p className="text-sm font-semibold">Models — <span className="text-cyan font-mono">{selectedSlug}</span></p>
              <button onClick={() => setSelectedSlug(null)} className="glass flex h-8 w-8 items-center justify-center rounded-lg hover:bg-[var(--glass-hover)]">
                <X className="h-4 w-4 text-muted-foreground" />
              </button>
            </div>

            {modelsLoading && (
              <div className="space-y-2">
                {[0, 1, 2].map((i) => <div key={i} className="h-10 animate-pulse rounded-lg bg-[var(--glass-hover)]" />)}
              </div>
            )}

            {!modelsLoading && models.length === 0 && (
              <p className="py-6 text-center text-xs text-muted-foreground italic">No models discovered for this provider.</p>
            )}

            {!modelsLoading && models.length > 0 && (
              <div className="overflow-x-auto">
                <table className="w-full text-xs">
                  <thead>
                    <tr className="text-left text-[0.68rem] uppercase tracking-[0.14em] text-muted-foreground border-b border-[var(--glass-border)]">
                      {["Model", "Input $/1M", "Output $/1M", "Context", "Enabled"].map((h) => (
                        <th key={h} className="pb-2.5 pr-4 font-medium">{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {models.map((m) => (
                      <tr key={m.modelId} className="border-b border-[var(--glass-border)] last:border-0">
                        <td className="py-3 pr-4">
                          <p className="font-mono text-cyan">{m.modelId}</p>
                          <p className="text-muted-foreground text-[0.65rem]">{m.displayName}</p>
                        </td>
                        <td className="py-3 pr-4 font-mono">
                          {m.pricingVerified ? <span className="text-emerald">${m.inputPricePer1M.toFixed(2)}</span> : "—"}
                        </td>
                        <td className="py-3 pr-4 font-mono">
                          {m.pricingVerified ? <span className="text-amber">${m.outputPricePer1M.toFixed(2)}</span> : "—"}
                        </td>
                        <td className="py-3 pr-4 text-muted-foreground">
                          {m.contextWindowTokens > 0 ? `${(m.contextWindowTokens / 1000).toFixed(0)}K` : "—"}
                        </td>
                        <td className="py-3 pr-4">
                          <Switch disabled={!providerHasKey} checked={m.enabled} onCheckedChange={(val) => toggleModel(selectedSlug, m.modelId, val)} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            {!providerHasKey && models.length > 0 && (
              <p className="mt-4 text-xs text-amber italic">API key missing. You must update the provider key before enabling models.</p>
            )}
          </motion.div>
          );
        })()}
      </AnimatePresence>

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
        success("Provider connected!", `${res.modelsDiscovered} models discovered.`);
        onSuccess();
      }, 800);
    } catch (err: any) {
      toastError("Connection failed", err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-background/80 backdrop-blur-md" onClick={(e) => e.target === e.currentTarget && onClose()}>
      <motion.div
        initial={{ opacity: 0, scale: 0.93 }}
        animate={{ opacity: 1, scale: 1 }}
        className="glass-strong w-full max-w-lg rounded-2xl shadow-2xl overflow-hidden"
      >
        <div className="flex items-center justify-between border-b border-[var(--glass-border)] px-5 py-4">
          <p className="text-sm font-semibold flex items-center gap-2">
            <Globe className="h-4 w-4 text-cyan" />
            {step === 1 ? "Select Provider" : `Configure ${selectedCatalog?.label}`}
          </p>
          <button onClick={onClose}><X className="h-4 w-4 text-muted-foreground" /></button>
        </div>

        <div className="p-5">
          {step === 1 && (
            <div className="grid grid-cols-3 gap-3">
              {PROVIDER_CATALOG.map((p) => (
                <motion.button
                  key={p.label}
                  onClick={() => { setSelectedCatalog(p); setStep(2); }}
                  whileHover={{ scale: 1.04, y: -2 }}
                  whileTap={{ scale: 0.97 }}
                  className="flex flex-col items-center gap-2.5 rounded-xl border border-[var(--glass-border)] bg-[var(--glass-bg)] p-4 text-xs font-medium transition-all hover:bg-[var(--glass-hover)] hover:border-[color-mix(in_oklab,var(--foreground)_20%,transparent)]"
                >
                  <div className="h-10 w-10 rounded-xl flex items-center justify-center font-bold text-white text-sm" style={{ background: p.color }}>
                    {p.label.slice(0, 2).toUpperCase()}
                  </div>
                  <span className="text-center leading-tight">{p.label}</span>
                  <ChevronRight className="h-3 w-3 text-muted-foreground" />
                </motion.button>
              ))}
            </div>
          )}

          {step === 2 && selectedCatalog && (
            <form onSubmit={submit} className="space-y-4">
              <div className="flex items-center gap-3 p-3 rounded-xl bg-[var(--glass-bg)] border border-[var(--glass-border)]">
                <div className="h-9 w-9 rounded-lg flex items-center justify-center font-bold text-white text-sm shrink-0" style={{ background: selectedCatalog.color }}>
                  {selectedCatalog.label.slice(0, 2).toUpperCase()}
                </div>
                <div>
                  <p className="text-sm font-semibold">{selectedCatalog.label}</p>
                  <p className="text-xs text-muted-foreground">{selectedCatalog.type}</p>
                </div>
                <button type="button" onClick={() => setStep(1)} className="ml-auto text-xs text-muted-foreground hover:text-foreground underline">Change</button>
              </div>

              <div className="grid gap-3 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <Label className="text-xs">Display Name</Label>
                  <Input value={form.displayName} onChange={(e) => setForm({ ...form, displayName: e.target.value })} placeholder={selectedCatalog.label} className="glass-input h-9 text-xs" />
                </div>
                <div className="space-y-1.5">
                  <Label className="text-xs">Slug (unique ID)</Label>
                  <Input value={form.slug} onChange={(e) => setForm({ ...form, slug: e.target.value.toLowerCase().replace(/\s+/g, "-") })} placeholder={selectedCatalog.label.toLowerCase().replace(/\s+/g, "-")} className="glass-input h-9 text-xs" autoComplete="off" data-lpignore="true" />
                </div>
              </div>

              {selectedCatalog.fields.map((f) => (
                <div key={f.key} className="space-y-1.5">
                  <Label className="text-xs">{f.label}</Label>
                  <Input
                    type={f.key === "apiKey" || f.key === "secretKey" ? "password" : "text"}
                    placeholder={f.ph}
                    value={form.fields[f.key] ?? ""}
                    onChange={(e) => setForm({ ...form, fields: { ...form.fields, [f.key]: e.target.value } })}
                    className="glass-input h-9 text-xs"
                    required={f.key === "apiKey"}
                  />
                </div>
              ))}

              <div className="flex gap-2 pt-2">
                <Button type="submit" disabled={loading || validated} className="flex-1 grad-primary h-10 rounded-xl text-sm text-primary-foreground">
                  {validated ? (
                    <span className="flex items-center gap-2 text-emerald"><Check className="h-4 w-4" /> Connected!</span>
                  ) : loading ? (
                    <span className="flex items-center gap-2"><Loader2 className="h-4 w-4 animate-spin" /> Validating...</span>
                  ) : (
                    <span className="flex items-center gap-2"><Zap className="h-4 w-4" /> Connect & Discover</span>
                  )}
                </Button>
                <Button type="button" onClick={onClose} variant="outline" className="glass h-10 rounded-xl text-xs">Cancel</Button>
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
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-background/80 p-4 backdrop-blur-md" onClick={(e) => e.target === e.currentTarget && onClose()}>
      <motion.div
        initial={{ opacity: 0, scale: 0.95 }}
        animate={{ opacity: 1, scale: 1 }}
        className="glass-strong w-full max-w-sm rounded-2xl p-6 shadow-2xl"
      >
        <div className="mb-4 flex items-center justify-between">
          <p className="text-sm font-medium flex items-center gap-2">
            <Key className="h-4 w-4 text-cyan" /> Update Credentials — <span className="font-mono text-xs text-muted-foreground">{slug}</span>
          </p>
          <button onClick={onClose}><X className="h-4 w-4 text-muted-foreground" /></button>
        </div>
        <form onSubmit={async (e) => { e.preventDefault(); if (!key.trim()) return; setLoading(true); await onSubmit(key.trim()); setLoading(false); }} className="space-y-4">
          <div className="space-y-1.5 relative">
            <Label className="text-xs">New API Key for {slug}</Label>
            <Input type={show ? "text" : "password"} value={key} onChange={(e) => setKey(e.target.value)} placeholder="sk-..." required className="glass-input h-10 text-xs pr-10" autoFocus />
            <button type="button" onClick={() => setShow((v) => !v)} className="absolute right-3 top-[calc(50%+4px)] text-muted-foreground hover:text-foreground">
              {show ? <span className="text-[0.7rem]">Hide</span> : <span className="text-[0.7rem]">Show</span>}
            </button>
          </div>
          <Button type="submit" disabled={loading} className="grad-primary h-10 w-full rounded-xl text-sm text-primary-foreground">
            {loading ? <span className="flex items-center gap-2"><Loader2 className="h-4 w-4 animate-spin" /> Updating...</span> : "Save New Key"}
          </Button>
        </form>
      </motion.div>
    </div>
  );
}
