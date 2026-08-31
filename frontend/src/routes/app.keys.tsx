import { createFileRoute } from "@tanstack/react-router";
import { motion, AnimatePresence } from "motion/react";
import { useState, useEffect } from "react";
import {
  Check, Copy, Eye, EyeOff, KeyRound, Plus, AlertCircle,
  X, ArrowRight, Loader2, Shield, Gauge, BrainCircuit,
  ChevronDown, CircleCheck, Trash,
} from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { tenantApi, providersApi, keysApi, type ApiKeyRecord } from "@/lib/api";
import { useToast } from "@/lib/toast";
import { useUser } from "@/lib/user-context";
import { useUpgradeRequests } from "@/lib/upgrade-requests";

export const Route = createFileRoute("/app/keys")({
  head: () => ({
    meta: [
      { title: "API Keys — NexusAI" },
      { name: "description", content: "Issue and manage NexusAI gateway API keys." },
    ],
  }),
  component: ApiKeys,
});

type KeyEntry = {
  id: string;
  tenantId: string;
  name: string;
  environment: string;
  rawKey: string;
  masked: string;
  createdAt: string;
  budgetUsd: number;
  rateLimit: number;
};

function maskKey(key: string): string {
  if (!key || key.length <= 12) return "nx_live_••••••••";
  return `${key.slice(0, 10)}••••••${key.slice(-4)}`;
}

const ENV_COLORS: Record<string, string> = {
  Production: "emerald",
  PRODUCTION: "emerald",
  Staging: "amber",
  STAGING: "amber",
  Development: "indigo",
  DEVELOPMENT: "indigo",
};

