import { createFileRoute, Outlet, redirect } from "@tanstack/react-router";

export const Route = createFileRoute("/app")({
  beforeLoad: () => {
    if (typeof window !== "undefined") {
      const jwt = sessionStorage.getItem("nexus_jwt");
      if (!jwt) {
        throw redirect({ to: "/" });
      }
    }
  },
  component: () => <Outlet />,
});
