import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { motion, AnimatePresence } from "motion/react";
import { useEffect, useState, useMemo } from "react";
import {
  CircleCheck, CircleSlash, Plus, RefreshCw, Server, X, Key,
  Loader2, Globe, Zap, Check, Cpu, Activity, ShieldCheck, Sparkles,
  Search, SlidersHorizontal, AlertTriangle, ExternalLink, HelpCircle, ChevronRight, Layers, ArrowUpRight
} from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { Switch } from "@/components/ui/switch";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { providersApi, getBaseUrl, authFetch, type ProviderSummary, type ModelSummary } from "@/lib/api";
import { useToast } from "@/lib/toast";
import { useUser } from "@/lib/user-context";
import { useUpgradeRequests } from "@/lib/upgrade-requests";
import { ProviderLogo } from "@/components/ProviderLogos";
import { PageLoadingSkeleton } from "@/components/nexus/PageLoadingSkeleton";

export const Route = createFileRoute("/app/providers")({
  head: () => ({
    meta: [
      { title: "Provider Hub — NexusAI Gateway" },
      { name: "description", content: "Manage upstream AI providers, BYOK credentials, and model selection." },
    ],
  }),
  component: Providers,
});

type ProviderType = "OPENAI_COMPATIBLE" | "GOOGLE" | "ANTHROPIC" | "AWS_BEDROCK" | "AZURE_OPENAI" | "OLLAMA";

interface ProviderCatalogEntry {
  slug: string;
  label: string;
  badge: string;
  type: ProviderType;
  category: "popular" | "cloud" | "opensource" | "media";
  color: string;
  baseUrl?: string;
  keyPrefix?: string;
  portalUrl?: string;
  description: string;
  steps: string[];
}

// Famous / Popular providers highlighted by default
const POPULAR_SLUGS = [
  "groq", "gemini", "openai", "anthropic", "deepseek",
  "openrouter", "mistral", "perplexity", "ollama", "bedrock",
  "azure", "together_ai", "fireworks_ai", "cerebras", "cohere", "xai"
];

// Catalogue of 130+ Providers supported by NexusAI Gateway & LiteLLM
const FULL_PROVIDER_CATALOG: ProviderCatalogEntry[] = [
  {
    slug: "groq",
    label: "Groq Cloud",
    badge: "GROQ",
    type: "OPENAI_COMPATIBLE",
    category: "popular",
    color: "#f97316",
    baseUrl: "https://api.groq.com/openai/v1",
    keyPrefix: "gsk_...",
    portalUrl: "https://console.groq.com/keys",
    description: "Ultra-fast LPU inference for Llama 3.3, DeepSeek-R1, and Qwen models.",
    steps: [
      "Log in to Groq Console at console.groq.com",
      "Navigate to API Keys section and click 'Create API Key'",
      "Copy your key starting with 'gsk_' and paste it below",
      "Click 'Verify & Save Key' to load working models"
    ]
  },
  {
    slug: "gemini",
    label: "Google Gemini",
    badge: "GEMINI",
    type: "GOOGLE",
    category: "popular",
    color: "#4285f4",
    keyPrefix: "AIzaSy...",
    portalUrl: "https://aistudio.google.com/app/apikey",
    description: "Google Gemini 2.5 Flash, 1.5 Pro, Thinking, and multimodal vision models.",
    steps: [
      "Visit Google AI Studio at aistudio.google.com/app/apikey",
      "Click 'Create API Key' in a new or existing GCP project",
      "Copy your API key (starts with 'AIzaSy')",
      "Paste below and click 'Verify & Save Key'"
    ]
  },
  {
    slug: "openai",
    label: "OpenAI",
    badge: "OPENAI",
    type: "OPENAI_COMPATIBLE",
    category: "popular",
    color: "#10a37f",
    baseUrl: "https://api.openai.com/v1",
    keyPrefix: "sk-proj-...",
    portalUrl: "https://platform.openai.com/api-keys",
    description: "Industry standard GPT-4o, GPT-4o-mini, o1, o3-mini reasoning models.",
    steps: [
      "Open OpenAI Developer Platform at platform.openai.com/api-keys",
      "Click 'Create new secret key'",
      "Copy key starting with 'sk-proj-' or 'sk-'",
      "Paste below and click 'Verify & Save Key'"
    ]
  },
  {
    slug: "anthropic",
    label: "Anthropic Claude",
    badge: "CLAUDE",
    type: "ANTHROPIC",
    category: "popular",
    color: "#d97706",
    keyPrefix: "sk-ant-...",
    portalUrl: "https://console.anthropic.com/settings/keys",
    description: "State-of-the-art coding and reasoning with Claude 3.5 Sonnet & Haiku.",
    steps: [
      "Access Anthropic Console at console.anthropic.com",
      "Go to Settings → API Keys",
      "Generate a key starting with 'sk-ant-'",
      "Paste below and click 'Verify & Save Key'"
    ]
  },
  {
    slug: "deepseek",
    label: "DeepSeek AI",
    badge: "DEEPSEEK",
    type: "OPENAI_COMPATIBLE",
    category: "popular",
    color: "#1d4ed8",
    baseUrl: "https://api.deepseek.com/v1",
    keyPrefix: "sk-...",
    portalUrl: "https://platform.deepseek.com/api_keys",
    description: "DeepSeek-V3 & DeepSeek-R1 reasoning models at fraction of traditional cost.",
    steps: [
      "Log in to DeepSeek Platform at platform.deepseek.com",
      "Navigate to API Keys and click 'Create API Key'",
      "Copy your key and paste it below",
      "Click 'Verify & Save Key' to activate DeepSeek routing"
    ]
  },
  {
    slug: "openrouter",
    label: "OpenRouter",
    badge: "OPENROUTER",
    type: "OPENAI_COMPATIBLE",
    category: "popular",
    color: "#6366f1",
    baseUrl: "https://openrouter.ai/api/v1",
    keyPrefix: "sk-or-v1-...",
    portalUrl: "https://openrouter.ai/keys",
    description: "Unified marketplace access to 300+ models via single API key.",
    steps: [
      "Visit OpenRouter Dashboard at openrouter.ai/keys",
      "Click 'Create Key' and set credit limits if desired",
      "Copy key starting with 'sk-or-v1-'",
      "Paste key below and verify connection"
    ]
  },
  {
    slug: "mistral",
    label: "Mistral AI",
    badge: "MISTRAL",
    type: "OPENAI_COMPATIBLE",
    category: "popular",
    color: "#ff7000",
    baseUrl: "https://api.mistral.ai/v1",
    keyPrefix: "...",
    portalUrl: "https://console.mistral.ai/api-keys/",
    description: "Mistral Large, Codestral, Pixtral, and Embed models.",
    steps: [
      "Open Mistral Console at console.mistral.ai",
      "Navigate to API Keys section",
      "Create a secret API Key",
      "Paste below and save credentials"
    ]
  },
  {
    slug: "perplexity",
    label: "Perplexity AI",
    badge: "PERPLEXITY",
    type: "OPENAI_COMPATIBLE",
    category: "popular",
    color: "#22c55e",
    baseUrl: "https://api.perplexity.ai",
    keyPrefix: "pplx-...",
    portalUrl: "https://www.perplexity.ai/settings/api",
    description: "Perplexity Sonar & Sonar Reasoning models with live web grounding.",
    steps: [
      "Go to Perplexity Settings → API at perplexity.ai/settings/api",
      "Generate an API Key (starts with 'pplx-')",
      "Paste below and save key"
    ]
  },
  {
    slug: "ollama",
    label: "Ollama (Local)",
    badge: "OLLAMA",
    type: "OPENAI_COMPATIBLE",
    category: "opensource",
    color: "#a855f7",
    baseUrl: "http://localhost:11434/v1",
    portalUrl: "https://ollama.com",
    description: "Run Llama 3, DeepSeek-R1, Mistral locally on your machine zero key needed.",
    steps: [
      "Ensure Ollama is running locally (`ollama serve`)",
      "Default Base URL is `http://localhost:11434/v1`",
      "No API key required — click 'Verify & Save' to discover local models"
    ]
  },
  {
    slug: "bedrock",
    label: "AWS Bedrock",
    badge: "BEDROCK",
    type: "AWS_BEDROCK",
    category: "cloud",
    color: "#ff9900",
    portalUrl: "https://console.aws.amazon.com/bedrock/",
    description: "Enterprise managed AWS models (Claude, Llama 3, Amazon Nova, Titan).",
    steps: [
      "Log in to AWS Console and navigate to Amazon Bedrock",
      "Ensure Model Access is enabled for requested models",
      "Provide AWS Access Key ID and Secret Access Key below",
      "Set your target AWS region (e.g. us-east-1)"
    ]
  },
  {
    slug: "azure",
    label: "Azure OpenAI",
    badge: "AZURE",
    type: "AZURE_OPENAI",
    category: "cloud",
    color: "#0078d4",
    portalUrl: "https://portal.azure.com/",
    description: "Dedicated enterprise Azure OpenAI deployment with private SLA.",
    steps: [
      "Go to Azure Portal → Cognitive Services → Azure OpenAI",
      "Copy your Resource Endpoint URL (`https://your-resource.openai.azure.com`)",
      "Copy API Key under 'Keys and Endpoint'",
      "Paste Endpoint & Key below"
    ]
  },
  {
    slug: "together_ai",
    label: "Together AI",
    badge: "TOGETHER",
    type: "OPENAI_COMPATIBLE",
    category: "opensource",
    color: "#0ea5e9",
    baseUrl: "https://api.together.ai/v1",
    portalUrl: "https://api.together.ai/settings/api-keys",
    description: "High-speed open source model hosting for Llama, Qwen, DeepSeek.",
    steps: [
      "Log in to Together AI at api.together.ai",
      "Go to Settings → API Keys",
      "Copy your key and paste it below"
    ]
  },
  {
    slug: "fireworks_ai",
    label: "Fireworks AI",
    badge: "FIREWORKS",
    type: "OPENAI_COMPATIBLE",
    category: "opensource",
    color: "#ef4444",
    baseUrl: "https://api.fireworks.ai/inference/v1",
    portalUrl: "https://fireworks.ai/account/api-keys",
    description: "Sub-second inference acceleration for open LLMs and fine-tunes.",
    steps: [
      "Visit Fireworks AI Console at fireworks.ai/account/api-keys",
      "Generate an API token",
      "Paste below and verify connection"
    ]
  },
  {
    slug: "cerebras",
    label: "Cerebras Cloud",
    badge: "CEREBRAS",
    type: "OPENAI_COMPATIBLE",
    category: "popular",
    color: "#ec4899",
    baseUrl: "https://api.cerebras.ai/v1",
    portalUrl: "https://cloud.cerebras.ai/platform",
    description: "2000+ tokens/sec hardware accelerated wafer-scale Llama 3 inference.",
    steps: [
      "Access Cerebras Cloud Platform at cloud.cerebras.ai",
      "Create API key in settings",
      "Paste below and click 'Verify & Save'"
    ]
  },
  {
    slug: "cohere",
    label: "Cohere",
    badge: "COHERE",
    type: "OPENAI_COMPATIBLE",
    category: "popular",
    color: "#10b981",
    baseUrl: "https://api.cohere.com/v1",
    portalUrl: "https://dashboard.cohere.com/api-keys",
    description: "Command R+ enterprise models, multilingual text generation & embeddings.",
    steps: [
      "Log in to Cohere Dashboard at dashboard.cohere.com",
      "Copy Trial or Production API Key",
      "Paste below and save key"
    ]
  },
  {
    slug: "xai",
    label: "xAI Grok",
    badge: "XAI",
    type: "OPENAI_COMPATIBLE",
    category: "popular",
    color: "#64748b",
    baseUrl: "https://api.x.ai/v1",
    portalUrl: "https://console.x.ai/",
    description: "xAI Grok-2 and Grok Vision models.",
    steps: [
      "Open xAI Console at console.x.ai",
      "Generate an API Key",
      "Paste below to connect Grok models"
    ]
  },
  {
    slug: "replicate",
    label: "Replicate",
    badge: "REPLICATE",
    type: "OPENAI_COMPATIBLE",
    category: "media",
    color: "#3b82f6",
    portalUrl: "https://replicate.com/account/api-tokens",
    description: "Cloud execution for open-source AI models, SDXL, Flux, and custom fine-tunes.",
    steps: ["Get API Token from replicate.com/account/api-tokens", "Paste below"]
  },
  {
    slug: "fal_ai",
    label: "FAL AI",
    badge: "FAL",
    type: "OPENAI_COMPATIBLE",
    category: "media",
    color: "#8b5cf6",
    portalUrl: "https://fal.ai/dashboard/keys",
    description: "Fast generative media APIs for Flux, Whispers, and multimodal models.",
    steps: ["Get FAL API Key from fal.ai/dashboard/keys", "Paste below"]
  },
  {
    slug: "deepinfra",
    label: "DeepInfra",
    badge: "DEEPINFRA",
    type: "OPENAI_COMPATIBLE",
    category: "opensource",
    color: "#06b6d4",
    baseUrl: "https://api.deepinfra.com/v1/openai",
    portalUrl: "https://deepinfra.com/dash/api_keys",
    description: "Pay-as-you-go inference for open source models.",
    steps: ["Create API Key on deepinfra.com/dash/api_keys", "Paste below"]
  },
  {
    slug: "novita",
    label: "Novita AI",
    badge: "NOVITA",
    type: "OPENAI_COMPATIBLE",
    category: "opensource",
    color: "#f43f5e",
    baseUrl: "https://api.novita.ai/v3/openai",
    portalUrl: "https://novita.ai/dashboard/key",
    description: "Serverless LLM APIs and Stable Diffusion image generation.",
    steps: ["Copy Key from novita.ai/dashboard/key", "Paste below"]
  },
  {
    slug: "cloudflare",
    label: "Cloudflare Workers AI",
    badge: "CLOUDFLARE",
    type: "OPENAI_COMPATIBLE",
    category: "cloud",
    color: "#f97316",
    portalUrl: "https://dash.cloudflare.com/",
    description: "Edge serverless AI inference on Cloudflare global network.",
    steps: ["Obtain Workers AI API token from Cloudflare Dashboard", "Paste token below"]
  }
];

