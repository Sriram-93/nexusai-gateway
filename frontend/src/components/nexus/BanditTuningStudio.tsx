import { useState } from "react";
import { Sliders, Save, Sparkles, CheckCircle2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { dashboardApi } from "@/lib/api";
import { useToast } from "@/lib/toast";

export function BanditTuningStudio() {
  const [alpha, setAlpha] = useState<number>(0.5);
  const [updating, setUpdating] = useState(false);
  const { success, error } = useToast();

  const handleSave = async () => {
    setUpdating(true);
    try {
      const res = await dashboardApi.updateBanditHyperparameters(alpha);
      if (res.updated) {
        success("Bandit Alpha Updated", `Exploration weight set to ${alpha.toFixed(2)}.`);
      } else {
        error("Update Pending", res.message);
      }
    } catch (err: any) {
      error("Failed to update hyperparameter", err.message);
    } finally {
      setUpdating(false);
    }
  };

  return (
    <div className="glass rounded-2xl p-6">
      <div className="flex items-center gap-2 mb-4">
        <Sliders className="h-4 w-4 text-indigo" />
        <div>
          <p className="text-sm font-medium tracking-tight">LinUCB Bandit Exploration Studio</p>
          <p className="text-xs text-muted-foreground">Adjust exploration weight (α) to balance model discovery vs performance exploitation</p>
        </div>
      </div>

      <div className="space-y-4">
        <div className="space-y-2">
          <div className="flex justify-between text-xs font-mono">
            <span className="text-muted-foreground">Exploration Parameter (α):</span>
            <span className="text-indigo font-bold">{alpha.toFixed(2)}</span>
          </div>
          <input
            type="range"
            min="0.0"
            max="3.0"
            step="0.05"
            value={alpha}
            onChange={(e) => setAlpha(parseFloat(e.target.value))}
            className="w-full h-1.5 bg-border rounded-lg appearance-none cursor-pointer accent-indigo"
          />
          <div className="flex justify-between text-[0.65rem] text-muted-foreground font-mono">
            <span>0.0 (Pure Exploitation)</span>
            <span>1.5 (Balanced UCB)</span>
            <span>3.0 (High Exploration)</span>
          </div>
        </div>

        <div className="p-3 rounded-xl bg-[var(--surface-subtle)] border border-[var(--glass-border)] text-xs space-y-1.5">
          <p className="font-semibold flex items-center gap-1 text-cyan">
            <Sparkles className="h-3.5 w-3.5" /> Exploration Dynamics
          </p>
          <p className="text-muted-foreground text-[0.7rem] leading-relaxed">
            Lower α prioritizes proven high-performing models. Higher α forces the orchestrator to route a fraction of requests to under-explored provider arms to collect fresh latency and quality telemetry.
          </p>
        </div>

        <Button
          onClick={handleSave}
          disabled={updating}
          size="sm"
          className="grad-primary w-full h-9 rounded-xl text-xs gap-1.5 text-foreground"
        >
          {updating ? <CheckCircle2 className="h-3.5 w-3.5 animate-spin" /> : <Save className="h-3.5 w-3.5" />}
          Apply LinUCB Hyperparameters
        </Button>
      </div>
    </div>
  );
}
