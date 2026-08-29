import { Moon, Sun, Monitor } from "lucide-react";
import { motion, AnimatePresence } from "motion/react";
import { useTheme } from "@/lib/theme";
import { useState, useRef, useEffect } from "react";

export function ThemeToggle() {
  const { theme, setTheme } = useTheme();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  const options = [
    { value: "light" as const, icon: Sun, label: "Light" },
    { value: "dark" as const, icon: Moon, label: "Dark" },
    { value: "system" as const, icon: Monitor, label: "System" },
  ];

  const current = options.find((o) => o.value === theme) ?? options[1]!;

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-label="Toggle theme"
        className="flex h-8 w-8 items-center justify-center rounded-lg border border-[var(--glass-border)] transition-colors hover:bg-[var(--glass-hover)]"
      >
        <current.icon className="h-3.5 w-3.5 text-muted-foreground" />
      </button>

      <AnimatePresence>
        {open && (
          <motion.div
            initial={{ opacity: 0, y: -4, scale: 0.97 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -4, scale: 0.97 }}
            transition={{ duration: 0.15 }}
            className="absolute right-0 top-full mt-1.5 w-36 rounded-xl border border-[var(--glass-border)] bg-[var(--card)] p-1 shadow-xl z-50"
          >
            {options.map((opt) => (
              <button
                key={opt.value}
                onClick={() => { setTheme(opt.value); setOpen(false); }}
                className={`flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-[0.75rem] transition-colors ${
                  theme === opt.value
                    ? "bg-primary/10 text-primary font-medium"
                    : "text-muted-foreground hover:text-foreground hover:bg-[var(--glass-hover)]"
                }`}
              >
                <opt.icon className="h-3.5 w-3.5" />
                {opt.label}
              </button>
            ))}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
