/**
 * NexusAI — Toast Notification System
 * Replaces all alert() and window.confirm() calls across the app.
 */
import { createContext, useContext, useState, useCallback, type ReactNode } from "react";
import { AnimatePresence, motion } from "motion/react";
import { CheckCircle2, XCircle, AlertTriangle, Info, X } from "lucide-react";

export type ToastType = "success" | "error" | "warning" | "info";

export interface Toast {
  id: string;
  type: ToastType;
  title: string;
  message?: string | undefined;
}

interface ToastContextValue {
  toast: (t: Omit<Toast, "id">) => void;
  success: (title: string, message?: string) => void;
  error: (title: string, message?: string) => void;
  warning: (title: string, message?: string) => void;
  info: (title: string, message?: string) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

const ICONS = {
  success: CheckCircle2,
  error: XCircle,
  warning: AlertTriangle,
  info: Info,
};

const COLORS = {
  success: {
    border: "border-emerald/30",
    bg: "bg-emerald/10",
    icon: "text-emerald",
    title: "text-emerald",
  },
  error: {
    border: "border-destructive/30",
    bg: "bg-destructive/10",
    icon: "text-destructive",
    title: "text-destructive",
  },
  warning: {
    border: "border-amber/30",
    bg: "bg-amber/10",
    icon: "text-amber",
    title: "text-amber",
  },
  info: {
    border: "border-cyan/30",
    bg: "bg-cyan/10",
    icon: "text-cyan",
    title: "text-cyan",
  },
};

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const remove = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const toast = useCallback((t: Omit<Toast, "id">) => {
    const id = `toast-${Date.now()}-${Math.random()}`;
    setToasts((prev) => [...prev.slice(-4), { ...t, id }]);
    setTimeout(() => remove(id), 4500);
  }, [remove]);

  const success = useCallback((title: string, message?: string) => toast({ type: "success", title, ...(message ? { message } : {}) }), [toast]);
  const error = useCallback((title: string, message?: string) => toast({ type: "error", title, ...(message ? { message } : {}) }), [toast]);
  const warning = useCallback((title: string, message?: string) => toast({ type: "warning", title, ...(message ? { message } : {}) }), [toast]);
  const info = useCallback((title: string, message?: string) => toast({ type: "info", title, ...(message ? { message } : {}) }), [toast]);

  return (
    <ToastContext.Provider value={{ toast, success, error, warning, info }}>
      {children}
      {/* Toast container */}
      <div className="fixed bottom-5 right-5 z-[100] flex flex-col gap-2.5 w-[22rem] max-w-[calc(100vw-2.5rem)]">
        <AnimatePresence initial={false}>
          {toasts.map((t) => {
            const Icon = ICONS[t.type];
            const c = COLORS[t.type];
            return (
              <motion.div
                key={t.id}
                initial={{ opacity: 0, x: 40, scale: 0.95 }}
                animate={{ opacity: 1, x: 0, scale: 1 }}
                exit={{ opacity: 0, x: 40, scale: 0.9 }}
                transition={{ type: "spring", stiffness: 380, damping: 30 }}
                className={`glass-strong flex items-start gap-3 rounded-xl border p-3.5 shadow-2xl ${c.border} ${c.bg}`}
              >
                <Icon className={`mt-0.5 h-4 w-4 shrink-0 ${c.icon}`} />
                <div className="min-w-0 flex-1">
                  <p className={`text-sm font-medium leading-snug ${c.title}`}>{t.title}</p>
                  {t.message && (
                    <p className="mt-0.5 text-xs text-muted-foreground leading-relaxed">{t.message}</p>
                  )}
                </div>
                <button
                  onClick={() => remove(t.id)}
                  className="shrink-0 text-muted-foreground transition-colors hover:text-foreground"
                >
                  <X className="h-3.5 w-3.5" />
                </button>
              </motion.div>
            );
          })}
        </AnimatePresence>
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error("useToast must be used within a ToastProvider");
  return ctx;
}
