package com.llm.nexusai_gateway.Security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
public class ApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);
    private static final String KEY_PREFIX = "nx_live_";

    private final ApiKeyRepository apiKeyRepository;
    private final ProjectRepository projectRepository;
    private final WorkspaceRepository workspaceRepository;
    private final OrganizationRepository organizationRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public ApiKeyService(ApiKeyRepository apiKeyRepository,
                         ProjectRepository projectRepository,
                         WorkspaceRepository workspaceRepository,
                         OrganizationRepository organizationRepository,
                         AuditLogRepository auditLogRepository,
                         UserRepository userRepository) {
        this.apiKeyRepository = apiKeyRepository;
        this.projectRepository = projectRepository;
        this.workspaceRepository = workspaceRepository;
        this.organizationRepository = organizationRepository;
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    public record GeneratedKeyResult(ApiKey apiKey, String rawSecretKey) {}

    /**
     * Generate a new API Key.
     * The rawSecretKey is returned ONLY once to the caller. Only the SHA-256 hash is saved in DB.
     */
    @org.springframework.transaction.annotation.Transactional
    public GeneratedKeyResult generateApiKey(String name, String projectId, Environment environment, String actorEmail) {
        return generateApiKey(name, projectId, environment, actorEmail, null);
    }

    @org.springframework.transaction.annotation.Transactional
    public GeneratedKeyResult generateApiKey(String name, String projectId, Environment environment, String actorEmail, String organizationId) {
        Project project = getOrCreateProject(projectId, organizationId, actorEmail);

        byte[] randomBytes = new byte[24];
        new SecureRandom().nextBytes(randomBytes);
        String rawSecret = KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        String keyHash = SecretEncryptionService.hashApiKey(rawSecret);
        String keyPrefixDisplay = rawSecret.substring(0, Math.min(12, rawSecret.length())) + "••••";

        ApiKey apiKey = new ApiKey(name, keyHash, keyPrefixDisplay, project, environment);
        ApiKey saved = apiKeyRepository.save(apiKey);

        try {
            String orgId = (project != null && project.getWorkspace() != null && project.getWorkspace().getOrganization() != null)
                    ? project.getWorkspace().getOrganization().getId() : "default";
            String projName = (project != null && project.getName() != null) ? project.getName() : "default";
            auditLogRepository.save(new AuditLog(
                    actorEmail != null ? actorEmail : "system",
                    "API_KEY_CREATED",
                    "ApiKey:" + saved.getId() + " (" + name + ")",
                    orgId,
                    "{\"environment\":\"" + environment + "\",\"project\":\"" + projName + "\"}"
            ));
        } catch (Exception e) {
            log.warn("Could not save audit log for API key creation: {}", e.getMessage());
        }

        log.info("Generated new API Key id={}, prefix={} for project={}", saved.getId(), keyPrefixDisplay, project.getName());
        return new GeneratedKeyResult(saved, rawSecret);
    }

    /**
     * Validate an incoming Bearer API Key from request headers.
     */
    public Optional<ApiKey> validateApiKey(String rawApiKey) {
        if (rawApiKey == null || !rawApiKey.startsWith(KEY_PREFIX)) {
            return Optional.empty();
        }
        String hash = SecretEncryptionService.hashApiKey(rawApiKey);
        Optional<ApiKey> apiKeyOpt = apiKeyRepository.findByKeyHash(hash);

        if (apiKeyOpt.isPresent()) {
            ApiKey key = apiKeyOpt.get();
            if ("ACTIVE".equalsIgnoreCase(key.getStatus())) {
                key.setLastUsedAt(Instant.now());
                apiKeyRepository.save(key);
                return Optional.of(key);
            }
        }
        return Optional.empty();
    }

    @Transactional
    public void revokeApiKey(String keyId, String actorEmail) {
        apiKeyRepository.findById(keyId).ifPresent(key -> {
            String orgId = null;
            try {
                if (key.getProject() != null && key.getProject().getWorkspace() != null && key.getProject().getWorkspace().getOrganization() != null) {
                    orgId = key.getProject().getWorkspace().getOrganization().getId();
                }
            } catch (Exception e) {
                log.warn("Could not extract orgId: {}", e.getMessage());
            }

            key.setStatus("REVOKED");
            apiKeyRepository.save(key);

            try {
                auditLogRepository.save(new AuditLog(
                        actorEmail != null ? actorEmail : "system",
                        "API_KEY_REVOKED",
                        "ApiKey:" + key.getId(),
                        orgId,
                        "{\"prefix\":\"" + key.getKeyPrefix() + "\"}"
                ));
            } catch (Exception e) {
                log.warn("Could not save audit log: {}", e.getMessage());
            }
            log.info("Revoked API Key id={}", keyId);
        });
    }

    public List<ApiKey> getProjectKeys(String projectId) {
        return apiKeyRepository.findByProjectId(projectId);
    }

    @org.springframework.transaction.annotation.Transactional
    private Project getOrCreateProject(String projectId, String organizationId, String actorEmail) {
        if (projectId != null && !projectId.isBlank()) {
            Optional<Project> existing = projectRepository.findById(projectId);
            if (existing.isPresent()) return existing.get();
        }

        Organization org = null;
        if (organizationId != null && !organizationId.isBlank()) {
            org = organizationRepository.findById(organizationId).orElse(null);
        }
        if (org == null && actorEmail != null && !actorEmail.isBlank()) {
            org = userRepository.findByEmail(actorEmail)
                    .map(User::getOrganization)
                    .orElse(null);
        }
        if (org == null) {
            org = organizationRepository.findAll().stream().findFirst()
                    .orElseGet(() -> organizationRepository.save(new Organization("Default Organization", "ADMINISTRATION")));
        }

        final Organization finalOrg = org;
        Workspace ws = workspaceRepository.findAll().stream()
                .filter(w -> w.getOrganization() != null && w.getOrganization().getId().equals(finalOrg.getId()))
                .findFirst()
                .orElseGet(() -> workspaceRepository.save(new Workspace("Production Workspace", finalOrg)));

        return projectRepository.findAll().stream()
                .filter(p -> p.getWorkspace() != null && p.getWorkspace().getOrganization() != null && p.getWorkspace().getOrganization().getId().equals(finalOrg.getId()))
                .findFirst()
                .orElseGet(() -> projectRepository.save(new Project("Main Project", ws)));
    }
}
