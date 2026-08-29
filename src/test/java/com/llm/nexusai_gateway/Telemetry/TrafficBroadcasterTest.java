package com.llm.nexusai_gateway.Telemetry;

import com.llm.nexusai_gateway.Security.AuditLog;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class TrafficBroadcasterTest {

    @Test
    void whenAuditLogEmitted_subscribersReceiveEvent() {
        TrafficBroadcaster broadcaster = new TrafficBroadcaster();
        Flux<AuditLog> stream = broadcaster.getStream();

        AuditLog log1 = new AuditLog("user-1", "GATEWAY_REQUEST", "Tenant:test", "test-org", "{}");

        StepVerifier.create(stream)
                .then(() -> broadcaster.broadcast(log1))
                .expectNextMatches(entry -> entry.getAction().equals("GATEWAY_REQUEST") && entry.getActorEmail().equals("user-1"))
                .thenCancel()
                .verify();
    }
}