// All 130 Supported Provider Slugs in LiteLLM / NexusAI Gateway specification
const ALL_130_SLUGS = [
  "agentcore", "ai21", "aiml", "amazon_nova", "anthropic", "anyscale", "apiserpent", "assemblyai",
  "aws_polly", "azure", "azure_ai", "azure_text", "baseten", "bedrock", "bedrock_converse",
  "bedrock_mantle", "bing_grounding", "black_forest_labs", "cerebras", "chatgpt", "claude",
  "cloudflare", "codestral", "cognition", "cohere", "cohere_chat", "crusoe", "darkbloom",
  "dashscope", "databricks", "dataforseo", "deepgram", "deepinfra", "deepseek", "duckduckgo",
  "elevenlabs", "exa_ai", "fal_ai", "featherless_ai", "firecrawl", "fireworks_ai",
  "friendliai", "gemini", "gigachat", "github_copilot", "gmi", "google_pse", "gradient_ai",
  "groq", "heroku", "hyperbolic", "inception", "jina_ai", "lambda_ai", "lemonade", "libertai",
  "linkup", "llamagate", "meta", "meta_llama", "minimax", "mistral", "moonshot", "morph",
  "nebius", "nimble", "nlp_cloud", "novita", "nscale", "nvidia_nim", "oci", "ollama",
  "openai", "openrouter", "ovhcloud", "palm", "parallel_ai", "perplexity", "pinstripes",
  "publicai", "recraft", "reducto", "replicate", "runwayml", "sagemaker", "sambanova",
  "sarvam", "scaleway", "scx-ai", "searxng", "serper", "snowflake", "soniox", "stability",
  "tavily", "tencent", "tensormesh", "tinyfish", "together_ai", "v0", "vercel_ai_gateway",
  "vertex_ai", "vertex_ai-anthropic_models", "vertex_ai-deepseek_models", "vertex_ai-llama_models",
  "vertex_ai-mistral_models", "vertex_ai-qwen_models", "volcengine", "voyage", "wandb",
  "watsonx", "xai", "you_com", "zai"
];

function formatSlugToLabel(slug: string): string {
  if (slug === "github_copilot") return "GitHub Copilot";
  if (slug === "nvidia_nim") return "NVIDIA NIM";
  if (slug === "elevenlabs") return "ElevenLabs Voice";
  if (slug === "assemblyai") return "AssemblyAI Transcribe";
  if (slug === "dashscope") return "Alibaba DashScope (Qwen)";
  if (slug === "minimax") return "MiniMax AI";
  if (slug === "moonshot") return "Moonshot Kimi AI";
  if (slug === "sambanova") return "SambaNova Systems";
  if (slug === "volcengine") return "ByteDance Volcengine";
  if (slug === "black_forest_labs") return "Black Forest Labs (Flux)";
  if (slug === "ai21") return "AI21 Labs (Jamba)";
  if (slug === "hyperbolic") return "Hyperbolic AI";
  if (slug === "nebius") return "Nebius AI Cloud";
  if (slug === "databricks") return "Databricks Mosaic AI";
  if (slug === "searxng") return "SearXNG Web Search";
  if (slug === "tavily") return "Tavily Search API";
  if (slug === "vertex_ai") return "Google Vertex AI";
  if (slug.startsWith("vertex_ai-")) {
    const sub = slug.replace("vertex_ai-", "").replace("_models", "").replace("-", " ");
    return `Google Vertex AI (${sub.charAt(0).toUpperCase() + sub.slice(1)})`;
  }
  return slug
    .replace(/_/g, " ")
    .replace(/-/g, " ")
    .replace(/\b\w/g, (c) => c.toUpperCase());
}

// Helper to fill in entry defaults & developer setup instructions for all 130 providers
function getCatalogEntry(slug: string, displayName?: string): ProviderCatalogEntry {
  const found = FULL_PROVIDER_CATALOG.find((c) => c.slug.toLowerCase() === slug.toLowerCase());
  if (found) return found;

  const title = displayName || formatSlugToLabel(slug);
  const isCloud = slug.includes("vertex") || slug.includes("azure") || slug.includes("sagemaker") || slug.includes("oci") || slug.includes("databricks");
  const isMedia = slug.includes("fal") || slug.includes("runway") || slug.includes("replicate") || slug.includes("elevenlabs") || slug.includes("stability") || slug.includes("recraft") || slug.includes("black_forest");

  let portalUrl: string | undefined = undefined;
  let keyPrefix = "API Key / Auth Token";
  let baseUrl: string | undefined = undefined;

  if (slug.includes("github")) {
    portalUrl = "https://github.com/settings/tokens";
    keyPrefix = "ghp_... or github_pat_...";
    baseUrl = "https://models.inference.ai.azure.com";
  } else if (slug.includes("nvidia")) {
    portalUrl = "https://build.nvidia.com/";
    keyPrefix = "nvapi-...";
    baseUrl = "https://integrate.api.nvidia.com/v1";
  } else if (slug.includes("dashscope")) {
    portalUrl = "https://dashscope.console.aliyun.com/";
    keyPrefix = "sk-...";
    baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
  } else if (slug.includes("moonshot")) {
    portalUrl = "https://platform.moonshot.cn/";
    keyPrefix = "sk-...";
    baseUrl = "https://api.moonshot.cn/v1";
  } else if (slug.includes("minimax")) {
    portalUrl = "https://platform.minimaxi.com/";
    baseUrl = "https://api.minimax.chat/v1";
  } else if (slug.includes("sambanova")) {
    portalUrl = "https://cloud.sambanova.ai/";
    baseUrl = "https://api.sambanova.ai/v1";
  } else if (slug.includes("nebius")) {
    portalUrl = "https://nebius.ai/";
    baseUrl = "https://api.studio.nebius.ai/v1";
  } else if (slug.includes("hyperbolic")) {
    portalUrl = "https://app.hyperbolic.xyz/";
    baseUrl = "https://api.hyperbolic.xyz/v1";
  } else if (slug.includes("ai21")) {
    portalUrl = "https://studio.ai21.com/";
    baseUrl = "https://api.ai21.com/studio/v1";
  } else if (slug.includes("volcengine")) {
    portalUrl = "https://console.volcengine.com/ark";
    baseUrl = "https://ark.cn-beijing.volces.com/api/v3";
  } else if (slug.includes("tavily")) {
    portalUrl = "https://tavily.com/";
    keyPrefix = "tvly-...";
  } else if (slug.includes("elevenlabs")) {
    portalUrl = "https://elevenlabs.io/app/settings/api-keys";
  } else if (slug.includes("assemblyai")) {
    portalUrl = "https://www.assemblyai.com/app/account";
  } else if (slug.includes("voyage")) {
    portalUrl = "https://dash.voyageai.com/api-keys";
    keyPrefix = "pa-...";
  } else if (slug.includes("stability")) {
    portalUrl = "https://platform.stability.ai/account/keys";
    keyPrefix = "sk-...";
  } else if (slug.includes("anyscale")) {
    portalUrl = "https://console.anyscale.com/";
    keyPrefix = "ese_...";
    baseUrl = "https://api.endpoints.anyscale.com/v1";
  } else if (slug.includes("databricks")) {
    portalUrl = "https://docs.databricks.com/";
    keyPrefix = "dapi...";
  }

  return {
    slug: slug.toLowerCase(),
    label: title,
    badge: slug.replace(/[^a-zA-Z0-9]/g, "").slice(0, 8).toUpperCase(),
    type: slug.includes("bedrock") ? "AWS_BEDROCK" : slug.includes("azure") ? "AZURE_OPENAI" : slug.includes("gemini") ? "GOOGLE" : slug.includes("claude") || slug.includes("anthropic") ? "ANTHROPIC" : "OPENAI_COMPATIBLE",
    category: isCloud ? "cloud" : isMedia ? "media" : "opensource",
    color: "#0284c7",
    baseUrl,
    keyPrefix,
    portalUrl,
    description: `Enterprise AI provider configuration endpoint for ${title}.`,
    steps: [
      portalUrl ? `Log in to ${title} Developer Console at ${portalUrl}` : `Open developer settings for ${title}`,
      `Copy your active API Key / Auth Token (${keyPrefix})`,
      baseUrl ? `Confirm Base URL is prefilled as '${baseUrl}'` : `Enter your credentials below and click 'Verify & Save Key'`,
      `The gateway performs an instant endpoint ping test to discover ready models.`
    ]
  };
}

