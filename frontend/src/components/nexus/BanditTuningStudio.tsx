import { useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { Sliders, Save, Sparkles, CheckCircle2, RefreshCw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { dashboardApi } from "@/lib/api";
import { useToast } from "@/lib/toast";

export function BanditTuningStudio() {
  const [alpha, setAlpha] = useState<number>(0.5);
  const [updating, setUpdating] = useState(false);
  const [justSaved, setJustSaved] = useState(false);
  const { success, error } = useToast();

  const handleSave = async () => {
    setUpdating(true);
    setJustSaved(false);
    try {
      const res = await dashboardApi.updateBanditHyperparameters(alpha);
      if (res.updated) {
        success("Bandit Alpha Updated", `Exploration weight set to ${alpha.toFixed(2)}.`);
        setJustSaved(true);
        setTimeout(() => setJustSaved(false), 2500);
      } else {
        error("Update Failed", res.message || "Failed to update hyperparameter");
      }
    } catch (err: any) {
      error("Update Failed", err?.message || "Failed to save hyperparameter to backend.");
    } finally {
      setUpdating(false);
    }
  };

  return (
    <div className="glass relative overflow-hidden rounded-2xl p-6 border border-indigo-500/20 shadow-md">
      <div className="flex items-center gap-2 mb-4">
        <Sliders className="h-4 w-4 text-indigo-400" />
        <div>
          <p className="text-sm font-medium tracking-tight text-foreground">LinUCB Bandit Exploration Studio</p>
          <p className="text-xs text-muted-foreground">Adjust exploration weight (α) to balance model discovery vs performance exploitation</p>
        </div>
      </div>

      <div className="space-y-4">
        <div className="space-y-2">
          <div className="flex justify-between items-center text-xs font-mono">
            <span className="text-muted-foreground">Exploration Parameter (α):</span>
            <AnimatePresence mode="wait">
              <motion.span
                key={alpha}
                initial={{ scale: 1.3, color: "#818cf8" }}
                animate={{ scale: 1, color: "#6366f1" }}
                transition={{ duration: 0.2 }}
                className="font-bold text-sm bg-indigo-500/10 px-2 py-0.5 rounded-md border border-indigo-500/30"
              >
                {alpha.toFixed(2)}
              </motion.span>
            </AnimatePresence>
          </div>
          <input
            type="range"
            min="0.0"
            max="3.0"
            step="0.05"
            value={alpha}
            onChange={(e) => setAlpha(parseFloat(e.target.value))}
            className="w-full h-2 bg-muted rounded-lg appearance-none cursor-pointer accent-indigo-500 transition-all hover:brightness-125"
          />
          <div className="flex justify-between text-[0.65rem] text-muted-foreground font-mono">
            <span>0.0 (Pure Exploitation)</span>
            <span>1.5 (Balanced UCB)</span>
            <span>3.0 (High Exploration)</span>
          </div>
        </div>

        <div className="p-3.5 rounded-xl bg-indigo-500/5 border border-indigo-500/20 text-xs space-y-1.5">
          <p className="font-semibold flex items-center gap-1.5 text-indigo-400">
            <Sparkles className="h-3.5 w-3.5" /> Exploration Dynamics
          </p>
          <p className="text-muted-foreground text-[0.7rem] leading-relaxed">
            Lower α prioritizes proven high-performing models. Higher α forces the orchestrator to route a fraction of requests to under-explored provider arms to collect fresh latency and quality telemetry.
          </p>
        </div>

        <motion.div whileHover={{ scale: 1.01 }} whileTap={{ scale: 0.99 }}>
          <Button
            onClick={handleSave}
            disabled={updating}
            size="sm"
            className={`w-full h-9 rounded-xl text-xs gap-1.5 transition-all shadow-md ${
              justSaved
                ? "bg-emerald-600 text-white font-bold"
                : "grad-primary text-white font-bold hover:brightness-110"
            }`}
          >
            {updating ? (
              <RefreshCw className="h-3.5 w-3.5 animate-spin" />
            ) : justSaved ? (
              <CheckCircle2 className="h-3.5 w-3.5 text-white" />
            ) : (
              <Save className="h-3.5 w-3.5" />
            )}
            {updating
              ? "Updating Hyperparameters..."
              : justSaved
              ? "Hyperparameters Applied!"
              : "Apply LinUCB Hyperparameters"}
          </Button>
        </motion.div>
      </div>
    </div>
  );
}
