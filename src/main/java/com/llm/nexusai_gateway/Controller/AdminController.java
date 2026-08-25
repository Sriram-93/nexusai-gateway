package com.llm.nexusai_gateway.Controller;

import com.llm.nexusai_gateway.Model.RequestLog;
import com.llm.nexusai_gateway.Repository.RequestLogRepository;
import com.llm.nexusai_gateway.Security.JwtUtil;
import com.llm.nexusai_gateway.Security.Organization;
import com.llm.nexusai_gateway.Security.OrganizationRepository;
import com.llm.nexusai_gateway.Security.User;
import com.llm.nexusai_gateway.Security.UserRepository;
import com.llm.nexusai_gateway.Tenant.TenantRegistry;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Admin-only REST API for org-level management.
 *
 * <h3>Accessible by:</h3>
 * <ul>
 *   <li>ORG_ADMIN — full access to all endpoints</li>
 *   <li>TEAM_LEAD — read-only access to logs and member list</li>
 * </ul>
 *
 * All endpoints require a valid Bearer JWT. Role is extracted from the token.
 */
@RestController
@RequestMapping("/api/admin")
@Transactional
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final UserRepository userRepository;
    private final RequestLogRepository logRepository;
    private final OrganizationRepository organizationRepository;
    private final TenantRegistry tenantRegistry;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final com.llm.nexusai_gateway.Team.TeamMembershipRepository membershipRepository;
    private final com.llm.nexusai_gateway.Team.TeamRepository teamRepository;

    public AdminController(UserRepository userRepository,
                           RequestLogRepository logRepository,
                           OrganizationRepository organizationRepository,
                           TenantRegistry tenantRegistry,
                           JwtUtil jwtUtil,
                           PasswordEncoder passwordEncoder,
                           com.llm.nexusai_gateway.Team.TeamMembershipRepository membershipRepository,
                           com.llm.nexusai_gateway.Team.TeamRepository teamRepository) {
        this.userRepository = userRepository;
        this.logRepository = logRepository;
        this.organizationRepository = organizationRepository;
        this.tenantRegistry = tenantRegistry;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.membershipRepository = membershipRepository;
        this.teamRepository = teamRepository;
    }

    // ─── Team Logs ─────────────────────────────────────────────────────────────

    /**
     * GET /api/admin/logs
     * Returns all recent request logs for the entire organization.
     * Accessible by ORG_ADMIN and TEAM_LEAD.
     */
    @GetMapping("/logs")
    public ResponseEntity<?> getTeamLogs(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Claims claims = extractClaims(authHeader);
        if (claims == null) return unauthorized();

        String role = claims.get("role", String.class);
        if (!isAdminOrLead(role)) return forbidden();

        String tenantId = claims.get("tenantId", String.class);
        List<RequestLog> logs = logRepository.findTop50ByTenantIdOrderByIdDesc(tenantId);
        return ResponseEntity.ok(logs);
    }

    // ─── Member Management ──────────────────────────────────────────────────────

    /**
     * GET /api/admin/members
     * Returns all users in the caller's organization.
     * Accessible by ORG_ADMIN and TEAM_LEAD.
     */
    @GetMapping("/members")
    public ResponseEntity<?> listMembers(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Claims claims = extractClaims(authHeader);
        if (claims == null) return unauthorized();

        String role = claims.get("role", String.class);
        if (!isAdminOrLead(role)) return forbidden();

        String callerEmail = claims.getSubject();
        return userRepository.findByEmail(callerEmail)
            .map(caller -> {
                Organization org = caller.getOrganization();
                if (org == null) return ResponseEntity.status(500).<Object>body(Map.of("error", "Organization not found"));

                List<MemberSummary> members = userRepository.findAllByOrganizationId(org.getId())
                    .stream()
                    .map(u -> {
                        // Find their team membership
                        var memberships = membershipRepository.findAllByUserId(u.getId());
                        String teamId = null;
                        String teamName = null;
                        if (!memberships.isEmpty()) {
                            var mem = memberships.get(0);
                            teamId = mem.getTeamId();
                            var teamOpt = teamRepository.findById(teamId);
                            if (teamOpt.isPresent()) {
                                teamName = teamOpt.get().getName();
                            }
                        }
                        return new MemberSummary(u.getId(), u.getEmail(), u.getRole(), teamId, teamName);
                    })
                    .collect(Collectors.toList());
                return ResponseEntity.ok(members);
            })
            .orElse(ResponseEntity.status(404).<Object>body(Map.of("error", "Caller not found")));
    }

    /**
     * GET /api/admin/members/{userId}/logs
     * Returns the full request log for a specific member.
     * ORG_ADMIN can inspect any member. TEAM_LEAD can inspect any member in the org.
     */
    @GetMapping("/members/{userId}/logs")
    public ResponseEntity<?> getMemberLogs(
            @PathVariable String userId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Claims claims = extractClaims(authHeader);
        if (claims == null) return unauthorized();

        String role = claims.get("role", String.class);
        if (!isAdminOrLead(role)) return forbidden();

        String tenantId = claims.get("tenantId", String.class);
        // Verify the target user is actually in the same org before returning logs
        Optional<User> targetUser = userRepository.findById(userId);
        if (targetUser.isEmpty()) return ResponseEntity.notFound().build();

        // Security: ensure caller and target are in the same org
        String callerEmail = claims.getSubject();
        Optional<User> caller = userRepository.findByEmail(callerEmail);
        if (caller.isEmpty()) return unauthorized();
        if (!sameOrg(caller.get(), targetUser.get())) return forbidden();

        List<RequestLog> logs = logRepository.findTop100ByTenantIdAndUserIdOrderByIdDesc(tenantId, userId);
        return ResponseEntity.ok(logs);
    }

    /**
     * GET /api/admin/members/{userId}/summary
     * Returns usage summary stats for a specific member (for the inspector panel).
     */
    @GetMapping("/members/{userId}/summary")
    public ResponseEntity<?> getMemberSummary(
            @PathVariable String userId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Claims claims = extractClaims(authHeader);
        if (claims == null) return unauthorized();

        String role = claims.get("role", String.class);
        if (!isAdminOrLead(role)) return forbidden();

        String tenantId = claims.get("tenantId", String.class);
        Optional<User> targetUser = userRepository.findById(userId);
        if (targetUser.isEmpty()) return ResponseEntity.notFound().build();

        // Security: same-org check
        String callerEmail = claims.getSubject();
        Optional<User> caller = userRepository.findByEmail(callerEmail);
        if (caller.isEmpty() || !sameOrg(caller.get(), targetUser.get())) return forbidden();

        long totalRequests = logRepository.countByTenantIdAndUserId(tenantId, userId);
        Double totalCost = logRepository.sumCostUsdByTenantAndUser(tenantId, userId);
        Double avgLatency = logRepository.avgLatencyMsByTenantAndUser(tenantId, userId);

        // Provider breakdown
        List<RequestLog> recentLogs = logRepository.findTop100ByTenantIdAndUserIdOrderByIdDesc(tenantId, userId);
        Map<String, Long> providerBreakdown = recentLogs.stream()
            .filter(l -> l.getProvider() != null)
            .collect(Collectors.groupingBy(RequestLog::getProvider, Collectors.counting()));

        return ResponseEntity.ok(Map.of(
            "userId", userId,
            "email", targetUser.get().getEmail(),
            "role", targetUser.get().getRole(),
            "totalRequests", totalRequests,
            "totalCostUsd", totalCost != null ? totalCost : 0.0,
            "avgLatencyMs", avgLatency != null ? Math.round(avgLatency) : 0,
            "providerBreakdown", providerBreakdown
        ));
    }

    /**
     * PATCH /api/admin/members/{userId}/role
     * Promote or demote a member. ORG_ADMIN only.
     * Body: { "role": "TEAM_LEAD" | "TEAM_MEMBER" }
     */
    @PatchMapping("/members/{userId}/role")
    public ResponseEntity<?> updateMemberRole(
            @PathVariable String userId,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Claims claims = extractClaims(authHeader);
        if (claims == null) return unauthorized();

        String callerRole = claims.get("role", String.class);
        if (!"ORG_ADMIN".equals(callerRole)) return forbidden();

        String newRole = body.get("role");
        if (newRole == null || (!newRole.equals("TEAM_LEAD") && !newRole.equals("TEAM_MEMBER"))) {
            return ResponseEntity.badRequest().body(Map.of("error", "role must be TEAM_LEAD or TEAM_MEMBER"));
        }

        Optional<User> targetUser = userRepository.findById(userId);
        if (targetUser.isEmpty()) return ResponseEntity.notFound().build();

        // Security: same-org check
        String callerEmail = claims.getSubject();
        Optional<User> caller = userRepository.findByEmail(callerEmail);
        if (caller.isEmpty() || !sameOrg(caller.get(), targetUser.get())) return forbidden();

        // Prevent demoting self
        if (targetUser.get().getEmail().equals(callerEmail)) {
            return ResponseEntity.badRequest().body(Map.of("error", "You cannot change your own role"));
        }

        targetUser.get().setRole(newRole);
        userRepository.save(targetUser.get());
        log.info("Admin {} changed role of {} to {}", callerEmail, targetUser.get().getEmail(), newRole);
        return ResponseEntity.ok(Map.of("userId", userId, "role", newRole, "message", "Role updated successfully"));
    }

    /**
     * DELETE /api/admin/members/{userId}
     * Remove a member from the organization. ORG_ADMIN only.
     */
    @DeleteMapping("/members/{userId}")
    public ResponseEntity<?> removeMember(
            @PathVariable String userId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Claims claims = extractClaims(authHeader);
        if (claims == null) return unauthorized();

        String callerRole = claims.get("role", String.class);
        if (!"ORG_ADMIN".equals(callerRole)) return forbidden();

        Optional<User> targetUser = userRepository.findById(userId);
        if (targetUser.isEmpty()) return ResponseEntity.notFound().build();

        // Security: same-org check + prevent self-deletion
        String callerEmail = claims.getSubject();
        Optional<User> caller = userRepository.findByEmail(callerEmail);
        if (caller.isEmpty() || !sameOrg(caller.get(), targetUser.get())) return forbidden();

        if (targetUser.get().getEmail().equals(callerEmail)) {
            return ResponseEntity.badRequest().body(Map.of("error", "You cannot remove yourself"));
        }

        userRepository.delete(targetUser.get());
        log.info("Admin {} removed member {} from org {}", callerEmail, targetUser.get().getEmail(),
                 caller.get().getOrganization().getName());
        return ResponseEntity.ok(Map.of("message", "Member removed successfully"));
    }

    /**
     * POST /api/admin/members/invite
     * Add a new member to the organization directly (admin creates account).
     * ORG_ADMIN only. Uses a temporary password provided by admin.
     * Body: { "email": "...", "password": "...", "role": "TEAM_LEAD" | "TEAM_MEMBER" }
     */
    @PostMapping("/members/invite")
    public ResponseEntity<?> inviteMember(
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Claims claims = extractClaims(authHeader);
        if (claims == null) return unauthorized();

        String callerRole = claims.get("role", String.class);
        if (!"ORG_ADMIN".equals(callerRole)) return forbidden();

        String email = body.get("email");
        String password = body.get("password");
        String role = body.getOrDefault("role", "TEAM_MEMBER");
        String teamId = body.get("teamId");

        if (email == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "email and password are required"));
        }
        if (!role.equals("TEAM_LEAD") && !role.equals("TEAM_MEMBER")) {
            return ResponseEntity.badRequest().body(Map.of("error", "role must be TEAM_LEAD or TEAM_MEMBER"));
        }
        if (userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email already registered"));
        }

        String callerEmail = claims.getSubject();
        return userRepository.findByEmail(callerEmail)
            .map(caller -> {
                Organization org = caller.getOrganization();
                if (org == null) return ResponseEntity.status(500).<Object>body(Map.of("error", "Organization not found"));

                User newMember = new User(email, passwordEncoder.encode(password), role, org);
                userRepository.save(newMember);
                log.info("Admin {} added member {} ({}) to org {}", callerEmail, email, role, org.getName());

                if (teamId != null && !teamId.isBlank()) {
                    teamRepository.findByIdAndOrganizationId(teamId, org.getId()).ifPresent(team -> {
                        membershipRepository.save(new com.llm.nexusai_gateway.Team.TeamMembership(teamId, newMember.getId(), email, role));
                        
                        // If they are invited as TEAM_LEAD, auto-assign them as the team lead
                        if ("TEAM_LEAD".equals(role)) {
                            // Demote old lead if exists
                            if (team.getLeadUserId() != null && !team.getLeadUserId().equals(newMember.getId())) {
                                userRepository.findById(team.getLeadUserId()).ifPresent(oldLead -> {
                                    oldLead.setRole("TEAM_MEMBER");
                                    userRepository.save(oldLead);
                                });
                            }
                            team.setLeadUserId(newMember.getId());
                            team.setLeadEmail(email);
                            teamRepository.save(team);
                        }
                    });
                }

                return ResponseEntity.ok(Map.<String, Object>of(
                    "message", "Member added successfully",
                    "userId", newMember.getId(),
                    "email", email,
                    "role", role
                ));
            })
            .orElse(ResponseEntity.status(404).<Object>body(Map.of("error", "Caller not found")));
    }

    /**
     * POST /api/admin/members/bulk-invite
     * Add multiple new members to the organization via JSON list (representing parsed CSV).
     * Body: [{ "email": "...", "role": "TEAM_MEMBER", "teamId": "..." }, ...]
     */
    @PostMapping("/members/bulk-invite")
    public ResponseEntity<?> bulkInviteMembers(
            @RequestBody List<Map<String, String>> bulkMembers,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Claims claims = extractClaims(authHeader);
        if (claims == null) return unauthorized();

        String callerRole = claims.get("role", String.class);
        if (!"ORG_ADMIN".equals(callerRole)) return forbidden();

        String callerEmail = claims.getSubject();
        Optional<User> callerOpt = userRepository.findByEmail(callerEmail);
        if (callerOpt.isEmpty()) return ResponseEntity.status(404).<Object>body(Map.of("error", "Caller not found"));
        
        Organization org = callerOpt.get().getOrganization();
        if (org == null) return ResponseEntity.status(500).<Object>body(Map.of("error", "Organization not found"));

        int addedCount = 0;
        int skippedCount = 0;

        for (Map<String, String> row : bulkMembers) {
            String email = row.get("email");
            if (email == null || email.isBlank()) { skippedCount++; continue; }
            
            String role = row.getOrDefault("role", "TEAM_MEMBER");
            String teamId = row.get("teamId");

            if (userRepository.findByEmail(email).isPresent()) { skippedCount++; continue; }

            String tempPassword = "Temp@" + UUID.randomUUID().toString().substring(0, 8);
            User newMember = new User(email, passwordEncoder.encode(tempPassword), role, org);
            userRepository.save(newMember);
            addedCount++;

            if (teamId != null && !teamId.isBlank()) {
                teamRepository.findByIdAndOrganizationId(teamId, org.getId()).ifPresent(team -> {
                    membershipRepository.save(new com.llm.nexusai_gateway.Team.TeamMembership(teamId, newMember.getId(), email, role));
                    
                    if ("TEAM_LEAD".equals(role)) {
                        if (team.getLeadUserId() != null && !team.getLeadUserId().equals(newMember.getId())) {
                            userRepository.findById(team.getLeadUserId()).ifPresent(oldLead -> {
                                oldLead.setRole("TEAM_MEMBER");
                                userRepository.save(oldLead);
                            });
                        }
                        team.setLeadUserId(newMember.getId());
                        team.setLeadEmail(email);
                        teamRepository.save(team);
                    }
                });
            }
        }

        return ResponseEntity.ok(Map.of(
            "message", "Bulk import completed",
            "added", addedCount,
            "skipped", skippedCount
        ));
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private Claims extractClaims(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        try {
            return jwtUtil.extractClaim(authHeader.substring(7), c -> c);
        } catch (Exception e) {
            log.warn("Invalid JWT in admin endpoint: {}", e.getMessage());
            return null;
        }
    }

    private boolean isAdminOrLead(String role) {
        return "ORG_ADMIN".equals(role) || "TEAM_LEAD".equals(role) || "SOLO".equals(role);
    }

    private boolean sameOrg(User caller, User target) {
        if (caller.getOrganization() == null || target.getOrganization() == null) return false;
        return caller.getOrganization().getId().equals(target.getOrganization().getId());
    }

    private ResponseEntity<Object> unauthorized() {
        return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
    }

    private ResponseEntity<Object> forbidden() {
        return ResponseEntity.status(403).body(Map.of("error", "Insufficient permissions"));
    }

    // ─── DTOs ────────────────────────────────────────────────────────────────────

    public record MemberSummary(String id, String email, String role, String teamId, String teamName) {}
}
