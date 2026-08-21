import { createFileRoute, Link } from "@tanstack/react-router";
import { motion, AnimatePresence } from "motion/react";
import { useState, useEffect } from "react";
import { Users, UserPlus, Eye, Shield, Trash2, CheckCircle2 } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { Button } from "@/components/ui/button";
import { adminApi } from "@/lib/api";
import { useUser } from "@/lib/user-context";
import { Authorize } from "@/components/Authorize";

export const Route = createFileRoute("/app/members")({
  component: Members,
});

function Members() {
  const [members, setMembers] = useState<Array<{ id: string; email: string; role: string }>>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const { session } = useUser();

  const [inviteEmail, setInviteEmail] = useState("");
  const [invitePassword, setInvitePassword] = useState("");
  const [inviteRole, setInviteRole] = useState<"TEAM_MEMBER" | "TEAM_LEAD">("TEAM_MEMBER");
  const [inviting, setInviting] = useState(false);
  const [inviteSuccess, setInviteSuccess] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      const data = await adminApi.getMembers();
      setMembers(data);
    } catch (err: any) {
      setError(err.message ?? "Failed to load members");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const handleRoleChange = async (userId: string, newRole: "TEAM_MEMBER" | "TEAM_LEAD") => {
    try {
      await adminApi.updateMemberRole(userId, newRole);
      setMembers(members.map(m => m.id === userId ? { ...m, role: newRole } : m));
    } catch (err: any) {
      alert(err.message || "Failed to update role");
    }
  };

  const handleRemove = async (userId: string) => {
    if (!confirm("Are you sure you want to remove this member?")) return;
    try {
      await adminApi.removeMember(userId);
      setMembers(members.filter(m => m.id !== userId));
    } catch (err: any) {
      alert(err.message || "Failed to remove member");
    }
  };

  const handleInvite = async (e: React.FormEvent) => {
    e.preventDefault();
    setInviting(true);
    setInviteSuccess(false);
    setError(null);
    try {
      await adminApi.inviteMember(inviteEmail, invitePassword, inviteRole);
      setInviteSuccess(true);
      setInviteEmail("");
      setInvitePassword("");
      load();
      setTimeout(() => setInviteSuccess(false), 3000);
    } catch (err: any) {
      setError(err.message || "Failed to invite member");
    } finally {
      setInviting(false);
    }
  };

  return (
    <AppShell title={session.role === "TEAM_LEAD" ? "My Team" : "Organization Members"} subtitle="Manage access and roles">
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 glass overflow-hidden rounded-2xl">
          <div className="flex flex-wrap items-center justify-between gap-3 border-b px-5 py-4">
            <div className="flex items-center gap-2">
              <Users className="h-4 w-4 text-indigo" />
              <p className="text-sm font-medium tracking-tight">Active Members</p>
            </div>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-[0.7rem] uppercase tracking-[0.14em] text-muted-foreground">
                  <th className="px-5 py-3 font-medium">Email</th>
                  <th className="px-5 py-3 font-medium">Role</th>
                  <th className="px-5 py-3 font-medium text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {loading && (
                  <tr>
                    <td colSpan={3} className="px-5 py-8 text-center text-xs text-muted-foreground">
                      Loading members...
                    </td>
                  </tr>
                )}
                {!loading && members.map((m) => (
                  <motion.tr
                    key={m.id}
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    className="border-t transition-colors hover:bg-[var(--glass-hover)]"
                  >
                    <td className="px-5 py-3 font-mono text-xs">{m.email}</td>
                    <td className="px-5 py-3">
                      <span className={`rounded-full border px-2.5 py-0.5 text-[0.7rem] font-medium ${
                        m.role === 'ORG_ADMIN' ? 'border-amber/40 bg-amber/10 text-amber' :
                        m.role === 'TEAM_LEAD' ? 'border-cyan/40 bg-cyan/10 text-cyan' :
                        'border-slate-500/40 bg-slate-500/10 text-slate-300'
                      }`}>
                        {m.role.replace('_', ' ')}
                      </span>
                    </td>
                    <td className="px-5 py-3 text-right">
                      <div className="flex justify-end gap-2">
                        <Link to={`/app/inspect/$userId`} params={{ userId: m.id }}>
                          <Button variant="ghost" size="icon" className="h-8 w-8 hover:bg-[var(--glass-border)]">
                            <Eye className="h-4 w-4 text-muted-foreground" />
                          </Button>
                        </Link>
                        <Authorize roles={["ORG_ADMIN", "SUPER_ADMIN"]}>
                          {m.role !== "ORG_ADMIN" && (
                            <>
                              <Button 
                                variant="ghost" 
                                size="icon" 
                                onClick={() => handleRoleChange(m.id, m.role === "TEAM_MEMBER" ? "TEAM_LEAD" : "TEAM_MEMBER")}
                                className="h-8 w-8 hover:bg-[var(--glass-border)]"
                                title="Toggle Role"
                              >
                                <Shield className="h-4 w-4 text-indigo" />
                              </Button>
                              <Button 
                                variant="ghost" 
                                size="icon" 
                                onClick={() => handleRemove(m.id)}
                                className="h-8 w-8 hover:bg-destructive/20 hover:text-destructive"
                              >
                                <Trash2 className="h-4 w-4" />
                              </Button>
                            </>
                          )}
                        </Authorize>
                      </div>
                    </td>
                  </motion.tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <Authorize roles={["ORG_ADMIN", "SUPER_ADMIN"]}>
          <div className="glass rounded-2xl p-6 h-fit">
            <div className="flex items-center gap-2 mb-4">
              <UserPlus className="h-5 w-5 text-cyan" />
              <h3 className="text-sm font-medium">Add Member</h3>
            </div>
            
            <form onSubmit={handleInvite} className="space-y-4">
              <div>
                <label className="text-[0.7rem] uppercase tracking-wider text-muted-foreground mb-1 block">Email</label>
                <input
                  type="email"
                  required
                  value={inviteEmail}
                  onChange={e => setInviteEmail(e.target.value)}
                  className="w-full h-9 rounded-lg border-[var(--glass-border)] bg-[var(--glass-bg)] px-3 text-xs focus:outline-none focus:border-cyan"
                  placeholder="member@company.com"
                />
              </div>
              
              <div>
                <label className="text-[0.7rem] uppercase tracking-wider text-muted-foreground mb-1 block">Temporary Password</label>
                <input
                  type="password"
                  required
                  value={invitePassword}
                  onChange={e => setInvitePassword(e.target.value)}
                  className="w-full h-9 rounded-lg border-[var(--glass-border)] bg-[var(--glass-bg)] px-3 text-xs focus:outline-none focus:border-cyan"
                  placeholder="••••••••"
                />
              </div>

              <div>
                <label className="text-[0.7rem] uppercase tracking-wider text-muted-foreground mb-1 block">Role</label>
                <select
                  value={inviteRole}
                  onChange={e => setInviteRole(e.target.value as any)}
                  className="w-full h-9 rounded-lg border-[var(--glass-border)] bg-[var(--glass-bg)] px-3 text-xs focus:outline-none focus:border-cyan"
                >
                  <option value="TEAM_MEMBER">Team Member</option>
                  <option value="TEAM_LEAD">Team Lead</option>
                </select>
              </div>

              {error && (
                <div className="text-xs text-destructive bg-destructive/10 p-2 rounded">
                  {error}
                </div>
              )}

              <Button type="submit" disabled={inviting} className="w-full grad-primary h-9 rounded-lg text-xs font-medium text-primary-foreground">
                {inviting ? "Adding..." : "Add to Organization"}
              </Button>

              <AnimatePresence>
                {inviteSuccess && (
                  <motion.div 
                    initial={{ opacity: 0, y: 10 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0 }}
                    className="flex items-center gap-2 text-xs text-emerald justify-center mt-2"
                  >
                    <CheckCircle2 className="h-4 w-4" />
                    Member added successfully!
                  </motion.div>
                )}
              </AnimatePresence>
            </form>
          </div>
        </Authorize>
      </div>
    </AppShell>
  );
}