const COMPLETE_130_CATALOG: ProviderCatalogEntry[] = ALL_130_SLUGS.map((slug) => {
  return getCatalogEntry(slug);
}).sort((a, b) => a.label.localeCompare(b.label));

function Providers() {
  const [providers, setProviders] = useState<ProviderSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showAddModal, setShowAddModal] = useState(false);
  const [selectedProviderSlug, setSelectedProviderSlug] = useState<string | null>(null);
  const [editingKeySlug, setEditingKeySlug] = useState<string | null>(null);
  const [addModalInitialSlug, setAddModalInitialSlug] = useState<string | null>(null);
  const [catalogSearch, setCatalogSearch] = useState("");
  const [activeCategory, setActiveCategory] = useState<"all" | "popular" | "cloud" | "opensource">("all");

  const { success, error: toastError, info } = useToast();
  const { session } = useUser();
  const navigate = useNavigate();
  const { openModal: openUpgradeModal } = useUpgradeRequests();
  const isTeamHead = session.role === "TEAM_HEAD";
  const role = session.role ?? "SOLO";

  const [testingProviderSlug, setTestingProviderSlug] = useState<string | null>(null);

  const handleTestAndLoadProvider = async (slug: string, displayName: string) => {
    setTestingProviderSlug(slug);
    info(`Testing ${displayName}...`, `Pinging candidate models to verify working endpoints...`);
    try {
      const res = await authFetch(`/api/providers/${slug}/test-and-load`, { method: "POST" });
      const data = await res.json();
      if (data.status === "SUCCESS") {
        success(`${displayName} Verified`, `Loaded ${data.totalActive} active working models (${data.verifiedWorkingModels?.join(", ") || "none"}).`);
      } else if (data.status === "MISSING_KEY") {
        toastError("API Key Missing", `No valid API key configured for ${displayName}. Please add an API key first.`);
      } else {
        toastError("Verification Failed", data.message || data.error || "Failed to load models");
      }
      await load();
    } catch (err: any) {
      toastError("Execution Error", err.message);
    } finally {
      setTestingProviderSlug(null);
    }
  };

  const handleTestAndLoadAll = async () => {
    setTestingProviderSlug("ALL");
    info("Testing All Providers...", "Verifying models across all configured AI providers...");
    try {
      const res = await authFetch(`/api/providers/test-and-load-all`, { method: "POST" });
      const data = await res.json();
      if (data.status === "SUCCESS") {
        success("All Providers Tested", "Completed live model verification across all upstream providers.");
      } else {
        toastError("Verification Warning", data.message || "Partial provider verification");
      }
      await load();
    } catch (err: any) {
      toastError("Execution Error", err.message);
    } finally {
      setTestingProviderSlug(null);
    }
  };

  useEffect(() => {
    if (role !== "ORG_ADMIN" && role !== "SOLO" && role !== "SUPER_ADMIN" && role !== "OWNER") {
      navigate({ to: "/app" });
    }
  }, [role, navigate]);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await providersApi.listProviders();
      setProviders(data);
    } catch (err: any) {
      setError(err.message ?? "Failed to load providers");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const toggleEnabled = async (slug: string, newEnabled: boolean) => {
    try {
      await providersApi.setEnabled(slug, newEnabled);
      setProviders((prev) => prev.map((p) => (p.slug === slug ? { ...p, enabled: newEnabled } : p)));
      success(newEnabled ? "Provider Enabled" : "Provider Disabled", `Routing policy updated for ${slug}.`);
    } catch (err: any) {
      toastError("Update Failed", err.message);
    }
  };

  const handleUpdateKey = async (slug: string, apiKey: string) => {
    try {
      await providersApi.updateCredentials(slug, apiKey);
      success("Credentials Saved", `${slug} is now live and ready for traffic.`);
      setEditingKeySlug(null);
      load();
    } catch (err: any) {
      toastError("Update Failed", err.message);
    }
  };

  // Compute stats and partition providers (Configured / Active pinned at top)
  const totalModelsActive = providers.reduce((acc, p) => acc + (p.hasKey && p.enabled ? p.enabledModelCount : 0), 0);
  const activeProvidersCount = providers.filter((p) => p.enabled && p.hasKey).length;
  const activeProvider = providers.find((p) => p.slug === selectedProviderSlug);

  // Configured Providers (HAS KEY) -> PINNED TOP
  const configuredProviders = useMemo(() => {
    return providers.filter((p) => p.hasKey);
  }, [providers]);

  // Unconfigured Famous Providers
  const famousUnconfiguredProviders = useMemo(() => {
    const configuredSlugs = new Set(configuredProviders.map((p) => p.slug.toLowerCase()));
    return FULL_PROVIDER_CATALOG.filter(
      (c) => POPULAR_SLUGS.includes(c.slug) && !configuredSlugs.has(c.slug)
    );
  }, [configuredProviders]);

  // Filtered Catalog for "All 130+ Providers" Search
  const filteredCatalog = useMemo(() => {
    return COMPLETE_130_CATALOG.filter((c) => {
      const matchesSearch =
        c.label.toLowerCase().includes(catalogSearch.toLowerCase()) ||
        c.slug.toLowerCase().includes(catalogSearch.toLowerCase()) ||
        c.description.toLowerCase().includes(catalogSearch.toLowerCase());

      const matchesCat =
        activeCategory === "all" ||
        (activeCategory === "popular" && (c.category === "popular" || POPULAR_SLUGS.includes(c.slug))) ||
        (activeCategory === "cloud" && c.category === "cloud") ||
        (activeCategory === "opensource" && c.category === "opensource");

      return matchesSearch && matchesCat;
    }).sort((a, b) => a.label.localeCompare(b.label));
  }, [catalogSearch, activeCategory]);

  if (loading) {
    return (
      <AppShell title="Provider Hub" subtitle="Enterprise AI Gateway Upstream Connectivity & Model Management">
        <PageLoadingSkeleton
          title="Loading Provider Hub (130+ AI Providers)..."
          subtitle="Discovering upstream endpoints, credentials, and model health diagnostics."
          cardsCount={3}
        />
      </AppShell>
    );
  }

  return (
    <AppShell title="Provider Hub" subtitle="Enterprise AI Gateway Upstream Connectivity & Model Management">
      {/* Floating High-Tech Testing & Diagnostic Scan Banner */}
      <AnimatePresence>
        {testingProviderSlug && (
          <motion.div
            initial={{ opacity: 0, y: -30, scale: 0.95 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -20, scale: 0.95 }}
            transition={{ duration: 0.25 }}
            className="fixed top-16 left-1/2 -translate-x-1/2 z-50 flex items-center gap-4 rounded-2xl border border-amber-500/50 bg-background/95 px-6 py-3.5 shadow-[0_0_40px_rgba(245,158,11,0.4)] backdrop-blur-xl"
          >
            <div className="relative flex h-10 w-10 items-center justify-center rounded-xl bg-amber-500/20 text-amber-500 border border-amber-500/40">
              <motion.div
                animate={{ scale: [1, 1.4, 1], opacity: [0.7, 0, 0.7] }}
                transition={{ repeat: Infinity, duration: 1.3 }}
                className="absolute inset-0 rounded-xl bg-amber-500/30"
              />
              <motion.div animate={{ rotate: 360 }} transition={{ repeat: Infinity, duration: 1.8, ease: "linear" }}>
                <RefreshCw className="h-5 w-5 text-amber-500" />
              </motion.div>
            </div>

            <div>
              <div className="flex items-center gap-2">
                <p className="text-xs font-bold text-foreground">
                  {testingProviderSlug === "ALL" ? "Executing Full Provider Diagnostic Scan..." : `Testing & Loading ${testingProviderSlug.toUpperCase()}...`}
                </p>
                <span className="flex h-2.5 w-2.5 rounded-full bg-amber-500 animate-ping" />
              </div>
              <p className="text-[0.7rem] text-muted-foreground">Pinging candidate models to verify endpoint accessibility and response latency.</p>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Top Banner Stats Ribbon */}
      <div className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-3">
        <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} className="section-panel p-5">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-[0.7rem] uppercase tracking-widest text-sky-600 dark:text-sky-400 font-bold">Active Providers</p>
              <h4 className="mt-1 text-2xl font-extrabold text-foreground">
                {activeProvidersCount} <span className="text-xs font-normal text-muted-foreground">/ {configuredProviders.length} configured</span>
              </h4>
            </div>
            <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-sky-500/10 border border-sky-500/30 text-sky-500">
              <Server className="h-5 w-5" />
            </div>
          </div>
        </motion.div>

        <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.05 }} className="section-panel p-5">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-[0.7rem] uppercase tracking-widest text-emerald-600 dark:text-emerald-400 font-bold">Active Models in Routing</p>
              <h4 className="mt-1 text-2xl font-extrabold text-foreground">
                {totalModelsActive} <span className="text-xs font-normal text-muted-foreground">ready for requests</span>
              </h4>
            </div>
            <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-emerald-500/10 border border-emerald-500/30 text-emerald-500">
              <Cpu className="h-5 w-5" />
            </div>
          </div>
        </motion.div>

        <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }} className="section-panel p-5">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-[0.7rem] uppercase tracking-widest text-indigo-600 dark:text-indigo-400 font-bold">Supported Catalogue</p>
              <h4 className="mt-1 text-2xl font-extrabold text-emerald-600 dark:text-emerald-400 flex items-center gap-1.5 text-lg">
                <ShieldCheck className="h-5 w-5" /> 130+ LLM Providers
              </h4>
            </div>
            <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-indigo-500/10 border border-indigo-500/30 text-indigo-500">
              <Activity className="h-5 w-5 animate-pulse" />
            </div>
          </div>
        </motion.div>
      </div>

      {/* Action Header */}
      <div className="mb-6 flex flex-wrap items-center justify-between gap-3 section-panel p-4">
        <div>
          <h3 className="text-sm font-bold tracking-tight text-foreground flex items-center gap-2">
            <Sparkles className="h-4 w-4 text-cyan" /> Upstream AI Infrastructure
          </h3>
          <p className="text-xs text-muted-foreground">
            Configure your API keys (BYOK). Connected providers automatically pin to the top of your dashboard.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            onClick={handleTestAndLoadAll}
            disabled={testingProviderSlug === "ALL"}
            variant="outline"
            size="sm"
            className="h-9 rounded-xl text-xs gap-1.5 border-amber-500/40 text-amber-600 dark:text-amber-400 bg-amber-500/10 hover:bg-amber-500/20 font-bold shadow-sm"
          >
            {testingProviderSlug === "ALL" ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Zap className="h-3.5 w-3.5 text-amber-500" />}
            Test & Load All Keys
          </Button>
          <Button onClick={load} variant="outline" size="sm" className="h-9 rounded-xl text-xs gap-1.5 border-border hover:bg-accent">
            <RefreshCw className={`h-3.5 w-3.5 ${loading ? "animate-spin" : ""}`} /> Sync Catalog
          </Button>
          {isTeamHead ? (
            <Button onClick={openUpgradeModal} className="h-9 rounded-xl text-xs bg-amber-500 text-black font-bold hover:bg-amber-400 shadow-md">
              Request Access
            </Button>
          ) : (
            <Button
              onClick={() => { setAddModalInitialSlug(null); setShowAddModal(true); }}
              className="grad-primary h-9 rounded-xl text-xs text-white font-bold shadow-md hover:brightness-110 flex items-center gap-1.5"
            >
              <Plus className="h-4 w-4" /> Add AI Provider
            </Button>
          )}
        </div>
      </div>

      {error && (
        <div className="mb-4 rounded-xl border border-destructive/40 bg-destructive/10 px-4 py-3 text-xs text-destructive flex items-center gap-2">
          <CircleSlash className="h-4 w-4 shrink-0" />
          {error}
        </div>
      )}

      {/* SECTION 1: CONFIGURING & ACTIVE PROVIDERS (PINNED AT VERY TOP) */}
      <div className="mb-8">
        <div className="mb-3 flex items-center justify-between">
          <h3 className="text-xs uppercase tracking-wider font-extrabold text-emerald-600 dark:text-emerald-400 flex items-center gap-2 font-mono">
            <span className="h-2 w-2 rounded-full bg-emerald-500 animate-ping" />
            Configured Workspace Providers ({configuredProviders.length})
          </h3>
          <span className="text-[0.68rem] text-muted-foreground font-mono">Pinned at Top</span>
        </div>

        {loading && (
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {[0, 1].map((i) => (
              <div key={i} className="h-48 animate-pulse rounded-2xl bg-muted" />
            ))}
          </div>
        )}

        {!loading && configuredProviders.length === 0 && (
          <div className="section-panel p-8 text-center border-dashed border-emerald-500/30 bg-emerald-500/5">
            <Key className="mx-auto mb-3 h-10 w-10 text-emerald-500 opacity-60" />
            <h4 className="text-sm font-bold text-foreground">No Provider API Keys Configured Yet</h4>
            <p className="mt-1 text-xs text-muted-foreground max-w-md mx-auto">
              Add your API key for Groq, Gemini, OpenAI, Claude, or DeepSeek to enable high-performance model routing.
            </p>
            <Button
              onClick={() => { setAddModalInitialSlug(null); setShowAddModal(true); }}
              className="grad-primary mt-4 h-9 rounded-xl text-xs font-bold text-white shadow-md"
            >
              <Plus className="mr-1.5 h-4 w-4" /> Connect Your First Key
            </Button>
          </div>
        )}

        {!loading && configuredProviders.length > 0 && (
          <div className="grid gap-5 md:grid-cols-2 lg:grid-cols-3">
            {configuredProviders.map((p) => {
              const catalogEntry = getCatalogEntry(p.slug, p.displayName);
              const activeModels = p.hasKey && p.enabled ? p.enabledModelCount : 0;

              return (
                <motion.div
                  key={p.slug}
                  initial={{ opacity: 0, y: 12 }}
                  animate={{ opacity: 1, y: 0 }}
                  whileHover={{ y: -3 }}
                  className="relative overflow-hidden p-5 rounded-3xl border-2 border-emerald-500/40 bg-gradient-to-br from-emerald-500/10 via-background to-teal-500/5 hover:border-emerald-500/80 hover:shadow-[0_4px_30px_rgba(16,185,129,0.2)] transition-all duration-300 cursor-pointer group shadow-sm"
                  onClick={() => setSelectedProviderSlug(p.slug)}
                >
                  {/* High-Tech Animated Testing Overlay */}
                  <AnimatePresence>
                    {(testingProviderSlug === p.slug || testingProviderSlug === "ALL") && (
                      <motion.div
                        initial={{ opacity: 0 }}
                        animate={{ opacity: 1 }}
                        exit={{ opacity: 0 }}
                        className="absolute inset-0 z-30 flex flex-col items-center justify-center bg-background/90 backdrop-blur-md p-4 text-center border-2 border-amber-500/60 shadow-[0_0_30px_rgba(245,158,11,0.35)] rounded-2xl"
                      >
                        <div className="relative mb-3 flex h-12 w-12 items-center justify-center rounded-2xl bg-amber-500/20 text-amber-500 border border-amber-500/40">
                          <motion.div
                            animate={{ scale: [1, 1.5, 1], opacity: [0.8, 0, 0.8] }}
                            transition={{ repeat: Infinity, duration: 1.2, ease: "easeInOut" }}
                            className="absolute inset-0 rounded-2xl bg-amber-500/30"
                          />
                          <motion.div animate={{ rotate: 360 }} transition={{ repeat: Infinity, duration: 2, ease: "linear" }}>
                            <RefreshCw className="h-6 w-6 text-amber-500" />
                          </motion.div>
                        </div>

                        <p className="text-xs font-extrabold text-foreground tracking-tight">
                          Testing {p.displayName}...
                        </p>
                        <p className="mt-1 text-[0.68rem] text-muted-foreground font-mono">
                          Pinging live models & verifying availability
                        </p>

                        {/* Signal Equalizer Animation */}
                        <div className="mt-3 flex items-center gap-1">
                          {[0, 1, 2, 3, 4].map((bar) => (
                            <motion.span
                              key={bar}
                              animate={{ height: ["6px", "18px", "6px"] }}
                              transition={{ repeat: Infinity, duration: 0.8, delay: bar * 0.15 }}
                              className="w-1 rounded-full bg-amber-500"
                            />
                          ))}
                        </div>
                      </motion.div>
                    )}
                  </AnimatePresence>

                  <div className="absolute top-0 right-0 px-3 py-1 bg-emerald-500/20 text-emerald-600 dark:text-emerald-400 text-[0.625rem] font-extrabold rounded-bl-xl border-l border-b border-emerald-500/30 font-mono flex items-center gap-1">
                    <Check className="h-3 w-3" /> CONFIGURING & ACTIVE
                  </div>

                  <div className="flex items-start justify-between gap-3 mt-1">
                    <div className="flex items-center gap-3">
                      <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-background border border-emerald-500/30 text-foreground shadow-sm group-hover:border-emerald-500">
                        <ProviderLogo slug={p.slug} name={p.displayName} className="h-6 w-6" />
                      </div>
                      <div>
                        <h4 className="text-sm font-bold tracking-tight text-foreground group-hover:text-emerald-600 dark:group-hover:text-emerald-400 transition-colors flex items-center gap-1.5">
                          {p.displayName}
                        </h4>
                        <p className="font-mono text-[0.68rem] text-muted-foreground flex items-center gap-1.5 mt-0.5">
                          <span className="inline-block h-1.5 w-1.5 rounded-full bg-emerald-500" /> {p.slug}
                        </p>
                      </div>
                    </div>
                    <div onClick={(e) => e.stopPropagation()} className="mt-4">
                      <Switch disabled={!p.hasKey} checked={p.enabled && p.hasKey} onCheckedChange={(val) => toggleEnabled(p.slug, val)} />
                    </div>
                  </div>

                  <div className="mt-4 grid grid-cols-2 gap-2 p-3 rounded-xl bg-background/80 border border-emerald-500/20 text-xs">
                    <div>
                      <p className="text-[0.6rem] uppercase tracking-wider text-muted-foreground font-mono font-bold">Active Models</p>
                      <p className="mt-0.5 font-mono text-xs font-extrabold text-emerald-600 dark:text-emerald-400">
                        {activeModels} Active Models
                      </p>
                    </div>
                    <div>
                      <p className="text-[0.6rem] uppercase tracking-wider text-muted-foreground font-mono font-bold">Key Status</p>
                      <p className="mt-0.5 font-mono text-xs font-extrabold text-emerald-600 dark:text-emerald-400 flex items-center gap-1">
                        <CircleCheck className="h-3 w-3 text-emerald-500" /> Connected
                      </p>
                    </div>
                  </div>

                  <div className="mt-4 flex items-center justify-between border-t border-emerald-500/20 pt-3 text-xs" onClick={(e) => e.stopPropagation()}>
                    <button
                      onClick={() => handleTestAndLoadProvider(p.slug, p.displayName)}
                      disabled={testingProviderSlug === p.slug}
                      className="px-2.5 py-1 rounded-lg bg-amber-500/10 border border-amber-500/30 text-[0.7rem] font-bold text-amber-600 dark:text-amber-400 hover:bg-amber-500/20 transition-colors flex items-center gap-1"
                    >
                      {testingProviderSlug === p.slug ? <Loader2 className="h-3 w-3 animate-spin" /> : <Zap className="h-3 w-3 text-amber-500" />}
                      Test & Load
                    </button>

                    <div className="flex gap-1.5">
                      <button
                        onClick={() => setEditingKeySlug(p.slug)}
                        className="px-2.5 py-1 rounded-lg bg-sky-500/10 text-[0.7rem] font-bold text-sky-600 dark:text-sky-400 hover:bg-sky-500/20 transition-colors"
                      >
                        Update Key
                      </button>
                      <button
                        onClick={() => setSelectedProviderSlug(p.slug)}
                        className="px-3 py-1 rounded-lg grad-primary text-[0.7rem] font-bold text-white shadow-sm hover:brightness-110 transition-all flex items-center gap-1"
                      >
                        <SlidersHorizontal className="h-3 w-3" /> Select Models
                      </button>
                    </div>
                  </div>
                </motion.div>
              );
            })}
          </div>
        )}
      </div>

      {/* SECTION 2: POPULAR & FEATURED AI PROVIDERS */}
      {famousUnconfiguredProviders.length > 0 && (
        <div className="mb-8">
          <div className="mb-3 flex items-center justify-between">
            <h3 className="text-xs uppercase tracking-wider font-extrabold text-foreground flex items-center gap-2 font-mono">
              <Sparkles className="h-3.5 w-3.5 text-amber-500" /> Popular AI Infrastructure
            </h3>
            <span className="text-[0.68rem] text-muted-foreground">Click card or '+ Add Key' to configure</span>
          </div>

          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
            {famousUnconfiguredProviders.map((c) => (
              <motion.div
                key={c.slug}
                whileHover={{ y: -2 }}
                className="section-panel p-4 relative overflow-hidden transition-all hover:border-cyan-500/50 hover:shadow-md cursor-pointer group flex flex-col justify-between"
                onClick={() => {
                  setAddModalInitialSlug(c.slug);
                  setShowAddModal(true);
                }}
              >
                <div>
                  <div className="flex items-center justify-between">
                    <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-muted/80 border border-border group-hover:border-cyan-500/50">
                      <ProviderLogo slug={c.slug} name={c.label} className="h-5 w-5" />
                    </div>
                    <span className="text-[0.625rem] font-mono px-2 py-0.5 rounded-full bg-muted text-muted-foreground font-bold">
                      {c.badge}
                    </span>
                  </div>

                  <h4 className="mt-3 text-sm font-bold text-foreground group-hover:text-cyan transition-colors">{c.label}</h4>
                  <p className="mt-1 text-[0.7rem] text-muted-foreground line-clamp-2 leading-relaxed">{c.description}</p>
                </div>

                <div className="mt-4 border-t border-border pt-2.5 flex items-center justify-between text-xs">
                  <span className="text-[0.65rem] text-muted-foreground font-mono">Key Required</span>
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      setAddModalInitialSlug(c.slug);
                      setShowAddModal(true);
                    }}
                    className="px-2.5 py-1 rounded-lg grad-primary text-[0.7rem] font-bold text-white shadow-sm hover:brightness-110 transition-all flex items-center gap-1"
                  >
                    <Plus className="h-3 w-3" /> Add Key
                  </button>
                </div>
              </motion.div>
            ))}
          </div>
        </div>
      )}

      {/* SECTION 3: EXPLORE ALL 130+ PROVIDERS (SEARCHABLE ALPHABETICAL CATALOG) */}
      <div className="section-panel p-6">
        <div className="flex flex-wrap items-center justify-between gap-4 border-b border-border pb-4 mb-5">
          <div>
            <h3 className="text-sm font-bold text-foreground flex items-center gap-2">
              <Globe className="h-4 w-4 text-cyan" /> All 130+ Supported AI Providers (Alphabetical)
            </h3>
            <p className="text-xs text-muted-foreground">Search and connect any upstream AI API endpoint supported by NexusAI Gateway.</p>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <div className="relative w-64">
              <Search className="absolute left-3 top-2.5 h-3.5 w-3.5 text-muted-foreground" />
              <Input
                value={catalogSearch}
                onChange={(e) => setCatalogSearch(e.target.value)}
                placeholder="Search 130+ providers..."
                className="h-8 pl-9 text-xs rounded-xl border-border bg-background"
              />
            </div>

            <div className="flex rounded-xl bg-muted p-0.5 text-[0.7rem] font-bold">
              <button
                onClick={() => setActiveCategory("all")}
                className={`px-3 py-1 rounded-lg transition-all ${activeCategory === "all" ? "bg-background text-foreground shadow-sm" : "text-muted-foreground"}`}
              >
                All (130+)
              </button>
              <button
                onClick={() => setActiveCategory("popular")}
                className={`px-3 py-1 rounded-lg transition-all ${activeCategory === "popular" ? "bg-background text-foreground shadow-sm" : "text-muted-foreground"}`}
              >
                Popular
              </button>
              <button
                onClick={() => setActiveCategory("opensource")}
                className={`px-3 py-1 rounded-lg transition-all ${activeCategory === "opensource" ? "bg-background text-foreground shadow-sm" : "text-muted-foreground"}`}
              >
                Open Source
              </button>
            </div>
          </div>
        </div>

        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 max-h-[520px] overflow-y-auto pr-1">
          {filteredCatalog.map((c) => {
            const isConfigured = configuredProviders.some((p) => p.slug.toLowerCase() === c.slug.toLowerCase());

            return (
              <motion.div
                key={c.slug}
                whileHover={{ scale: 1.01, y: -1 }}
                className={`p-3.5 rounded-2xl border transition-all duration-150 flex items-center justify-between cursor-pointer ${
                  isConfigured
                    ? "bg-emerald-500/10 border-emerald-500/40 text-foreground"
                    : "bg-card border-border hover:border-cyan-500/50 hover:bg-accent/40"
                }`}
                onClick={() => {
                  if (isConfigured) {
                    setSelectedProviderSlug(c.slug);
                  } else {
                    setAddModalInitialSlug(c.slug);
                    setShowAddModal(true);
                  }
                }}
              >
                <div className="flex items-center gap-3">
                  <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-muted border border-border">
                    <ProviderLogo slug={c.slug} name={c.label} className="h-4 w-4" />
                  </div>
                  <div className="truncate">
                    <h5 className="text-xs font-bold text-foreground truncate">{c.label}</h5>
                    <p className="font-mono text-[0.625rem] text-muted-foreground truncate">{c.slug}</p>
                  </div>
                </div>

                <div>
                  {isConfigured ? (
                    <span className="rounded-full bg-emerald-500/20 px-2 py-0.5 text-[0.6rem] font-bold text-emerald-600 dark:text-emerald-400 border border-emerald-500/30 flex items-center gap-1">
                      <Check className="h-2.5 w-2.5" /> Active
                    </span>
                  ) : (
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        setAddModalInitialSlug(c.slug);
                        setShowAddModal(true);
                      }}
                      className="px-2 py-1 rounded-lg bg-sky-500/10 text-[0.65rem] font-bold text-sky-600 dark:text-sky-400 hover:bg-sky-500/20 transition-colors flex items-center gap-0.5"
                    >
                      + Add
                    </button>
                  )}
                </div>
              </motion.div>
            );
          })}
        </div>
      </div>

      {/* Model Selector Drawer */}
      {selectedProviderSlug && activeProvider && (
        <IndividualModelSelectorModal
          provider={activeProvider}
          onClose={() => setSelectedProviderSlug(null)}
          onRefreshProviders={load}
          onUpdateKey={() => {
            const slug = selectedProviderSlug;
            setSelectedProviderSlug(null);
            setEditingKeySlug(slug);
          }}
        />
      )}

      {/* Comprehensive Add Provider Modal */}
      {showAddModal && (
        <AddProviderModal
          initialSlug={addModalInitialSlug}
          onClose={() => { setShowAddModal(false); setAddModalInitialSlug(null); }}
          onSuccess={() => { setShowAddModal(false); setAddModalInitialSlug(null); load(); }}
        />
      )}

      {/* Update Key Modal */}
      {editingKeySlug && (
        <UpdateKeyModal
          slug={editingKeySlug}
          onClose={() => setEditingKeySlug(null)}
          onSubmit={(key) => handleUpdateKey(editingKeySlug, key)}
        />
      )}
    </AppShell>
  );
}