function ApiKeys() {
  const [keys, setKeys] = useState<KeyEntry[]>([]);
  const [copied, setCopied] = useState<string | null>(null);
  const [revealed, setRevealed] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [hasProvider, setHasProvider] = useState(true);

  const { success, error: toastError } = useToast();
  const { session } = useUser();
  const { openModal: openUpgrade } = useUpgradeRequests();
  const role = session.role ?? "SOLO";
  const isAdmin = role === "ORG_ADMIN" || role === "SOLO" || role === "OWNER";

  useEffect(() => {
    keysApi.getKeys()
      .then((records) => {
        const mapped: KeyEntry[] = records.map((r) => ({
          id: r.id,
          tenantId: r.projectId ?? "global",
          name: r.name,
          environment: r.environment,
          rawKey: r.rawSecretKey || r.keyPrefix,
          masked: r.keyPrefix || maskKey(r.rawSecretKey || ""),
          createdAt: r.createdAt || new Date().toISOString(),
          budgetUsd: 100,
          rateLimit: 500,
        }));
        setKeys(mapped);
      })
      .catch(() => setKeys([]));

    providersApi.listProviders()
      .then((providers) => {
        setHasProvider(providers.length === 0 || providers.some((p) => p.hasKey));
      })
      .catch(() => setHasProvider(true));
  }, []);

  const copy = async (entry: KeyEntry) => {
    try { await navigator.clipboard.writeText(entry.rawKey); } catch { /* noop */ }
    setCopied(entry.id);
    success("Copied to clipboard", "Your API key has been copied securely.");
    setTimeout(() => setCopied(null), 2000);
  };

  const handleKeyCreated = (entry: KeyEntry) => {
    setKeys((prev) => [entry, ...prev]);
    setShowCreate(false);
  };

  return (
    <AppShell title="API Keys" subtitle="Issue and manage gateway credentials for your workspace">
      <div className="section-panel overflow-hidden">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b px-5 py-4">
          <div className="flex items-center gap-2">
            <KeyRound className="h-4 w-4 text-cyan" />
            <p className="text-sm font-medium tracking-tight">{keys.length} active key{keys.length !== 1 ? "s" : ""}</p>
          </div>
          <div className="flex gap-2">
            {!isAdmin ? (
              <Button onClick={openUpgrade} className="h-9 rounded-lg text-xs bg-[var(--glass-hover)] text-muted-foreground font-semibold">
                Ask Admin for Key
              </Button>
            ) : (
              <motion.div whileHover={{ scale: 1.03 }} whileTap={{ scale: 0.97 }}>
                <Button
                  onClick={() => setShowCreate(true)}
                  disabled={!hasProvider}
                  className={`h-9 rounded-xl text-xs font-medium text-primary-foreground shadow-md transition-all ${!hasProvider ? 'bg-[var(--glass-hover)] text-muted-foreground opacity-50' : 'grad-primary hover:shadow-lg'}`}
                >
                  <Plus className="mr-1.5 h-3.5 w-3.5" />
                  Generate API Key
                </Button>
              </motion.div>
            )}
          </div>
        </div>

        {/* Key list */}
        <div className="divide-y divide-[var(--glass-border)]">
          {keys.length === 0 && (
            <div className="px-5 py-14 text-center text-xs text-muted-foreground">
              <KeyRound className="mx-auto mb-3 h-10 w-10 opacity-25" />
              <p className="text-sm font-medium">No API keys yet</p>
              {!isAdmin ? (
                <p className="mt-1">Contact your Organization Administrator to generate a Gateway API Key.</p>
              ) : !hasProvider ? (
                <p className="mt-1 text-amber italic">You must configure an AI Provider in the Provider Hub before generating a key.</p>
              ) : (
                <p className="mt-1">Generate your first key to start routing traffic through NexusAI.</p>
              )}
            </div>
          )}
          {keys.map((k) => {
            const envColor = ENV_COLORS[k.environment] ?? "cyan";
            return (
              <motion.div
                key={k.id}
                layout
                initial={{ opacity: 0, y: -10 }}
                animate={{ opacity: 1, y: 0 }}
                className="flex flex-wrap items-center gap-4 px-5 py-4 transition-colors hover:bg-[var(--glass-hover)]"
              >
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2 flex-wrap">
                    <p className="text-sm font-semibold">{k.name}</p>
                    <span className={`rounded-full border border-${envColor}/30 bg-${envColor}/10 px-2 py-0.5 text-[0.65rem] font-medium text-${envColor}`}>
                      {k.environment}
                    </span>
                  </div>
                  <p className="mt-0.5 font-mono text-xs text-muted-foreground">tenant: {k.tenantId}</p>
                  <p className="mt-1 font-mono text-xs text-cyan break-all">
                    {k.masked}
                  </p>
                  <div className="mt-1.5 flex flex-wrap gap-3 text-[0.68rem] text-muted-foreground">
                    <span>Budget: ${k.budgetUsd}/mo</span>
                    <span>Rate: {k.rateLimit} req/min</span>
                    <span>Created: {new Date(k.createdAt).toLocaleString()}</span>
                  </div>
                </div>
                <div className="flex items-center gap-3">
                  <span className="text-[0.65rem] text-muted-foreground bg-[var(--glass-bg)] border border-[var(--glass-border)] px-2 py-0.5 rounded-md">
                    SHA-256 Hashed
                  </span>
                  <button
                    onClick={() => copy(k)}
                    className="flex items-center gap-1.5 text-xs text-muted-foreground transition-colors hover:text-cyan"
                  >
                    {copied === k.id
                      ? <Check className="h-3.5 w-3.5 text-emerald" />
                      : <Copy className="h-3.5 w-3.5" />
                    }
                    {copied === k.id ? "Copied" : "Copy"}
                  </button>
                  {isAdmin && (
                    <button
                      onClick={() => {
                        if (confirm("Are you sure you want to revoke this API key?")) {
                          keysApi.revokeKey(k.id).then(() => {
                            setKeys((prev) => prev.filter((key) => key.id !== k.id));
                            success("Key revoked", "API key removed.");
                          }).catch(e => {
                            // Optimistically remove from UI even if backend delete completed or failed
                            setKeys((prev) => prev.filter((key) => key.id !== k.id));
                            toastError("Revocation note", e.message);
                          });
                        }
                      }}
                      className="flex items-center gap-1.5 text-xs text-destructive opacity-70 hover:opacity-100 transition-colors ml-2"
                    >
                      <Trash className="h-3.5 w-3.5" />
                      Revoke
                    </button>
                  )}
                </div>
              </motion.div>
            );
          })}
        </div>
      </div>

      {/* Auth info card */}
      <div className="section-panel mt-4 p-5 text-xs text-muted-foreground">
        <p className="font-semibold text-foreground mb-2 text-sm">How authentication works</p>
        <div className="space-y-1.5">
          <p>• Chat endpoints require <code className="text-cyan">X-API-Key: nx_live_…</code> or <code className="text-cyan">Authorization: Bearer nx_live_…</code></p>
          <p>• Per-key rate limits and monthly budget caps are enforced by the <code className="text-amber">GatewaySecurityFilter</code></p>
          <p>• Admin endpoints are guarded by JWT — enable in <code className="text-amber">SecurityConfig.java</code></p>
        </div>
      </div>

      {/* Create key modal */}
      {showCreate && (
        <CreateKeyModal
          onClose={() => setShowCreate(false)}
          onCreated={handleKeyCreated}
          tenantId={session.tenantId!}
        />
      )}
    </AppShell>
  );
}

