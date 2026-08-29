import { motion } from "motion/react";
import { TrendingUp, TrendingDown } from "lucide-react";
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
  value: string | number;
  delta?: number;
  icon: ComponentType<{ className?: string }>;
  tone?: "cyan" | "emerald" | "amber" | "indigo" | "rose";
  spark?: number[];
  className?: string;
}) {
  const up = (delta ?? 0) >= 0;
  const points = spark ?? [];
  const max = Math.max(...points, 1);
  const min = Math.min(...points, 0);
  const range = Math.max(max - min, 1);
  
  // Build smooth SVG path
  const svgW = 120;
  const svgH = 36;
  const padY = 4;
  const coords = points.map((p, i) => ({
    x: (i / Math.max(points.length - 1, 1)) * svgW,
    y: padY + (svgH - padY * 2) - ((p - min) / range) * (svgH - padY * 2),
  }));

  const linePath = coords.map((c, i) => `${i === 0 ? "M" : "L"}${c.x.toFixed(1)},${c.y.toFixed(1)}`).join(" ");
  const areaPath = linePath + ` L${svgW},${svgH} L0,${svgH} Z`;

  const toneColors: Record<string, string> = {
    cyan: "var(--cyan)",
    emerald: "var(--emerald)",
    amber: "var(--amber)",
    indigo: "var(--indigo)",
    rose: "var(--rose)",
  };
  const color = toneColors[tone] || toneColors.cyan;

  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, ease: [0.25, 1, 0.5, 1] }}
      className={`metric-card group ${className}`}
    >
      {/* Ambient glow on hover */}
      <div
        className="pointer-events-none absolute -right-12 -top-12 h-32 w-32 rounded-full opacity-0 blur-3xl transition-opacity duration-500 group-hover:opacity-40"
        style={{ background: `radial-gradient(circle, ${color}, transparent 70%)` }}
      />

      {/* Header: label + icon */}
      <div className="relative flex items-center justify-between mb-3">
        <p className="text-[0.6875rem] font-medium text-muted-foreground tracking-wide uppercase">
          {label}
        </p>
        <span
          className="flex h-8 w-8 items-center justify-center rounded-lg"
          style={{ background: `color-mix(in srgb, ${color} 14%, transparent)` }}
        >
          <Icon className={`h-4 w-4 text-${tone}`} />
        </span>
      </div>

      {/* Value */}
      <p className="relative text-[1.75rem] font-bold tracking-tight leading-none">
        {value}
      </p>

      {/* Delta */}
      {delta !== undefined && (
        <div className={`relative mt-2 flex items-center gap-1 text-[0.6875rem] font-medium ${up ? "text-emerald" : "text-rose"}`}>
          {up ? <TrendingUp className="h-3 w-3" /> : <TrendingDown className="h-3 w-3" />}
          <span>{Math.abs(delta)}% vs yesterday</span>
        </div>
      )}

      {/* Sparkline */}
      {points.length > 1 && (
        <svg
          viewBox={`0 0 ${svgW} ${svgH}`}
          className="relative mt-3 h-9 w-full"
          preserveAspectRatio="none"
        >
          <defs>
            <linearGradient id={`spark-fill-${tone}`} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={color} stopOpacity="0.2" />
              <stop offset="100%" stopColor={color} stopOpacity="0" />
            </linearGradient>
          </defs>
          <path d={areaPath} fill={`url(#spark-fill-${tone})`} />
          <path d={linePath} fill="none" stroke={color} strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" opacity="0.8" />
          {/* Endpoint dot */}
          {coords.length > 0 && (
            <circle
              cx={coords[coords.length - 1].x}
              cy={coords[coords.length - 1].y}
              r="2"
              fill={color}
              opacity="0.9"
            />
          )}
        </svg>
      )}
    </motion.div>
  );
}