/** Granular Individual Model Selector Modal */
function IndividualModelSelectorModal({
  provider,
  onClose,
  onRefreshProviders,
  onUpdateKey,
}: {
  provider: ProviderSummary;
  onClose: () => void;
  onRefreshProviders: () => void;
  onUpdateKey: () => void;
}) {
  const [models, setModels] = useState<ModelSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [showAddCustom, setShowAddCustom] = useState(false);
  const [customModelId, setCustomModelId] = useState("");
  const [customDisplayName, setCustomDisplayName] = useState("");
  const [customSubmitting, setCustomSubmitting] = useState(false);

  const { success, error: toastError, info } = useToast();

  const loadModels = async () => {
    setLoading(true);
    try {
      const data = await providersApi.listModels(provider.slug);
      setModels(data);
    } catch (err: any) {
      toastError("Failed to Load Models", err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadModels(); }, [provider.slug]);

  const toggleSingleModel = async (modelId: string, enable: boolean) => {
    if (!provider.hasKey && enable) {
      toastError("API Key Required", `Please add an API key for ${provider.displayName} before enabling models.`);
      return;
    }
    if (enable) {
      info("Validating Endpoint...", `Pinging live endpoint for ${modelId}...`);
      try {
        const pingRes = await authFetch(`/api/models/health/verify-single?providerSlug=${provider.slug}&modelId=${encodeURIComponent(modelId)}`, { method: "POST" });
        const pingData = await pingRes.json();
        if (!pingData.healthy) {
          const confirmForce = window.confirm(
            `⚠️ LIVE PING WARNING for '${modelId}':\n\n` +
            `Reason: ${pingData.message || pingData.error || "Endpoint verification failed (404/401)."}\n\n` +
            `Do you still want to force-enable this model anyway?`
          );
          if (!confirmForce) return;
        } else {
          success("Model Health Verified", `HTTP 200 OK (${pingData.latencyMs}ms) — ${modelId} is responsive!`);
        }
      } catch (pingErr: any) {
        console.warn("Model health ping failed:", pingErr);
      }
    }
    try {
      if (enable) await providersApi.enableModel(provider.slug, modelId);
      else await providersApi.disableModel(provider.slug, modelId);

      setModels((prev) => prev.map((m) => (m.modelId === modelId ? { ...m, enabled: enable } : m)));
      success(
        enable ? "Model Active" : "Model Disabled",
        `${modelId} is now ${enable ? "enabled for" : "excluded from"} AI routing.`
      );
      onRefreshProviders();
    } catch (err: any) {
      toastError("Toggle Failed", err.message);
    }
  };

  const handleSelectAll = async (enable: boolean) => {
    if (!provider.hasKey && enable) {
      toastError("API Key Required", `Please add an API key for ${provider.displayName} first.`);
      return;
    }
    try {
      await Promise.all(
        models.map((m) =>
          enable
            ? providersApi.enableModel(provider.slug, m.modelId)
            : providersApi.disableModel(provider.slug, m.modelId)
        )
      );
      setModels((prev) => prev.map((m) => ({ ...m, enabled: enable })));
      success(enable ? "All Models Enabled" : "All Models Disabled", `Updated routing state for ${provider.displayName}.`);
      onRefreshProviders();
    } catch (err: any) {
      toastError("Batch Update Failed", err.message);
    }
  };

  const handleRegisterCustomModel = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!customModelId.trim()) return;
    setCustomSubmitting(true);
    try {
      await providersApi.registerModel(provider.slug, {
        modelId: customModelId.trim(),
        displayName: customDisplayName.trim() || customModelId.trim(),
        enabled: provider.hasKey,
      });
      success("Custom Model Added", `${customModelId} added to ${provider.displayName}.`);
      setCustomModelId("");
      setCustomDisplayName("");
      setShowAddCustom(false);
      loadModels();
      onRefreshProviders();
    } catch (err: any) {
      toastError("Registration Failed", err.message);
    } finally {
      setCustomSubmitting(false);
    }
  };

  const filteredModels = useMemo(() => {
    return models.filter((m) =>
      m.modelId.toLowerCase().includes(search.toLowerCase()) ||
      m.displayName.toLowerCase().includes(search.toLowerCase())
    );
  }, [models, search]);

  const enabledCount = models.filter((m) => m.enabled && provider.hasKey).length;

  return (
    <div className="fixed inset-0 z-[9999] flex items-center justify-center p-4 sm:p-6 bg-black/60 backdrop-blur-md overflow-y-auto" onClick={(e) => e.target === e.currentTarget && onClose()}>
      <motion.div
        initial={{ opacity: 0, scale: 0.96 }}
        animate={{ opacity: 1, scale: 1 }}
        exit={{ opacity: 0, scale: 0.96 }}
        className="w-full max-w-3xl rounded-3xl bg-card border border-border text-card-foreground shadow-2xl overflow-hidden flex flex-col max-h-[85vh] relative"
      >
        {/* Header */}
        <div className="flex items-center justify-between border-b border-border px-6 py-4 bg-muted/30">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-background border border-border shadow-sm">
              <ProviderLogo slug={provider.slug} name={provider.displayName} className="h-5 w-5" />
            </div>
            <div>
              <h3 className="text-base font-extrabold text-foreground flex items-center gap-2">
                {provider.displayName} Models
              </h3>
              <p className="text-xs text-muted-foreground font-mono">
                {enabledCount} of {models.length} models active in gateway routing policy
              </p>
            </div>
          </div>
          <button onClick={onClose} className="p-1 rounded-lg text-muted-foreground hover:text-foreground hover:bg-accent transition-colors"><X className="h-5 w-5" /></button>
        </div>

        {/* Action Toolbar */}
        <div className="p-4 border-b border-border bg-muted/10 flex flex-wrap items-center justify-between gap-3">
          <div className="relative flex-1 min-w-[200px]">
            <Search className="absolute left-3 top-2.5 h-3.5 w-3.5 text-muted-foreground" />
            <Input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search model catalog..."
              className="h-9 pl-9 text-xs rounded-xl border-border bg-background text-foreground placeholder:text-muted-foreground"
            />
          </div>

          <div className="flex items-center gap-2">
            <Button onClick={() => handleSelectAll(true)} disabled={!provider.hasKey} variant="outline" size="sm" className="h-8 text-xs rounded-lg font-bold border-emerald-500/40 text-emerald-700 dark:text-emerald-400 bg-emerald-500/10 hover:bg-emerald-500/20">
              Enable All
            </Button>
            <Button onClick={() => handleSelectAll(false)} variant="outline" size="sm" className="h-8 text-xs rounded-lg border-border hover:bg-accent text-foreground font-medium">
              Disable All
            </Button>
            <Button onClick={() => setShowAddCustom(!showAddCustom)} variant="outline" size="sm" className="h-8 text-xs rounded-lg border-sky-500/40 text-sky-600 dark:text-sky-400 bg-sky-500/10 font-bold gap-1 hover:bg-sky-500/20">
              <Plus className="h-3.5 w-3.5" /> Custom Model
            </Button>
          </div>
        </div>

        {/* Custom Model Form */}
        <AnimatePresence>
          {showAddCustom && (
            <motion.form
              initial={{ height: 0, opacity: 0 }}
              animate={{ height: "auto", opacity: 1 }}
              exit={{ height: 0, opacity: 0 }}
              onSubmit={handleRegisterCustomModel}
              className="p-4 border-b border-border bg-sky-500/5 dark:bg-sky-500/10 space-y-3"
            >
              <p className="text-xs font-bold text-sky-600 dark:text-sky-400 flex items-center gap-1.5">
                <Plus className="h-3.5 w-3.5" /> Register Custom Model ID
              </p>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                <Input
                  value={customModelId}
                  onChange={(e) => setCustomModelId(e.target.value)}
                  placeholder="e.g. llama-3.3-70b-versatile"
                  className="h-8 text-xs font-mono rounded-lg border-border bg-background text-foreground"
                  required
                />
                <Input
                  value={customDisplayName}
                  onChange={(e) => setCustomDisplayName(e.target.value)}
                  placeholder="Display Name (optional)"
                  className="h-8 text-xs rounded-lg border-border bg-background text-foreground"
                />
              </div>
              <div className="flex justify-end gap-2">
                <Button type="button" onClick={() => setShowAddCustom(false)} variant="outline" size="sm" className="h-7 text-xs rounded-lg border-border">Cancel</Button>
                <Button type="submit" disabled={customSubmitting} size="sm" className="grad-primary h-7 text-xs text-white rounded-lg font-bold">
                  {customSubmitting ? "Adding..." : "Add Model"}
                </Button>
              </div>
            </motion.form>
          )}
        </AnimatePresence>

        {/* Models List */}
        <div className="p-5 overflow-y-auto space-y-3 flex-1 bg-background/50">
          {loading && (
            <div className="space-y-3">
              {[0, 1, 2, 3].map((i) => (
                <div key={i} className="h-16 animate-pulse rounded-2xl bg-muted" />
              ))}
            </div>
          )}

          {!loading && filteredModels.length === 0 && (
            <div className="py-12 text-center text-xs text-muted-foreground border border-dashed border-border rounded-2xl">
              No models cataloged. Click <span className="text-sky-600 dark:text-sky-400 font-bold">Custom Model</span> above to manually add a model ID.
            </div>
          )}

          {!loading &&
            filteredModels.map((m) => {
              const isActive = m.enabled && provider.hasKey;
              return (
                <motion.div
                  key={m.modelId}
                  initial={{ opacity: 0, y: 6 }}
                  animate={{ opacity: 1, y: 0 }}
                  className={`flex items-center justify-between p-4 rounded-2xl border transition-all duration-200 ${
                    isActive
                      ? "bg-emerald-500/10 dark:bg-emerald-500/15 border-2 border-emerald-500/40 shadow-sm"
                      : "bg-background border-border text-foreground/80 opacity-80 hover:opacity-100 hover:border-border"
                  }`}
                >
                  <div className="flex items-center gap-3">
                    <div className={`flex h-10 w-10 items-center justify-center rounded-xl font-mono text-xs font-extrabold ${isActive ? "bg-emerald-500/20 text-emerald-700 dark:text-emerald-300 border border-emerald-500/40" : "bg-muted text-muted-foreground"}`}>
                      {m.modelId.slice(0, 3).toUpperCase()}
                    </div>

                    <div>
                      <div className="flex items-center gap-2">
                        <h4 className="text-sm font-extrabold font-mono text-foreground">{m.modelId}</h4>
                        {isActive ? (
                          <span className="rounded-full bg-emerald-500/20 px-2.5 py-0.5 text-[0.65rem] font-extrabold text-emerald-700 dark:text-emerald-300 border border-emerald-500/40">
                            Active in Routing
                          </span>
                        ) : (
                          <span className="rounded-full bg-muted px-2.5 py-0.5 text-[0.65rem] font-semibold text-muted-foreground">
                            {!provider.hasKey ? "Key Missing" : "Disabled"}
                          </span>
                        )}
                      </div>

                      <div className="mt-1 flex flex-wrap items-center gap-3 text-[0.7rem] text-muted-foreground font-mono">
                        <span>Arm Key: <strong className="text-foreground font-semibold">{m.armKey}</strong></span>
                        {m.pricingVerified && (
                          <span>${m.inputPricePer1M.toFixed(2)} in / ${m.outputPricePer1M.toFixed(2)} out per 1M</span>
                        )}
                        {m.contextWindowTokens > 0 && (
                          <span>{(m.contextWindowTokens / 1000).toFixed(0)}K context</span>
                        )}
                      </div>
                    </div>
                  </div>

                  <div className="flex items-center gap-3">
                    <Switch
                      disabled={!provider.hasKey}
                      checked={isActive}
                      onCheckedChange={(val) => toggleSingleModel(m.modelId, val)}
                    />
                  </div>
                </motion.div>
              );
            })}
        </div>

        {/* Modal Footer */}
        <div className="border-t border-border px-6 py-4 bg-muted/30 flex items-center justify-between text-xs text-muted-foreground">
          <p>
            <strong className="text-emerald-600 dark:text-emerald-400 font-bold">{enabledCount}</strong> of <strong className="text-foreground">{models.length}</strong> models active for {provider.displayName}.
          </p>
          <Button onClick={onClose} className="grad-primary h-9 rounded-xl text-xs text-white font-bold">
            Done Selecting
          </Button>
        </div>
      </motion.div>
    </div>
  );
}

