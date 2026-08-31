import { motion } from "framer-motion";
import { Cpu, RefreshCw, Sparkles } from "lucide-react";

interface PageLoadingSkeletonProps {
  title?: string;
  subtitle?: string;
  cardsCount?: number;
}

export function PageLoadingSkeleton({
  title = "Loading Infrastructure Data...",
  subtitle = "Connecting to NexusAI Gateway cluster and verifying active model routing endpoints.",
  cardsCount = 4,
}: PageLoadingSkeletonProps) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -10 }}
      transition={{ duration: 0.25 }}
      className="space-y-6 py-2"
    >
      {/* Top Banner Loader */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-4 border-b border-border/60">
        <div className="space-y-2">
          <div className="flex items-center gap-2">
            <motion.div
              animate={{ rotate: 360 }}
              transition={{ repeat: Infinity, duration: 2, ease: "linear" }}
              className="flex h-7 w-7 items-center justify-center rounded-lg bg-cyan/15 text-cyan border border-cyan/30"
            >
              <Sparkles className="h-4 w-4" />
            </motion.div>
            <h1 className="text-xl font-bold tracking-tight text-foreground">{title}</h1>
          </div>
          <p className="text-xs text-muted-foreground">{subtitle}</p>
        </div>

        <div className="flex items-center gap-2">
          <div className="flex items-center gap-2 rounded-xl bg-card/60 border border-border px-3 py-1.5 text-xs text-cyan font-mono shadow-sm">
            <motion.div
              animate={{ rotate: 360 }}
              transition={{ repeat: Infinity, duration: 1.5, ease: "linear" }}
            >
              <RefreshCw className="h-3.5 w-3.5" />
            </motion.div>
            <span>Live Sync Active</span>
          </div>
        </div>
      </div>

      {/* Metric Cards Skeleton Grid */}
      <div className={`grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-${cardsCount} gap-4`}>
        {Array.from({ length: cardsCount }).map((_, i) => (
          <motion.div
            key={i}
            initial={{ opacity: 0, scale: 0.95 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ delay: i * 0.05, duration: 0.25 }}
            className="relative overflow-hidden rounded-2xl border border-border/80 bg-card/60 p-5 shadow-sm"
          >
            <div className="flex items-center justify-between mb-3">
              <div className="h-3.5 w-24 rounded-md bg-muted/60 animate-pulse" />
              <div className="h-7 w-7 rounded-lg bg-muted/40 animate-pulse" />
            </div>
            <div className="h-7 w-32 rounded-lg bg-muted/70 animate-pulse mb-2" />
            <div className="h-3 w-40 rounded-md bg-muted/40 animate-pulse" />

            <motion.div
              animate={{ opacity: [0.2, 0.6, 0.2] }}
              transition={{ repeat: Infinity, duration: 1.8, ease: "easeInOut", delay: i * 0.2 }}
              className="absolute inset-0 bg-gradient-to-r from-transparent via-cyan/5 to-transparent -translate-x-full animate-shimmer"
            />
          </motion.div>
        ))}
      </div>

      {/* Large Content Panel Skeleton */}
      <motion.div
        initial={{ opacity: 0, y: 15 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.2, duration: 0.3 }}
        className="rounded-3xl border border-border/80 bg-card/40 p-6 backdrop-blur-md shadow-lg space-y-4"
      >
        <div className="flex items-center justify-between border-b border-border/60 pb-4">
          <div className="flex items-center gap-3">
            <div className="h-4 w-32 rounded-md bg-muted/70 animate-pulse" />
            <div className="h-5 w-16 rounded-full bg-cyan/10 border border-cyan/20 animate-pulse" />
          </div>
          <div className="h-8 w-28 rounded-xl bg-muted/50 animate-pulse" />
        </div>

        {/* Skeleton List Items */}
        <div className="space-y-3 pt-2">
          {Array.from({ length: 4 }).map((_, idx) => (
            <div
              key={idx}
              className="flex items-center justify-between p-4 rounded-2xl bg-muted/30 border border-border/40"
            >
              <div className="flex items-center gap-3.5">
                <div className="h-9 w-9 rounded-xl bg-muted/60 animate-pulse" />
                <div className="space-y-1.5">
                  <div className="h-3.5 w-36 rounded-md bg-muted/70 animate-pulse" />
                  <div className="h-2.5 w-56 rounded-md bg-muted/40 animate-pulse" />
                </div>
              </div>
              <div className="flex items-center gap-3">
                <div className="h-6 w-16 rounded-lg bg-muted/50 animate-pulse" />
                <div className="h-6 w-20 rounded-lg bg-muted/60 animate-pulse" />
              </div>
            </div>
          ))}
        </div>
      </motion.div>
    </motion.div>
  );
}
