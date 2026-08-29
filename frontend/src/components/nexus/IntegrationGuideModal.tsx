import { motion } from "motion/react";
import { useState } from "react";
import { Code2, Copy, Check, Terminal, ExternalLink, X, BookOpen, Layers } from "lucide-react";
import { Button } from "@/components/ui/button";

const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080") as string;

interface IntegrationGuideModalProps {
  apiKey?: string;
  onClose: () => void;
}

type Language = "python" | "node" | "curl" | "langchain" | "llamaindex";

export function IntegrationGuideModal({ apiKey, onClose }: IntegrationGuideModalProps) {
  const [activeLang, setActiveLang] = useState<Language>("python");
  const [copied, setCopied] = useState(false);

  const activeKey = apiKey || "nx_live_YOUR_API_KEY";

  const SNIPPETS: Record<Language, { label: string; code: string; langName: string }> = {
    python: {
      label: "Python (OpenAI SDK)",
      langName: "python",
      code: `import openai

client = openai.OpenAI(
    base_url="${API_BASE}/v1",
    api_key="${activeKey}"
)

response = client.chat.completions.create(
    model="auto",  # LinUCB bandit will select optimal provider
    messages=[
        {"role": "user", "content": "Explain quantum computing in 2 sentences."}
    ],
    temperature=0.7
)

print(response.choices[0].message.content)`
    },
    node: {
      label: "Node.js / TypeScript",
      langName: "typescript",
      code: `import OpenAI from "openai";

const openai = new OpenAI({
  baseURL: "${API_BASE}/v1",
  apiKey: "${activeKey}",
});

async function main() {
  const completion = await openai.chat.completions.create({
    model: "auto",
    messages: [{ role: "user", content: "Summarize the law of thermodynamics." }],
    stream: true,
  });

  for await (const chunk of completion) {
    process.stdout.write(chunk.choices[0]?.delta?.content || "");
  }
}

main();`
    },
    curl: {
      label: "cURL Command",
      langName: "bash",
      code: `curl ${API_BASE}/v1/chat/completions \\
  -H "Authorization: Bearer ${activeKey}" \\
  -H "Content-Type: application/json" \\
  -d '{
    "model": "auto",
    "messages": [
      { "role": "user", "content": "Hello NexusAI Control Plane!" }
    ],
    "stream": false
  }'`
    },
    langchain: {
      label: "LangChain (Python)",
      langName: "python",
      code: `from langchain_openai import ChatOpenAI

llm = ChatOpenAI(
    openai_api_base="${API_BASE}/v1",
    openai_api_key="${activeKey}",
    model_name="auto"
)

result = llm.invoke("What are the core components of an AI Control Plane?")
print(result.content)`
    },
    llamaindex: {
      label: "LlamaIndex (Python)",
      langName: "python",
      code: `from llama_index.llms.openai import OpenAI

llm = OpenAI(
    api_base="${API_BASE}/v1",
    api_key="${activeKey}",
    model="auto"
)

response = llm.complete("Explain the LinUCB multi-armed bandit algorithm.")
print(response)`
    }
  };

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(SNIPPETS[activeLang].code);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch { /* noop */ }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-background/80 backdrop-blur-md"
      onClick={(e) => e.target === e.currentTarget && onClose()}
    >
      <motion.div
        initial={{ opacity: 0, scale: 0.94, y: 16 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        transition={{ type: "spring", stiffness: 380, damping: 28 }}
        className="glass-strong w-full max-w-3xl rounded-2xl shadow-2xl overflow-hidden"
      >
        {/* Header */}
        <div className="flex items-center justify-between border-b border-[var(--glass-border)] px-6 py-4">
          <div className="flex items-center gap-2">
            <BookOpen className="h-5 w-5 text-cyan" />
            <div>
              <h2 className="text-sm font-semibold">Developer Integration & SDK Guide</h2>
              <p className="text-[0.7rem] text-muted-foreground">Plug & play drop-in replacement for OpenAI SDKs</p>
            </div>
          </div>
          <button onClick={onClose} className="rounded-lg p-1 text-muted-foreground hover:bg-[var(--glass-hover)]">
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* Tab Selection */}
        <div className="flex border-b border-[var(--glass-border)] bg-[var(--glass-bg)] px-6 pt-2 overflow-x-auto gap-2">
          {(Object.keys(SNIPPETS) as Language[]).map((lang) => {
            const active = activeLang === lang;
            return (
              <button
                key={lang}
                onClick={() => setActiveLang(lang)}
                className={`pb-2.5 px-3 text-xs font-mono transition-colors relative ${
                  active ? "text-cyan font-bold" : "text-muted-foreground hover:text-foreground"
                }`}
              >
                {SNIPPETS[lang].label}
                {active && (
                  <motion.div
                    layoutId="tab-active"
                    className="absolute bottom-0 left-0 right-0 h-0.5 bg-cyan"
                  />
                )}
              </button>
            );
          })}
        </div>

        {/* Code View */}
        <div className="p-6 space-y-4">
          <div className="flex items-center justify-between">
            <span className="text-xs font-mono text-muted-foreground flex items-center gap-1.5">
              <Terminal className="h-3.5 w-3.5 text-cyan" /> Base Endpoint: <code className="text-cyan">{API_BASE}/v1</code>
            </span>

            <div className="flex items-center gap-2">
              <a
                href={`${API_BASE}/swagger-ui.html`}
                target="_blank"
                rel="noreferrer"
                className="flex items-center gap-1 text-[0.7rem] font-mono text-indigo hover:underline"
              >
                OpenAPI Swagger UI <ExternalLink className="h-3 w-3" />
              </a>

              <Button onClick={handleCopy} size="sm" className="h-8 rounded-lg text-xs grad-primary">
                {copied ? <Check className="mr-1.5 h-3.5 w-3.5" /> : <Copy className="mr-1.5 h-3.5 w-3.5" />}
                {copied ? "Copied" : "Copy Code"}
              </Button>
            </div>
          </div>

          <div className="rounded-xl border border-[var(--glass-border)] bg-black/60 p-4 font-mono text-xs overflow-x-auto">
            <pre className="text-foreground/90 leading-relaxed">
              <code>{SNIPPETS[activeLang].code}</code>
            </pre>
          </div>

          <div className="rounded-xl border border-[var(--glass-border)] bg-[var(--glass-bg)] p-3 text-[0.7rem] text-muted-foreground flex items-center justify-between">
            <span>✨ Passing <code className="text-cyan">model: "auto"</code> enables LinUCB multi-armed bandit routing with fallback cascade.</span>
            <span className="font-mono text-emerald">100% OpenAI Specification Compatible</span>
          </div>
        </div>
      </motion.div>
    </div>
  );
}
