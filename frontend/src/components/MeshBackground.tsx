import { motion } from "motion/react";
import { useEffect, useState } from "react";

/**
 * Cinematic ambient mesh — vibrant aurora that drifts and animates.
 * Provides a dynamic, premium feel to the background.
 */
export function MeshBackground({ className = "" }: { className?: string }) {
  const [mousePos, setMousePos] = useState({ x: 0, y: 0 });

  useEffect(() => {
    const handleMouseMove = (e: MouseEvent) => {
      setMousePos({ x: e.clientX, y: e.clientY });
    };
    window.addEventListener("mousemove", handleMouseMove);
    return () => window.removeEventListener("mousemove", handleMouseMove);
  }, []);

  return (
    <div
      aria-hidden
      className={`pointer-events-none fixed inset-0 z-0 overflow-hidden bg-background ${className}`}
    >
      {/* Primary cyan — top left, large and vibrant */}
      <motion.div
        className="absolute -left-[10%] -top-[10%] h-[60vh] w-[60vh] rounded-full mix-blend-screen dark:mix-blend-lighten"
        style={{
          background: "radial-gradient(circle, color-mix(in srgb, var(--cyan) 18%, transparent), transparent 70%)",
          filter: "blur(90px)",
        }}
        animate={{
          x: [0, 120, -50, 0],
          y: [0, 80, 20, 0],
          scale: [1, 1.2, 0.9, 1],
        }}
        transition={{ duration: 25, repeat: Infinity, ease: "easeInOut" }}
      />

      {/* Secondary indigo — right side, warm accent */}
      <motion.div
        className="absolute -right-[10%] top-[30%] h-[50vh] w-[50vh] rounded-full mix-blend-screen dark:mix-blend-lighten"
        style={{
          background: "radial-gradient(circle, color-mix(in srgb, var(--indigo) 15%, transparent), transparent 70%)",
          filter: "blur(90px)",
        }}
        animate={{
          x: [0, -100, 40, 0],
          y: [0, 60, -40, 0],
          scale: [1, 1.15, 0.95, 1],
        }}
        transition={{ duration: 28, repeat: Infinity, ease: "easeInOut" }}
      />

      {/* Tertiary emerald — bottom left, subtle warmth */}
      <motion.div
        className="absolute -bottom-[20%] left-[20%] h-[60vh] w-[60vh] rounded-full mix-blend-screen dark:mix-blend-lighten"
        style={{
          background: "radial-gradient(circle, color-mix(in srgb, var(--emerald) 12%, transparent), transparent 70%)",
          filter: "blur(100px)",
        }}
        animate={{
          x: [0, 80, -80, 0],
          y: [0, -60, 20, 0],
          scale: [1, 1.1, 1, 1],
        }}
        transition={{ duration: 32, repeat: Infinity, ease: "easeInOut" }}
      />

      {/* Interactive mouse follow glow */}
      <motion.div
        className="absolute h-[40vh] w-[40vh] rounded-full mix-blend-screen dark:mix-blend-lighten"
        style={{
          background: "radial-gradient(circle, color-mix(in srgb, var(--cyan) 10%, transparent), transparent 70%)",
          filter: "blur(80px)",
        }}
        animate={{
          x: mousePos.x - window.innerWidth / 2,
          y: mousePos.y - window.innerHeight / 2,
        }}
        transition={{ type: "tween", ease: "easeOut", duration: 1.5 }}
      />

      {/* Animated Dot grid — structural texture that slowly pans */}
      <motion.div
        className="absolute inset-[-100%] opacity-[0.25] dark:opacity-[0.15]"
        style={{
          backgroundImage:
            "radial-gradient(circle, var(--muted-foreground) 1px, transparent 1px)",
          backgroundSize: "32px 32px",
          maskImage: "radial-gradient(ellipse at center, black 40%, transparent 80%)",
        }}
        animate={{
          y: [0, -32],
          x: [0, -32],
        }}
        transition={{
          duration: 4,
          repeat: Infinity,
          ease: "linear",
        }}
      />
    </div>
  );
}