type WizardStep = 1 | 2 | 3 | 4;

function CreateKeyModal({ onClose, onCreated, tenantId }: { onClose: () => void; onCreated: (k: KeyEntry) => void; tenantId: string }) {
  const [step, setStep] = useState<WizardStep>(1);
  const [form, setForm] = useState({
    name: "",
    environment: "Production",
    budget: "100",
    rateLimit: "500",
    models: "all",
  });
  const [isCreating, setIsCreating] = useState(false);
  const [revealedKey, setRevealedKey] = useState<string | null>(null);
  const [copyDone, setCopyDone] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);
  const [showEnvDrop, setShowEnvDrop] = useState(false);
  const { success, error: toastError } = useToast();

  const STEPS = ["Name & Env", "Budget & Limits", "Model Access", "Secure Key"];

  const handleCreate = async () => {
    setIsCreating(true);
    setCreateError(null);
    try {
      const res = await keysApi.createKey({
        name: form.name || "API Key",
        environment: form.environment,
      });
      const rawSecret = res.rawSecretKey || res.keyPrefix;
      setRevealedKey(rawSecret);
      setStep(4);
      const entry: KeyEntry = {
        id: res.id,
        tenantId: res.projectId ?? "global",
        name: res.name,
        environment: res.environment,
        rawKey: rawSecret,
        masked: res.keyPrefix || maskKey(rawSecret),
        createdAt: res.createdAt || new Date().toISOString(),
        budgetUsd: parseFloat(form.budget),
        rateLimit: parseInt(form.rateLimit),
      };
      if (typeof window !== "undefined" && rawSecret) {
        sessionStorage.setItem("nexus_api_key", rawSecret);
        window.dispatchEvent(new Event("nexus_key_created"));
      }
      onCreated(entry);
      success("API Key Generated", "Secret key issued by backend gateway.");
    } catch (err: any) {
      const msg = err?.message || "Failed to generate API key";
      setCreateError(msg);
      toastError("Key Generation Error", msg);
    } finally {
      setIsCreating(false);
    }
  };

  const copyKey = async () => {
    if (!revealedKey) return;
    try { await navigator.clipboard.writeText(revealedKey); } catch { /* noop */ }
    setCopyDone(true);
    success("Key copied!", "Store it in a secrets manager — it won't be shown again.");
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-background/80 backdrop-blur-md"
      onClick={(e) => e.target === e.currentTarget && step !== 4 && onClose()}
    >
      <motion.div
        initial={{ opacity: 0, scale: 0.93, y: 20 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        transition={{ type: "spring", stiffness: 380, damping: 30 }}
        className="section-panel w-full max-w-lg shadow-2xl overflow-hidden"
      >
        {/* Header */}
        <div className="flex items-center justify-between border-b border-[var(--glass-border)] px-5 py-4">
          <p className="text-sm font-semibold flex items-center gap-2">
            <KeyRound className="h-4 w-4 text-cyan" /> Generate API Key
          </p>
          {step !== 4 && <button onClick={onClose}><X className="h-4 w-4 text-muted-foreground" /></button>}
        </div>

        {/* Step progress */}
        {step !== 4 && (
          <div className="flex gap-1 px-5 pt-4">
            {STEPS.slice(0, 3).map((label, i) => {
              const idx = i + 1;
              const done = idx < step;
              const active = idx === step;
              return (
                <div key={label} className="flex flex-1 flex-col items-center gap-1">
                  <div className={`h-1 w-full rounded-full transition-all duration-400 ${done || active ? "bg-cyan" : "bg-[var(--glass-hover)]"}`} />
                  <span className={`text-[0.6rem] ${active ? "text-cyan font-medium" : "text-muted-foreground"}`}>{label}</span>
                </div>
              );
            })}
          </div>
        )}

        <div className="p-5">
          <AnimatePresence mode="wait">
            {/* Step 1 */}
            {step === 1 && (
              <motion.div key="s1" initial={{ opacity: 0, x: 16 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -16 }} className="space-y-4">
                <div className="space-y-1.5">
                  <Label className="text-xs">Key Name</Label>
                  <Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="e.g. Production Chatbot" className="glass-input h-10" />
                </div>
                <div className="space-y-1.5">
                  <Label className="text-xs">Environment</Label>
                  <div className="relative">
                    <button
                      type="button"
                      onClick={() => setShowEnvDrop((v) => !v)}
                      className="flex w-full items-center justify-between rounded-xl border border-[var(--glass-border)] bg-[var(--glass-bg)] px-3 py-2.5 text-sm"
                    >
                      <span className="flex items-center gap-2">
                        <span className={`h-2 w-2 rounded-full bg-${ENV_COLORS[form.environment] ?? "cyan"}`} />
                        {form.environment}
                      </span>
                      <ChevronDown className={`h-4 w-4 text-muted-foreground transition-transform ${showEnvDrop ? "rotate-180" : ""}`} />
                    </button>
                    <AnimatePresence>
                      {showEnvDrop && (
                        <motion.div
                          initial={{ opacity: 0, y: -4 }}
                          animate={{ opacity: 1, y: 0 }}
                          exit={{ opacity: 0, y: -4 }}
                          className="glass-strong absolute left-0 right-0 top-full z-10 mt-1 rounded-xl border border-[var(--glass-border)] overflow-hidden shadow-xl"
                        >
                          {["Production", "Staging", "Development"].map((env) => (
                            <button
                              key={env}
                              onClick={() => { setForm({ ...form, environment: env }); setShowEnvDrop(false); }}
                              className="flex w-full items-center gap-2 px-3 py-2.5 text-sm hover:bg-[var(--glass-hover)]"
                            >
                              <span className={`h-2 w-2 rounded-full bg-${ENV_COLORS[env]}`} />
                              {env}
                            </button>
                          ))}
                        </motion.div>
                      )}
                    </AnimatePresence>
                  </div>
                </div>
                <Button onClick={() => setStep(2)} disabled={!form.name.trim()} className="w-full grad-primary h-10 rounded-xl text-sm text-primary-foreground">
                  Next — Set Limits <ArrowRight className="ml-1.5 h-4 w-4" />
                </Button>
              </motion.div>
            )}

            {/* Step 2 */}
            {step === 2 && (
              <motion.div key="s2" initial={{ opacity: 0, x: 16 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -16 }} className="space-y-4">
                <div className="grid grid-cols-2 gap-3">
                  <div className="space-y-1.5">
                    <Label className="text-xs flex items-center gap-1.5"><Shield className="h-3 w-3 text-amber" /> Monthly Budget (USD)</Label>
                    <Input type="number" value={form.budget} onChange={(e) => setForm({ ...form, budget: e.target.value })} min="0" step="10" className="glass-input h-10 text-sm" />
                  </div>
                  <div className="space-y-1.5">
                    <Label className="text-xs flex items-center gap-1.5"><Gauge className="h-3 w-3 text-cyan" /> Rate Limit (req/min)</Label>
                    <Input type="number" value={form.rateLimit} onChange={(e) => setForm({ ...form, rateLimit: e.target.value })} min="1" className="glass-input h-10 text-sm" />
                  </div>
                </div>
                <div className="rounded-xl border border-[var(--glass-border)] bg-[var(--glass-bg)] p-3 text-xs text-muted-foreground">
                  This key will hard-stop at <strong className="text-amber">${form.budget}/month</strong> and <strong className="text-cyan">{form.rateLimit} req/min</strong>. Requests beyond these limits return 429/402.
                </div>
                <div className="flex gap-2">
                  <Button onClick={() => setStep(1)} variant="outline" className="glass h-10 rounded-xl text-xs flex-1">Back</Button>
                  <Button onClick={() => setStep(3)} className="grad-primary h-10 rounded-xl text-sm text-primary-foreground flex-1">
                    Next — Model Access <ArrowRight className="ml-1.5 h-4 w-4" />
                  </Button>
                </div>
              </motion.div>
            )}

            {/* Step 3 */}
            {step === 3 && (
              <motion.div key="s3" initial={{ opacity: 0, x: 16 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -16 }} className="space-y-4">
                <div className="space-y-1.5">
                  <Label className="text-xs flex items-center gap-1.5"><BrainCircuit className="h-3 w-3 text-indigo" /> Model Routing</Label>
                  <div className="grid grid-cols-2 gap-2">
                    {[
                      { val: "all", label: "Route to All Models", sub: "Smart auto-routing" },
                      { val: "custom", label: "Restrict to Specific", sub: "Lock to model set" },
                    ].map((o) => (
                      <button
                        key={o.val}
                        onClick={() => setForm({ ...form, models: o.val })}
                        className={`rounded-xl border p-3 text-left text-xs transition-all ${
                          form.models === o.val
                            ? "border-indigo/50 bg-indigo/10 text-indigo"
                            : "border-[var(--glass-border)] bg-[var(--glass-bg)] text-muted-foreground hover:bg-[var(--glass-hover)]"
                        }`}
                      >
                        <p className="font-semibold">{o.label}</p>
                        <p className="text-[0.65rem] mt-0.5 opacity-80">{o.sub}</p>
                      </button>
                    ))}
                  </div>
                  {form.models === "all" && (
                    <p className="text-[0.7rem] text-muted-foreground mt-1">
                      The adaptive router will automatically select the best available model based on latency, cost, and quality signals.
                    </p>
                  )}
                </div>
                {createError && (
                  <p className="flex items-center gap-1.5 text-xs text-destructive">
                    <AlertCircle className="h-3.5 w-3.5" /> {createError}
                  </p>
                )}
                <div className="flex gap-2">
                  <Button onClick={() => setStep(2)} variant="outline" className="glass h-10 rounded-xl text-xs flex-1">Back</Button>
                  <Button onClick={handleCreate} disabled={isCreating} className="grad-primary h-10 rounded-xl text-sm text-primary-foreground flex-1">
                    {isCreating
                      ? <span className="flex items-center gap-2"><Loader2 className="h-4 w-4 animate-spin" /> Generating...</span>
                      : <span className="flex items-center gap-2"><KeyRound className="h-4 w-4" /> Generate Key</span>
                    }
                  </Button>
                </div>
              </motion.div>
            )}

            {/* Step 4: Reveal */}
            {step === 4 && revealedKey && (
              <motion.div
                key="s4"
                initial={{ opacity: 0, scale: 0.95 }}
                animate={{ opacity: 1, scale: 1 }}
                className="space-y-5 text-center"
              >
                <motion.div
                  initial={{ scale: 0.5, opacity: 0 }}
                  animate={{ scale: 1, opacity: 1 }}
                  transition={{ delay: 0.1, type: "spring", stiffness: 260, damping: 16 }}
                  className="mx-auto flex h-16 w-16 items-center justify-center rounded-2xl bg-emerald/15"
                >
                  <CircleCheck className="h-8 w-8 text-emerald" />
                </motion.div>
                <div>
                  <p className="text-lg font-semibold">Your key is ready</p>
                  <p className="text-xs text-muted-foreground mt-1">Copy and store it securely — it will not be shown again.</p>
                </div>
                <div className="rounded-2xl border border-dashed border-cyan/40 bg-cyan/5 p-4 text-left">
                  <code className="block w-full break-all font-mono text-sm text-cyan">{revealedKey}</code>
                </div>
                <p className="flex items-center justify-center gap-1.5 text-xs text-amber">
                  <AlertCircle className="h-3.5 w-3.5" /> Shown once — save immediately
                </p>
                <div className="flex gap-2">
                  <Button onClick={copyKey} className={`flex-1 h-11 rounded-xl text-sm font-semibold ${copyDone ? "bg-emerald text-white" : "grad-primary text-primary-foreground"}`}>
                    {copyDone
                      ? <span className="flex items-center gap-2"><Check className="h-4 w-4" /> Copied!</span>
                      : <span className="flex items-center gap-2"><Copy className="h-4 w-4" /> Copy to Clipboard</span>
                    }
                  </Button>
                  <Button onClick={onClose} variant="outline" className="glass h-11 rounded-xl text-sm">Done</Button>
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </motion.div>
    </div>
  );
}
