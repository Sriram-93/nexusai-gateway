import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { AnimatePresence, motion } from "motion/react";
import { useState } from "react";
import {
  ArrowRight, Building2, Lock, Mail, ShieldCheck, Sparkles, Zap, User as UserIcon,
  Eye, EyeOff,
} from "lucide-react";
import { NeuralMesh } from "@/components/NeuralMesh";
import { MeshBackground } from "@/components/MeshBackground";
import { ThemeToggle } from "@/components/ThemeToggle";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { authApi, ApiError } from "@/lib/api";
import { useUser, deriveRoleFromSignup } from "@/lib/user-context";
import { useToast } from "@/lib/toast";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "NexusAI — Adaptive AI Routing Gateway for Enterprises" },
      {
        name: "description",
        content:
          "NexusAI routes every LLM request to the fastest, cheapest provider in real time. Sign in or create your organization workspace.",
      },
      { property: "og:title", content: "NexusAI — Adaptive AI Routing Gateway" },
      {
        property: "og:description",
        content:
          "Enterprise AI gateway with adaptive routing, BYOK provider keys, and full observability.",
      },
    ],
  }),
  component: AuthPage,
});


const TABS = [
  { id: "signin", label: "Sign In" },
  { id: "signup", label: "Create Account" },
] as const;

