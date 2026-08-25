package com.llm.nexusai_gateway.Controller;

import com.llm.nexusai_gateway.Repository.RequestLogRepository;
import com.llm.nexusai_gateway.Security.*;
import com.llm.nexusai_gateway.Service.NotificationService;
import com.llm.nexusai_gateway.Team.*;
import com.llm.nexusai_gateway.Tenant.TenantConfig;
import com.llm.nexusai_gateway.Repository.TenantConfigRepository;
import com.llm.nexusai_gateway.Tenant.TenantRegistry;
import com.llm.nexusai_gateway.Provider.ProviderBootstrapService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * TeamController — manages the full Team lifecycle for ORG_ADMIN users.
 *
 * Endpoints:
 *   POST   /api/admin/teams                          – Create team
 *   GET    /api/admin/teams                          – List all org teams
 *   GET    /api/admin/teams/{teamId}                 – Team detail + members
 *   POST   /api/admin/teams/{teamId}/lead            – Assign/create Team Lead
 *   POST   /api/admin/teams/{teamId}/members         – Add member to team
 *   DELETE /api/admin/teams/{teamId}/members/{uid}   – Remove member
 *   POST   /api/admin/teams/{teamId}/generate-key    – Generate gateway API key
 *   PATCH  /api/admin/teams/{teamId}/key/enable      – Enable key
 *   PATCH  /api/admin/teams/{teamId}/key/disable     – Disable key
 *   POST   /api/admin/teams/{teamId}/key/email       – Re-send key email
 *   GET    /api/admin/teams/analytics                – Usage leaderboard
 *   PATCH  /api/admin/teams/{teamId}/status          – Suspend/unsuspend
 *
 *   GET    /api/my-team                              – TEAM_LEAD: see own team + members
 */
@RestController
@Transactional
public class TeamController {

    private static final Logger log = LoggerFactory.getLogger(TeamController.class);

    private final TeamRepository teamRepository;
    private final TeamMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final TenantRegistry tenantRegistry;
    private final TenantConfigRepository tenantConfigRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final NotificationService notificationService;
    private final RequestLogRepository requestLogRepository;
    private final ProviderBootstrapService providerBootstrapService;

