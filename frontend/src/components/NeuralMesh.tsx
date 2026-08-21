import { useEffect, useRef } from "react";

type Node = { x: number; y: number; z: number };

/**
 * Rotating 3D glowing particle mesh representing adaptive neural routing.
 * Pure canvas, no external deps. Respects prefers-reduced-motion.
 */
export function NeuralMesh({ className = "" }: { className?: string }) {
  const ref = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    const canvas = ref.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    const reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    const COUNT = 96;
    const nodes: Node[] = Array.from({ length: COUNT }, () => {
      // Fibonacci-ish sphere distribution
      const u = Math.random() * 2 - 1;
      const t = Math.random() * Math.PI * 2;
      const r = Math.sqrt(1 - u * u);
      return { x: r * Math.cos(t), y: u, z: r * Math.sin(t) };
    });

    let raf = 0;
    let angle = 0;
    let w = 0;
    let h = 0;

    const resize = () => {
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      const rect = canvas.getBoundingClientRect();
      w = rect.width;
      h = rect.height;
      canvas.width = w * dpr;
      canvas.height = h * dpr;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    };
    resize();
    window.addEventListener("resize", resize);

    const palette = ["rgba(56,232,255,", "rgba(52,231,178,", "rgba(255,196,92,"];

    const draw = () => {
      ctx.clearRect(0, 0, w, h);
      const cx = w / 2;
      const cy = h / 2;
      const radius = Math.min(w, h) * 0.36;
      angle += reduced ? 0 : 0.0022;

      const pts = nodes.map((n, i) => {
        const cos = Math.cos(angle + i * 0.0001);
        const sin = Math.sin(angle + i * 0.0001);
        const x = n.x * cos - n.z * sin;
        const z = n.x * sin + n.z * cos;
        const y = n.y * Math.cos(angle * 0.35) - z * Math.sin(angle * 0.35) * 0.25;
        const depth = (z + 1.6) / 2.6;
        return { sx: cx + x * radius, sy: cy + y * radius, depth, color: palette[i % 3] };
      });

      for (let i = 0; i < pts.length; i++) {
        const a = pts[i]!;
        for (let j = i + 1; j < pts.length; j++) {
          const b = pts[j]!;
          const d = Math.hypot(a.sx - b.sx, a.sy - b.sy);
          if (d < radius * 0.42) {
            const alpha = (1 - d / (radius * 0.42)) * 0.22 * a.depth;
            ctx.strokeStyle = `${a.color}${alpha.toFixed(3)})`;
            ctx.lineWidth = 1;
            ctx.beginPath();
            ctx.moveTo(a.sx, a.sy);
            ctx.lineTo(b.sx, b.sy);
            ctx.stroke();
          }
        }
      }


      for (const p of pts) {
        const r = 1 + p.depth * 2.4;
        ctx.beginPath();
        ctx.fillStyle = `${p.color}${(0.25 + p.depth * 0.7).toFixed(3)})`;
        ctx.shadowBlur = 16 * p.depth;
        ctx.shadowColor = `${p.color}0.8)`;
        ctx.arc(p.sx, p.sy, r, 0, Math.PI * 2);
        ctx.fill();
        ctx.shadowBlur = 0;
      }

      raf = window.requestAnimationFrame(draw);
    };
    raf = window.requestAnimationFrame(draw);

    return () => {
      window.cancelAnimationFrame(raf);
      window.removeEventListener("resize", resize);
    };
  }, []);

  return <canvas ref={ref} className={className} aria-hidden />;
}