function AuthPage() {
  const [tab, setTab] = useState<"signin" | "signup">("signin");
  const navigate = useNavigate();
  const { setSession } = useUser();
  const { success, error: toastError } = useToast();

  const [tier, setTier] = useState<"SOLO" | "ADMINISTRATION">("SOLO");
  const [isLoading, setIsLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [signInError, setSignInError] = useState<string | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSignInError(null);

    const emailEl = document.getElementById("email") as HTMLInputElement;
    const passwordEl = document.getElementById("password") as HTMLInputElement;
    const orgEl = document.getElementById("org") as HTMLInputElement;

    const email = emailEl?.value ?? "";
    const password = passwordEl?.value ?? "";

    if (tab === "signup") {
      const orgName = orgEl?.value ?? "";
      setIsLoading(true);
      try {
        const signupPayload: Parameters<typeof authApi.signup>[0] = { tier, email, password };
        if (orgName) signupPayload.organizationName = orgName;
        const data = await authApi.signup(signupPayload);

        const role = deriveRoleFromSignup(tier);

        setSession({
          jwt: data.token,
          tenantId: data.tenantId,
          apiKey: data.apiKey,
          tier,
          role,
          orgName: orgName || null,
          email,
        });

        success("Workspace provisioned!", `Welcome to NexusAI — ${tier === "SOLO" ? "Solo workspace" : orgName} is live.`);
        
        navigate({ to: "/onboarding" });
      } catch (err: any) {
        const msg = err instanceof ApiError ? err.message : (err.message ?? "Signup failed. Please try again.");
        setSignInError(msg);
      } finally {
        setIsLoading(false);
      }
    } else {
      setIsLoading(true);
      try {
        const res = await authApi.login({ email, password });
        
        setSession({
          jwt: res.token,
          tenantId: res.tenantId,
          apiKey: null, // fetched later if needed
          tier: res.tier as any,
          role: res.role as any,
          orgName: res.orgName || null,
          email,
        });
        
        success("Welcome back!", "Login successful.");
        
        // Custom routing based on role
        if (res.role === "ORG_ADMIN") {
          navigate({ to: "/app/members" });
        } else if (res.role === "TEAM_LEAD" || res.role === "TEAM_MEMBER") {
          navigate({ to: "/app" });
        } else if (res.role === "SOLO") {
          try {
            const status = await providersApi.getStatus();
            if (!status.hasProviders) {
              navigate({ to: "/app/providers" });
            } else {
              navigate({ to: "/app" });
            }
          } catch (e) {
            navigate({ to: "/app/providers" });
          }
        } else {
          navigate({ to: "/app" });
        }
      } catch (err: any) {
        setSignInError(err.message ?? "Invalid email or password.");
      } finally {
        setIsLoading(false);
      }
    }
  };

  return (
    <div className="relative min-h-screen bg-background text-foreground lg:grid lg:grid-cols-[1.1fr_1fr]">
      <MeshBackground />

      {/* Left: neural routing visual */}
      <section className="relative flex min-h-[26rem] flex-col justify-between overflow-hidden border-b p-8 lg:min-h-screen lg:border-b-0 lg:border-r lg:p-14">
        <div className="flex items-center gap-2 text-sm font-medium tracking-tight">
          <span className="grad-primary flex h-8 w-8 items-center justify-center rounded-lg">
            <Sparkles className="h-4 w-4 text-primary-foreground" />
          </span>
          NexusAI
        </div>

        <NeuralMesh className="pointer-events-none absolute inset-0 h-full w-full opacity-90" />

        <div className="relative max-w-xl">
          <motion.h1
            initial={{ opacity: 0, y: 24 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8, ease: [0.22, 1, 0.36, 1] }}
            className="text-gradient text-4xl font-semibold leading-[1.08] tracking-tight sm:text-5xl"
          >
            The Ultimate Adaptive Routing Gateway
          </motion.h1>
          <motion.p
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8, delay: 0.12 }}
            className="mt-5 max-w-md text-sm leading-relaxed text-muted-foreground"
          >
            One endpoint. Every model. Federated bandit routing continuously learns which provider
            wins on latency, cost, and quality — request by request.
          </motion.p>
          <div className="mt-8 flex flex-wrap gap-2">
            {[
              { icon: Zap, label: "24ms median overhead" },
              { icon: ShieldCheck, label: "SOC2 · BYOK encryption" },
            ].map((f) => (
              <span
                key={f.label}
                className="glass flex items-center gap-2 rounded-full px-3 py-1.5 text-xs text-muted-foreground"
              >
                <f.icon className="h-3.5 w-3.5 text-cyan" /> {f.label}
              </span>
            ))}
          </div>
        </div>
      </section>

      {/* Right: auth card */}
      <section className="relative flex items-center justify-center p-6 sm:p-10">
        <div className="absolute right-6 top-6">
          <ThemeToggle />
        </div>

        <motion.div
          initial={{ opacity: 0, y: 26, filter: "blur(6px)" }}
          animate={{ opacity: 1, y: 0, filter: "blur(0px)" }}
          transition={{ duration: 0.7, ease: [0.22, 1, 0.36, 1] }}
          className="glass-strong glow-primary w-full max-w-md rounded-3xl p-7 sm:p-9"
        >
          <h2 className="text-xl font-semibold tracking-tight">Welcome to the gateway</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Enterprise access to every frontier model.
          </p>

          {/* Tab switcher */}
          <div className="glass relative mt-6 grid grid-cols-2 gap-1 rounded-xl p-1">
            {TABS.map((t) => (
              <button
                key={t.id}
                onClick={() => { setTab(t.id); setSignInError(null); }}
                className={`relative z-10 rounded-lg py-2 text-sm font-medium transition-colors ${
                  tab === t.id ? "text-primary-foreground" : "text-muted-foreground hover:text-foreground"
                }`}
              >
                {tab === t.id && (
                  <motion.span
                    layoutId="auth-tab"
                    transition={{ type: "spring", stiffness: 420, damping: 34 }}
                    className="grad-primary absolute inset-0 -z-10 rounded-lg"
                  />
                )}
                {t.label}
              </button>
            ))}
          </div>

          <form onSubmit={submit} className="mt-6 space-y-4">
            <AnimatePresence mode="popLayout" initial={false}>
              {tab === "signup" && (
                <motion.div
                  key="tier-selector"
                  initial={{ opacity: 0, height: 0 }}
                  animate={{ opacity: 1, height: "auto" }}
                  exit={{ opacity: 0, height: 0 }}
                  transition={{ duration: 0.28 }}
                  className="space-y-4 overflow-hidden"
                >
                  {/* Tier selector */}
                  <div>
                    <p className="mb-2 text-xs font-medium text-muted-foreground">Choose workspace type</p>
                    <div className="grid grid-cols-2 gap-3">
                      {([
                        {
                          id: "SOLO" as const,
                          icon: UserIcon,
                          label: "Solo Developer",
                          sub: "Personal workspace",
                          color: "cyan",
                        },
                        {
                          id: "ADMINISTRATION" as const,
                          icon: Building2,
                          label: "Organization",
                          sub: "Teams & RBAC",
                          color: "indigo",
                        },
                      ] as const).map((t) => {
                        const isSelected = tier === t.id;
                        return (
                          <motion.div
                            key={t.id}
                            onClick={() => setTier(t.id)}
                            whileHover={{ scale: 1.02 }}
                            whileTap={{ scale: 0.98 }}
                            className={`cursor-pointer rounded-xl border p-3.5 flex flex-col items-center gap-2 transition-all duration-200 ${
                              isSelected
                                ? `border-${t.color} bg-${t.color}/10 shadow-[0_0_20px_-8px_var(--${t.color})]`
                                : "border-[var(--glass-border)] bg-[var(--glass-bg)] hover:bg-[var(--glass-hover)]"
                            }`}
                          >
                            <t.icon className={`h-5 w-5 ${isSelected ? `text-${t.color}` : "text-muted-foreground"}`} />
                            <div className="text-center">
                              <p className={`text-xs font-semibold ${isSelected ? `text-${t.color}` : ""}`}>{t.label}</p>
                              <p className="text-[0.65rem] text-muted-foreground mt-0.5">{t.sub}</p>
                            </div>
                          </motion.div>
                        );
                      })}
                    </div>
                  </div>

                  <AnimatePresence mode="popLayout">
                    {tier === "ADMINISTRATION" && (
                      <motion.div
                        key="org-input"
                        initial={{ opacity: 0, height: 0 }}
                        animate={{ opacity: 1, height: "auto" }}
                        exit={{ opacity: 0, height: 0 }}
                        className="space-y-2 overflow-hidden"
                      >
                        <Label htmlFor="org" className="text-xs text-muted-foreground">Organization Name</Label>
                        <Field icon={Building2}>
                          <Input id="org" required placeholder="Acme Intelligence Corp" className="glass-input" />
                        </Field>
                      </motion.div>
                    )}
                  </AnimatePresence>
                </motion.div>
              )}
            </AnimatePresence>

            <div className="space-y-2">
              <Label htmlFor="email" className="text-xs text-muted-foreground">
                {tab === "signup" ? "Work Email" : "Email"}
              </Label>
              <Field icon={Mail}>
                <Input
                  id="email"
                  type="email"
                  required
                  placeholder="you@acme.com"
                  className="glass-input"
                />
              </Field>
            </div>

            <div className="space-y-2">
              <Label htmlFor="password" className="text-xs text-muted-foreground">
                Password
              </Label>
              <div className="relative">
                <Field icon={Lock}>
                  <Input
                    id="password"
                    type={showPassword ? "text" : "password"}
                    required
                    placeholder="••••••••••••"
                    className="glass-input pr-10"
                  />
                </Field>
                <button
                  type="button"
                  onClick={() => setShowPassword((v) => !v)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground transition-colors hover:text-foreground"
                  tabIndex={-1}
                >
                  {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
            </div>

            <AnimatePresence>
              {signInError && (
                <motion.div
                  initial={{ opacity: 0, height: 0 }}
                  animate={{ opacity: 1, height: "auto" }}
                  exit={{ opacity: 0, height: 0 }}
                  className="rounded-xl border border-destructive/40 bg-destructive/10 px-3 py-2.5 text-xs text-destructive overflow-hidden"
                >
                  {signInError}
                </motion.div>
              )}
            </AnimatePresence>

            <motion.div whileHover={{ scale: 1.015 }} whileTap={{ scale: 0.985 }}>
              <Button
                type="submit"
                disabled={isLoading}
                className="grad-primary h-11 w-full rounded-xl text-sm font-medium text-primary-foreground transition-shadow duration-300 hover:shadow-[0_0_38px_-6px_var(--cyan)]"
              >
                {isLoading
                  ? "Processing..."
                  : tab === "signin"
                    ? "Enter Console"
                    : tier === "SOLO"
                      ? "Provision Solo Workspace"
                      : "Provision Organization"}
                {!isLoading && <ArrowRight className="ml-1.5 h-4 w-4" />}
              </Button>
            </motion.div>

            <p className="pt-1 text-center text-[0.7rem] leading-relaxed text-muted-foreground">
              By continuing you accept the NexusAI enterprise terms and zero-retention data policy.
            </p>
          </form>
        </motion.div>
      </section>
    </div>
  );
}

function Field({
  icon: Icon,
  children,
}: {
  icon: React.ComponentType<{ className?: string }>;
  children: React.ReactNode;
}) {
  return (
    <div className="group relative">
      <Icon className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground transition-colors group-focus-within:text-cyan" />
      <div className="[&_input]:h-11 [&_input]:rounded-xl [&_input]:border-[var(--glass-border)] [&_input]:bg-[var(--glass-bg)] [&_input]:pl-9 [&_input]:backdrop-blur-md">
        {children}
      </div>
    </div>
  );
}
