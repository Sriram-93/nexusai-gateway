import type { SVGProps } from "react";
import { Server } from "lucide-react";

export function OpenAiLogo({ className = "h-6 w-6", ...props }: SVGProps<SVGSVGElement> & { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" className={className} {...props}>
      <path d="M22.2819 9.8211a5.9847 5.9847 0 0 0-.5157-4.9108 6.0462 6.0462 0 0 0-6.5098-2.9A6.0651 6.0651 0 0 0 4.9807 4.1818a5.9847 5.9847 0 0 0-3.9977 2.9 6.0462 6.0462 0 0 0 .7427 7.0966 5.98 5.98 0 0 0 .511 4.9107 6.051 6.051 0 0 0 6.5146 2.9001A5.9847 5.9847 0 0 0 13.2599 24a6.0557 6.0557 0 0 0 5.7718-4.2058 5.9894 5.9894 0 0 0 3.9977-2.9001 6.0557 6.0557 0 0 0-.7475-7.0729zm-9.022 12.6081a4.4755 4.4755 0 0 1-2.8764-1.0408l.1419-.0811 4.7792-2.7582a.7948.7948 0 0 0 .3927-.6813v-6.7369l2.02 1.1686a.071.071 0 0 1 .038.052v5.5826a4.5045 4.5045 0 0 1-4.4954 4.4951zM3.6047 18.3411a4.4903 4.4903 0 0 1-.5355-3.0142l.142.0859 4.7839 2.7582a.7948.7948 0 0 0 .7854 0l5.833-3.368-2.02-1.1686a.071.071 0 0 1-.038-.0523v-5.5826a4.4903 4.4903 0 0 1 4.5-4.4954v.1616l-4.7839 2.7582a.7948.7948 0 0 0-.3927.6813v6.7369l-2.02-1.1686a.071.071 0 0 1-.038-.052v-5.5826zm1.0664-9.9723a4.4951 4.4951 0 0 1 2.3409-1.9734v5.6873a.7948.7948 0 0 0 .3927.6813l5.833 3.368-2.02 1.1686a.071.071 0 0 1-.071 0l-4.8364-2.7915a4.5045 4.5045 0 0 1-1.6392-6.1403zM18.847 14.1952a4.4951 4.4951 0 0 1-2.3409 1.9734v-5.6873a.7948.7948 0 0 0-.3927-.6813l-5.833-3.368 2.02-1.1686a.071.071 0 0 1 .071 0l4.8364 2.7915a4.5045 4.5045 0 0 1 1.6392 6.1403zm1.547-4.5375l-.142-.0859-4.7839-2.7582a.7948.7948 0 0 0-.7854 0l-5.833 3.368 2.02 1.1686a.071.071 0 0 1 .038.052v5.5826a4.4903 4.4903 0 0 1-4.5 4.4954v-.1616l4.7839-2.7582a.7948.7948 0 0 0 .3927-.6813v-6.7369l2.02 1.1686a.071.071 0 0 1 .038.052v5.5826a4.5045 4.5045 0 0 1 5.0312-1.4777z"/>
    </svg>
  );
}

export function GeminiLogo({ className = "h-6 w-6", ...props }: SVGProps<SVGSVGElement> & { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" className={className} {...props}>
      <path d="M12 24C12 17.3726 17.3726 12 24 12C17.3726 12 12 6.62742 12 0C12 6.62742 6.62742 12 0 12C6.62742 12 12 17.3726 12 24Z" fill="url(#geminiGrad)" />
      <defs>
        <linearGradient id="geminiGrad" x1="0" y1="0" x2="24" y2="24" gradientUnits="userSpaceOnUse">
          <stop stopColor="#1A73E8"/>
          <stop offset="0.5" stopColor="#8AB4F8"/>
          <stop offset="1" stopColor="#A142F4"/>
        </linearGradient>
      </defs>
    </svg>
  );
}

export function AnthropicLogo({ className = "h-6 w-6", ...props }: SVGProps<SVGSVGElement> & { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" className={className} {...props}>
      <path d="M17.416 3H13.68L22 21h3.736L17.416 3zM6.584 3L.848 21h3.736l1.39-4.47h6.052L13.416 21h3.736L11.416 3H6.584zm2.146 10.33l1.87-6.03 1.87 6.03H8.73z"/>
    </svg>
  );
}

export function GroqLogo({ className = "h-6 w-6", ...props }: SVGProps<SVGSVGElement> & { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" className={className} {...props}>
      <rect width="24" height="24" rx="5" fill="#F05A28"/>
      <path d="M13 3L5 14H11L9 21L19 9H12L13 3Z" fill="white" stroke="white" strokeWidth="0.5" strokeLinejoin="round"/>
    </svg>
  );
}

