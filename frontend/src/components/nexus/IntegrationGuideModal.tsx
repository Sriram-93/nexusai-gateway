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
        className="glass-strong w-full max-w-3xl rounded-2xl shadow-2xl overflow-hidden border border-[var(--glass-border)] bg-[var(--glass-bg)]"
      >
        {/* Header */}
        <div className="flex items-center justify-between border-b border-[var(--glass-border)] px-6 py-4">
          <div className="flex items-center gap-3">
            <div className="rounded-lg bg-indigo/10 p-2 text-indigo">
              <svg viewBox="0 0 24 24" fill="currentColor" className="h-5 w-5">
                <path d="M22.2819 9.8211a5.9847 5.9847 0 0 0-.5157-4.9108 6.0462 6.0462 0 0 0-6.5098-2.9A6.0651 6.0651 0 0 0 4.9807 4.1818a5.9847 5.9847 0 0 0-3.9977 2.9 6.0462 6.0462 0 0 0 .7427 7.0966 5.98 5.98 0 0 0 .511 4.9107 6.051 6.051 0 0 0 6.5146 2.9001A5.9847 5.9847 0 0 0 13.2599 24a6.033 6.033 0 0 0 5.4806-3.5186 5.98 5.98 0 0 0 3.9929-2.9001 6.0462 6.0462 0 0 0-.4515-7.7602zM13.2599 22.4285a4.4239 4.4239 0 0 1-2.913-1.0945l.0807-.0478 6.3151-3.6496a.7153.7153 0 0 0 .3613-.6242v-7.3916l2.1287 1.2299v.081a4.5029 4.5029 0 0 1-5.9728 4.2968zM5.3217 19.3404a4.4143 4.4143 0 0 1-1.0435-2.9348v-.0809l6.3151 3.6495v7.3917a.7153.7153 0 0 0 1.0766.6241l2.1287-1.2299-6.3151-3.6496a4.4981 4.4981 0 0 1-2.1618-3.7701zM2.8715 9.0765a4.4143 4.4143 0 0 1 1.8695-2.5292l.0673.0478 6.3151 3.6496v7.3916a.7153.7153 0 0 0 .3613.6242l2.1287 1.2299v-7.3917a4.5029 4.5029 0 0 1 2.1618-5.9727zm2.4636-2.5292a4.4239 4.4239 0 0 1 2.913 1.0945l-.0807.0478-6.3151 3.6496a.7153.7153 0 0 0-.3613.6242v7.3916L.7623 18.135v-.081a4.5029 4.5029 0 0 1 5.9728-4.2968zM18.6783 4.6596a4.4143 4.4143 0 0 1 1.0435 2.9348v.0809l-6.3151-3.6495V-3.3664a.7153.7153 0 0 0-1.0766-.6241l-2.1287 1.2299 6.3151 3.6496a4.4981 4.4981 0 0 1 2.1618 3.7701zm2.45-3.3516a4.4143 4.4143 0 0 1-1.8695 2.5292l-.0673-.0478-6.3151-3.6496V-7.2318a.7153.7153 0 0 0-.3613-.6242l-2.1287-1.2299v7.3917a4.5029 4.5029 0 0 1-2.1618 5.9727zM12 15.2036a3.197 3.197 0 1 1 0-6.394 3.197 3.197 0 0 1 0 6.394z"/>
              </svg>
            </div>
            <div>
              <h2 className="text-sm font-semibold text-foreground">OpenAI Drop-In Replacement</h2>
              <p className="text-[0.7rem] text-muted-foreground">Compatible with all major AI SDKs and Frameworks</p>
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
                  active ? "text-indigo font-bold" : "text-muted-foreground hover:text-foreground"
                }`}
              >
                {SNIPPETS[lang].label}
                {active && (
                  <motion.div
                    layoutId="tab-active"
                    className="absolute bottom-0 left-0 right-0 h-0.5 bg-indigo"
                  />
                )}
              </button>
            );
          })}
        </div>

        {/* Code View */}
        <div className="p-6 space-y-4 bg-background">
          <div className="flex items-center justify-between">
            <span className="text-xs font-mono text-muted-foreground flex items-center gap-1.5">
              <Terminal className="h-3.5 w-3.5 text-indigo" /> Base Endpoint: <code className="text-indigo bg-indigo/10 px-1.5 py-0.5 rounded-md">{API_BASE}/v1</code>
            </span>

            <div className="flex items-center gap-2">
              <a
                href={`${API_BASE}/swagger-ui.html`}
                target="_blank"
                rel="noreferrer"
                className="flex items-center gap-1 text-[0.7rem] font-mono text-indigo hover:underline"
              >
                Swagger API Docs <ExternalLink className="h-3 w-3" />
              </a>

              <Button onClick={handleCopy} size="sm" className="h-8 rounded-lg text-xs grad-primary text-white">
                {copied ? <Check className="mr-1.5 h-3.5 w-3.5" /> : <Copy className="mr-1.5 h-3.5 w-3.5" />}
                {copied ? "Copied" : "Copy Code"}
              </Button>
            </div>
          </div>

          <div className="rounded-xl border border-cyan/20 bg-[#090d16] p-5 font-mono text-[0.825rem] overflow-x-auto shadow-2xl relative group">
            <div className="absolute right-4 top-3 text-[0.65rem] font-mono text-cyan/60 uppercase tracking-widest select-none">
              {SNIPPETS[activeLang].langName}
            </div>
            <pre className="text-[#e2e8f0] leading-relaxed font-mono selection:bg-cyan/30">
              <code>{SNIPPETS[activeLang].code}</code>
            </pre>
          </div>

          <div className="rounded-xl border border-indigo/20 bg-indigo/5 p-3 text-[0.75rem] text-muted-foreground flex items-center justify-between">
            <span className="flex items-center gap-1.5">
              <span className="text-indigo font-bold">✨ Pro Tip:</span> 
              Passing <code className="text-indigo font-bold">model: "auto"</code> enables LinUCB multi-armed bandit routing.
            </span>
            <span className="font-mono text-emerald font-bold bg-emerald/10 px-2 py-1 rounded-md">100% OpenAI Compatible</span>
          </div>
        </div>
      </motion.div>
    </div>
  );
}
