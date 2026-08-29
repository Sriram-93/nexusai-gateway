import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { Database, Plus, Trash2, Search, FileText, Sparkles, RefreshCw, Upload } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ragApi, type KnowledgeChunk } from "@/lib/api";
import { useToast } from "@/lib/toast";

export const Route = createFileRoute("/app/rag")({
  head: () => ({
    meta: [
      { title: "RAG Vector Knowledge Base — NexusAI" },
      { name: "description", content: "Manage and search semantic vector document chunks in the NexusAI RAG index." },
    ],
  }),
  component: KnowledgeBaseStudio,
});

function KnowledgeBaseStudio() {
  const [chunks, setChunks] = useState<KnowledgeChunk[]>([]);
  const [loading, setLoading] = useState(true);
  const [docName, setDocName] = useState("");
  const [content, setContent] = useState("");
  const [searchQuery, setSearchQuery] = useState("");
  const [searchResults, setSearchResults] = useState<KnowledgeChunk[] | null>(null);
  const [isIngesting, setIsIngesting] = useState(false);
  const [isSearching, setIsSearching] = useState(false);
  const { success, error } = useToast();

  const fetchChunks = async () => {
    setLoading(true);
    try {
      const data = await ragApi.getChunks();
      setChunks(data);
    } catch (err: any) {
      error("Failed to load vector chunks", err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchChunks();
  }, []);

  const handleIngest = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!content.trim()) return;

    setIsIngesting(true);
    try {
      await ragApi.ingestChunk({
        documentName: docName.trim() || "Enterprise_Document.md",
        content: content.trim(),
        metadata: { category: "custom", source: "RAG Studio UI" },
      });
      success("Chunk Ingested", "Document successfully embedded and added to vector store.");
      setDocName("");
      setContent("");
      fetchChunks();
    } catch (err: any) {
      error("Ingestion Failed", err.message);
    } finally {
      setIsIngesting(false);
    }
  };

  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!searchQuery.trim()) {
      setSearchResults(null);
      return;
    }

    setIsSearching(true);
    try {
      const results = await ragApi.search(searchQuery);
      setSearchResults(results);
    } catch (err: any) {
      error("Search Failed", err.message);
    } finally {
      setIsSearching(false);
    }
  };

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    
    setDocName(file.name);
    const reader = new FileReader();
    reader.onload = (ev) => {
      setContent(ev.target?.result as string);
    };
    reader.readAsText(file);
    e.target.value = ''; // Reset input
  };

  const handleDelete = async (id: string) => {
    try {
      await ragApi.deleteChunk(id);
      success("Chunk Deleted", `Knowledge chunk ${id} removed from index.`);
      fetchChunks();
      if (searchResults) {
        setSearchResults((prev) => prev?.filter((c) => c.id !== id) || null);
      }
    } catch (err: any) {
      error("Deletion Failed", err.message);
    }
  };

  const displayChunks = searchResults !== null ? searchResults : chunks;

  return (
    <AppShell title="RAG Knowledge Studio" subtitle="Semantic Vector Store & Ingestion Control Plane">
      <div className="grid gap-5 lg:grid-cols-[1.2fr_1fr]">
        {/* Left: Knowledge Index */}
        <div className="space-y-4">
          <div className="section-panel">
            <div className="section-panel-header">
              <div className="flex items-center gap-2">
                <Database className="h-4 w-4 text-cyan" />
                <h3 className="text-[0.8125rem] font-semibold tracking-tight">
                  Vector Index
                  <span className="ml-1.5 text-muted-foreground font-normal">({displayChunks.length} chunks)</span>
                </h3>
              </div>
              <Button onClick={fetchChunks} variant="outline" size="sm" className="h-7 text-xs gap-1 rounded-lg border-border">
                <RefreshCw className={`h-3 w-3 ${loading ? "animate-spin" : ""}`} /> Sync
              </Button>
            </div>

            <div className="p-4">
              {/* Semantic Search */}
              <form onSubmit={handleSearch} className="mb-4 flex gap-2">
                <div className="relative flex-1">
                  <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
                  <Input
                    placeholder="Semantic vector search query..."
                    value={searchQuery}
                    onChange={(e) => {
                      setSearchQuery(e.target.value);
                      if (!e.target.value.trim()) setSearchResults(null);
                    }}
                    className="h-9 pl-9 text-xs rounded-lg border-border bg-background"
                  />
                </div>
                <Button type="submit" disabled={isSearching} size="sm" className="grad-primary h-9 text-xs rounded-lg px-4 text-primary-foreground">
                  {isSearching ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : "Search"}
                </Button>
              </form>

              {/* Chunks List */}
              {loading && (
                <div className="space-y-2">
                  {[0, 1, 2].map((i) => (
                    <div key={i} className="skeleton h-20 w-full" />
                  ))}
                </div>
              )}

              {!loading && displayChunks.length === 0 && (
                <div className="flex flex-col items-center py-10 text-center border border-dashed border-[var(--glass-border)] rounded-xl">
                  <Database className="h-8 w-8 text-muted-foreground/20 mb-3" />
                  <p className="text-[0.8125rem] text-muted-foreground">
                    {searchResults !== null ? "No relevant knowledge found" : "No knowledge chunks in index"}
                  </p>
                  <p className="text-[0.6875rem] text-muted-foreground/60 mt-1">
                    {searchResults !== null
                      ? "Try a different query or broader search terms."
                      : "Ingest your first document to populate the vector store."}
                  </p>
                </div>
              )}

              {!loading && displayChunks.length > 0 && (
                <div className="space-y-2.5 max-h-[550px] overflow-y-auto pr-1">
                  {displayChunks.map((chunk) => (
                    <div key={chunk.id} className="p-3.5 rounded-xl border border-[var(--glass-border)] bg-[var(--surface-subtle)] space-y-2">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <FileText className="h-3.5 w-3.5 text-indigo" />
                          <span className="font-semibold text-xs text-foreground">{chunk.documentName}</span>
                          <span className="font-mono text-[0.6rem] text-muted-foreground/50">#{chunk.id}</span>
                        </div>
                        <Button
                          onClick={() => handleDelete(chunk.id)}
                          variant="ghost"
                          size="icon"
                          className="h-6 w-6 text-muted-foreground hover:text-destructive"
                        >
                          <Trash2 className="h-3.5 w-3.5" />
                        </Button>
                      </div>

                      <p className="text-xs text-foreground/80 leading-relaxed line-clamp-3 bg-[var(--surface-inset)] p-3 rounded-lg border border-[var(--glass-border)]">
                        {chunk.content}
                      </p>

                      {chunk.similarityScore !== undefined && chunk.similarityScore < 1.0 && (
                        <div className="flex items-center gap-1.5 text-[0.625rem] font-mono text-emerald">
                          <Sparkles className="h-3 w-3" /> Cosine Similarity: {(chunk.similarityScore * 100).toFixed(1)}%
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Right: Ingest Panel */}
        <div>
          <div className="section-panel sticky top-20">
            <div className="section-panel-header">
              <div className="flex items-center gap-2">
                <Plus className="h-4 w-4 text-emerald" />
                <h3 className="text-[0.8125rem] font-semibold tracking-tight">Ingest Document Chunk</h3>
              </div>
            </div>
            <div className="p-5 space-y-4">
              <p className="text-[0.6875rem] text-muted-foreground">
                Add new enterprise documentation to the in-memory vector database for real-time RAG context retrieval.
              </p>

              <form onSubmit={handleIngest} className="space-y-4">
                <div className="space-y-1.5">
                  <label className="text-[0.6875rem] font-medium text-muted-foreground">Document Name</label>
                  <Input
                    placeholder="e.g., Security_Compliance_Guide_2026.md"
                    value={docName}
                    onChange={(e) => setDocName(e.target.value)}
                    className="h-9 text-xs rounded-lg border-border bg-background"
                  />
                </div>

                <div className="space-y-1.5">
                  <div className="flex items-center justify-between">
                    <label className="text-[0.6875rem] font-medium text-muted-foreground">Content</label>
                    <div className="flex items-center gap-2">
                      <label className="cursor-pointer flex items-center gap-1 text-[0.65rem] font-medium text-emerald bg-emerald/10 hover:bg-emerald/20 px-2 py-0.5 rounded transition-colors">
                        <Upload className="h-3 w-3" /> Upload File
                        <input type="file" accept=".txt,.md,.json,.csv" className="hidden" onChange={handleFileUpload} />
                      </label>
                      {content.length > 0 && (
                        <span className="text-[0.6rem] text-muted-foreground/50 font-mono">{content.length} chars</span>
                      )}
                    </div>
                  </div>
                  <textarea
                    rows={8}
                    placeholder="Paste raw documentation text or markdown chunk here..."
                    value={content}
                    onChange={(e) => setContent(e.target.value)}
                    className="w-full p-3 text-xs rounded-lg border border-border bg-[var(--surface-subtle)] text-foreground focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary resize-none font-mono"
                  />
                </div>

                <Button
                  type="submit"
                  disabled={isIngesting || !content.trim()}
                  className="grad-primary w-full h-10 rounded-xl text-xs font-medium text-primary-foreground gap-1.5"
                >
                  {isIngesting ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <Plus className="h-3.5 w-3.5" />}
                  Ingest Chunk into Index
                </Button>
              </form>
            </div>
          </div>
        </div>
      </div>
    </AppShell>
  );
}