    public TeamController(TeamRepository teamRepository,
                          TeamMembershipRepository membershipRepository,
                          UserRepository userRepository,
                          OrganizationRepository organizationRepository,
                          TenantRegistry tenantRegistry,
                          TenantConfigRepository tenantConfigRepository,
                          PasswordEncoder passwordEncoder,
                          JwtUtil jwtUtil,
                          NotificationService notificationService,
                          RequestLogRepository requestLogRepository,
                          ProviderBootstrapService providerBootstrapService) {
        this.teamRepository = teamRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.tenantRegistry = tenantRegistry;
        this.tenantConfigRepository = tenantConfigRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.notificationService = notificationService;
        this.requestLogRepository = requestLogRepository;
        this.providerBootstrapService = providerBootstrapService;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String extractRole(String auth) {
        if (auth == null || !auth.startsWith("Bearer ")) return null;
        try { return jwtUtil.extractClaim(auth.substring(7), c -> c.get("role", String.class)); }
        catch (Exception e) { return null; }
    }

    private String extractOrgId(String auth) {
        if (auth == null || !auth.startsWith("Bearer ")) return null;
        try { 
            String orgId = jwtUtil.extractClaim(auth.substring(7), c -> c.get("orgId", String.class));
            if (orgId != null) return orgId;
            
            // Fallback: get orgId from tenant config using the tenantId claim
            String tenantId = jwtUtil.extractClaim(auth.substring(7), c -> c.get("tenantId", String.class));
            if (tenantId != null) {
                return tenantRegistry.get(tenantId)
                        .map(com.llm.nexusai_gateway.Tenant.TenantConfig::getOrganizationId)
                        .orElse(null);
            }
            return null;
        }
        catch (Exception e) { return null; }
    }

    private String extractUserId(String auth) {
        if (auth == null || !auth.startsWith("Bearer ")) return null;
        try { 
            String token = auth.substring(7);
            String userId = jwtUtil.extractClaim(token, c -> c.get("userId", String.class));
            if (userId != null) return userId;
            
            String email = jwtUtil.extractClaim(token, c -> c.getSubject());
            if (email != null) {
                return userRepository.findByEmail(email).map(com.llm.nexusai_gateway.Security.User::getId).orElse(null);
            }
            return null;
        }
        catch (Exception e) { return null; }
    }

    private boolean isOrgAdmin(String auth) { return "ORG_ADMIN".equals(extractRole(auth)); }
    private boolean isTeamLead(String auth) { return "TEAM_LEAD".equals(extractRole(auth)); }

    private Map<String, Object> teamToMap(Team t) {
        long memberCount = membershipRepository.countByTeamId(t.getId());
        boolean hasKey = t.getTenantId() != null &&
            tenantConfigRepository.findById(t.getTenantId())
                .map(tc -> tc.getApiKey() != null && !tc.getApiKey().isBlank())
                .orElse(false);
        boolean keyActive = t.getTenantId() != null &&
            tenantConfigRepository.findById(t.getTenantId())
                .map(TenantConfig::isActive).orElse(false);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", t.getId());
        map.put("name", t.getName());
        map.put("description", t.getDescription() != null ? t.getDescription() : "");
        map.put("leadEmail", t.getLeadEmail() != null ? t.getLeadEmail() : "");
        map.put("leadUserId", t.getLeadUserId() != null ? t.getLeadUserId() : "");
        map.put("active", t.isActive());
        map.put("createdAt", t.getCreatedAt().toString());
        map.put("memberCount", memberCount);
        map.put("tenantId", t.getTenantId() != null ? t.getTenantId() : "");
        map.put("hasKey", hasKey);
        map.put("keyActive", keyActive);
        map.put("dailyBudgetUsd", t.getDailyBudgetUsd() != null ? t.getDailyBudgetUsd() : 0.0);
        return map;
    }

    // ─── Create Team ─────────────────────────────────────────────────────────

    @PostMapping("/api/admin/teams")
    public ResponseEntity<?> createTeam(
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (!isOrgAdmin(auth)) return ResponseEntity.status(403).body(Map.of("error", "ORG_ADMIN role required"));
        String orgId = extractOrgId(auth);
        if (orgId == null) return ResponseEntity.status(400).body(Map.of("error", "Cannot determine organization"));

        String name = body.get("name");
        if (name == null || name.isBlank()) return ResponseEntity.badRequest().body(Map.of("error", "Team name required"));
        if (teamRepository.existsByNameAndOrganizationId(name, orgId))
            return ResponseEntity.badRequest().body(Map.of("error", "A team with this name already exists"));

        Team team = new Team(name, body.get("description"), orgId);
        teamRepository.save(team);
        log.info("Team created: {} (org={})", name, orgId);
        return ResponseEntity.ok(teamToMap(team));
    }

    // ─── Update Team Budget ───────────────────────────────────────────────────

    @PatchMapping("/api/admin/teams/{teamId}/budget")
    public ResponseEntity<?> updateTeamBudget(
            @PathVariable String teamId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (!isOrgAdmin(auth)) return ResponseEntity.status(403).body(Map.of("error", "ORG_ADMIN role required"));
        String orgId = extractOrgId(auth);
        
        return teamRepository.findByIdAndOrganizationId(teamId, orgId)
            .map(team -> {
                Number newBudget = (Number) body.get("dailyBudgetUsd");
                if (newBudget != null) {
                    team.setDailyBudgetUsd(newBudget.doubleValue());
                } else {
                    team.setDailyBudgetUsd(null); // Remove limit
                }
                
                // If they have a TenantConfig, we should update that too for fallback consistency
                if (team.getTenantId() != null) {
                    tenantConfigRepository.findById(team.getTenantId()).ifPresent(tc -> {
                        tc.setDailyBudgetUsd(team.getDailyBudgetUsd() != null ? team.getDailyBudgetUsd() : 999999.0);
                        tenantConfigRepository.save(tc);
                        tenantRegistry.register(tc);
                    });
                }
                
                teamRepository.save(team);
                return ResponseEntity.ok(teamToMap(team));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    // ─── List Teams ──────────────────────────────────────────────────────────

    @GetMapping("/api/admin/teams")
    public ResponseEntity<?> listTeams(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (!isOrgAdmin(auth)) return ResponseEntity.status(403).body(Map.of("error", "ORG_ADMIN role required"));
        String orgId = extractOrgId(auth);
        List<Map<String, Object>> teams = teamRepository.findAllByOrganizationId(orgId)
            .stream().map(this::teamToMap).collect(Collectors.toList());
        return ResponseEntity.ok(teams);
    }

    // ─── Team Detail ─────────────────────────────────────────────────────────

    @GetMapping("/api/admin/teams/{teamId}")
    public ResponseEntity<?> getTeam(
            @PathVariable String teamId,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (!isOrgAdmin(auth)) return ResponseEntity.status(403).body(Map.of("error", "ORG_ADMIN role required"));
        String orgId = extractOrgId(auth);
        log.info("getTeam called for teamId: {}, orgId: {}", teamId, orgId);
        return teamRepository.findByIdAndOrganizationId(teamId, orgId)
            .map(team -> {
                List<TeamMembership> memberships = membershipRepository.findAllByTeamId(teamId);
                List<Map<String, Object>> members = memberships.stream().map(m -> Map.<String,Object>of(
                    "userId", m.getUserId(),
                    "email", m.getUserEmail(),
                    "role", m.getRole(),
                    "joinedAt", m.getJoinedAt().toString()
                )).collect(Collectors.toList());
                Map<String, Object> detail = new LinkedHashMap<>(teamToMap(team));
                detail.put("members", members);
                return ResponseEntity.ok(detail);
            })
            .orElseGet(() -> {
                log.warn("Team not found for teamId: {} and orgId: {}", teamId, orgId);
                return ResponseEntity.notFound().build();
            });
    }

    // ─── Delete Team ──────────────────────────────────────────────────────────

    @DeleteMapping("/api/admin/teams/{teamId}")
    public ResponseEntity<?> deleteTeam(
            @PathVariable String teamId,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (!isOrgAdmin(auth)) return ResponseEntity.status(403).body(Map.of("error", "ORG_ADMIN role required"));
        String orgId = extractOrgId(auth);
        
        Optional<Team> teamOpt = teamRepository.findByIdAndOrganizationId(teamId, orgId);
        if (teamOpt.isEmpty()) return ResponseEntity.notFound().build();
        
        Team team = teamOpt.get();
        
        // 1. Delete all memberships for this team
        List<TeamMembership> memberships = membershipRepository.findAllByTeamId(teamId);
        membershipRepository.deleteAll(memberships);
        
        // 2. Delete TenantConfig if exists
        if (team.getTenantId() != null) {
            tenantRegistry.remove(team.getTenantId());
        }
        
        // 3. Delete the team itself
        teamRepository.delete(team);
        
        log.info("Team deleted: {} (id={}, org={})", team.getName(), teamId, orgId);
        return ResponseEntity.ok(Map.of("message", "Team deleted successfully"));
    }

    // ─── Assign / Create Team Lead ────────────────────────────────────────────

    @PostMapping("/api/admin/teams/{teamId}/lead")
    public ResponseEntity<?> assignTeamLead(
            @PathVariable String teamId,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (!isOrgAdmin(auth)) return ResponseEntity.status(403).body(Map.of("error", "ORG_ADMIN role required"));
        String orgId = extractOrgId(auth);

        Optional<Team> teamOpt = teamRepository.findByIdAndOrganizationId(teamId, orgId);
        if (teamOpt.isEmpty()) return ResponseEntity.notFound().build();
        Team team = teamOpt.get();

        String email = body.get("email");
        if (email == null || email.isBlank()) return ResponseEntity.badRequest().body(Map.of("error", "Email required"));

        // Find org for name
        Organization org = organizationRepository.findById(orgId).orElse(null);
        String orgName = org != null ? org.getName() : "Your Organization";

        // Check if user already exists
        Optional<User> existingUser = userRepository.findByEmail(email);
        if (existingUser.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "User not found in organization. Please invite them first from the Members page."));
        }
        
        User lead = existingUser.get();
        if (lead.getOrganization() == null || !lead.getOrganization().getId().equals(orgId)) {
            return ResponseEntity.status(403).body(Map.of("error", "User belongs to another organization"));
        }
        
        lead.setRole("TEAM_LEAD");
        userRepository.save(lead);

        // Demote old lead if it exists and is different from the new lead
        if (team.getLeadUserId() != null && !team.getLeadUserId().equals(lead.getId())) {
            userRepository.findById(team.getLeadUserId()).ifPresent(oldLead -> {
                oldLead.setRole("TEAM_MEMBER");
                userRepository.save(oldLead);
            });
        }

        // Assign lead to team
        team.setLeadUserId(lead.getId());
        team.setLeadEmail(email);
        teamRepository.save(team);

        // Add to TeamMembership if not already there
        if (!membershipRepository.existsByTeamIdAndUserId(teamId, lead.getId())) {
            membershipRepository.save(new TeamMembership(teamId, lead.getId(), email, "TEAM_LEAD"));
        }

        // Send welcome email (async, log-only if no SMTP)
        log.info("Team Lead assigned: {} → team {}", email, team.getName());
        return ResponseEntity.ok(Map.of(
            "message", "Team Lead assigned.",
            "userId", lead.getId(),
            "email", email,
            "isNewUser", false
        ));
    }

    // ─── Add Member to Team ───────────────────────────────────────────────────

    @PostMapping("/api/admin/teams/{teamId}/members")
    public ResponseEntity<?> addMember(
            @PathVariable String teamId,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (!isOrgAdmin(auth)) return ResponseEntity.status(403).body(Map.of("error", "ORG_ADMIN role required"));
        String orgId = extractOrgId(auth);

        Optional<Team> teamOpt = teamRepository.findByIdAndOrganizationId(teamId, orgId);
        if (teamOpt.isEmpty()) return ResponseEntity.notFound().build();
        Team team = teamOpt.get();

        String email = body.get("email");
        String role = body.getOrDefault("role", "TEAM_MEMBER");
        if (email == null || email.isBlank()) return ResponseEntity.badRequest().body(Map.of("error", "Email required"));

        Organization org = organizationRepository.findById(orgId).orElse(null);
        Optional<User> existingUser = userRepository.findByEmail(email);
        if (existingUser.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "User not found in organization. Please invite them first from the Members page."));
        }
        
        User member = existingUser.get();
        if (member.getOrganization() == null || !member.getOrganization().getId().equals(orgId)) {
            return ResponseEntity.status(403).body(Map.of("error", "User belongs to another organization"));
        }

        if (membershipRepository.existsByTeamIdAndUserId(teamId, member.getId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "User is already a member of this team"));
        }

