import { motion } from "motion/react";

/** Slow glowing mesh gradients (cyan / emerald / amber) behind everything. */
export function MeshBackground({ className = "" }: { className?: string }) {
  return (
    <div
      aria-hidden
      className={`pointer-events-none fixed inset-0 z-0 overflow-hidden ${className}`}
      style={{ opacity: "var(--mesh-opacity)" }}
    >
      <motion.div
        className="absolute -left-40 -top-40 h-[46rem] w-[46rem] rounded-full blur-[140px]"
        style={{ background: "radial-gradient(circle, var(--cyan), transparent 65%)" }}
        animate={{ x: [0, 90, -30, 0], y: [0, 60, 20, 0], scale: [1, 1.12, 0.96, 1] }}
        transition={{ duration: 34, repeat: Infinity, ease: "easeInOut" }}
      />
      <motion.div
        className="absolute -right-52 top-1/4 h-[40rem] w-[40rem] rounded-full blur-[150px]"
        style={{ background: "radial-gradient(circle, var(--emerald), transparent 65%)" }}
        animate={{ x: [0, -80, 20, 0], y: [0, 70, -40, 0], scale: [1, 1.08, 1.02, 1] }}
        transition={{ duration: 42, repeat: Infinity, ease: "easeInOut" }}
      />
      <motion.div
        className="absolute bottom-[-16rem] left-1/3 h-[34rem] w-[34rem] rounded-full blur-[140px]"
        style={{ background: "radial-gradient(circle, var(--amber), transparent 68%)" }}
        animate={{ x: [0, 60, -60, 0], y: [0, -50, 10, 0], scale: [1, 1.15, 1, 1] }}
        transition={{ duration: 38, repeat: Infinity, ease: "easeInOut" }}
      />
      <div
        className="absolute inset-0 opacity-[0.25] dark:opacity-[0.18]"
        style={{
          backgroundImage:
            "linear-gradient(var(--glass-border) 1px, transparent 1px), linear-gradient(90deg, var(--glass-border) 1px, transparent 1px)",
          backgroundSize: "64px 64px",
          maskImage: "radial-gradient(ellipse at 50% 0%, black, transparent 75%)",
        }}
      />
    </div>
  );
}
