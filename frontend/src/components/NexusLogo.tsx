import type { SVGProps } from "react";

interface NexusLogoProps extends SVGProps<SVGSVGElement> {
  size?: number;
  showText?: boolean;
  textClassName?: string;
  iconOnly?: boolean;
}

export function NexusLogoIcon({ size = 32, className = "", ...props }: SVGProps<SVGSVGElement> & { size?: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 100 100"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={`shrink-0 ${className}`}
      {...props}
    >
      <defs>
        <linearGradient id="nexusGradIcon" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#00f2fe" />
          <stop offset="50%" stopColor="#3a7bd5" />
          <stop offset="100%" stopColor="#6366f1" />
        </linearGradient>
        <linearGradient id="glassBorderIcon" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="rgba(0, 242, 254, 0.6)" />
          <stop offset="100%" stopColor="rgba(99, 102, 241, 0.2)" />
        </linearGradient>
        <linearGradient id="coreGradIcon" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#ffffff" />
          <stop offset="100%" stopColor="#00f2fe" />
        </linearGradient>
      </defs>

      {/* Dark Squircle Background */}
      <rect x="2" y="2" width="96" height="96" rx="26" fill="#090d16" />
      <rect x="2" y="2" width="96" height="96" rx="26" fill="none" stroke="url(#glassBorderIcon)" strokeWidth="2" />

      {/* Stylized 'N' Monogram */}
      <path d="M 28 75 V 25 H 39 V 75 Z" fill="url(#nexusGradIcon)" opacity="0.9" />
      <path d="M 61 25 V 75 H 72 V 25 Z" fill="url(#nexusGradIcon)" opacity="0.9" />
      <path d="M 28 25 L 72 75 H 61 L 28 37 Z" fill="url(#nexusGradIcon)" />

      {/* Central Nexus Core Sphere */}
      <circle cx="50" cy="50" r="11" fill="#090d16" stroke="url(#nexusGradIcon)" strokeWidth="2.5" />
      <circle cx="50" cy="50" r="5" fill="url(#coreGradIcon)" />
    </svg>
  );
}

export function NexusLogo({
  size = 32,
  showText = true,
  className = "",
  textClassName = "",
}: NexusLogoProps) {
  return (
    <div className={`flex items-center gap-2.5 ${className}`}>
      <NexusLogoIcon size={size} />
      {showText && (
        <span className={`leading-none flex flex-col ${textClassName}`}>
          <span className="text-[0.9375rem] font-extrabold tracking-tight text-foreground flex items-center gap-0.5">
            Nexus<span className="bg-gradient-to-r from-cyan-400 via-sky-400 to-indigo-500 bg-clip-text text-transparent">AI</span>
          </span>
          <span className="text-[0.5625rem] font-semibold uppercase tracking-[0.2em] text-muted-foreground/80 mt-0.5">
            Gateway
          </span>
        </span>
      )}
    </div>
  );
}
