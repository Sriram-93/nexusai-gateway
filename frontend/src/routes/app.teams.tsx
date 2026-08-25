import { createFileRoute } from "@tanstack/react-router";
import { motion, AnimatePresence } from "motion/react";
import { useState, useEffect } from "react";
import {
  Network, Plus, Search, Shield, KeyRound, UserPlus, Zap, Trash2, Users, Activity, BarChart3, CheckCircle2, AlertTriangle, Send
} from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { teamsApi, adminApi, type TeamSummary, type TeamMember } from "@/lib/api";
import { useUser } from "@/lib/user-context";

export const Route = createFileRoute("/app/teams")({
  head: () => ({
    meta: [
      { title: "Teams — NexusAI" },
      { name: "description", content: "Manage teams, assign leads, and provision API keys." },
    ],
  }),
  component: TeamsPage,
});

function TeamsPage() {
  const { session } = useUser();
  const [teams, setTeams] = useState<TeamSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  
  // Create Team state
  const [showCreate, setShowCreate] = useState(false);
  const [newTeamName, setNewTeamName] = useState("");
  const [newTeamDesc, setNewTeamDesc] = useState("");
  
  // Selected team for details
  const [selectedTeam, setSelectedTeam] = useState<TeamSummary | null>(null);
  const [teamMembers, setTeamMembers] = useState<TeamMember[]>([]);
  const [leadEmail, setLeadEmail] = useState("");
  const [memberEmail, setMemberEmail] = useState("");
  const [memberRole, setMemberRole] = useState("TEAM_MEMBER");
  const [budgetInput, setBudgetInput] = useState<string>("");

  const loadTeams = async () => {
    setLoading(true);
    try {
      const data = await teamsApi.getAnalytics();
      setTeams(data);
    } catch (err: any) {
      setError(err.message ?? "Failed to load teams");
    } finally {
      setLoading(false);
    }
  };

  const [orgMembers, setOrgMembers] = useState<any[]>([]);

  useEffect(() => { 
    loadTeams(); 
    adminApi.getMembers().then(setOrgMembers).catch(console.error);
  }, []);

  const handleCreateTeam = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await teamsApi.createTeam(newTeamName, newTeamDesc);
      setNewTeamName("");
      setNewTeamDesc("");
      setShowCreate(false);
      loadTeams();
    } catch (err: any) {
      alert(err.message || "Failed to create team");
    }
  };

  const loadTeamDetails = async (teamId: string) => {
    try {
      const data = await teamsApi.getTeam(teamId);
      setSelectedTeam(data);
      setTeamMembers(data.members || []);
      setBudgetInput(data.dailyBudgetUsd ? data.dailyBudgetUsd.toString() : "");
    } catch (err: any) {
      alert("Failed to load team details");
    }
  };

  const handleAssignLead = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedTeam) return;
    try {
      await teamsApi.assignLead(selectedTeam.id, leadEmail);
      setLeadEmail("");
      loadTeamDetails(selectedTeam.id);
      alert("Team Lead assigned successfully. An email has been sent to them.");
    } catch (err: any) {
      alert(err.message || "Failed to assign team lead");
    }
  };

  const handleAddMember = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedTeam) return;
    try {
      await teamsApi.addMember(selectedTeam.id, memberEmail, memberRole);
      setMemberEmail("");
      loadTeamDetails(selectedTeam.id);
    } catch (err: any) {
      alert(err.message || "Failed to add member");
    }
  };

  const handleRemoveMember = async (userId: string) => {
    if (!selectedTeam || !confirm("Remove this member?")) return;
    try {
      await teamsApi.removeMember(selectedTeam.id, userId);
      loadTeamDetails(selectedTeam.id);
    } catch (err: any) {
      alert(err.message || "Failed to remove member");
    }
  };

  const handleGenerateKey = async () => {
    if (!selectedTeam || !confirm("Generate a new API key? The raw key will be emailed to the team lead once.")) return;
    try {
      const res = await teamsApi.generateKey(selectedTeam.id);
      alert(`Key generated and emailed to ${res.emailedTo}.`);
      loadTeamDetails(selectedTeam.id);
      loadTeams();
    } catch (err: any) {
      alert(err.message || "Failed to generate key");
    }
  };

  const handleToggleKey = async (active: boolean) => {
    if (!selectedTeam) return;
    try {
      if (active) await teamsApi.enableKey(selectedTeam.id);
      else await teamsApi.disableKey(selectedTeam.id);
      loadTeamDetails(selectedTeam.id);
    } catch (err: any) {
      alert(err.message || "Failed to toggle key status");
    }
  };

  const handleResendKeyEmail = async () => {
    if (!selectedTeam) return;
    try {
      await teamsApi.resendKeyEmail(selectedTeam.id);
      alert("Status email sent to Team Lead.");
    } catch (err: any) {
      alert(err.message || "Failed to resend email");
    }
  };

  const handleUpdateBudget = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedTeam) return;
    try {
      const budgetVal = budgetInput.trim() === "" ? null : parseFloat(budgetInput);
      await teamsApi.updateTeamBudget(selectedTeam.id, budgetVal);
      loadTeamDetails(selectedTeam.id);
      alert("Team budget updated successfully.");
    } catch (err: any) {
      alert(err.message || "Failed to update budget");
    }
  };

  const handleDeleteTeam = async () => {
    if (!selectedTeam || !confirm("Are you sure you want to completely delete this team? This action cannot be undone.")) return;
    try {
      await teamsApi.deleteTeam(selectedTeam.id);
      setSelectedTeam(null);
      loadTeams();
    } catch (err: any) {
      alert(err.message || "Failed to delete team");
    }
  };

  return (
    <AppShell title="Teams" subtitle="Organization teams, members, and API access">
      <datalist id="org-members-list">
        {orgMembers.map(m => (
          <option key={m.id} value={m.email} />
        ))}
      </datalist>

      {/* Create Team Form */}
      {showCreate && (
        <motion.div initial={{ opacity: 0, y: -10 }} animate={{ opacity: 1, y: 0 }} className="mb-6 glass p-6 rounded-2xl border border-[var(--glass-border)]">
          <h3 className="text-sm font-semibold mb-4">Create New Team</h3>
          <form onSubmit={handleCreateTeam} className="flex flex-col gap-4 max-w-md">
            <Input value={newTeamName} onChange={e => setNewTeamName(e.target.value)} placeholder="Team Name" required className="bg-[var(--glass-bg)]" />
            <Input value={newTeamDesc} onChange={e => setNewTeamDesc(e.target.value)} placeholder="Description (optional)" className="bg-[var(--glass-bg)]" />
            <div className="flex gap-2">
              <Button type="submit" className="grad-primary">Create Team</Button>
              <Button type="button" variant="ghost" onClick={() => setShowCreate(false)}>Cancel</Button>
            </div>
          </form>
        </motion.div>
      )}

      {/* Main Grid: Team List vs Details */}
      <div className="grid grid-cols-1 xl:grid-cols-3 gap-6">
        
        {/* Teams List */}
        <div className="xl:col-span-2 glass overflow-hidden rounded-2xl flex flex-col h-[700px]">
          <div className="flex items-center justify-between border-b px-5 py-4 shrink-0">
            <div className="flex items-center gap-2">
              <Network className="h-4 w-4 text-cyan" />
              <h2 className="text-sm font-medium">All Teams</h2>
            </div>
            <Button onClick={() => setShowCreate(!showCreate)} size="sm" className="h-8 rounded-lg grad-primary text-xs">
              <Plus className="h-3.5 w-3.5 mr-1" /> New Team
            </Button>
          </div>

          <div className="overflow-y-auto p-5 space-y-4 flex-1">
            {loading && <p className="text-xs text-muted-foreground">Loading teams...</p>}
            {error && <p className="text-xs text-destructive">{error}</p>}
            {!loading && teams.length === 0 && <p className="text-xs text-muted-foreground">No teams created yet.</p>}
            
            {teams.map(team => (
              <motion.div 
                key={team.id}
                whileHover={{ scale: 1.01 }}
                onClick={() => loadTeamDetails(team.id)}
                className={`cursor-pointer rounded-xl border p-4 transition-colors ${selectedTeam?.id === team.id ? 'border-cyan bg-cyan/5' : 'border-[var(--glass-border)] bg-[var(--glass-bg)] hover:bg-[var(--glass-hover)]'}`}
              >
                <div className="flex justify-between items-start mb-3">
                  <div>
                    <h3 className="text-sm font-semibold text-cyan">{team.name}</h3>
                    <p className="text-xs text-muted-foreground">{team.leadEmail || "No Lead Assigned"}</p>
                  </div>
                  <div className="flex gap-2">
                    {team.hasKey ? (
                      <span className={`text-[0.65rem] uppercase tracking-wider px-2 py-0.5 rounded-full border ${team.keyActive ? 'bg-emerald/10 border-emerald/30 text-emerald' : 'bg-amber/10 border-amber/30 text-amber'}`}>
                        {team.keyActive ? 'Key Active' : 'Key Disabled'}
                      </span>
                    ) : (
                      <span className="text-[0.65rem] uppercase tracking-wider px-2 py-0.5 rounded-full border bg-destructive/10 border-destructive/30 text-destructive">No Key</span>
                    )}
                  </div>
                </div>

                {/* Analytics summary */}
                <div className="grid grid-cols-3 gap-2 mt-4 pt-4 border-t border-[var(--glass-border)]">
                  <div>
                    <p className="text-[0.65rem] text-muted-foreground uppercase tracking-wider">Requests</p>
                    <p className="text-sm font-medium">{team.totalRequests?.toLocaleString() || 0}</p>
                  </div>
                  <div>
                    <p className="text-[0.65rem] text-muted-foreground uppercase tracking-wider">Cost</p>
                    <p className="text-sm font-medium text-emerald">${(team.totalCostUsd || 0).toFixed(4)}</p>
                  </div>
                  <div>
                    <p className="text-[0.65rem] text-muted-foreground uppercase tracking-wider">Avg Latency</p>
                    <p className="text-sm font-medium text-amber">{team.avgLatencyMs ? Math.round(team.avgLatencyMs) : 0}ms</p>
                  </div>
                </div>

                {/* Progress bar for budget */}
                {team.dailyBudgetUsd && team.dailyBudgetUsd > 0 && (
                  <div className="mt-4 pt-4 border-t border-[var(--glass-border)]">
                    <div className="flex justify-between text-[0.65rem] uppercase tracking-wider mb-1">
                      <span className="text-muted-foreground">Budget Usage</span>
                      <span className={(team.totalCostUsd || 0) >= team.dailyBudgetUsd * 0.8 ? "text-amber" : "text-emerald"}>
                        ${(team.totalCostUsd || 0).toFixed(2)} / ${team.dailyBudgetUsd.toFixed(2)}
                      </span>
                    </div>
                    <div className="h-1.5 w-full bg-slate-800 rounded-full overflow-hidden">
                      <div 
                        className={`h-full ${(team.totalCostUsd || 0) >= team.dailyBudgetUsd ? "bg-destructive" : (team.totalCostUsd || 0) >= team.dailyBudgetUsd * 0.8 ? "bg-amber" : "bg-emerald"}`}
                        style={{ width: `${Math.min(100, ((team.totalCostUsd || 0) / team.dailyBudgetUsd) * 100)}%` }}
                      />
                    </div>
                  </div>
                )}
              </motion.div>
            ))}
          </div>
        </div>

        {/* Selected Team Details Sidebar */}
        <div className="glass overflow-y-auto rounded-2xl h-[700px] border border-[var(--glass-border)]">
          {!selectedTeam ? (
            <div className="h-full flex flex-col items-center justify-center text-muted-foreground p-6 text-center">
              <Network className="h-10 w-10 mb-3 opacity-20" />
              <p className="text-sm">Select a team to manage members and API keys.</p>
            </div>
          ) : (
            <div className="p-6">
              <div className="mb-6">
                <div className="flex items-center justify-between mb-1">
                  <h2 className="text-lg font-semibold tracking-tight text-cyan">{selectedTeam.name}</h2>
                  <Button variant="ghost" size="sm" onClick={handleDeleteTeam} className="h-8 text-xs text-destructive hover:text-destructive hover:bg-destructive/10">
                    <Trash2 className="h-3.5 w-3.5 mr-1" /> Delete Team
                  </Button>
                </div>
                <p className="text-xs text-muted-foreground mb-4">{selectedTeam.description || "No description provided."}</p>
                
                {/* Team Lead Section */}
                <div className="bg-[var(--glass-hover)] rounded-xl p-4 border border-[var(--glass-border)] mb-6">
                  <div className="flex items-center gap-2 mb-3">
                    <Shield className="h-4 w-4 text-indigo" />
                    <h3 className="text-xs font-semibold uppercase tracking-wider">Team Lead</h3>
                  </div>
                  {selectedTeam.leadEmail ? (
                    <div className="flex items-center justify-between">
                      <span className="text-sm font-medium">{selectedTeam.leadEmail}</span>
                      <CheckCircle2 className="h-4 w-4 text-emerald" />
                    </div>
                  ) : (
                    <form onSubmit={handleAssignLead} className="flex gap-2">
                      <select 
                        value={leadEmail} 
                        onChange={e => setLeadEmail(e.target.value)} 
                        required 
                        className="flex h-8 w-full rounded-md border border-[var(--glass-border)] bg-[var(--glass-bg)] px-3 py-1 text-xs shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-cyan"
                      >
                        <option value="" disabled>Select an existing member</option>
                        {orgMembers.map(m => (
                          <option key={m.id} value={m.email}>{m.email} ({m.role.replace('_', ' ')})</option>
                        ))}
                      </select>
                      <Button type="submit" size="sm" className="h-8 text-xs bg-indigo hover:bg-indigo/80 text-white">Assign</Button>
                    </form>
                  )}
                </div>

                {/* Budget Controls */}
                <div className="bg-[var(--glass-hover)] rounded-xl p-4 border border-[var(--glass-border)] mb-6">
                  <div className="flex items-center gap-2 mb-3">
                    <Activity className="h-4 w-4 text-emerald" />
                    <h3 className="text-xs font-semibold uppercase tracking-wider">Budget Allocation</h3>
                  </div>
                  <form onSubmit={handleUpdateBudget} className="flex gap-2">
                    <Input 
                      value={budgetInput} 
                      onChange={e => setBudgetInput(e.target.value)} 
                      type="number" 
                      step="0.01" 
                      min="0"
                      placeholder="Unlimited" 
                      className="h-8 text-xs bg-[var(--glass-bg)]" 
                    />
                    <Button type="submit" size="sm" className="h-8 text-xs bg-emerald hover:bg-emerald/80 text-white">Save Limit</Button>
                  </form>
                  <p className="text-[0.65rem] text-muted-foreground mt-2">
                    Set a daily USD limit. If exceeded, API calls will be suspended until midnight. Leave empty for unlimited.
                  </p>
                </div>

                {/* API Key Management */}
                <div className="bg-[var(--glass-hover)] rounded-xl p-4 border border-[var(--glass-border)] mb-6">
                  <div className="flex items-center gap-2 mb-3">
                    <KeyRound className="h-4 w-4 text-amber" />
                    <h3 className="text-xs font-semibold uppercase tracking-wider">Gateway Access</h3>
                  </div>
                  
                  {!selectedTeam.hasKey ? (
                    <div>
                      <p className="text-xs text-muted-foreground mb-3">No API key has been provisioned for this team. Generate one to enable gateway access.</p>
                      <Button onClick={handleGenerateKey} className="w-full text-xs h-8 grad-primary text-white">Generate Team Key</Button>
                    </div>
                  ) : (
                    <div className="space-y-3">
                      <div className="flex justify-between items-center p-2 bg-background/50 rounded border border-[var(--glass-border)]">
                        <span className="text-xs text-muted-foreground">Status</span>
                        <span className={`text-xs font-bold ${selectedTeam.keyActive ? 'text-emerald' : 'text-destructive'}`}>
                          {selectedTeam.keyActive ? 'ACTIVE' : 'SUSPENDED'}
                        </span>
                      </div>
                      <div className="flex gap-2">
                        {selectedTeam.keyActive ? (
                          <Button onClick={() => handleToggleKey(false)} variant="destructive" className="flex-1 h-8 text-xs">Suspend Access</Button>
                        ) : (
                          <Button onClick={() => handleToggleKey(true)} className="flex-1 h-8 text-xs bg-emerald hover:bg-emerald/80 text-white">Enable Access</Button>
                        )}
                      </div>
                      <Button onClick={handleResendKeyEmail} variant="outline" className="w-full h-8 text-xs"><Send className="h-3 w-3 mr-1" /> Resend Status Email</Button>
                    </div>
                  )}
                </div>

                {/* Team Members */}
                <div>
                  <div className="flex items-center gap-2 mb-3">
                    <Users className="h-4 w-4 text-cyan" />
                    <h3 className="text-xs font-semibold uppercase tracking-wider">Members</h3>
                  </div>
                  
                  <form onSubmit={handleAddMember} className="flex gap-2 mb-4">
                    <select 
                      value={memberEmail} 
                      onChange={e => setMemberEmail(e.target.value)} 
                      required 
                      className="flex h-8 w-full rounded-md border border-[var(--glass-border)] bg-[var(--glass-bg)] px-3 py-1 text-xs shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-cyan"
                    >
                      <option value="" disabled>Select an existing member</option>
                      {orgMembers.filter(m => m.role !== 'ORG_ADMIN').map(m => (
                        <option key={m.id} value={m.email}>{m.email} ({m.role.replace('_', ' ')})</option>
                      ))}
                    </select>
                    <Button type="submit" size="sm" variant="secondary" className="h-8 w-8 p-0 shrink-0"><Plus className="h-4 w-4" /></Button>
                  </form>

                  <div className="space-y-2">
                    {teamMembers.length === 0 && <p className="text-xs text-muted-foreground text-center py-2">No members in this team.</p>}
                    {teamMembers.map(m => (
                      <div key={m.userId} className="flex items-center justify-between p-2 rounded-lg bg-[var(--glass-bg)] border border-[var(--glass-border)]">
                        <div>
                          <p className="text-xs font-medium">{m.email}</p>
                          <p className="text-[0.6rem] text-muted-foreground">{m.role}</p>
                        </div>
                        {m.role !== "TEAM_LEAD" && (
                          <Button variant="ghost" size="sm" onClick={() => handleRemoveMember(m.userId)} className="h-6 w-6 p-0 text-muted-foreground hover:text-destructive">
                            <Trash2 className="h-3 w-3" />
                          </Button>
                        )}
                      </div>
                    ))}
                  </div>
                </div>

              </div>
            </div>
          )}
        </div>

      </div>
    </AppShell>
  );
}
