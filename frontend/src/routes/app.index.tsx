import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { dashboardApi, tenantApi, getBaseUrl, type GlobalMetrics, type ActivityLog, type StreamEvent } from "@/lib/api";
import { useUser } from "@/lib/user-context";
import { useUpgradeRequests } from "@/lib/upgrade-requests";
import { 
  PlatformDashboard, OrgDashboard, TeamDashboard, 
  DeveloperDashboard, SoloDashboard 
} from "@/components/dashboards/RoleDashboards";

export const Route = createFileRoute("/app/")({
  head: () => ({
    meta: [
      { title: "Dashboard — NexusAI" },
      {
        name: "description",
        content: "Live NexusAI gateway telemetry: active agents, request volume, median latency, token spend, and a streaming activity feed.",
      },
    ],
  }),
  component: DashboardSwitch,
});

function DashboardSwitch() {
  const { session } = useUser();
  const { openModal } = useUpgradeRequests();
  const navigate = useNavigate();
  const role = session.role ?? "SOLO";

  const [logs, setLogs] = useState<(ActivityLog | StreamEvent)[]>([]);
  const [live, setLive] = useState(true);
  const [metrics, setMetrics] = useState<GlobalMetrics | null>(null);
  const [metricsError, setMetricsError] = useState<string | null>(null);
  const [tenant, setTenant] = useState<any>(null);

  useEffect(() => {
    dashboardApi.getActivity()
      .then((data) => setLogs(data))
      .catch(console.error);
    if (session.tenantId) {
      tenantApi.getTenant(session.tenantId)
        .then((t) => {
          setTenant(t);
          if (t.hasApiKey === false && role === "SOLO") {
            navigate({ to: "/app/providers" });
          }
        })
        .catch(console.error);
    }

    async function fetchMetrics() {
      try {
        const m = await dashboardApi.getMetrics();
        setMetrics(m);
        setMetricsError(null);
      } catch (e: any) {
        setMetricsError(e.message ?? "Unable to reach backend");
      }
    }
    fetchMetrics();
    const id = window.setInterval(fetchMetrics, 8000);
    return () => window.clearInterval(id);
  }, []);

  useEffect(() => {
    if (!live) return;
    const eventSource = new EventSource(`${getBaseUrl()}/api/dashboard/stream`);
    eventSource.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        setLogs((prev) => {
          if (prev.some((log) => (log as any).id === data.id)) return prev;
          return [data, ...prev].slice(0, 50);
        });
      } catch (err) { console.error("SSE parse error", err); }
    };
    return () => eventSource.close();
  }, [live]);

  const props = {
    metrics,
    logs,
    live,
    setLive,
    tenant,
    openModal,
    session
  };

  switch (role) {
    case "SUPER_ADMIN": return <PlatformDashboard {...props} />;
    case "ORG_ADMIN": return <OrgDashboard {...props} />;
    case "TEAM_LEAD": return <TeamDashboard {...props} />;
    case "TEAM_MEMBER": return <DeveloperDashboard {...props} />;
    case "SOLO": return <SoloDashboard {...props} />;
    default: return <DeveloperDashboard {...props} />;
  }
}
