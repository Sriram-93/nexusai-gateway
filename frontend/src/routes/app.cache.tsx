import { createFileRoute } from "@tanstack/react-router";
import { motion, AnimatePresence } from "motion/react";
import { useState, useEffect } from "react";
import {
  Database, Zap, DollarSign, Gauge, Trash2, Sliders,
  RefreshCw, CheckCircle2, Flame, AlertTriangle, X,
} from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { MetricCard } from "@/components/nexus/MetricCard";
import { Button } from "@/components/ui/button";
import { cacheApi, type CacheStats } from "@/lib/api";
import { useToast } from "@/lib/toast";

export const Route = createFileRoute("/app/cache")({
  head: () => ({
    meta: [
      { title: "Prompt Cache — NexusAI" },
      { name: "description", content: "Manage and optimize exact and semantic prompt caching." },
    ],
  }),
  component: PromptCacheStudio,
});

function PromptCacheStudio() {
  const [stats, setStats] = useState<CacheStats>({
    hits: 0,
    misses: 0,
    hitRatio: 0,
    costSavedUsd: 0,
    latencySavedMs: 0,
  });
  const [flushing, setFlushing] = useState(false);
  const [showFlushConfirm, setShowFlushConfirm] = useState(false);
  const [similarityThreshold, setSimilarityThreshold] = useState(95);
  const [cacheTtlHours, setCacheTtlHours] = useState(1);
  const { success, error: toastError } = useToast();

  const loadStats = () => {
    cacheApi.getStats()
      .then(setStats)
      .catch(console.error);
  };

  useEffect(() => {
    loadStats();
    const timer = setInterval(loadStats, 4000);
    return () => clearInterval(timer);
  }, []);

  const handleFlush = () => {
    setFlushing(true);
    setShowFlushConfirm(false);
    cacheApi.flushCache()
      .then(() => {
        success("Cache Flushed", "All prompt response cache entries cleared from Redis.");
        loadStats();
      })
      .catch((e) => toastError("Flush Failed", e.message))
      .finally(() => setFlushing(false));
  };

  const hasActivity = stats.hits > 0 || stats.misses > 0;

  return (
    <AppShell
      title="Prompt Cache & Optimization Studio"
      subtitle="Ultra-fast Redis prompt response caching, cost reduction & TTL governance"
    >
      {/* Top Metrics */}
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <MetricCard
          label="Total Cost Saved"
          value={`$${stats.costSavedUsd.toFixed(4)}`}
          {...(stats.hits > 0 ? { delta: stats.hits } : {})}
          icon={DollarSign}
          tone="emerald"
          {...(hasActivity ? { spark: [0, 0, 0, 0, 0, stats.hits] } : {})}
        />
        <MetricCard
          label="Latency Saved"
          value={`${(stats.latencySavedMs / 1000).toFixed(2)}s`}
          {...(stats.hits > 0 ? { delta: stats.hits } : {})}
          icon={Gauge}
          tone="cyan"
          {...(hasActivity ? { spark: [0, 0, 0, 0, 0, stats.hits] } : {})}
        />
        <MetricCard
          label="Cache Hit Ratio"
          value={hasActivity ? `${stats.hitRatio.toFixed(1)}%` : "—"}
          icon={Zap}
          tone="amber"
          {...(hasActivity ? { spark: [stats.hitRatio] } : {})}
        />
        <MetricCard
          label="Total Cache Hits"
          value={`${stats.hits}`}
          icon={Database}
          tone="indigo"
          {...(hasActivity ? { spark: [0, stats.hits] } : {})}
        />
      </div>

      {/* Zero-data state */}
      {!hasActivity && (
        <motion.div
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          className="mt-5 section-panel"
        >
          <div className="flex flex-col items-center py-10 px-6 text-center">
            <Database className="h-10 w-10 text-muted-foreground/20 mb-4" />
            <p className="text-[0.875rem] font-medium text-muted-foreground">No cache activity yet</p>
            <p className="text-[0.75rem] text-muted-foreground/60 mt-1.5 max-w-md">
              Cache metrics will appear after the gateway serves cacheable requests. Send prompts through the Sandbox to start populating the cache.
            </p>
          </div>
        </motion.div>
      )}

      {/* Optimization Control Panel */}
      <div className="mt-5 grid gap-4 lg:grid-cols-[1.5fr_1fr]">

        {/* Left: Cache Policy Settings */}
        <div className="section-panel">
          <div className="section-panel-header">
            <div className="flex items-center gap-2">
              <Sliders className="h-4 w-4 text-cyan" />
              <div>
                <h3 className="text-[0.8125rem] font-semibold">Semantic Caching Controls</h3>
                <p className="text-[0.625rem] text-muted-foreground">Adjust similarity threshold and expiration policies</p>
              </div>
            </div>
            <Button
              onClick={() => setShowFlushConfirm(true)}
              disabled={flushing}
              variant="outline"
              size="sm"
              className="h-8 rounded-lg text-xs border-destructive/30 text-destructive hover:bg-destructive/10 gap-1.5"
            >
              {flushing ? (
                <RefreshCw className="h-3.5 w-3.5 animate-spin" />
              ) : (
                <Trash2 className="h-3.5 w-3.5" />
              )}
              Flush Cache
            </Button>
          </div>

          <div className="p-5 space-y-6">
            {/* Similarity threshold */}
            <div className="space-y-2.5">
              <div className="flex items-center justify-between text-xs">
                <div>
                  <span className="font-medium text-foreground">Similarity Threshold</span>
                  <p className="text-[0.625rem] text-muted-foreground mt-0.5">Higher = stricter matching</p>
                </div>
                <span className="font-mono text-cyan font-semibold text-sm">{similarityThreshold}%</span>
              </div>
              <input
                type="range"
                min="80"
                max="100"
                value={similarityThreshold}
                onChange={(e) => setSimilarityThreshold(Number(e.target.value))}
                className="w-full accent-[var(--cyan)] bg-[var(--surface-subtle)] h-1.5 rounded-lg cursor-pointer appearance-none"
              />
              <p className="text-[0.625rem] text-muted-foreground/60">
                Hashes combined model string and prompt body to prevent cross-provider response leakage.
              </p>
            </div>

            {/* Cache TTL */}
            <div className="space-y-2.5">
              <div className="flex items-center justify-between text-xs">
                <div>
                  <span className="font-medium text-foreground">Cache TTL Duration</span>
                  <p className="text-[0.625rem] text-muted-foreground mt-0.5">Cached responses auto-expire</p>
                </div>
                <span className="font-mono text-amber font-semibold text-sm">{cacheTtlHours}h</span>
              </div>
              <input
                type="range"
                min="1"
                max="24"
                value={cacheTtlHours}
                onChange={(e) => setCacheTtlHours(Number(e.target.value))}
                className="w-full accent-[var(--amber)] bg-[var(--surface-subtle)] h-1.5 rounded-lg cursor-pointer appearance-none"
              />
              <p className="text-[0.625rem] text-muted-foreground/60">
                Cached prompt responses automatically expire after {cacheTtlHours} hour(s) to guarantee fresh results.
              </p>
            </div>
          </div>
        </div>

        {/* Right: Live Cache Status */}
        <div className="section-panel relative overflow-hidden">
          <div className="section-panel-header">
            <div className="flex items-center gap-2">
              <Flame className="h-4 w-4 text-amber" />
              <h3 className="text-[0.8125rem] font-semibold">Redis Cache Engine</h3>
            </div>
          </div>
          <div className="p-5 space-y-4">
            <p className="text-xs text-muted-foreground leading-relaxed">
              When requests hit the gateway, NexusAI checks Redis cache in &lt;2ms. Cache hits bypass upstream LLM API calls entirely, cutting latency by 90%+ and reducing spend to zero.
            </p>

            <div className="space-y-2 font-mono text-xs">
              {[
                { label: "Total Hits", value: stats.hits, color: "text-emerald font-medium" },
                { label: "Total Misses", value: stats.misses, color: "text-amber font-medium" },
                { label: "Latency Saved", value: `~${stats.latencySavedMs}ms`, color: "text-cyan font-medium" },
                { label: "Hit Ratio", value: hasActivity ? `${stats.hitRatio.toFixed(1)}%` : "—", color: "text-foreground" },
              ].map((row) => (
                <div key={row.label} className="flex items-center justify-between p-2.5 rounded-lg bg-[var(--surface-subtle)] border border-[var(--glass-border)]">
                  <span className="text-muted-foreground">{row.label}</span>
                  <span className={row.color}>{row.value}</span>
                </div>
              ))}
            </div>

            {stats.redisActive ? (
              <div className="flex items-center gap-2 text-[0.6875rem] text-emerald font-mono pt-2 border-t border-[var(--glass-border)]">
                <CheckCircle2 className="h-3.5 w-3.5 text-emerald" /> Redis Reactive Cache Active
              </div>
            ) : (
              <div className="flex items-center gap-2 text-[0.6875rem] text-amber font-mono pt-2 border-t border-[var(--glass-border)]">
                <AlertTriangle className="h-3.5 w-3.5 text-amber" /> Redis Offline (In-Memory Fallback Active)
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Flush Confirmation Modal */}
      <AnimatePresence>
        {showFlushConfirm && (
          <div
            className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-background/80 backdrop-blur-md"
            onClick={(e) => e.target === e.currentTarget && setShowFlushConfirm(false)}
          >
            <motion.div
              initial={{ opacity: 0, scale: 0.95, y: 12 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.95, y: 12 }}
              className="section-panel w-full max-w-md shadow-2xl"
            >
              <div className="section-panel-header">
                <div className="flex items-center gap-2">
                  <AlertTriangle className="h-4 w-4 text-destructive" />
                  <p className="text-sm font-semibold">Flush Prompt Cache</p>
                </div>
                <button onClick={() => setShowFlushConfirm(false)}>
                  <X className="h-4 w-4 text-muted-foreground" />
                </button>
              </div>
              <div className="p-5 space-y-4">
                <p className="text-xs text-muted-foreground leading-relaxed">
                  This will permanently delete <strong className="text-foreground">all cached prompt responses</strong> from Redis.
                  Future requests will be routed to upstream providers until the cache is repopulated.
                </p>
                <div className="rounded-lg border border-amber/30 bg-amber/5 p-3 text-xs text-amber">
                  <strong>Warning:</strong> This action cannot be undone. Temporarily increased latency and cost are expected until the cache warms up again.
                </div>
                <div className="flex gap-2">
                  <Button onClick={() => setShowFlushConfirm(false)} variant="outline" className="flex-1 h-9 rounded-lg text-xs">
                    Cancel
                  </Button>
                  <Button onClick={handleFlush} className="flex-1 h-9 rounded-lg text-xs bg-destructive text-destructive-foreground hover:bg-destructive/90">
                    <Trash2 className="h-3.5 w-3.5 mr-1.5" /> Confirm Flush
                  </Button>
                </div>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>
    </AppShell>
  );
}