        membershipRepository.save(new TeamMembership(teamId, member.getId(), email, role));

        // Notify team lead
        if (team.getLeadEmail() != null) {
            notificationService.sendMemberAddedToTeam(team.getLeadEmail(), team.getName(), email, role);
        }

        return ResponseEntity.ok(Map.of(
            "message", "Member added",
            "userId", member.getId(),
            "email", email,
            "role", role,
            "isNewUser", false
        ));
    }

    // ─── Remove Member ────────────────────────────────────────────────────────

    @DeleteMapping("/api/admin/teams/{teamId}/members/{userId}")
    public ResponseEntity<?> removeMember(
            @PathVariable String teamId,
            @PathVariable String userId,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (!isOrgAdmin(auth)) return ResponseEntity.status(403).body(Map.of("error", "ORG_ADMIN role required"));
        membershipRepository.deleteByTeamIdAndUserId(teamId, userId);
        return ResponseEntity.ok(Map.of("message", "Member removed from team"));
    }

    // ─── Generate API Key for Team ────────────────────────────────────────────

    @PostMapping("/api/admin/teams/{teamId}/generate-key")
    public ResponseEntity<?> generateKey(
            @PathVariable String teamId,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (!isOrgAdmin(auth)) return ResponseEntity.status(403).body(Map.of("error", "ORG_ADMIN role required"));
        String orgId = extractOrgId(auth);

        Optional<Team> teamOpt = teamRepository.findByIdAndOrganizationId(teamId, orgId);
        if (teamOpt.isEmpty()) return ResponseEntity.notFound().build();
        Team team = teamOpt.get();

        // Create or update TenantConfig for this team
        String tenantId = team.getTenantId() != null ? team.getTenantId() :
            "team-" + teamId.substring(0, 8);

        String rawKey = "nx_live_" + UUID.randomUUID().toString().replace("-", "");
        String hashedKey = TenantConfig.hashApiKey(rawKey);

        TenantConfig tc = tenantConfigRepository.findById(tenantId).orElseGet(() -> {
            TenantConfig newTc = new TenantConfig(
                tenantId, team.getName(), orgId, 10.0,
                List.of(), List.of(), List.of(), 60, false, false,
                new double[]{0.4, 0.3, 0.2, 0.1}
            );
            return newTc;
        });
        tc.setApiKey(hashedKey);
        tc.setActive(false); // Admin must explicitly enable
        tenantConfigRepository.save(tc);
        tenantRegistry.register(tc);

        // Seed providers for this team
        providerBootstrapService.seedProvidersForTenant(tenantId);

        // Link team → tenantId
        team.setTenantId(tenantId);
        teamRepository.save(team);

        // Email the raw key to the Team Lead
        if (team.getLeadEmail() != null) {
            notificationService.sendTeamApiKey(team.getLeadEmail(), team.getName(), rawKey);
        }

        log.info("API key generated for team {} (tenantId={}). Key emailed to {}", team.getName(), tenantId, team.getLeadEmail());
        return ResponseEntity.ok(Map.of(
            "rawKey", rawKey,
            "tenantId", tenantId,
            "message", "Key generated. Save this — it will not be shown again.",
            "emailedTo", team.getLeadEmail() != null ? team.getLeadEmail() : "no lead assigned"
        ));
    }

    // ─── Enable / Disable Key ─────────────────────────────────────────────────

    @PatchMapping("/api/admin/teams/{teamId}/key/enable")
    public ResponseEntity<?> enableKey(
            @PathVariable String teamId,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        return setKeyActive(teamId, true, auth);
    }

    @PatchMapping("/api/admin/teams/{teamId}/key/disable")
    public ResponseEntity<?> disableKey(
            @PathVariable String teamId,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        return setKeyActive(teamId, false, auth);
    }

    private ResponseEntity<?> setKeyActive(String teamId, boolean active, String auth) {
        if (!isOrgAdmin(auth)) return ResponseEntity.status(403).body(Map.of("error", "ORG_ADMIN role required"));
        String orgId = extractOrgId(auth);
        return teamRepository.findByIdAndOrganizationId(teamId, orgId)
            .map(team -> {
                if (team.getTenantId() == null)
                    return ResponseEntity.badRequest().body(Map.of("error", "No API key generated yet for this team"));
                tenantConfigRepository.findById(team.getTenantId()).ifPresent(tc -> {
                    tc.setActive(active);
                    tenantConfigRepository.save(tc);
                    tenantRegistry.register(tc);
                    notificationService.sendKeyStatusChange(team.getLeadEmail(), team.getName(), active);
                });
                return ResponseEntity.ok(Map.of("keyActive", active, "teamId", teamId));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    // ─── Re-send Key Email ────────────────────────────────────────────────────

    @PostMapping("/api/admin/teams/{teamId}/key/email")
    public ResponseEntity<?> resendKeyEmail(
            @PathVariable String teamId,
            @RequestBody(required = false) Map<String, String> body,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (!isOrgAdmin(auth)) return ResponseEntity.status(403).body(Map.of("error", "ORG_ADMIN role required"));
        String orgId = extractOrgId(auth);
        return teamRepository.findByIdAndOrganizationId(teamId, orgId)
            .map(team -> {
                if (team.getLeadEmail() == null)
                    return ResponseEntity.badRequest().body(Map.of("error", "No Team Lead assigned yet"));
                // Only re-send a new key if the body has one, otherwise just send a status email
                String rawKey = body != null ? body.get("rawKey") : null;
                if (rawKey != null && !rawKey.isBlank()) {
                    notificationService.sendTeamApiKey(team.getLeadEmail(), team.getName(), rawKey);
                } else {
                    notificationService.sendKeyStatusChange(team.getLeadEmail(), team.getName(), true);
                }
                return ResponseEntity.ok(Map.of("message", "Email sent to " + team.getLeadEmail()));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    // ─── Suspend / Unsuspend Team ─────────────────────────────────────────────

    @PatchMapping("/api/admin/teams/{teamId}/status")
    public ResponseEntity<?> updateTeamStatus(
            @PathVariable String teamId,
            @RequestBody Map<String, Boolean> body,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (!isOrgAdmin(auth)) return ResponseEntity.status(403).body(Map.of("error", "ORG_ADMIN role required"));
        String orgId = extractOrgId(auth);
        return teamRepository.findByIdAndOrganizationId(teamId, orgId)
            .map(team -> {
                boolean active = Boolean.TRUE.equals(body.get("active"));
                team.setActive(active);
                teamRepository.save(team);
                return ResponseEntity.ok(Map.of("active", active));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    // ─── Team Analytics Leaderboard ───────────────────────────────────────────

    @GetMapping("/api/admin/teams/analytics")
    public ResponseEntity<?> getTeamAnalytics(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (!isOrgAdmin(auth)) return ResponseEntity.status(403).body(Map.of("error", "ORG_ADMIN role required"));
        String orgId = extractOrgId(auth);

        List<Team> teams = teamRepository.findAllByOrganizationId(orgId);
        List<Map<String, Object>> analytics = teams.stream().map(team -> {
            Map<String, Object> stat = new LinkedHashMap<>(teamToMap(team));
            String tid = team.getTenantId();
            if (tid != null) {
                long totalRequests = requestLogRepository.countByTenantId(tid);
                Double totalCost = requestLogRepository.sumCostUsdByTenant(tid);
                Double avgLatency = requestLogRepository.avgLatencyMsByTenant(tid);
                stat.put("totalRequests", totalRequests);
                stat.put("totalCostUsd", totalCost != null ? totalCost : 0.0);
                stat.put("avgLatencyMs", avgLatency != null ? avgLatency : 0.0);
            } else {
                stat.put("totalRequests", 0);
                stat.put("totalCostUsd", 0.0);
                stat.put("avgLatencyMs", 0.0);
            }
            return stat;
        })
        .sorted(Comparator.comparingLong(m -> -((Number) m.get("totalRequests")).longValue()))
        .collect(Collectors.toList());

        return ResponseEntity.ok(analytics);
    }

    // ─── TEAM_LEAD: My Team View ──────────────────────────────────────────────

    @GetMapping("/api/my-team")
    public ResponseEntity<?> getMyTeam(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (!isTeamLead(auth)) return ResponseEntity.status(403).body(Map.of("error", "TEAM_LEAD role required"));
        String userId = extractUserId(auth);
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        List<Team> myTeams = teamRepository.findAllByLeadUserId(userId);
        if (myTeams.isEmpty()) return ResponseEntity.ok(Map.of("teams", List.of()));

        Team team = myTeams.get(0); // lead is typically assigned to one team
        List<TeamMembership> memberships = membershipRepository.findAllByTeamId(team.getId());

        List<Map<String, Object>> members = memberships.stream()
            .map(m -> {
                long reqCount = m.getUserId() != null ?
                    requestLogRepository.countByUserId(m.getUserId()) : 0;
                return Map.<String, Object>of(
                    "id", m.getUserId(),
                    "email", m.getUserEmail(),
                    "role", m.getRole(),
                    "joinedAt", m.getJoinedAt().toString(),
                    "totalRequests", reqCount
                );
            }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>(teamToMap(team));
        result.put("members", members);
        return ResponseEntity.ok(result);
    }
}
