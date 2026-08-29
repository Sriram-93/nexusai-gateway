import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";

type ThemeMode = "dark" | "light" | "system";
type ResolvedTheme = "dark" | "light";

const ThemeContext = createContext<{ theme: ThemeMode; resolved: ResolvedTheme; setTheme: (t: ThemeMode) => void; toggle: () => void }>({
  theme: "dark",
  resolved: "dark",
  setTheme: () => {},
  toggle: () => {},
});

function getSystemTheme(): ResolvedTheme {
  if (typeof window === "undefined") return "dark";
  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<ThemeMode>("dark");
  const [resolved, setResolved] = useState<ResolvedTheme>("dark");

  useEffect(() => {
    const stored = window.localStorage.getItem("nexusai-theme");
    if (stored === "light" || stored === "dark" || stored === "system") {
      setThemeState(stored);
    }
  }, []);

  useEffect(() => {
    const r = theme === "system" ? getSystemTheme() : theme;
    setResolved(r);
    const root = document.documentElement;
    root.classList.toggle("dark", r === "dark");
    root.style.colorScheme = r;
    window.localStorage.setItem("nexusai-theme", theme);
  }, [theme]);

  // Listen for system theme changes
  useEffect(() => {
    if (theme !== "system") return;
    const mql = window.matchMedia("(prefers-color-scheme: dark)");
    const handler = () => setResolved(mql.matches ? "dark" : "light");
    mql.addEventListener("change", handler);
    return () => mql.removeEventListener("change", handler);
  }, [theme]);

  useEffect(() => {
    if (theme === "system") {
      const root = document.documentElement;
      root.classList.toggle("dark", resolved === "dark");
      root.style.colorScheme = resolved;
    }
  }, [resolved, theme]);

  const setTheme = useCallback((t: ThemeMode) => setThemeState(t), []);
  const toggle = useCallback(() => setThemeState((t) => {
    if (t === "dark") return "light";
    if (t === "light") return "dark";
    return getSystemTheme() === "dark" ? "light" : "dark";
  }), []);

  return <ThemeContext.Provider value={{ theme, resolved, setTheme, toggle }}>{children}</ThemeContext.Provider>;
}

export const useTheme = () => useContext(ThemeContext);
