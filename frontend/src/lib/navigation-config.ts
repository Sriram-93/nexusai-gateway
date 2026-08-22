import { ComponentType } from "react";
import {
  Activity, BarChart3, Boxes, FlaskConical, Hexagon, KeyRound,
  LayoutDashboard, Route as RouteIcon, ScrollText, ShieldAlert, Network,
  TestTubeDiagonal, Database, Users, Building2, Server
} from "lucide-react";
import type { UserRole } from "./user-context";

export type NavItem = {
  to: string;
  label: string;
  icon: ComponentType<{ className?: string }>;
  exact?: boolean;
  disabled?: boolean;
};

export type NavSection = {
  group: string;
  items: NavItem[];
};

export function getNavigation(role: UserRole, isLocked: boolean): NavSection[] {
  let nav: NavSection[] = [];

  switch (role) {
    case "SUPER_ADMIN":
      nav = [
        {
          group: "PLATFORM",
          items: [
            { to: "/app", label: "Overview", icon: LayoutDashboard, exact: true },
            { to: "/app/organizations", label: "Organizations", icon: Building2 },
            { to: "/app/providers", label: "Providers", icon: Boxes },
            { to: "/app/models", label: "Models", icon: Database },
          ],
        },
        {
          group: "OPERATIONS",
          items: [
            { to: "/app/security", label: "Security", icon: ShieldAlert },
            { to: "/app/logs", label: "Audit Logs", icon: ScrollText },
          ],
        },
      ];
      break;

    case "ORG_ADMIN":
      nav = [
        {
          group: "ORGANIZATION",
          items: [
            { to: "/app", label: "Dashboard", icon: LayoutDashboard, exact: true },
            { to: "/app/analytics", label: "Analytics", icon: BarChart3 },
            { to: "/app/members", label: "Members", icon: Users },
            { to: "/app/teams", label: "Teams", icon: Network },
            { to: "/app/team-logs", label: "Org Logs", icon: ScrollText },
          ],
        },
        {
          group: "AI INFRASTRUCTURE",
          items: [
            { to: "/app/providers", label: "Providers", icon: Boxes },
            { to: "/app/models", label: "Models", icon: Database },
            { to: "/app/routing", label: "Routing", icon: RouteIcon },
            { to: "/app/keys", label: "API Keys", icon: KeyRound },
          ],
        },
        {
          group: "GOVERNANCE",
          items: [
            { to: "/app/security", label: "Security & Policies", icon: ShieldAlert },
          ],
        },
        {
          group: "DEVELOPER",
          items: [
            { to: "/app/sandbox", label: "Sandbox", icon: FlaskConical },
            { to: "/app/agents", label: "Agent Pipelines", icon: Network },
            { to: "/app/labs", label: "Benchmarking", icon: TestTubeDiagonal },
          ],
        },
      ];
      break;

    case "TEAM_LEAD":
      nav = [
        {
          group: "MY TEAM",
          items: [
            { to: "/app", label: "Dashboard", icon: LayoutDashboard, exact: true },
            { to: "/app/members", label: "Members", icon: Users },
            { to: "/app/analytics", label: "Team Analytics", icon: BarChart3 },
            { to: "/app/team-logs", label: "Team Logs", icon: ScrollText },
          ],
        },
        {
          group: "AI",
          items: [
            { to: "/app/models", label: "Models", icon: Database },
            { to: "/app/sandbox", label: "Sandbox", icon: FlaskConical },
            { to: "/app/keys", label: "API Keys", icon: KeyRound },
          ],
        },
        {
          group: "PERSONAL",
          items: [
            { to: "/app/logs", label: "My Logs", icon: ScrollText },
          ],
        },
      ];
      break;

    case "TEAM_MEMBER":
      nav = [
        {
          group: "WORKSPACE",
          items: [
            { to: "/app", label: "Dashboard", icon: LayoutDashboard, exact: true },
            { to: "/app/sandbox", label: "Sandbox", icon: FlaskConical },
          ],
        },
        {
          group: "AI",
          items: [
            { to: "/app/models", label: "Models", icon: Database },
            { to: "/app/keys", label: "API Keys", icon: KeyRound },
          ],
        },
        {
          group: "OBSERVABILITY",
          items: [
            { to: "/app/logs", label: "My Logs", icon: ScrollText },
          ],
        },
        {
          group: "DEVELOPER",
          items: [
            { to: "/app/agents", label: "Agent Pipelines", icon: Network },
            { to: "/app/labs", label: "Benchmarking", icon: TestTubeDiagonal },
          ],
        },
      ];
      break;

    case "SOLO":
    default:
      nav = [
        {
          group: "WORKSPACE",
          items: [
            { to: "/app", label: "Dashboard", icon: LayoutDashboard, exact: true },
            { to: "/app/sandbox", label: "Sandbox", icon: FlaskConical },
          ],
        },
        {
          group: "AI INFRASTRUCTURE",
          items: [
            { to: "/app/providers", label: "Providers", icon: Boxes },
            { to: "/app/models", label: "Models", icon: Database },
            { to: "/app/routing", label: "Routing", icon: RouteIcon },
            { to: "/app/keys", label: "API Keys", icon: KeyRound },
          ],
        },
        {
          group: "OBSERVABILITY",
          items: [
            { to: "/app/analytics", label: "Analytics", icon: BarChart3 },
            { to: "/app/logs", label: "My Logs", icon: ScrollText },
          ],
        },
        {
          group: "DEVELOPER",
          items: [
            { to: "/app/agents", label: "Agent Pipelines", icon: Network },
            { to: "/app/labs", label: "Benchmarking", icon: TestTubeDiagonal },
          ],
        },
      ];
      break;
  }

  // Filter out locked items if necessary
  if (isLocked) {
    const allowedLockedRoutes = ["/app", "/app/providers", "/app/keys"];
    return nav.map(section => ({
      ...section,
      items: section.items.map(item => ({
        ...item,
        disabled: !allowedLockedRoutes.includes(item.to)
      }))
    })).filter(section => section.items.length > 0);
  }

  return nav;
}
