package com.llm.nexusai_gateway.Telemetry;

import com.llm.nexusai_gateway.Security.AuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * In-Memory Event Broadcaster for Real-Time Telemetry & Traffic Stream.
 *
 * Multicasts AuditLog events live to all connected SSE clients (/api/telemetry/stream).
 */
@Service
public class TrafficBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(TrafficBroadcaster.class);

    private final Sinks.Many<AuditLog> sink = Sinks.many().multicast().onBackpressureBuffer(256, false);

    public void broadcast(AuditLog logEntry) {
        if (logEntry == null) return;
        Sinks.EmitResult result = sink.tryEmitNext(logEntry);
        if (result.isFailure()) {
            log.trace("TrafficBroadcaster emit result: {}", result);
        }
    }

    public Flux<AuditLog> getStream() {
        return sink.asFlux();
    }
}
