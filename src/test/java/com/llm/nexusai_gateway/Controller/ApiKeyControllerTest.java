package com.llm.nexusai_gateway.Controller;

import com.llm.nexusai_gateway.Security.ApiKey;
import com.llm.nexusai_gateway.Security.ApiKeyRepository;
import com.llm.nexusai_gateway.Security.ApiKeyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
class ApiKeyControllerTest {

    @Autowired
    private ApiKeyController apiKeyController;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Test
    void createKey_returnsRawSecretKeyOnceAndPersistsKey() {
        Map<String, Object> body = Map.of(
                "name", "Integration Test Key",
                "environment", "PRODUCTION",
                "actorEmail", "tester@nexusai.io"
        );

        ResponseEntity<Map<String, Object>> response = apiKeyController.createKey(body);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        Map<String, Object> res = response.getBody();
        assertThat(res).isNotNull();

        String rawKey = (String) res.get("rawSecretKey");
        String id = (String) res.get("id");

        assertThat(rawKey).startsWith("nx_live_");
        assertThat(apiKeyRepository.findById(id)).isPresent();
    }

    @Test
    void getKeys_returnsActiveKeysList() {
        ResponseEntity<List<Map<String, Object>>> response = apiKeyController.getKeys(null);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void revokeKey_updatesStatusToRevoked() {
        Map<String, Object> body = Map.of("name", "To Revoke Key");
        ResponseEntity<Map<String, Object>> createRes = apiKeyController.createKey(body);
        String keyId = (String) createRes.getBody().get("id");

        ResponseEntity<Map<String, Object>> revokeRes = apiKeyController.revokeKey(keyId, "admin@nexusai.io");
        assertThat(revokeRes.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(revokeRes.getBody().get("status")).isEqualTo("REVOKED");

        ApiKey key = apiKeyRepository.findById(keyId).orElseThrow();
        assertThat(key.getStatus()).isEqualTo("REVOKED");
    }
}
