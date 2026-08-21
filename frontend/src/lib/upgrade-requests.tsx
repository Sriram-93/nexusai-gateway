/**
 * NexusAI — Upgrade Request System
 * Allows Team Heads to request more budget / model access / rate limits from Super Admins.
 */
import { createContext, useContext, useState, useCallback, type ReactNode } from "react";
import { AnimatePresence, motion } from "motion/react";
import { X, Layers, ArrowUp, CheckCircle2, XCircle, Loader2, ChevronDown } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useToast } from "@/lib/toast";
import { useUser } from "@/lib/user-context";

export type RequestType = "MORE_BUDGET" | "UNLOCK_MODEL" | "RATE_LIMIT" | "ENTERPRISE_FEATURE";
export type RequestStatus = "PENDING" | "APPROVED" | "DENIED";

export interface UpgradeRequest {
  id: string;
  type: RequestType;
  justification: string;
  priority: "URGENT" | "STANDARD";
  status: RequestStatus;
  teamName: string;
  createdAt: string;
}

interface UpgradeRequestContextValue {
  requests: UpgradeRequest[];
  openModal: () => void;
  closeModal: () => void;
  addRequest: (r: Omit<UpgradeRequest, "id" | "status" | "createdAt" | "teamName">) => void;
  approveRequest: (id: string) => void;
  denyRequest: (id: string) => void;
  pendingCount: number;
}

const UpgradeRequestContext = createContext<UpgradeRequestContextValue | null>(null);

const REQUEST_TYPE_LABELS: Record<RequestType, string> = {
  MORE_BUDGET: "Request More Budget",
  UNLOCK_MODEL: "Unlock a Restricted Model",
  RATE_LIMIT: "Increase Rate Limit",
  ENTERPRISE_FEATURE: "Unlock Enterprise Feature",
};

export function UpgradeRequestProvider({ children }: { children: ReactNode }) {
  const [requests, setRequests] = useState<UpgradeRequest[]>([]);
  const [isOpen, setIsOpen] = useState(false);
  const { success, warning } = useToast();
  const { session, setSession } = useUser();

  const openModal = useCallback(() => setIsOpen(true), []);
  const closeModal = useCallback(() => setIsOpen(false), []);

  const addRequest = useCallback((r: Omit<UpgradeRequest, "id" | "status" | "createdAt" | "teamName">) => {
    const newReq: UpgradeRequest = {
      ...r,
      id: `req-${Date.now()}`,
      status: "PENDING",
      createdAt: new Date().toISOString(),
      teamName: session.teamName ?? "Your Team",
    };
    setRequests((prev) => [newReq, ...prev]);
    // Notify admin (in-app: increment pendingRequests in context)
    setSession({ pendingRequests: (session.pendingRequests ?? 0) + 1 });
    success("Request submitted", "Your admin has been notified and will review this shortly.");
    setIsOpen(false);
  }, [session, setSession, success]);

  const approveRequest = useCallback((id: string) => {
    setRequests((prev) => prev.map((r) => r.id === id ? { ...r, status: "APPROVED" } : r));
    const count = Math.max(0, (session.pendingRequests ?? 0) - 1);
    setSession({ pendingRequests: count });
    success("Request approved", "The team head has been notified.");
  }, [session, setSession, success]);

  const denyRequest = useCallback((id: string) => {
    setRequests((prev) => prev.map((r) => r.id === id ? { ...r, status: "DENIED" } : r));
    const count = Math.max(0, (session.pendingRequests ?? 0) - 1);
    setSession({ pendingRequests: count });
    warning("Request denied", "The team head has been notified.");
  }, [session, setSession, warning]);

  const pendingCount = requests.filter((r) => r.status === "PENDING").length;

  return (
    <UpgradeRequestContext.Provider value={{ requests, openModal, closeModal, addRequest, approveRequest, denyRequest, pendingCount }}>
      {children}
      <UpgradeRequestModal isOpen={isOpen} onClose={closeModal} onSubmit={addRequest} />
    </UpgradeRequestContext.Provider>
  );
}

