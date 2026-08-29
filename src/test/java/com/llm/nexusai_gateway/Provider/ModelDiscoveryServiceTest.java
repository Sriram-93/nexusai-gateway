package com.llm.nexusai_gateway.Provider;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThatNoException;

@SpringBootTest
@ActiveProfiles("test")
class ModelDiscoveryServiceTest {

    @Autowired
    private ModelDiscoveryService modelDiscoveryService;

    @Test
    void discoverAllProviders_executesWithoutException() {
        assertThatNoException().isThrownBy(() -> {
            modelDiscoveryService.discoverAllProviders();
        });
    }
}
