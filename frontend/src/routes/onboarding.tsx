import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import { motion, AnimatePresence } from "motion/react";
import { useMemo, useState } from "react";
import {
  ArrowRight, Check, CheckCircle2, Copy, KeyRound, ShieldAlert,
  Building2, Layers, ChevronRight, Server, Globe, Bot,
  CircleCheck, Loader2,
} from "lucide-react";
import { MeshBackground } from "@/components/MeshBackground";
import { ThemeToggle } from "@/components/ThemeToggle";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useUser } from "@/lib/user-context";
import { useToast } from "@/lib/toast";
import { providersApi, tenantApi } from "@/lib/api";

export const Route = createFileRoute("/onboarding")({
  head: () => ({
    meta: [
      { title: "Provision Your Workspace — NexusAI" },
      {
        name: "description",
        content:
          "Your NexusAI workspace is live. Connect your first AI provider to start routing traffic.",
      },
    ],
  }),
  component: Onboarding,
});

type ProviderDef = {
  id: string;
  label: string;
  slug: string;
  type: "OPENAI_COMPATIBLE" | "GOOGLE" | "ANTHROPIC" | "AWS_BEDROCK" | "AZURE_OPENAI";
  color: string;
  fields: Array<{ key: string; label: string; placeholder: string; type?: string }>;
};

const PROVIDERS: ProviderDef[] = [
  {
    id: "openai", label: "OpenAI", slug: "openai", type: "OPENAI_COMPATIBLE", color: "#10a37f",
    fields: [{ key: "apiKey", label: "API Key", placeholder: "sk-proj-••••••••" }],
  },
  {
    id: "anthropic", label: "Anthropic", slug: "anthropic", type: "ANTHROPIC", color: "#d97706",
    fields: [{ key: "apiKey", label: "API Key", placeholder: "sk-ant-••••••••" }],
  },
  {
    id: "groq", label: "Groq", slug: "groq", type: "OPENAI_COMPATIBLE", color: "#f97316",
    fields: [{ key: "apiKey", label: "API Key", placeholder: "gsk_••••••••" }],
  },
  {
    id: "google", label: "Google AI", slug: "google", type: "GOOGLE", color: "#4285f4",
    fields: [{ key: "apiKey", label: "API Key", placeholder: "AI••••••••" }],
  },
  {
    id: "azure", label: "Azure OpenAI", slug: "azure-openai", type: "AZURE_OPENAI", color: "#0078d4",
    fields: [
      { key: "baseUrl", label: "Endpoint URL", placeholder: "https://your-resource.openai.azure.com" },
      { key: "apiKey", label: "API Key", placeholder: "••••••••••••••••" },
    ],
  },
  {
    id: "bedrock", label: "AWS Bedrock", slug: "aws-bedrock", type: "AWS_BEDROCK", color: "#ff9900",
    fields: [
      { key: "apiKey", label: "Access Key ID", placeholder: "AKIA••••••••••••••••" },
      { key: "secretKey", label: "Secret Access Key", placeholder: "••••••••••••••••" },
      { key: "region", label: "AWS Region", placeholder: "us-east-1" },
    ],
  },
];

