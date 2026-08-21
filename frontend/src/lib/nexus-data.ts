export type LogRow = {
  id: string;
  ts: string;
  provider: string;
  model: string;
  latency: number;
  tokens: number;
  cost: number;
  status: "success" | "fail";
};

const PROVIDERS: Array<[string, string]> = [
  ["Google", "gemini-2.5-pro"],
  ["Groq", "llama-3.3-70b"],
  ["OpenAI", "gpt-4.1-mini"],
  ["Anthropic", "claude-sonnet-4"],
  ["Groq", "mixtral-8x7b"],
];

let seed = 42;
function rnd() {
  seed = (seed * 1664525 + 1013904223) % 4294967296;
  return seed / 4294967296;
}

export function makeLog(offsetMs = 0): LogRow {
  const pair = PROVIDERS[Math.floor(rnd() * PROVIDERS.length)]!;
  const d = new Date(Date.now() - offsetMs);
  return {
    id: Math.random().toString(36).slice(2, 10),
    ts: d.toISOString().slice(11, 23),
    provider: pair[0],
    model: pair[1],
    latency: Math.round(9 + rnd() * 180),
    tokens: Math.round(180 + rnd() * 4200),
    cost: Number((rnd() * 0.019).toFixed(4)),
    status: rnd() > 0.07 ? "success" : "fail",
  };
}

export const initialLogs = (n = 12) => Array.from({ length: n }, (_, i) => makeLog(i * 1400));

export const throughputSeries = Array.from({ length: 24 }, (_, i) => ({
  t: `${String(i).padStart(2, "0")}:00`,
  gemini: Math.round(240 + Math.sin(i / 2) * 90 + i * 6),
  groq: Math.round(180 + Math.cos(i / 3) * 70 + i * 4),
  latency: Math.round(20 + Math.sin(i / 4) * 8),
}));

export function newApiKey() {
  const body = Array.from({ length: 32 }, () =>
    "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".charAt(
      Math.floor(Math.random() * 62),
    ),
  ).join("");
  return `nx_live_${body}`;
}