/** Comprehensive Add Provider Modal with Setup Instructions & Live Verification */
function AddProviderModal({
  initialSlug,
  onClose,
  onSuccess,
}: {
  initialSlug?: string | null;
  onClose: () => void;
  onSuccess: () => void;
}) {
  const [selectedEntry, setSelectedEntry] = useState<ProviderCatalogEntry>(() => {
    if (initialSlug) {
      return getCatalogEntry(initialSlug);
    }
    return FULL_PROVIDER_CATALOG[0]; // Default Groq
  });

  const [form, setForm] = useState({
    displayName: selectedEntry.label,
    slug: selectedEntry.slug,
    apiKey: "",
    secretKey: "",
    region: "us-east-1",
    baseUrl: selectedEntry.baseUrl || "",
    apiVersion: "2024-02-15-preview",
    organization: "",
  });

  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(false);
  const [testResult, setTestResult] = useState<{ status: string; message: string; modelsCount?: number } | null>(null);
  const { success, error: toastError, info } = useToast();

  useEffect(() => {
    if (initialSlug) {
      const entry = getCatalogEntry(initialSlug);
      setSelectedEntry(entry);
      setForm({
        displayName: entry.label,
        slug: entry.slug,
        apiKey: "",
        secretKey: "",
        region: "us-east-1",
        baseUrl: entry.baseUrl || "",
        apiVersion: "2024-02-15-preview",
        organization: "",
      });
    }
  }, [initialSlug]);

  const selectCatalogItem = (entry: ProviderCatalogEntry) => {
    setSelectedEntry(entry);
    setForm({
      displayName: entry.label,
      slug: entry.slug,
      apiKey: "",
      secretKey: "",
      region: "us-east-1",
      baseUrl: entry.baseUrl || "",
      apiVersion: "2024-02-15-preview",
      organization: "",
    });
    setTestResult(null);
  };

  const handleConnectAndSave = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.apiKey && selectedEntry.slug !== "ollama" && selectedEntry.type !== "AWS_BEDROCK") {
      toastError("API Key Required", `Please enter your ${selectedEntry.label} API key.`);
      return;
    }

    setLoading(true);
    setTestResult(null);
    info(`Connecting ${selectedEntry.label}...`, `Saving provider settings and testing endpoint health...`);

    try {
      const credentialsMap: Record<string, string> = {};
      if (form.apiKey) credentialsMap["api_key"] = form.apiKey;
      if (form.secretKey) credentialsMap["secret_key"] = form.secretKey;
      if (form.apiVersion) credentialsMap["api_version"] = form.apiVersion;
      if (form.organization) credentialsMap["organization"] = form.organization;

      const regPayload: Parameters<typeof providersApi.registerProvider>[0] = {
        displayName: form.displayName || selectedEntry.label,
        slug: form.slug || selectedEntry.slug,
        type: selectedEntry.type,
        apiKey: form.apiKey,
        region: selectedEntry.type === "AWS_BEDROCK" ? form.region : undefined,
        baseUrl: form.baseUrl || undefined,
      };

      await providersApi.registerProvider(regPayload);

      // Perform live verification test
      const testRes = await authFetch(`/api/providers/${form.slug || selectedEntry.slug}/test-and-load`, { method: "POST" });
      const testData = await testRes.json();

      if (testData.status === "SUCCESS") {
        setTestResult({
          status: "SUCCESS",
          message: `Verified! Loaded ${testData.totalActive} active working models.`,
          modelsCount: testData.totalActive,
        });
        success("Provider Configured & Live!", `${selectedEntry.label} settings saved and verified.`);
        setTimeout(() => {
          onSuccess();
        }, 1000);
      } else {
        setTestResult({
          status: "WARNING",
          message: testData.message || "Settings saved, but model endpoint verification returned warnings.",
        });
        toastError("Endpoint Verification Warning", testData.message || "Please check provider configuration.");
        setTimeout(() => {
          onSuccess();
        }, 1500);
      }
    } catch (err: any) {
      toastError("Connection Failed", err.message);
    } finally {
      setLoading(false);
    }
  };

  const filteredCatalog = useMemo(() => {
    return COMPLETE_130_CATALOG.filter(
      (c) =>
        c.label.toLowerCase().includes(search.toLowerCase()) ||
        c.slug.toLowerCase().includes(search.toLowerCase())
    ).sort((a, b) => a.label.localeCompare(b.label));
  }, [search]);

  return (
    <div className="fixed inset-0 z-[9999] flex items-center justify-center p-4 sm:p-6 bg-black/60 backdrop-blur-md overflow-y-auto" onClick={(e) => e.target === e.currentTarget && onClose()}>
      <motion.div
        initial={{ opacity: 0, scale: 0.96 }}
        animate={{ opacity: 1, scale: 1 }}
        className="w-full max-w-4xl rounded-3xl bg-card border border-border text-card-foreground shadow-2xl overflow-hidden flex flex-col md:flex-row max-h-[90vh] relative"
      >
        {/* Animated Live Key Verification & Diagnostic Overlay */}
        <AnimatePresence>
          {loading && (
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="absolute inset-0 z-50 flex flex-col items-center justify-center bg-background/95 backdrop-blur-xl p-8 text-center"
            >
              <div className="relative mb-6 flex h-20 w-20 items-center justify-center rounded-3xl bg-sky-500/20 text-sky-500 border border-sky-500/40 shadow-lg">
                <motion.div
                  animate={{ scale: [1, 1.6, 1], opacity: [0.8, 0, 0.8] }}
                  transition={{ repeat: Infinity, duration: 1.4 }}
                  className="absolute inset-0 rounded-3xl bg-sky-500/30"
                />
                <motion.div animate={{ rotate: 360 }} transition={{ repeat: Infinity, duration: 2, ease: "linear" }}>
                  <RefreshCw className="h-9 w-9 text-sky-500" />
                </motion.div>
              </div>

              <h3 className="text-lg font-extrabold tracking-tight text-foreground">
                Verifying & Testing {selectedEntry.label}...
              </h3>
              <p className="mt-2 text-xs text-muted-foreground max-w-sm">
                Pinging candidate endpoints to verify API key validity, context length, and live response latency.
              </p>

              {/* Signal Equalizer Waves */}
              <div className="mt-5 flex items-center gap-1.5">
                {[0, 1, 2, 3, 4, 5].map((bar) => (
                  <motion.span
                    key={bar}
                    animate={{ height: ["8px", "28px", "8px"] }}
                    transition={{ repeat: Infinity, duration: 0.7, delay: bar * 0.12 }}
                    className="w-1.5 rounded-full bg-sky-500"
                  />
                ))}
              </div>
            </motion.div>
          )}
        </AnimatePresence>

        {/* Left Column: Search & Select Provider (130+ Catalog) */}
        <div className="w-full md:w-80 border-r border-border bg-muted/20 p-4 flex flex-col">
          <p className="text-xs font-bold uppercase tracking-wider text-muted-foreground font-mono mb-2">
            Select Provider (130+)
          </p>

          <div className="relative mb-3">
            <Search className="absolute left-3 top-2.5 h-3.5 w-3.5 text-muted-foreground" />
            <Input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search providers..."
              className="h-8 pl-8 text-xs rounded-xl border-border bg-background text-foreground placeholder:text-muted-foreground"
            />
          </div>

          <div className="overflow-y-auto space-y-1.5 flex-1 pr-1 max-h-[500px]">
            {filteredCatalog.map((entry) => {
              const isSelected = selectedEntry.slug === entry.slug;
              return (
                <button
                  key={entry.slug}
                  onClick={() => selectCatalogItem(entry)}
                  className={`w-full flex items-center justify-between p-2.5 rounded-xl text-left text-xs transition-all ${
                    isSelected
                      ? "bg-sky-500/15 border border-sky-500/40 text-sky-600 dark:text-sky-400 font-bold shadow-sm"
                      : "hover:bg-accent text-foreground font-medium"
                  }`}
                >
                  <div className="flex items-center gap-2.5 truncate">
                    <ProviderLogo slug={entry.slug} name={entry.label} className="h-4 w-4 shrink-0" />
                    <span className="truncate">{entry.label}</span>
                  </div>
                  <ChevronRight className={`h-3.5 w-3.5 shrink-0 ${isSelected ? "text-sky-500" : "text-muted-foreground/40"}`} />
                </button>
              );
            })}
          </div>
        </div>

        {/* Right Column: Setup Steps & Provider-Specific Settings Form */}
        <div className="flex-1 p-6 overflow-y-auto flex flex-col justify-between bg-card text-card-foreground">
          <div>
            <div className="flex items-center justify-between border-b border-border pb-4 mb-5">
              <div className="flex items-center gap-3">
                <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-background border border-border shadow-sm">
                  <ProviderLogo slug={selectedEntry.slug} name={selectedEntry.label} className="h-6 w-6" />
                </div>
                <div>
                  <h3 className="text-base font-extrabold text-foreground flex items-center gap-2">
                    Configure {selectedEntry.label}
                  </h3>
                  <p className="text-xs text-muted-foreground">{selectedEntry.description}</p>
                </div>
              </div>
              <button onClick={onClose} className="p-1 rounded-lg text-muted-foreground hover:text-foreground hover:bg-accent transition-colors"><X className="h-5 w-5" /></button>
            </div>

            {/* Setup Instructions Box */}
            <div className="mb-5 rounded-2xl border border-sky-500/30 bg-sky-500/5 dark:bg-sky-500/10 p-4 text-xs">
              <div className="flex items-center justify-between mb-2">
                <p className="font-bold text-sky-600 dark:text-sky-400 flex items-center gap-1.5">
                  <HelpCircle className="h-4 w-4" /> Setup Instructions & Developer Portal
                </p>
                {selectedEntry.portalUrl && (
                  <a
                    href={selectedEntry.portalUrl}
                    target="_blank"
                    rel="noreferrer"
                    className="text-[0.7rem] font-bold text-sky-600 dark:text-sky-400 hover:underline flex items-center gap-1"
                  >
                    Open Developer Portal <ArrowUpRight className="h-3 w-3" />
                  </a>
                )}
              </div>

              <ol className="list-decimal list-inside space-y-1.5 text-muted-foreground leading-relaxed">
                {selectedEntry.steps.map((step, idx) => (
                  <li key={idx} className="text-foreground">
                    <span>{step}</span>
                  </li>
                ))}
              </ol>
            </div>

            {/* Provider Dynamic Settings Form */}
            <form onSubmit={handleConnectAndSave} className="space-y-4">
              <div className="grid gap-3 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <Label className="text-xs text-muted-foreground font-bold">Display Name</Label>
                  <Input
                    value={form.displayName}
                    onChange={(e) => setForm({ ...form, displayName: e.target.value })}
                    placeholder={selectedEntry.label}
                    className="h-9 text-xs rounded-xl border-border bg-background text-foreground"
                  />
                </div>
                <div className="space-y-1.5">
                  <Label className="text-xs text-muted-foreground font-bold">Slug Identifier</Label>
                  <Input
                    value={form.slug}
                    onChange={(e) => setForm({ ...form, slug: e.target.value.toLowerCase().replace(/\s+/g, "-") })}
                    placeholder={selectedEntry.slug}
                    className="h-9 text-xs rounded-xl border-border bg-background font-mono text-foreground"
                  />
                </div>
              </div>

              {/* DYNAMIC SETTINGS FOR AWS BEDROCK */}
              {selectedEntry.type === "AWS_BEDROCK" && (
                <>
                  <div className="space-y-1.5">
                    <Label className="text-xs text-muted-foreground font-bold">AWS Access Key ID</Label>
                    <Input
                      type="password"
                      value={form.apiKey}
                      onChange={(e) => setForm({ ...form, apiKey: e.target.value })}
                      placeholder="AKIA••••••••••••••••"
                      className="h-9 text-xs font-mono rounded-xl border-border bg-background text-foreground"
                      required
                    />
                  </div>
                  <div className="space-y-1.5">
                    <Label className="text-xs text-muted-foreground font-bold">AWS Secret Access Key</Label>
                    <Input
                      type="password"
                      value={form.secretKey}
                      onChange={(e) => setForm({ ...form, secretKey: e.target.value })}
                      placeholder="••••••••••••••••••••••••••••••••"
                      className="h-9 text-xs font-mono rounded-xl border-border bg-background text-foreground"
                      required
                    />
                  </div>
                  <div className="space-y-1.5">
                    <Label className="text-xs text-muted-foreground font-bold">AWS Region</Label>
                    <Input
                      type="text"
                      value={form.region}
                      onChange={(e) => setForm({ ...form, region: e.target.value })}
                      placeholder="us-east-1"
                      className="h-9 text-xs font-mono rounded-xl border-border bg-background text-foreground"
                      required
                    />
                  </div>
                </>
              )}

              {/* DYNAMIC SETTINGS FOR AZURE OPENAI */}
              {selectedEntry.type === "AZURE_OPENAI" && (
                <>
                  <div className="space-y-1.5">
                    <Label className="text-xs text-muted-foreground font-bold">Azure Resource Endpoint URL</Label>
                    <Input
                      type="text"
                      value={form.baseUrl}
                      onChange={(e) => setForm({ ...form, baseUrl: e.target.value })}
                      placeholder="https://your-resource.openai.azure.com"
                      className="h-9 text-xs font-mono rounded-xl border-border bg-background text-foreground"
                      required
                    />
                  </div>
                  <div className="space-y-1.5">
                    <Label className="text-xs text-muted-foreground font-bold">Azure API Key</Label>
                    <Input
                      type="password"
                      value={form.apiKey}
                      onChange={(e) => setForm({ ...form, apiKey: e.target.value })}
                      placeholder="••••••••••••••••••••••••••••••••"
                      className="h-9 text-xs font-mono rounded-xl border-border bg-background text-foreground"
                      required
                    />
                  </div>
                  <div className="space-y-1.5">
                    <Label className="text-xs text-muted-foreground font-bold">API Version</Label>
                    <Input
                      type="text"
                      value={form.apiVersion}
                      onChange={(e) => setForm({ ...form, apiVersion: e.target.value })}
                      placeholder="2024-02-15-preview"
                      className="h-9 text-xs font-mono rounded-xl border-border bg-background text-foreground"
                    />
                  </div>
                </>
              )}

              {/* DYNAMIC SETTINGS FOR OLLAMA */}
              {selectedEntry.slug === "ollama" && (
                <>
                  <div className="space-y-1.5">
                    <Label className="text-xs text-muted-foreground font-bold">Ollama Base URL</Label>
                    <Input
                      type="text"
                      value={form.baseUrl}
                      onChange={(e) => setForm({ ...form, baseUrl: e.target.value })}
                      placeholder="http://localhost:11434/v1"
                      className="h-9 text-xs font-mono rounded-xl border-border bg-background text-foreground"
                      required
                    />
                  </div>
                  <div className="space-y-1.5">
                    <Label className="text-xs text-muted-foreground font-bold">Auth Token (Optional)</Label>
                    <Input
                      type="password"
                      value={form.apiKey}
                      onChange={(e) => setForm({ ...form, apiKey: e.target.value })}
                      placeholder="Optional Bearer token"
                      className="h-9 text-xs font-mono rounded-xl border-border bg-background text-foreground"
                    />
                  </div>
                </>
              )}

              {/* DYNAMIC SETTINGS FOR STANDARD OPENAI / COMPATIBLE / GEMINI / ANTHROPIC */}
              {selectedEntry.type !== "AWS_BEDROCK" && selectedEntry.type !== "AZURE_OPENAI" && selectedEntry.slug !== "ollama" && (
                <>
                  <div className="space-y-1.5">
                    <Label className="text-xs text-muted-foreground font-bold flex items-center justify-between">
                      <span>API Key</span>
                      {selectedEntry.keyPrefix && (
                        <span className="text-[0.65rem] text-sky-600 dark:text-sky-400 font-mono font-normal">Expected prefix: {selectedEntry.keyPrefix}</span>
                      )}
                    </Label>
                    <Input
                      type="password"
                      value={form.apiKey}
                      onChange={(e) => setForm({ ...form, apiKey: e.target.value })}
                      placeholder={selectedEntry.keyPrefix || "Paste API key here..."}
                      className="h-10 text-xs font-mono rounded-xl border-border bg-background text-foreground"
                      required
                    />
                  </div>

                  <div className="space-y-1.5">
                    <Label className="text-xs text-muted-foreground font-bold flex items-center justify-between">
                      <span>Base URL Endpoint (Optional Override)</span>
                      <span className="text-[0.65rem] text-muted-foreground font-mono font-normal">OpenAI Compatible Format</span>
                    </Label>
                    <Input
                      type="text"
                      value={form.baseUrl}
                      onChange={(e) => setForm({ ...form, baseUrl: e.target.value })}
                      placeholder={selectedEntry.baseUrl || "https://api.provider.com/v1"}
                      className="h-9 text-xs font-mono rounded-xl border-border bg-background text-foreground"
                    />
                  </div>

                  {selectedEntry.slug === "openai" && (
                    <div className="space-y-1.5">
                      <Label className="text-xs text-muted-foreground font-bold">Organization ID (Optional)</Label>
                      <Input
                        type="text"
                        value={form.organization}
                        onChange={(e) => setForm({ ...form, organization: e.target.value })}
                        placeholder="org-••••••••"
                        className="h-9 text-xs font-mono rounded-xl border-border bg-background text-foreground"
                      />
                    </div>
                  )}
                </>
              )}

              {testResult && (
                <div className={`p-3 rounded-xl text-xs flex items-center gap-2 ${
                  testResult.status === "SUCCESS" ? "bg-emerald-500/10 border border-emerald-500/30 text-emerald-600 dark:text-emerald-400 font-bold" : "bg-amber-500/10 border border-amber-500/30 text-amber-600 dark:text-amber-400 font-bold"
                }`}>
                  <CircleCheck className="h-4 w-4 shrink-0" />
                  <span>{testResult.message}</span>
                </div>
              )}

              <div className="flex gap-2 pt-3">
                <Button type="submit" disabled={loading} className="flex-1 grad-primary h-10 rounded-xl text-xs text-white font-bold shadow-md">
                  {loading ? (
                    <span className="flex items-center gap-2"><Loader2 className="h-4 w-4 animate-spin" /> Verifying Endpoint & Saving Key...</span>
                  ) : (
                    <span className="flex items-center gap-2"><Zap className="h-4 w-4 text-amber-300" /> Verify & Save Key</span>
                  )}
                </Button>
                <Button type="button" onClick={onClose} variant="outline" className="h-10 rounded-xl text-xs border-border">Cancel</Button>
              </div>
            </form>
          </div>

          <div className="mt-6 border-t border-border pt-3 text-[0.7rem] text-muted-foreground flex items-center justify-between font-mono">
            <span>Enterprise BYOK Security Active</span>
            <span>AES-256 Encrypted Store</span>
          </div>
        </div>
      </motion.div>
    </div>
  );
}

