import { motion } from "motion/react";
import { ArrowDownRight, ArrowUpRight } from "lucide-react";
import type { ComponentType } from "react";

export function MetricCard({
  label,
  value,
  delta,
  icon: Icon,
  tone = "cyan",
  spark,
  className = "",
}: {
  label: string;
  value: string;
  delta?: number;
  icon: ComponentType<{ className?: string }>;
  tone?: "cyan" | "emerald" | "amber" | "indigo";
  spark?: number[];
  className?: string;
}) {
  const up = (delta ?? 0) >= 0;
  const points = spark ?? [];
  const max = Math.max(...points, 1);
  const min = Math.min(...points, 0);
  const path = points
    .map((p, i) => {
      const x = (i / Math.max(points.length - 1, 1)) * 100;
      const y = 30 - ((p - min) / Math.max(max - min, 1)) * 26;
      return `${i === 0 ? "M" : "L"}${x.toFixed(1)},${y.toFixed(1)}`;
    })
    .join(" ");

  return (
    <motion.div
      whileHover={{ y: -6 }}
      transition={{ type: "spring", stiffness: 320, damping: 24 }}
      className={`glass group relative overflow-hidden rounded-2xl p-5 transition-colors duration-300 hover:border-[color-mix(in_oklab,var(--foreground)_22%,transparent)] ${className}`}
    >
      <div
        className="pointer-events-none absolute -right-16 -top-16 h-40 w-40 rounded-full opacity-0 blur-3xl transition-opacity duration-500 group-hover:opacity-60"
        style={{ background: `radial-gradient(circle, var(--${tone}), transparent 70%)` }}
      />
      <div className="relative flex items-start justify-between">
        <p className="text-xs uppercase tracking-[0.16em] text-muted-foreground">{label}</p>
        <span
          className="flex h-8 w-8 items-center justify-center rounded-lg"
          style={{ background: `color-mix(in oklab, var(--${tone}) 16%, transparent)` }}
        >
          <Icon className={`h-4 w-4 text-${tone}`} />
        </span>
      </div>
      <p className="relative mt-4 text-3xl font-semibold tracking-tight">{value}</p>
      {delta !== undefined && (
        <p
          className={`relative mt-1.5 flex items-center gap-1 text-xs ${up ? "text-emerald" : "text-destructive"}`}
        >
          {up ? <ArrowUpRight className="h-3.5 w-3.5" /> : <ArrowDownRight className="h-3.5 w-3.5" />}
          {Math.abs(delta)}% vs last 24h
        </p>
      )}
      {points.length > 1 && (
        <svg viewBox="0 0 100 32" className="relative mt-4 h-8 w-full" preserveAspectRatio="none">
          <path d={path} fill="none" stroke={`var(--${tone})`} strokeWidth="1.5" opacity="0.85" />
        </svg>
      )}
    </motion.div>
  );
}
