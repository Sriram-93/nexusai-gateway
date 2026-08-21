import { Moon, Sun } from "lucide-react";
import { motion } from "motion/react";
import { useTheme } from "@/lib/theme";

export function ThemeToggle() {
  const { theme, toggle } = useTheme();
  const isDark = theme === "dark";

  return (
    <button
      type="button"
      onClick={toggle}
      aria-label={isDark ? "Switch to light mode" : "Switch to dark mode"}
      title={isDark ? "Switch to light mode" : "Switch to dark mode"}
      className="glass group relative flex h-9 w-[4.25rem] items-center rounded-full px-1 transition-colors hover:bg-[var(--glass-hover)]"
    >
      <motion.span
        layout
        transition={{ type: "spring", stiffness: 500, damping: 34 }}
        className="grad-primary flex h-7 w-7 items-center justify-center rounded-full text-primary-foreground shadow-lg"
        style={{ marginLeft: isDark ? 0 : "2.25rem" }}
      >
        <motion.span
          key={theme}
          initial={{ rotate: -90, opacity: 0 }}
          animate={{ rotate: 0, opacity: 1 }}
          transition={{ duration: 0.25 }}
        >
          {isDark ? <Moon className="h-3.5 w-3.5" /> : <Sun className="h-3.5 w-3.5" />}
        </motion.span>
      </motion.span>
    </button>
  );
}
