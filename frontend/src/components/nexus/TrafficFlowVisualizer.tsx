import { motion, AnimatePresence } from "motion/react";
import { useEffect, useState, useRef } from "react";
import { Radio, Zap, ShieldAlert, Cpu, Activity, Sparkles, CheckCircle2 } from "lucide-react";
import { type AuditLogEntry } from "@/lib/api";

const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080") as string;

interface TrafficFlowVisualizerProps {
  onEvent?: (event: AuditLogEntry) => void;
}

const PROVIDER_NODES = [
  { id: "groq", name: "Groq LLaMA 3.3", color: "from-cyan-500 to-blue-500", border: "border-cyan/40" },
  { id: "openai", name: "OpenAI GPT-4o", color: "from-emerald-500 to-teal-500", border: "border-emerald/40" },
  { id: "gemini", name: "Google Gemini 1.5", color: "from-purple-500 to-indigo-500", border: "border-indigo/40" },
  { id: "anthropic", name: "Anthropic Claude 3.5", color: "from-amber-500 to-orange-500", border: "border-amber/40" },
  { id: "ollama", name: "Local Ollama", color: "from-pink-500 to-rose-500", border: "border-pink-500/40" },
];

export function TrafficFlowVisualizer({ onEvent }: TrafficFlowVisualizerProps) {
  const [connected, setConnected] = useState(false);
  const [activeArm, setActiveArm] = useState<string | null>(null);
  const [events, setEvents] = useState<AuditLogEntry[]>([]);
  const [pulseKey, setPulseKey] = useState(0);
  const [stats, setStats] = useState({ requests: 0, fallbacks: 0, budgetBlocks: 0, qualityEvals: 0 });
  const eventSourceRef = useRef<EventSource | null>(null);

  useEffect(() => {
    const es = new EventSource(`${API_BASE}/api/telemetry/stream`);
    eventSourceRef.current = es;

    es.onopen = () => setConnected(true);

    es.onmessage = (evt) => {
      try {
        const data: AuditLogEntry = JSON.parse(evt.data);
        setEvents((prev) => [data, ...prev].slice(0, 15));
        setPulseKey((k) => k + 1);
        if (onEvent) onEvent(data);

        // Update active provider node based on event
        if (data.action === "ROUTING_DECISION" || data.action === "PROVIDER_FALLBACK") {
          const matched = PROVIDER_NODES.find((p) =>
            data.resource.toLowerCase().includes(p.id)
          );
          if (matched) setActiveArm(matched.id);
        }

        // Stats counter
        setStats((s) => ({
          requests: s.requests + (data.action === "GATEWAY_REQUEST" ? 1 : 0),
          fallbacks: s.fallbacks + (data.action === "PROVIDER_FALLBACK" ? 1 : 0),
          budgetBlocks: s.budgetBlocks + (data.action === "BUDGET_ENFORCEMENT" ? 1 : 0),
          qualityEvals: s.qualityEvals + (data.action === "QUALITY_EVALUATION" ? 1 : 0),
        }));
      } catch { /* ignore parse error */ }
    };

    es.onerror = () => {
      setConnected(false);
    };

    return () => {
      es.close();
    };
  }, []);

  return (
    <div className="glass rounded-2xl p-6 relative overflow-hidden">
      {/* Background radial glow */}
      <div className="absolute -right-20 -top-20 h-64 w-64 rounded-full bg-cyan/10 blur-3xl pointer-events-none" />
      <div className="absolute -left-20 -bottom-20 h-64 w-64 rounded-full bg-indigo/10 blur-3xl pointer-events-none" />

      {/* Header & Status Bar */}
      <div className="flex flex-wrap items-center justify-between gap-3 mb-6">
        <div className="flex items-center gap-2">
          <Activity className="h-5 w-5 text-cyan animate-pulse" />
          <h2 className="text-sm font-semibold tracking-tight">Real-Time Traffic Flow</h2>
        </div>

        <div className="flex items-center gap-4">
          <div className="flex items-center gap-1.5 text-xs">
            <span className={`h-2 w-2 rounded-full ${connected ? "bg-emerald animate-ping" : "bg-muted-foreground"}`} />
            <span className="font-mono text-[0.7rem] text-muted-foreground">
              {connected ? "LIVE (SSE Broadcaster)" : "CONNECTING..."}
            </span>
          </div>

          <div className="flex items-center gap-3 rounded-xl border border-[var(--glass-border)] bg-[var(--glass-hover)] px-3 py-1 text-[0.65rem] font-mono">
            <span className="text-cyan">{stats.requests} reqs</span>
            <span className="text-amber">{stats.fallbacks} fallbacks</span>
            <span className="text-indigo">{stats.qualityEvals} judge evals</span>
          </div>
        </div>
      </div>

      {/* Control Plane Visual Nodes */}
      <div className="grid gap-4 md:grid-cols-3 items-center my-6">

        {/* Client Ingress Node */}
        <motion.div
          key={`ingress-${pulseKey}`}
          animate={{ scale: [1, 1.03, 1] }}
          transition={{ duration: 0.3 }}
          className="rounded-xl border border-cyan/40 bg-cyan/5 p-4 text-center relative"
        >
          <div className="mx-auto mb-2 flex h-10 w-10 items-center justify-center rounded-xl bg-cyan/10 text-cyan">
            <Radio className="h-5 w-5" />
          </div>
          <p className="text-xs font-semibold text-cyan">Client Applications</p>
          <p className="text-[0.65rem] text-muted-foreground font-mono mt-0.5">/v1/chat/completions</p>
        </motion.div>

        {/* LinUCB Intelligent Gateway Center Engine */}
        <div className="rounded-xl border border-indigo/40 bg-indigo/5 p-5 text-center relative shadow-[0_0_30px_-8px_var(--indigo)]">
          <div className="absolute -top-2.5 left-1/2 -translate-x-1/2 rounded-full border border-indigo/30 bg-indigo/20 px-2 py-0.5 text-[0.6rem] font-mono text-indigo font-bold">
            LinUCB BANDIT
          </div>
          <div className="mx-auto my-1 flex h-12 w-12 items-center justify-center rounded-2xl bg-indigo/10 text-indigo">
            <Cpu className="h-6 w-6 animate-pulse" />
          </div>
          <p className="text-xs font-bold text-foreground">NexusAI Control Plane</p>
          <p className="text-[0.65rem] text-muted-foreground font-mono mt-0.5">Context → Policy → Reward</p>
        </div>

        {/* Provider Targets */}
        <div className="space-y-2">
          {PROVIDER_NODES.map((node) => {
            const isActive = activeArm === node.id;
            return (
              <motion.div
                key={node.id}
                animate={isActive ? { x: [0, 4, 0] } : {}}
                transition={{ duration: 0.3 }}
                className={`flex items-center justify-between p-2.5 rounded-lg border text-xs backdrop-blur-md transition-colors ${
                  isActive ? `${node.border} bg-white/5` : "border-[var(--glass-border)] bg-[var(--glass-bg)] opacity-70"
                }`}
              >
                <div className="flex items-center gap-2">
                  <span className={`h-2 w-2 rounded-full bg-gradient-to-r ${node.color}`} />
                  <span className="font-mono text-foreground font-medium">{node.name}</span>
                </div>
                {isActive && (
                  <motion.span
                    initial={{ opacity: 0, scale: 0.8 }}
                    animate={{ opacity: 1, scale: 1 }}
                    className="flex items-center gap-1 text-[0.6rem] font-mono text-emerald"
                  >
                    <CheckCircle2 className="h-3 w-3" /> ACTIVE
                  </motion.span>
                )}
              </motion.div>
            );
          })}
        </div>
      </div>

      {/* Live Event Stream Ticker */}
      <div className="mt-4 border-t border-[var(--glass-border)] pt-4">
        <p className="text-[0.65rem] uppercase tracking-wider text-muted-foreground mb-2 font-mono flex items-center gap-1">
          <Zap className="h-3 w-3 text-cyan" /> Event Broadcast Stream
        </p>
        <div className="space-y-1.5 max-h-36 overflow-y-auto font-mono text-[0.7rem]">
          <AnimatePresence initial={false}>
            {events.map((evt) => (
              <motion.div
                key={evt.id || Math.random()}
                initial={{ opacity: 0, height: 0, y: -6 }}
                animate={{ opacity: 1, height: "auto", y: 0 }}
                exit={{ opacity: 0 }}
                className="flex items-center justify-between p-1.5 rounded bg-[var(--glass-hover)]"
              >
                <div className="flex items-center gap-2 truncate">
                  <span className="text-cyan font-bold">[{evt.action}]</span>
                  <span className="text-muted-foreground truncate">{evt.resource}</span>
                </div>
                <span className="text-muted-foreground text-[0.6rem] shrink-0 ml-2">
                  {new Date(evt.timestamp).toLocaleTimeString()}
                </span>
              </motion.div>
            ))}
          </AnimatePresence>
          {events.length === 0 && (
            <p className="text-muted-foreground italic text-xs">Waiting for live traffic events…</p>
          )}
        </div>
      </div>
    </div>
  );
}
