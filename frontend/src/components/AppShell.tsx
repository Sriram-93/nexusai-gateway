import { Link, useRouterState, useNavigate } from "@tanstack/react-router";
import { motion, AnimatePresence } from "motion/react";
import {
  Activity, Hexagon, LogOut, Bell, ChevronRight, Layers,
} from "lucide-react";
import type { ReactNode } from "react";
import { useState, useEffect } from "react";
import { MeshBackground } from "./MeshBackground";
import { ThemeToggle } from "./ThemeToggle";
import { useUser, type UserRole } from "@/lib/user-context";
import { tenantApi } from "@/lib/api";
import { getNavigation } from "@/lib/navigation-config";

const ROLE_LABELS: Record<UserRole, string> = {
  SUPER_ADMIN: "Platform Admin",
  ORG_ADMIN: "Admin",
  TEAM_LEAD: "Team Lead",
  TEAM_MEMBER: "Member",
  SOLO: "Solo",
};

const TIER_LABELS: Record<string, string> = {
  SOLO: "Solo Developer",
  ADMINISTRATION: "Enterprise",
};

export function AppShell({
  title,
  subtitle,
  children,
}: {
  title: string;
  subtitle?: string;
  children: ReactNode;
}) {
  const pathname = useRouterState({ select: (s) => s.location.pathname });
  const navigate = useNavigate();
  const { session, logout } = useUser();
  const role = session.role ?? "SOLO";
  const [showNotifications, setShowNotifications] = useState(false);
  const [isLocked, setIsLocked] = useState(false);

  useEffect(() => {
    if (session.tenantId) {
      tenantApi.getTenant(session.tenantId).then(t => {
        if (t.hasApiKey === false && role === "SOLO") setIsLocked(true);
      }).catch(console.error);
    }
  }, [session.tenantId, role]);

  const navSections = getNavigation(role, isLocked);

  const handleLogout = () => {
    logout();
    navigate({ to: "/" });
  };

  const pendingCount = session.pendingRequests ?? 0;

  // Compute context breadcrumb
  let breadcrumb = "NexusAI";
  if (role === "SUPER_ADMIN") breadcrumb = "NexusAI Control Center";
  else if (role === "SOLO") breadcrumb = "My Workspace";
  else if (role === "ORG_ADMIN") breadcrumb = session.orgName ? `${session.orgName} ▼` : "Organization ▼";
  else if (role === "TEAM_LEAD" || role === "TEAM_MEMBER") {
    breadcrumb = `${session.orgName ?? "Organization"} / ${session.teamName ?? "Team"}`;
  }

  return (
    <div className="min-h-screen bg-background text-foreground">
      <MeshBackground />

      {/* Sidebar */}
      <aside className="glass-strong fixed inset-y-0 left-0 z-30 hidden w-[16.5rem] flex-col border-r px-4 py-6 lg:flex">
        <Link to={"/app" as never} className="mb-8 flex items-center gap-3 px-2">
          <span className="grad-primary flex h-9 w-9 items-center justify-center rounded-xl shadow-lg">
            <Hexagon className="h-4.5 w-4.5 text-primary-foreground" />
          </span>
          <span className="leading-tight">
            <span className="block text-sm font-semibold tracking-tight">NexusAI</span>
            <span className="block text-[0.68rem] uppercase tracking-[0.16em] text-muted-foreground">
              Gateway
            </span>
          </span>
        </Link>

        <nav className="flex-1 space-y-7 overflow-y-auto">
          {navSections.map((section) => (
            <div key={section.group}>
              <p className="mb-2 px-2 text-[0.65rem] font-medium uppercase tracking-[0.18em] text-muted-foreground">
                {section.group}
              </p>
              <ul className="space-y-1">
                {section.items.map((item) => {
                  const active = item.exact
                    ? pathname === item.to
                    : pathname.startsWith(item.to);
                  return (
                    <li key={item.to} className="relative">
                      {active && (
                        <motion.span
                          layoutId="nav-active"
                          transition={{ type: "spring", stiffness: 420, damping: 34 }}
                          className="absolute inset-y-0 left-0 w-[2px] rounded-full bg-cyan"
                          style={{ boxShadow: "0 0 14px 2px var(--cyan)" }}
                        />
                      )}
                      <Link
                        to={item.to as never}
                        className={`group flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition-all duration-200 ${
                          active
                            ? "bg-[var(--glass-hover)] font-medium text-cyan"
                            : "text-muted-foreground hover:translate-x-0.5 hover:bg-[var(--glass-hover)] hover:text-foreground"
                        } ${item.disabled ? "opacity-40 pointer-events-none" : ""}`}
                      >
                        <item.icon className="h-4 w-4" />
                        {item.label}
                      </Link>
                    </li>
                  );
                })}
              </ul>
            </div>
          ))}
        </nav>

        {/* Plan badge */}
        <div className="glass mt-6 rounded-xl p-3">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-xs font-semibold">
                {TIER_LABELS[session.tier ?? "SOLO"]} · {ROLE_LABELS[role]}
              </p>
              <p className="mt-0.5 text-[0.68rem] text-muted-foreground truncate">
                {session.orgName ?? session.email ?? "Personal Workspace"}
              </p>
            </div>
          </div>
          <button
            onClick={handleLogout}
            className="mt-2.5 flex w-full items-center gap-1.5 rounded-lg px-2 py-1.5 text-[0.7rem] text-muted-foreground transition-colors hover:bg-[var(--glass-hover)] hover:text-destructive"
          >
            <LogOut className="h-3 w-3" /> Sign out
          </button>
        </div>
      </aside>

      {/* Content */}
      <div className="lg:pl-[16.5rem]">
        <header className="glass sticky top-0 z-20 flex flex-wrap items-center justify-between gap-3 border-b px-5 py-4 sm:px-8">
          <div className="flex flex-col gap-1">
            <span className="text-xs font-medium text-cyan">{breadcrumb}</span>
            <div>
              <h1 className="text-lg font-semibold tracking-tight">{title}</h1>
              {subtitle && <p className="text-xs text-muted-foreground">{subtitle}</p>}
            </div>
          </div>
          <div className="flex items-center gap-3">
            <span className="glass flex items-center gap-2 rounded-full px-3 py-1.5 text-xs font-medium">
              <span className="relative flex h-2 w-2">
                <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald opacity-75" />
                <span className="relative inline-flex h-2 w-2 rounded-full bg-emerald" />
              </span>
              System Operational
            </span>

            {/* Notification bell — visible to SUPER_ADMIN, ORG_ADMIN, and TEAM_LEAD */}
            {(role === "SUPER_ADMIN" || role === "ORG_ADMIN" || role === "TEAM_LEAD") && (
              <div className="relative">
                <button
                  onClick={() => setShowNotifications((v) => !v)}
                  className="glass flex h-9 w-9 items-center justify-center rounded-xl transition-colors hover:bg-[var(--glass-hover)]"
                >
                  <Bell className="h-4 w-4" />
                  {pendingCount > 0 && (
                    <span className="absolute -right-1 -top-1 flex h-4.5 w-4.5 items-center justify-center rounded-full bg-amber text-[0.6rem] font-bold text-black">
                      {pendingCount}
                    </span>
                  )}
                </button>

                <AnimatePresence>
                  {showNotifications && (
                    <motion.div
                      initial={{ opacity: 0, y: -8, scale: 0.95 }}
                      animate={{ opacity: 1, y: 0, scale: 1 }}
                      exit={{ opacity: 0, y: -8, scale: 0.95 }}
                      transition={{ type: "spring", stiffness: 380, damping: 30 }}
                      className="glass-strong absolute right-0 top-full mt-2 w-72 rounded-2xl p-4 shadow-2xl border border-[var(--glass-border)]"
                    >
                      <p className="text-xs font-semibold uppercase tracking-widest text-muted-foreground mb-3">
                        Notifications
                      </p>
                      {pendingCount === 0 ? (
                        <p className="text-sm text-muted-foreground text-center py-4">All clear — no pending alerts.</p>
                      ) : (
                        <div className="space-y-2">
                          <div className="flex items-start gap-3 rounded-xl border border-amber/30 bg-amber/10 p-3">
                            <Layers className="h-4 w-4 text-amber mt-0.5 shrink-0" />
                            <div>
                              <p className="text-xs font-medium text-amber">{pendingCount} Action{pendingCount > 1 ? "s" : ""} Required</p>
                              <p className="text-[0.68rem] text-muted-foreground mt-0.5">Please review pending requests or alerts.</p>
                              <Link
                                to="/app"
                                onClick={() => setShowNotifications(false)}
                                className="mt-1.5 flex items-center gap-1 text-[0.68rem] text-cyan hover:underline"
                              >
                                Review <ChevronRight className="h-3 w-3" />
                              </Link>
                            </div>
                          </div>
                        </div>
                      )}
                    </motion.div>
                  )}
                </AnimatePresence>
              </div>
            )}

            <div className="glass flex items-center gap-2 rounded-full px-3 py-1.5 text-xs text-muted-foreground">
              {session.email}
            </div>

            <ThemeToggle />
          </div>
        </header>

        {/* Mobile nav */}
        <nav className="relative z-10 flex gap-2 overflow-x-auto px-5 py-3 lg:hidden">
          {navSections.flatMap((s) => s.items).map((item) => (
            <Link
              key={item.to}
              to={item.to as never}
              className={`glass whitespace-nowrap rounded-full px-3 py-1.5 text-xs text-muted-foreground ${item.disabled ? "opacity-40 pointer-events-none" : ""}`}
              activeProps={{ className: "glass whitespace-nowrap rounded-full px-3 py-1.5 text-xs text-cyan" }}
            >
              {item.label}
            </Link>
          ))}
        </nav>

        <motion.main
          key={pathname}
          initial={{ opacity: 0, y: 14 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, ease: [0.22, 1, 0.36, 1] }}
          className="relative z-10 px-5 py-6 sm:px-8 sm:py-8"
        >
          <div className="mx-auto max-w-[86rem]">{children}</div>
        </motion.main>

        <footer className="relative z-10 flex items-center gap-2 px-5 pb-8 text-xs text-muted-foreground sm:px-8">
          <Activity className="h-3.5 w-3.5" /> NexusAI Gateway · region us-east-1 · v3.0.0
        </footer>
      </div>
    </div>
  );
}
