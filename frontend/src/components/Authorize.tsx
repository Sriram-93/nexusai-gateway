import type { ReactNode } from "react";
import { useUser, type UserRole } from "@/lib/user-context";

interface AuthorizeProps {
  roles: UserRole[];
  children: ReactNode;
  fallback?: ReactNode;
}

/**
 * A wrapper component that conditionally renders its children
 * if the current user has one of the specified roles.
 * 
 * Used to implement role-based actions (e.g. only ORG_ADMIN can see a 'Disable Model' button).
 */
export function Authorize({ roles, children, fallback = null }: AuthorizeProps) {
  const { session } = useUser();
  const currentRole = session.role;

  if (!currentRole || !roles.includes(currentRole)) {
    return <>{fallback}</>;
  }

  return <>{children}</>;
}
