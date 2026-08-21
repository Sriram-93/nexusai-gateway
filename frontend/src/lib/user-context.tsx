/**
 * NexusAI — UserContext
 * Central store for session identity. Wraps the entire app.
 * Derived from sessionStorage on mount; updated after login/signup.
 */
import { createContext, useContext, useState, useCallback, type ReactNode } from "react";

export type UserTier = "SOLO" | "ADMINISTRATION";
export type UserRole = "SUPER_ADMIN" | "ORG_ADMIN" | "TEAM_LEAD" | "TEAM_MEMBER" | "SOLO";

export interface UserSession {
  jwt: string | null;
  apiKey: string | null;
  tenantId: string | null;
  tier: UserTier | null;
  role: UserRole | null;
  orgName: string | null;
  teamName: string | null;
  email: string | null;
  /** Pending upgrade requests count (for notification bell) */
  pendingRequests: number;
}

export interface UserContextValue {
  session: UserSession;
  setSession: (partial: Partial<UserSession>) => void;
  logout: () => void;
  isAuthenticated: boolean;
}

function readSession(): UserSession {
  if (typeof window === "undefined") return emptySession();
  return {
    jwt: sessionStorage.getItem("nexus_jwt"),
    apiKey: sessionStorage.getItem("nexus_api_key"),
    tenantId: sessionStorage.getItem("nexus_tenant_id"),
    tier: (sessionStorage.getItem("nexus_tier") as UserTier) ?? null,
    role: (sessionStorage.getItem("nexus_role") as UserRole) ?? null,
    orgName: sessionStorage.getItem("nexus_org_name"),
    teamName: sessionStorage.getItem("nexus_team_name"),
    email: sessionStorage.getItem("nexus_email"),
    pendingRequests: parseInt(sessionStorage.getItem("nexus_pending_requests") ?? "0"),
  };
}

function emptySession(): UserSession {
  return {
    jwt: null,
    apiKey: null,
    tenantId: null,
    tier: null,
    role: null,
    orgName: null,
    teamName: null,
    email: null,
    pendingRequests: 0,
  };
}

const UserContext = createContext<UserContextValue | null>(null);

export function UserProvider({ children }: { children: ReactNode }) {
  const [session, setSessionState] = useState<UserSession>(readSession);

  const setSession = useCallback((partial: Partial<UserSession>) => {
    setSessionState((prev) => {
      const next = { ...prev, ...partial };
      // Persist to sessionStorage
      if (typeof window !== "undefined") {
        const persist = (k: string, v: string | null) => {
          if (v !== null) sessionStorage.setItem(k, v);
          else sessionStorage.removeItem(k);
        };
        persist("nexus_jwt", next.jwt);
        persist("nexus_api_key", next.apiKey);
        persist("nexus_tenant_id", next.tenantId);
        persist("nexus_tier", next.tier);
        persist("nexus_role", next.role);
        persist("nexus_org_name", next.orgName);
        persist("nexus_team_name", next.teamName);
        persist("nexus_email", next.email);
        persist("nexus_pending_requests", String(next.pendingRequests));
      }
      return next;
    });
  }, []);

  const logout = useCallback(() => {
    if (typeof window !== "undefined") {
      const keys = [
        "nexus_jwt", "nexus_api_key", "nexus_tenant_id",
        "nexus_tier", "nexus_role", "nexus_org_name",
        "nexus_team_name", "nexus_email", "nexus_pending_requests",
      ];
      keys.forEach((k) => sessionStorage.removeItem(k));
    }
    setSessionState(emptySession());
  }, []);

  return (
    <UserContext.Provider
      value={{
        session,
        setSession,
        logout,
        isAuthenticated: !!session.jwt || !!session.apiKey,
      }}
    >
      {children}
    </UserContext.Provider>
  );
}

export function useUser(): UserContextValue {
  const ctx = useContext(UserContext);
  if (!ctx) throw new Error("useUser must be used within a UserProvider");
  return ctx;
}

/** Derive role from tier + backend response. On signup, role is ORG_ADMIN for ADMINISTRATION, SOLO for SOLO. */
export function deriveRoleFromSignup(tier: UserTier): UserRole {
  return tier === "SOLO" ? "SOLO" : "ORG_ADMIN";
}
