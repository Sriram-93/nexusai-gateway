package com.llm.nexusai_gateway.Security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class SecurityModuleTest {

    @Autowired
    private SecretEncryptionService secretEncryptionService;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private ProjectRepository projectRepository;

    private Project testProject;
    private Organization testOrg;

    @BeforeEach
    void setUp() {
        testOrg = organizationRepository.save(new Organization("Acme Corp", "ADMINISTRATION"));
        Workspace testWorkspace = workspaceRepository.save(new Workspace("Engineering", testOrg));
        testProject = projectRepository.save(new Project("Core Platform", testWorkspace));
    }

    @Test
    @DisplayName("SecretEncryptionService should encrypt and decrypt secrets cleanly using AES-256-GCM")
    void testAesEncryptionDecryption() {
        String rawSecret = "sk-proj-super-secret-api-key-123456789";

        String encrypted = secretEncryptionService.encrypt(rawSecret);
        assertNotNull(encrypted);
        assertNotEquals(rawSecret, encrypted);

        String decrypted = secretEncryptionService.decrypt(encrypted);
        assertEquals(rawSecret, decrypted);
    }

    @Test
    @DisplayName("ApiKeyService should generate raw keys starting with 'nx_live_', validate via hash, and handle revocation")
    void testApiKeyLifecycle() {
        ApiKeyService.GeneratedKeyResult keyResult = apiKeyService.generateApiKey(
                "Production Integration Key",
                testProject.getId(),
                Environment.PRODUCTION,
                "admin@acme.com"
        );

        assertNotNull(keyResult);
        assertNotNull(keyResult.apiKey());
        String rawSecret = keyResult.rawSecretKey();
        assertTrue(rawSecret.startsWith("nx_live_"));

        // Validate active key
        Optional<ApiKey> validated = apiKeyService.validateApiKey(rawSecret);
        assertTrue(validated.isPresent());
        assertEquals("Production Integration Key", validated.get().getName());
        assertEquals(Environment.PRODUCTION, validated.get().getEnvironment());
        assertNotNull(validated.get().getLastUsedAt());

        // Revoke key
        apiKeyService.revokeApiKey(validated.get().getId(), "admin@acme.com");

        // Validate revoked key (should be rejected)
        Optional<ApiKey> validatedAfterRevoke = apiKeyService.validateApiKey(rawSecret);
        assertFalse(validatedAfterRevoke.isPresent());
    }
}