export function OllamaLogo({ className = "h-6 w-6", ...props }: SVGProps<SVGSVGElement> & { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" className={className} {...props}>
      <path d="M12 2C10.5 2 9.5 3 9 4.5L8.5 7H7C5.5 7 4.5 8 4 9.5L3.5 12V16C3.5 17.5 4.5 18.5 6 18.5H7.5V21C7.5 21.8 8.2 22.5 9 22.5S10.5 21.8 10.5 21V18.5H13.5V21C13.5 21.8 14.2 22.5 15 22.5S16.5 21.8 16.5 21V18.5H18C19.5 18.5 20.5 17.5 20.5 16V12L20 9.5C19.5 8 18.5 7 17 7H15.5L15 4.5C14.5 3 13.5 2 12 2ZM9 11C9.6 11 10 11.4 10 12C10 12.6 9.6 13 9 13C8.4 13 8 12.6 8 12C8 11.4 8.4 11 9 11ZM15 11C15.6 11 16 11.4 16 12C16 12.6 15.6 13 15 13C14.4 13 14 12.6 14 12C14 11.4 14.4 11 15 11Z"/>
    </svg>
  );
}

export function DeepSeekLogo({ className = "h-6 w-6", ...props }: SVGProps<SVGSVGElement> & { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" className={className} {...props}>
      <path d="M3 14C3 9 7 5 12 5C17 5 21 9 21 14C21 17 18.5 19.5 15.5 19.5C13.5 19.5 12.5 18.5 11.5 18.5C10.5 18.5 9.5 19.5 7.5 19.5C4.5 19.5 3 17 3 14Z" fill="#1D4ED8" />
      <circle cx="8.5" cy="11.5" r="1.5" fill="white" />
      <path d="M19 13C20.5 12 22 13 22.5 14C22 15 20.5 15.5 19 14.5V13Z" fill="#1D4ED8" />
    </svg>
  );
}

export function MistralLogo({ className = "h-6 w-6", ...props }: SVGProps<SVGSVGElement> & { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="#FF7000" className={className} {...props}>
      <rect x="3" y="3" width="3.6" height="3.6" />
      <rect x="17.4" y="3" width="3.6" height="3.6" />
      <rect x="3" y="8.4" width="3.6" height="3.6" />
      <rect x="8.4" y="8.4" width="3.6" height="3.6" />
      <rect x="12" y="8.4" width="3.6" height="3.6" />
      <rect x="17.4" y="8.4" width="3.6" height="3.6" />
      <rect x="3" y="13.8" width="3.6" height="3.6" />
      <rect x="8.4" y="13.8" width="3.6" height="3.6" />
      <rect x="12" y="13.8" width="3.6" height="3.6" />
      <rect x="17.4" y="13.8" width="3.6" height="3.6" />
      <rect x="3" y="19.2" width="18" height="3.6" />
    </svg>
  );
}

export function CohereLogo({ className = "h-6 w-6", ...props }: SVGProps<SVGSVGElement> & { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" className={className} {...props}>
      <path d="M12 3C7.02944 3 3 7.02944 3 12C3 16.9706 7.02944 21 12 21C16.9706 21 21 16.9706 21 12C21 7.02944 16.9706 3 12 3Z" fill="#39594D"/>
      <path d="M15 8C12.7909 8 11 9.79086 11 12C11 14.2091 12.7909 16 15 16C17.2091 16 19 14.2091 19 12C19 9.79086 17.2091 8 15 8Z" fill="#D1E7DD"/>
    </svg>
  );
}

export function PerplexityLogo({ className = "h-6 w-6", ...props }: SVGProps<SVGSVGElement> & { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" className={className} {...props}>
      <path d="M12 2L4 6v12l8 4 8-4V6l-8-4zm6 15.3l-6 3-6-3V7.7l6-3 6 3v9.6zM12 7L7 9.5v5L12 17l5-2.5v-5L12 7z"/>
    </svg>
  );
}

export function ProviderLogo({
  slug,
  name,
  className = "h-5 w-5",
}: {
  slug?: string;
  name?: string;
  className?: string;
}) {
  const key = (slug || name || "").toLowerCase();
  if (key.includes("openai") || key.includes("gpt")) return <OpenAiLogo className={className} />;
  if (key.includes("gemini") || key.includes("google")) return <GeminiLogo className={className} />;
  if (key.includes("anthropic") || key.includes("claude")) return <AnthropicLogo className={className} />;
  if (key.includes("groq")) return <GroqLogo className={className} />;
  if (key.includes("ollama")) return <OllamaLogo className={className} />;
  if (key.includes("deepseek")) return <DeepSeekLogo className={className} />;
  if (key.includes("mistral")) return <MistralLogo className={className} />;
  if (key.includes("cohere")) return <CohereLogo className={className} />;
  if (key.includes("perplexity")) return <PerplexityLogo className={className} />;
  return <Server className={className} />;
}
