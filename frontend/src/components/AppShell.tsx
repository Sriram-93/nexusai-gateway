import { Link, useRouterState, useNavigate } from "@tanstack/react-router";
import { motion, AnimatePresence } from "framer-motion";
import {
  Activity, Hexagon, LogOut, Bell, ChevronRight, Layers, Lock,
  Menu, X, Command, AlertTriangle
} from "lucide-react";
import type { ReactNode } from "react";
import { useState, useEffect } from "react";
import { MeshBackground } from "./MeshBackground";
import { ThemeToggle } from "./ThemeToggle";
import { useUser, type UserRole } from "@/lib/user-context";
import { tenantApi, keysApi, providersApi } from "@/lib/api";
import { getNavigation } from "@/lib/navigation-config";

import { NexusLogo } from "./NexusLogo";

const pageVariants = {
  initial: { opacity: 0, y: 12, scale: 0.995, filter: "blur(4px)" },
  animate: { 
    opacity: 1, 
    y: 0, 
    scale: 1, 
    filter: "blur(0px)",
    transition: { duration: 0.28, ease: [0.16, 1, 0.3, 1] } 
  },
  exit: { 
    opacity: 0, 
    y: -8, 
    scale: 0.995, 
    filter: "blur(2px)",
    transition: { duration: 0.18, ease: [0.7, 0, 0.84, 0] } 
  }
};

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
  const isNavigating = useRouterState({ select: (s) => s.status === "pending" });
  const navigate = useNavigate();
  const { session, logout } = useUser();
  const role = session.role ?? "SOLO";
  const [showNotifications, setShowNotifications] = useState(false);
  const [isLocked, setIsLocked] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [hasActiveProviderKey, setHasActiveProviderKey] = useState<boolean | null>(null);

  useEffect(() => {
    providersApi.getStatus().then((status) => {
      setHasActiveProviderKey(status.readyToChat);
    }).catch(() => setHasActiveProviderKey(true));
  }, [pathname]);

  useEffect(() => {
    const checkKeyStatus = () => {
      const storedKey = sessionStorage.getItem("nexus_api_key");
      if (storedKey) {
        setIsLocked(false);
        return;
      }
      keysApi.getKeys().then(keys => {
        const activeKeys = keys.filter((k: any) => k.status !== "REVOKED");
        if (activeKeys.length > 0) {
          setIsLocked(false);
          const firstKey = activeKeys[0] as any;
          if (firstKey?.rawSecretKey) {
            sessionStorage.setItem("nexus_api_key", firstKey.rawSecretKey);
          }
        } else {
          setIsLocked(true);
        }
      }).catch(() => {
        setIsLocked(false);
      });
    };

    checkKeyStatus();
    window.addEventListener("nexus_key_created", checkKeyStatus);
    return () => window.removeEventListener("nexus_key_created", checkKeyStatus);
  }, [session.tenantId, role]);

  // Close mobile sidebar on route change
  useEffect(() => { setMobileOpen(false); }, [pathname]);

  const navSections = getNavigation(role, isLocked);

  const handleLogout = () => {
    logout();
    navigate({ to: "/" });
  };

  const pendingCount = session.pendingRequests ?? 0;

  // Compute context breadcrumb
  let breadcrumb = "NexusAI";
  if (role === "SUPER_ADMIN") breadcrumb = "Control Center";
  else if (role === "SOLO") breadcrumb = "Personal Workspace";
  else if (role === "ORG_ADMIN") breadcrumb = session.orgName ? `${session.orgName}` : "Organization";
  else if (role === "TEAM_LEAD" || role === "TEAM_MEMBER") {
    breadcrumb = `${session.orgName ?? "Organization"} / ${session.teamName ?? "Team"}`;
  }

  const SidebarContent = () => (
    <>
      {/* Brand */}
      <Link to={"/app" as never} className="mb-7 flex items-center px-2 group transition-transform duration-200 hover:scale-[1.02]">
        <NexusLogo size={34} />
      </Link>

      {/* Nav */}
      <nav className="flex-1 space-y-5 overflow-y-auto px-0.5">
        {navSections.map((section) => (
          <div key={section.group}>
            <p className="mb-1.5 px-2.5 text-[0.5625rem] font-semibold uppercase tracking-[0.14em] text-muted-foreground/40">
              {section.group}
            </p>
            <ul className="space-y-0.5">
              {section.items.map((item: any) => {
                const active = item.exact
                  ? pathname === item.to
                  : pathname.startsWith(item.to);
                return (
                  <li key={item.to}>
                    <Link
                      to={item.to as never}
                      className={`sidebar-nav-item ${active ? "active" : ""} ${item.disabled ? "opacity-30 pointer-events-none" : ""}`}
                    >
                      <item.icon className={`h-[0.875rem] w-[0.875rem] shrink-0 ${active ? "text-cyan" : ""}`} />
                      <span>{item.label}</span>
                    </Link>
                  </li>
                );
              })}
            </ul>
          </div>
        ))}
      </nav>

      {/* Bottom account card */}
      <div className="mt-3 rounded-xl border border-[var(--glass-border)] bg-[var(--surface-subtle)] p-3">
        <div className="flex items-center gap-2.5">
          <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-[0.6875rem] font-bold text-primary">
            {(session.email ?? "U")[0]?.toUpperCase() ?? "U"}
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-[0.75rem] font-medium text-foreground truncate">
              {ROLE_LABELS[role]}
            </p>
            <p className="text-[0.625rem] text-muted-foreground truncate">
              {session.email ?? "Personal Workspace"}
            </p>
          </div>
        </div>
        <button
          onClick={handleLogout}
          className="mt-2.5 flex w-full items-center gap-1.5 rounded-md px-2 py-1.5 text-[0.6875rem] text-muted-foreground transition-colors hover:bg-destructive/10 hover:text-destructive"
        >
          <LogOut className="h-3 w-3" /> Sign out
        </button>
      </div>
    </>
  );

  return (
    <div className="min-h-screen bg-background text-foreground noise-overlay">
      <MeshBackground />

      {/* ──── Desktop Sidebar ──── */}
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-[15rem] flex-col border-r border-[var(--sidebar-border)] bg-[var(--sidebar)] px-3 py-4 lg:flex">
        <SidebarContent />
      </aside>

      {/* ──── Mobile Sidebar Drawer ──── */}
      <AnimatePresence>
        {mobileOpen && (
          <>
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="fixed inset-0 z-40 bg-black/50 backdrop-blur-sm lg:hidden"
              onClick={() => setMobileOpen(false)}
            />
            <motion.aside
              initial={{ x: -280 }}
              animate={{ x: 0 }}
              exit={{ x: -280 }}
              transition={{ type: "spring", stiffness: 400, damping: 34 }}
              className="fixed inset-y-0 left-0 z-50 flex w-[15rem] flex-col border-r border-[var(--sidebar-border)] bg-[var(--sidebar)] px-3 py-4 lg:hidden"
            >
              <SidebarContent />
            </motion.aside>
          </>
        )}
      </AnimatePresence>

      {/* ──── Main Content ──── */}
      <div className="lg:pl-[15rem]">
        {/* Header */}
        <header className="sticky top-0 z-20 flex items-center justify-between gap-3 border-b border-[var(--glass-border)] bg-[var(--background)]/85 backdrop-blur-xl px-5 py-2.5 sm:px-7">
          {/* Left: hamburger + breadcrumb + title */}
          <div className="flex items-center gap-3 min-w-0">
            <button
              onClick={() => setMobileOpen(true)}
              className="flex h-8 w-8 items-center justify-center rounded-lg border border-[var(--glass-border)] transition-colors hover:bg-[var(--glass-hover)] lg:hidden"
              aria-label="Open menu"
            >
              <Menu className="h-4 w-4 text-muted-foreground" />
            </button>
            <div className="flex flex-col gap-0 min-w-0">
              <div className="flex items-center gap-1.5 text-[0.625rem] font-medium text-muted-foreground tracking-wide">
                <span className="text-primary">{breadcrumb}</span>
                <ChevronRight className="h-2.5 w-2.5 text-muted-foreground/40" />
                <span className="text-foreground/60 truncate">{title}</span>
              </div>
              <h1 className="text-[1rem] font-semibold tracking-tight leading-tight truncate">{title}</h1>
              {subtitle && <p className="text-[0.6875rem] text-muted-foreground truncate hidden sm:block">{subtitle}</p>}
            </div>
          </div>

          {/* Right: status + actions */}
          <div className="flex items-center gap-2 shrink-0">
            {/* Status pill */}
            <span className="hidden sm:flex items-center gap-1.5 rounded-full border border-[var(--glass-border)] px-2.5 py-1 text-[0.6875rem] font-medium text-muted-foreground">
              <span className="relative flex h-1.5 w-1.5">
                <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald opacity-75" />
                <span className="relative inline-flex h-1.5 w-1.5 rounded-full bg-emerald" />
              </span>
              Operational
            </span>

            {/* Notification bell — visible to SUPER_ADMIN, ORG_ADMIN, and TEAM_LEAD */}
            {(role === "SUPER_ADMIN" || role === "ORG_ADMIN" || role === "TEAM_LEAD") && (
              <div className="relative">
                <button
                  onClick={() => setShowNotifications((v) => !v)}
                  className="flex h-8 w-8 items-center justify-center rounded-lg border border-[var(--glass-border)] transition-colors hover:bg-[var(--glass-hover)]"
                >
                  <Bell className="h-3.5 w-3.5 text-muted-foreground" />
                  {pendingCount > 0 && (
                    <span className="absolute -right-1 -top-1 flex h-4 w-4 items-center justify-center rounded-full bg-rose text-[0.55rem] font-bold text-white">
                      {pendingCount}
                    </span>
                  )}
                </button>

                <AnimatePresence>
                  {showNotifications && (
                    <motion.div
                      initial={{ opacity: 0, y: -6, scale: 0.97 }}
                      animate={{ opacity: 1, y: 0, scale: 1 }}
                      exit={{ opacity: 0, y: -6, scale: 0.97 }}
                      transition={{ type: "spring", stiffness: 400, damping: 28 }}
                      className="absolute right-0 top-full mt-2 w-72 rounded-xl border border-[var(--glass-border)] bg-[var(--card)] p-4 shadow-2xl"
                    >
                      <p className="text-[0.5625rem] font-semibold uppercase tracking-widest text-muted-foreground mb-3">
                        Notifications
                      </p>
                      {pendingCount === 0 ? (
                        <p className="text-[0.75rem] text-muted-foreground text-center py-4">No pending alerts.</p>
                      ) : (
                        <div className="space-y-2">
                          <div className="flex items-start gap-3 rounded-lg border border-amber/20 bg-amber/5 p-3">
                            <Layers className="h-3.5 w-3.5 text-amber mt-0.5 shrink-0" />
                            <div>
                              <p className="text-[0.75rem] font-medium text-amber">{pendingCount} Action{pendingCount > 1 ? "s" : ""} Required</p>
                              <p className="text-[0.6875rem] text-muted-foreground mt-0.5">Review pending requests or alerts.</p>
                              <Link
                                to="/app"
                                onClick={() => setShowNotifications(false)}
                                className="mt-1.5 flex items-center gap-1 text-[0.6875rem] text-cyan hover:underline"
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

            {/* User pill */}
            <div className="hidden sm:flex items-center gap-2 rounded-full border border-[var(--glass-border)] px-2.5 py-1 text-[0.6875rem] text-muted-foreground">
              <div className="flex h-5 w-5 items-center justify-center rounded-full bg-primary/12 text-[0.55rem] font-semibold text-primary">
                {(session.email ?? "U")[0]?.toUpperCase() ?? "U"}
              </div>
              <span className="max-w-[120px] truncate">{session.email ?? ""}</span>
            </div>

            <ThemeToggle />
          </div>
        </header>

        {/* Top Page Route Switch Loading Bar */}
        <AnimatePresence>
          {isNavigating && (
            <motion.div
              initial={{ scaleX: 0, opacity: 1 }}
              animate={{ scaleX: 0.75, opacity: 1 }}
              exit={{ scaleX: 1, opacity: 0 }}
              transition={{ duration: 0.3, ease: "easeOut" }}
              className="fixed top-0 left-0 right-0 h-1 bg-gradient-to-r from-cyan-400 via-indigo-500 to-emerald-400 z-50 origin-left shadow-[0_0_15px_rgba(6,182,212,0.9)]"
            />
          )}
        </AnimatePresence>

        {hasActiveProviderKey === false && pathname !== "/app/providers" && (
          <div className="relative z-20 bg-amber-500/10 border-b border-amber-500/30 px-5 py-2.5 sm:px-7 text-xs text-amber-600 dark:text-amber-400 flex items-center justify-between gap-3 font-medium">
            <div className="flex items-center gap-2">
              <AlertTriangle className="h-4 w-4 shrink-0 text-amber-500" />
              <span><strong>API Key Required:</strong> No AI provider key is configured. Gateway execution features require an active API key.</span>
            </div>
            <Link to={"/app/providers" as never} className="px-2.5 py-1 rounded-lg bg-amber-500 text-black font-bold hover:bg-amber-400 text-[0.7rem] transition-colors shrink-0">
              Configure Key →
            </Link>
          </div>
        )}

        {/* Animated Page Route Content */}
        <AnimatePresence mode="wait">
          <motion.main
            key={pathname}
            variants={pageVariants}
            initial="initial"
            animate="animate"
            exit="exit"
            className="relative z-10 px-5 py-5 sm:px-7"
          >
            <div className="mx-auto max-w-[86rem]">{children}</div>
          </motion.main>
        </AnimatePresence>

        <footer className="relative z-10 flex items-center gap-1.5 px-5 pb-6 text-[0.625rem] text-muted-foreground/40 sm:px-7">
          <Activity className="h-3 w-3" /> NexusAI Gateway · us-east-1 · v3.0
        </footer>
      </div>
    </div>
  );
}