function Onboarding() {
  const { session } = useUser();
  const navigate = useNavigate();
  const { success, error: toastError, info } = useToast();

  const apiKey = useMemo(() => session.apiKey ?? sessionStorage.getItem("nexus_api_key") ?? "", []);
  const tier = useMemo(() => session.tier ?? (sessionStorage.getItem("nexus_tier") as any) ?? "SOLO", []);
  const isOrg = tier === "ADMINISTRATION";

  // Multi-step wizard state
  const totalSteps = isOrg ? 3 : 2;
  const [step, setStep] = useState(1);
  const [copied, setCopied] = useState(false);
  const [generatedKey, setGeneratedKey] = useState<string | null>(null);
  const [generatingKey, setGeneratingKey] = useState(false);
  
  const displayKey = generatedKey ?? apiKey ?? "nx_live_generating...";

  // Provider connection state
  const [selectedProvider, setSelectedProvider] = useState<ProviderDef | null>(null);
  const [providerFields, setProviderFields] = useState<Record<string, string>>({});
  const [connecting, setConnecting] = useState(false);
  const [connected, setConnected] = useState(false);

  // Team creation (org only, step 3)
  const [teamName, setTeamName] = useState("");
  const [headEmail, setHeadEmail] = useState("");
  const [creatingTeam, setCreatingTeam] = useState(false);

  const copy = async () => {
    try { await navigator.clipboard.writeText(displayKey); } catch { /* noop */ }
    setCopied(true);
    setTimeout(() => setCopied(false), 2200);
  };

  const connectProvider = async () => {
    if (!selectedProvider) return;
    setConnecting(true);
    try {
      const regPayload: Parameters<typeof providersApi.registerProvider>[0] = {
        displayName: selectedProvider.label,
        slug: selectedProvider.slug,
        type: selectedProvider.type,
      };
      if (providerFields["baseUrl"]) regPayload.baseUrl = providerFields["baseUrl"];
      if (providerFields["apiKey"]) regPayload.apiKey = providerFields["apiKey"];
      await providersApi.registerProvider(regPayload);
      setConnected(true);
      success(`${selectedProvider.label} connected!`, "Models are being discovered in the background.");
    } catch (err: any) {
      toastError("Connection failed", err.message ?? "Could not connect this provider.");
    } finally {
      setConnecting(false);
    }
  };

  const handleNext = () => {
    if (step < totalSteps) setStep((s) => s + 1);
    else {
      if (session.role === "ORG_ADMIN") {
        navigate({ to: "/app/members" });
      } else if (session.role === "SOLO") {
        navigate({ to: "/app/providers" });
      } else {
        navigate({ to: "/app" });
      }
    }
  };

  const steps = isOrg
    ? ["Connect Provider", "Workspace Ready", "Create First Team"]
    : ["Connect Provider", "Workspace Ready"];

  return (
    <div className="relative min-h-screen bg-background px-5 py-10 text-foreground sm:px-8">
      <MeshBackground />
      <div className="absolute right-6 top-6"><ThemeToggle /></div>

      <motion.div
        initial={{ opacity: 0, y: 28, scale: 0.97 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={{ duration: 0.65, ease: [0.22, 1, 0.36, 1] }}
        className="glass-strong glow-primary mx-auto max-w-2xl rounded-3xl p-7 sm:p-10"
      >
        {/* Step indicator */}
        <div className="flex items-center gap-2 mb-8">
          {steps.map((label, i) => {
            const idx = i + 1;
            const done = idx < step;
            const active = idx === step;
            return (
              <div key={label} className="flex items-center gap-2">
                <div className={`flex h-7 w-7 items-center justify-center rounded-full text-xs font-semibold transition-all duration-300 ${
                  done ? "bg-emerald text-white" : active ? "grad-primary text-white" : "bg-[var(--glass-hover)] text-muted-foreground"
                }`}>
                  {done ? <Check className="h-3.5 w-3.5" /> : idx}
                </div>
                <span className={`text-xs hidden sm:block transition-colors ${active ? "text-foreground font-medium" : "text-muted-foreground"}`}>
                  {label}
                </span>
                {i < steps.length - 1 && (
                  <div className={`h-px w-6 sm:w-10 transition-colors ${done ? "bg-emerald/50" : "bg-[var(--glass-border)]"}`} />
                )}
              </div>
            );
          })}
        </div>

        <AnimatePresence mode="wait">
          {/* ── STEP 2: Workspace Ready (API Key) ── */}
          {step === 2 && (
            <motion.div
              key="step1"
              initial={{ opacity: 0, x: 20 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: -20 }}
              transition={{ duration: 0.35, ease: [0.22, 1, 0.36, 1] }}
            >
              <motion.div
                initial={{ scale: 0.5, opacity: 0 }}
                animate={{ scale: 1, opacity: 1 }}
                transition={{ delay: 0.15, type: "spring", stiffness: 260, damping: 16 }}
                className="flex h-14 w-14 items-center justify-center rounded-2xl bg-emerald/15"
              >
                <CheckCircle2 className="h-7 w-7 text-emerald" />
              </motion.div>

              <h1 className="mt-5 text-2xl font-semibold tracking-tight">
                {isOrg ? "Organization workspace live 🎉" : "Your gateway is live 🎉"}
              </h1>
              <p className="mt-2 text-sm text-muted-foreground">
                {isOrg
                  ? `${session.orgName ?? "Your organization"} is provisioned in us-east-1. This master key has unrestricted access — guard it closely.`
                  : "Your personal gateway is live in us-east-1. Route your first request in under a minute."}
              </p>

              {/* API key reveal */}
              <div className="mt-8">
                <p className="mb-2 text-xs font-medium uppercase tracking-[0.16em] text-muted-foreground">
                  {isOrg ? "Master Gateway Key" : "Secret Gateway Key"}
                </p>
                <div className="flex flex-col gap-3 rounded-2xl border border-dashed border-cyan/40 bg-cyan/5 p-4 sm:flex-row sm:items-center">
                  <code className="min-w-0 flex-1 truncate font-mono text-sm text-cyan">{displayKey}</code>
                  <Button
                    onClick={copy}
                    variant="secondary"
                    className="glass h-9 shrink-0 rounded-lg text-xs font-medium"
                  >
                    <AnimatePresence mode="wait" initial={false}>
                      {copied ? (
                        <motion.span
                          key="ok"
                          initial={{ scale: 0.6, opacity: 0 }}
                          animate={{ scale: 1, opacity: 1 }}
                          exit={{ scale: 0.6, opacity: 0 }}
                          className="flex items-center gap-1.5 text-emerald"
                        >
                          <Check className="h-3.5 w-3.5" /> Copied
                        </motion.span>
                      ) : (
                        <motion.span
                          key="copy"
                          initial={{ scale: 0.6, opacity: 0 }}
                          animate={{ scale: 1, opacity: 1 }}
                          exit={{ scale: 0.6, opacity: 0 }}
                          className="flex items-center gap-1.5"
                        >
                          <Copy className="h-3.5 w-3.5" /> Copy to Clipboard
                        </motion.span>
                      )}
                    </AnimatePresence>
                  </Button>
                </div>
                <p className="mt-3 flex items-start gap-2 text-xs text-amber">
                  <ShieldAlert className="mt-0.5 h-3.5 w-3.5 shrink-0" />
                  This secret is shown exactly once. Save it securely before continuing.
                </p>
              </div>

              <div className="mt-8 flex flex-wrap items-center gap-3">
                <motion.div whileHover={{ scale: 1.015 }} whileTap={{ scale: 0.98 }}>
                  <Button
                    onClick={handleNext}
                    className="grad-primary h-11 rounded-xl text-sm text-primary-foreground transition-shadow hover:shadow-[0_0_38px_-6px_var(--cyan)]"
                  >
                    {step === totalSteps ? "Go to Dashboard" : "Continue"} <ArrowRight className="ml-1.5 h-4 w-4" />
                  </Button>
                </motion.div>
                <Link
                  to="/app"
                  className="glass flex h-11 items-center gap-1.5 rounded-xl px-4 text-sm transition-colors hover:bg-[var(--glass-hover)] text-muted-foreground"
                >
                  Skip to console <ChevronRight className="h-4 w-4" />
                </Link>
              </div>
            </motion.div>
          )}

          {/* ── STEP 1: Connect Provider ── */}
          {step === 1 && (
            <motion.div
              key="step2"
              initial={{ opacity: 0, x: 20 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: -20 }}
              transition={{ duration: 0.35, ease: [0.22, 1, 0.36, 1] }}
            >
              <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-cyan/10">
                <Server className="h-7 w-7 text-cyan" />
              </div>
              <h2 className="mt-5 text-2xl font-semibold tracking-tight">Connect your first provider</h2>
              <p className="mt-2 text-sm text-muted-foreground">
                {isOrg
                  ? "These are your organization's infrastructure-level credentials. They power all team routing."
                  : "Select an AI provider and paste your API key to unlock the Model Hub."}
              </p>

              {/* Provider grid */}
              <div className="mt-6 grid grid-cols-3 gap-2 sm:grid-cols-6">
                {PROVIDERS.map((p) => (
                  <motion.button
                    key={p.id}
                    onClick={() => {
                      setSelectedProvider(p);
                      setProviderFields({});
                      setConnected(false);
                    }}
                    whileHover={{ scale: 1.05, y: -2 }}
                    whileTap={{ scale: 0.97 }}
                    className={`relative flex flex-col items-center gap-2 rounded-xl border p-3 text-xs font-medium transition-all duration-200 ${
                      selectedProvider?.id === p.id
                        ? "border-cyan/60 bg-cyan/10 shadow-[0_0_18px_-6px_var(--cyan)]"
                        : "border-[var(--glass-border)] bg-[var(--glass-bg)] hover:bg-[var(--glass-hover)]"
                    }`}
                  >
                    <div
                      className="h-8 w-8 rounded-lg flex items-center justify-center text-white font-bold text-xs"
                      style={{ background: p.color }}
                    >
                      {p.label.slice(0, 2).toUpperCase()}
                    </div>
                    <span className="text-[0.65rem] text-center leading-tight">{p.label}</span>
                    {selectedProvider?.id === p.id && (
                      <span className="absolute -top-1 -right-1 h-3 w-3 rounded-full bg-cyan border-2 border-background" />
                    )}
                  </motion.button>
                ))}
              </div>

              {/* Credential fields */}
              <AnimatePresence>
                {selectedProvider && !connected && (
                  <motion.div
                    key={selectedProvider.id}
                    initial={{ opacity: 0, height: 0 }}
                    animate={{ opacity: 1, height: "auto" }}
                    exit={{ opacity: 0, height: 0 }}
                    className="mt-5 space-y-3 overflow-hidden"
                  >
                    {selectedProvider.fields.map((f) => (
                      <div key={f.key} className="space-y-1.5">
                        <Label className="text-xs text-muted-foreground">{f.label}</Label>
                        <Input
                          type={f.key === "apiKey" || f.key === "secretKey" ? "password" : "text"}
                          placeholder={f.placeholder}
                          value={providerFields[f.key] ?? ""}
                          onChange={(e) => setProviderFields((prev) => ({ ...prev, [f.key]: e.target.value }))}
                          className="glass-input h-10 text-xs"
                        />
                      </div>
                    ))}
                    <motion.div whileHover={{ scale: 1.01 }} whileTap={{ scale: 0.99 }}>
                      <Button
                        onClick={connectProvider}
                        disabled={connecting || !providerFields["apiKey"]}
                        className="grad-primary mt-2 h-10 w-full rounded-xl text-sm text-primary-foreground"
                      >
                        {connecting ? (
                          <span className="flex items-center gap-2">
                            <Loader2 className="h-4 w-4 animate-spin" /> Validating credentials...
                          </span>
                        ) : (
                          <span className="flex items-center gap-2">
                            <KeyRound className="h-4 w-4" /> Connect {selectedProvider.label}
                          </span>
                        )}
                      </Button>
                    </motion.div>
                  </motion.div>
                )}
              </AnimatePresence>

              {/* Connected state */}
              <AnimatePresence>
                {connected && (
                  <motion.div
                    initial={{ opacity: 0, scale: 0.95 }}
                    animate={{ opacity: 1, scale: 1 }}
                    className="mt-5 flex items-center gap-3 rounded-xl border border-emerald/30 bg-emerald/10 p-4"
                  >
                    <CircleCheck className="h-5 w-5 text-emerald shrink-0" />
                    <div>
                      <p className="text-sm font-medium text-emerald">{selectedProvider?.label} connected successfully</p>
                      <p className="text-xs text-muted-foreground mt-0.5">Models are being discovered and will be available in the Model Hub.</p>
                    </div>
                  </motion.div>
                )}
              </AnimatePresence>

              <div className="mt-8 flex flex-wrap items-center gap-3">
                <motion.div whileHover={{ scale: 1.015 }} whileTap={{ scale: 0.98 }}>
                  <Button
                    onClick={async () => {
                      if (step === 1 && !generatedKey && session.tenantId) {
                        setGeneratingKey(true);
                        try {
                          const res = await tenantApi.generateKey(session.tenantId);
                          setGeneratedKey(res.apiKey);
                          setStep(2);
                        } catch (err: any) {
                          toastError("Key Generation Failed", err.message);
                        } finally {
                          setGeneratingKey(false);
                        }
                      } else {
                        handleNext();
                      }
                    }}
                    disabled={step === 1 && (generatingKey || !connected)}
                    className="grad-primary h-11 rounded-xl text-sm text-primary-foreground"
                  >
                    {generatingKey ? (
                      <span className="flex items-center gap-2"><Loader2 className="h-4 w-4 animate-spin" /> Generating...</span>
                    ) : (
                      <>
                        Continue — Get API Key
                        <ArrowRight className="ml-1.5 h-4 w-4" />
                      </>
                    )}
                  </Button>
                </motion.div>
                {!connected && (
                  <button
                    onClick={() => navigate({ to: "/app" })}
                    className="text-sm text-muted-foreground underline underline-offset-4 hover:text-foreground transition-colors"
                  >
                    Set up later in Provider Hub
                  </button>
                )}
              </div>
            </motion.div>
          )}

          {/* ── STEP 3 (ORG ONLY): Create First Team ── */}
          {step === 3 && isOrg && (
            <motion.div
              key="step3"
              initial={{ opacity: 0, x: 20 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: -20 }}
              transition={{ duration: 0.35, ease: [0.22, 1, 0.36, 1] }}
            >
              <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-indigo/10">
                <Layers className="h-7 w-7 text-indigo" />
              </div>
              <h2 className="mt-5 text-2xl font-semibold tracking-tight">Create your first team</h2>
              <p className="mt-2 text-sm text-muted-foreground">
                Teams isolate model access and budget. A Team Head receives an invite link to claim their account and manage their developers.
              </p>

              <div className="mt-6 space-y-4">
                <div className="space-y-1.5">
                  <Label className="text-xs text-muted-foreground">Team Name</Label>
                  <Input
                    placeholder="e.g. Engineering, Marketing, Data Science"
                    value={teamName}
                    onChange={(e) => setTeamName(e.target.value)}
                    className="glass-input h-10 text-sm"
                  />
                </div>
                <div className="space-y-1.5">
                  <Label className="text-xs text-muted-foreground">Team Head Email</Label>
                  <Input
                    type="email"
                    placeholder="lead@acme.com"
                    value={headEmail}
                    onChange={(e) => setHeadEmail(e.target.value)}
                    className="glass-input h-10 text-sm"
                  />
                  <p className="text-[0.7rem] text-muted-foreground">
                    An invite link will be sent. The Team Head sets their own password.
                  </p>
                </div>
              </div>

              <div className="mt-8 flex flex-wrap items-center gap-3">
                <motion.div whileHover={{ scale: 1.015 }} whileTap={{ scale: 0.98 }}>
                  <Button
                    onClick={() => {
                      if (teamName) {
                        info("Invitation sent", `${headEmail || "Team Head"} has been invited to lead ${teamName}.`);
                      }
                      navigate({ to: "/app" });
                    }}
                    className="grad-primary h-11 rounded-xl text-sm text-primary-foreground"
                  >
                    {teamName ? "Create Team & Go to Dashboard" : "Go to Dashboard"}
                    <ArrowRight className="ml-1.5 h-4 w-4" />
                  </Button>
                </motion.div>
                <button
                  onClick={() => navigate({ to: "/app" })}
                  className="text-sm text-muted-foreground underline underline-offset-4 hover:text-foreground transition-colors"
                >
                  Create teams later
                </button>
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </motion.div>
    </div>
  );
}