/** Premium Glassmorphic Update Key Modal */
function UpdateKeyModal({
  slug,
  onClose,
  onSubmit,
}: {
  slug: string;
  onClose: () => void;
  onSubmit: (apiKey: string) => void;
}) {
  const [apiKey, setApiKey] = useState("");
  const [showKey, setShowKey] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!apiKey.trim()) return;
    setSubmitting(true);
    try {
      await onSubmit(apiKey.trim());
    } finally {
      setSubmitting(false);
    }
  };

  const displayName = slug.toUpperCase();

  return (
    <div
      className="fixed inset-0 z-[9999] flex items-center justify-center p-4 bg-black/60 backdrop-blur-md overflow-y-auto"
      onClick={(e) => e.target === e.currentTarget && onClose()}
    >
      <motion.div
        initial={{ opacity: 0, scale: 0.96 }}
        animate={{ opacity: 1, scale: 1 }}
        exit={{ opacity: 0, scale: 0.96 }}
        className="w-full max-w-md rounded-3xl bg-card border border-border text-card-foreground shadow-2xl p-6 relative overflow-hidden"
      >
        {/* Glow ambient header accent */}
        <div className="absolute -top-24 -left-24 w-48 h-48 bg-sky-500/10 rounded-full blur-3xl pointer-events-none" />

        <div className="flex items-center justify-between border-b border-border pb-4 mb-5 relative z-10">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-2xl bg-background border border-border shadow-sm">
              <ProviderLogo slug={slug} name={displayName} className="h-5 w-5" />
            </div>
            <div>
              <h3 className="text-sm font-extrabold text-foreground flex items-center gap-2">
                Update Key for {displayName}
              </h3>
              <p className="text-[0.7rem] text-muted-foreground">Configure new API credentials for this provider</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-xl text-muted-foreground hover:text-foreground hover:bg-accent transition-colors"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4 relative z-10">
          <div className="space-y-1.5">
            <Label className="text-xs font-bold text-foreground flex items-center justify-between">
              <span>New API Key</span>
              <button
                type="button"
                onClick={() => setShowKey(!showKey)}
                className="text-[0.65rem] text-sky-600 dark:text-sky-400 hover:underline font-mono font-normal"
              >
                {showKey ? "Hide Key" : "Show Key"}
              </button>
            </Label>
            <div className="relative">
              <Input
                type={showKey ? "text" : "password"}
                value={apiKey}
                onChange={(e) => setApiKey(e.target.value)}
                placeholder={`Paste new ${displayName} API key...`}
                className="h-10 text-xs font-mono rounded-xl bg-background border-border text-foreground placeholder:text-muted-foreground focus:border-sky-500 focus:ring-1 focus:ring-sky-500 pr-10"
                required
                autoFocus
              />
              <Key className="absolute right-3 top-3 h-4 w-4 text-muted-foreground pointer-events-none" />
            </div>
          </div>

          <div className="flex gap-2 pt-2">
            <Button
              type="submit"
              disabled={submitting || !apiKey.trim()}
              className="flex-1 grad-primary h-10 rounded-xl text-xs font-bold text-white shadow-md hover:brightness-110"
            >
              {submitting ? (
                <span className="flex items-center gap-2">
                  <Loader2 className="h-4 w-4 animate-spin" /> Verifying & Saving...
                </span>
              ) : (
                "Save & Update Key"
              )}
            </Button>
            <Button
              type="button"
              onClick={onClose}
              variant="outline"
              className="h-10 text-xs rounded-xl border-border text-foreground hover:bg-accent"
            >
              Cancel
            </Button>
          </div>
        </form>
      </motion.div>
    </div>
  );
}