export function useUpgradeRequests() {
  const ctx = useContext(UpgradeRequestContext);
  if (!ctx) throw new Error("useUpgradeRequests must be within UpgradeRequestProvider");
  return ctx;
}

function UpgradeRequestModal({
  isOpen,
  onClose,
  onSubmit,
}: {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (r: Omit<UpgradeRequest, "id" | "status" | "createdAt" | "teamName">) => void;
}) {
  const [type, setType] = useState<RequestType>("MORE_BUDGET");
  const [justification, setJustification] = useState("");
  const [priority, setPriority] = useState<"URGENT" | "STANDARD">("STANDARD");
  const [submitting, setSubmitting] = useState(false);
  const [showTypeDropdown, setShowTypeDropdown] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!justification.trim()) return;
    setSubmitting(true);
    await new Promise((r) => setTimeout(r, 800)); // Simulate API
    onSubmit({ type, justification, priority });
    setJustification("");
    setPriority("STANDARD");
    setType("MORE_BUDGET");
    setSubmitting(false);
  };

  return (
    <AnimatePresence>
      {isOpen && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-background/80 backdrop-blur-md"
          onClick={(e) => e.target === e.currentTarget && onClose()}
        >
          <motion.div
            initial={{ opacity: 0, scale: 0.93, y: 20 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.93, y: 20 }}
            transition={{ type: "spring", stiffness: 380, damping: 30 }}
            className="glass-strong w-full max-w-md rounded-2xl shadow-2xl border border-amber/20 overflow-hidden"
          >
            {/* Header */}
            <div className="flex items-center justify-between border-b border-[var(--glass-border)] bg-amber/5 px-5 py-4">
              <div className="flex items-center gap-2.5">
                <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-amber/15">
                  <ArrowUp className="h-4 w-4 text-amber" />
                </div>
                <div>
                  <p className="text-sm font-semibold">Request Upgrade</p>
                  <p className="text-[0.68rem] text-muted-foreground">Your admin will review this</p>
                </div>
              </div>
              <button onClick={onClose} className="text-muted-foreground hover:text-foreground transition-colors">
                <X className="h-4 w-4" />
              </button>
            </div>

            <form onSubmit={handleSubmit} className="p-5 space-y-4">
              {/* Type selector */}
              <div className="space-y-1.5">
                <label className="text-xs font-medium text-muted-foreground">Request Type</label>
                <div className="relative">
                  <button
                    type="button"
                    onClick={() => setShowTypeDropdown((v) => !v)}
                    className="flex w-full items-center justify-between rounded-xl border border-[var(--glass-border)] bg-[var(--glass-bg)] px-3 py-2.5 text-sm transition-colors hover:bg-[var(--glass-hover)]"
                  >
                    {REQUEST_TYPE_LABELS[type]}
                    <ChevronDown className={`h-4 w-4 text-muted-foreground transition-transform ${showTypeDropdown ? "rotate-180" : ""}`} />
                  </button>
                  <AnimatePresence>
                    {showTypeDropdown && (
                      <motion.div
                        initial={{ opacity: 0, y: -6 }}
                        animate={{ opacity: 1, y: 0 }}
                        exit={{ opacity: 0, y: -6 }}
                        className="glass-strong absolute left-0 right-0 top-full z-10 mt-1 rounded-xl border border-[var(--glass-border)] overflow-hidden shadow-xl"
                      >
                        {(Object.keys(REQUEST_TYPE_LABELS) as RequestType[]).map((t) => (
                          <button
                            key={t}
                            type="button"
                            onClick={() => { setType(t); setShowTypeDropdown(false); }}
                            className={`flex w-full items-center px-3 py-2.5 text-sm transition-colors hover:bg-[var(--glass-hover)] ${type === t ? "text-cyan" : ""}`}
                          >
                            {REQUEST_TYPE_LABELS[t]}
                          </button>
                        ))}
                      </motion.div>
                    )}
                  </AnimatePresence>
                </div>
              </div>

              {/* Justification */}
              <div className="space-y-1.5">
                <label className="text-xs font-medium text-muted-foreground">Justification</label>
                <textarea
                  value={justification}
                  onChange={(e) => setJustification(e.target.value)}
                  placeholder="Explain why your team needs this upgrade..."
                  rows={3}
                  required
                  className="w-full resize-none rounded-xl border border-[var(--glass-border)] bg-[var(--glass-bg)] px-3 py-2.5 text-sm outline-none transition-colors focus:border-amber/50 placeholder:text-muted-foreground"
                />
              </div>

              {/* Priority */}
              <div className="space-y-1.5">
                <label className="text-xs font-medium text-muted-foreground">Priority</label>
                <div className="grid grid-cols-2 gap-2">
                  {(["STANDARD", "URGENT"] as const).map((p) => (
                    <button
                      key={p}
                      type="button"
                      onClick={() => setPriority(p)}
                      className={`rounded-xl border py-2.5 text-xs font-medium transition-all duration-200 ${
                        priority === p
                          ? p === "URGENT"
                            ? "border-destructive/50 bg-destructive/10 text-destructive"
                            : "border-cyan/50 bg-cyan/10 text-cyan"
                          : "border-[var(--glass-border)] bg-[var(--glass-bg)] text-muted-foreground hover:bg-[var(--glass-hover)]"
                      }`}
                    >
                      {p === "URGENT" ? "🔴 Urgent" : "🟢 Standard"}
                    </button>
                  ))}
                </div>
              </div>

              <Button
                type="submit"
                disabled={submitting || !justification.trim()}
                className="w-full h-11 rounded-xl bg-amber text-black font-semibold text-sm hover:bg-amber/90 transition-all"
              >
                {submitting ? (
                  <span className="flex items-center gap-2"><Loader2 className="h-4 w-4 animate-spin" /> Sending...</span>
                ) : (
                  <span className="flex items-center gap-2"><ArrowUp className="h-4 w-4" /> Submit Request</span>
                )}
              </Button>
            </form>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}

/** Reusable admin panel showing all pending upgrade requests */
export function UpgradeRequestsPanel() {
  const { requests, approveRequest, denyRequest } = useUpgradeRequests();
  const pending = requests.filter((r) => r.status === "PENDING");

  if (pending.length === 0) return null;

  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      className="glass rounded-2xl border border-amber/20 overflow-hidden"
    >
      <div className="flex items-center gap-2 border-b border-[var(--glass-border)] bg-amber/5 px-5 py-3">
        <Layers className="h-4 w-4 text-amber" />
        <p className="text-sm font-semibold text-amber">Pending Upgrade Requests</p>
        <span className="ml-auto rounded-full bg-amber/20 px-2 py-0.5 text-[0.68rem] font-bold text-amber">
          {pending.length}
        </span>
      </div>
      <div className="divide-y divide-[var(--glass-border)]">
        {pending.map((r) => (
          <div key={r.id} className="flex flex-wrap items-center gap-4 px-5 py-4">
            <div className="flex-1 min-w-0">
              <p className="text-xs font-semibold">{REQUEST_TYPE_LABELS[r.type]}</p>
              <p className="text-xs text-muted-foreground mt-0.5 truncate">{r.justification}</p>
              <div className="mt-1.5 flex items-center gap-2">
                <span className="text-[0.65rem] text-muted-foreground">{r.teamName}</span>
                <span className={`text-[0.65rem] font-bold ${r.priority === "URGENT" ? "text-destructive" : "text-cyan"}`}>
                  {r.priority}
                </span>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <button
                onClick={() => approveRequest(r.id)}
                className="flex items-center gap-1.5 rounded-lg border border-emerald/30 bg-emerald/10 px-3 py-1.5 text-xs font-medium text-emerald transition-colors hover:bg-emerald/20"
              >
                <CheckCircle2 className="h-3.5 w-3.5" /> Approve
              </button>
              <button
                onClick={() => denyRequest(r.id)}
                className="flex items-center gap-1.5 rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-1.5 text-xs font-medium text-destructive transition-colors hover:bg-destructive/20"
              >
                <XCircle className="h-3.5 w-3.5" /> Deny
              </button>
            </div>
          </div>
        ))}
      </div>
    </motion.div>
  );
}
