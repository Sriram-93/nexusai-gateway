package com.llm.nexusai_gateway.Service;

import com.llm.nexusai_gateway.Model.RequestLog;
import com.llm.nexusai_gateway.Repository.RequestLogRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;

@Service
public class LoggingService {

    private final RequestLogRepository repository;
    private final MetricsService metricsService;

    public LoggingService(RequestLogRepository repository, MetricsService metricsService) {
        this.repository = repository;
        this.metricsService = metricsService;
    }

    /**
     * Asynchronously saves a request log to the database using a reactive-friendly thread pool.
     */
    public Mono<RequestLog> saveLog(RequestLog log, Integer inputTokens, Integer outputTokens) {
        return Mono.fromCallable(() -> {
            int inTok = (inputTokens != null && inputTokens > 0) ? inputTokens : estimateTokens(log.getPrompt());
            int outTok = (outputTokens != null && outputTokens > 0) ? outputTokens : estimateTokens(log.getResponse());
            log.setTokenUsage(inTok + outTok);
            log.setCostUsd(calculateCost(log.getProvider(), log.getModel(), inTok, outTok));
            if (log.getTimestamp() == null) {
                log.setTimestamp(LocalDateTime.now());
            }
            RequestLog saved = repository.save(log);

            // Record Prometheus metrics
            metricsService.recordRequest(saved.getProvider(), saved.getModel(), saved.getPriority(), saved.getStatus());
            metricsService.recordLatency(saved.getProvider(), saved.getLatencyMs());
            metricsService.recordTokens(saved.getProvider(), saved.getTokenUsage());
            metricsService.recordCost(saved.getProvider(), saved.getCostUsd());

            // Record fallback metrics if applicable
            if ("FALLBACK_RECOVERY".equalsIgnoreCase(saved.getStatus())) {
                recordFallbackMetrics(saved);
            }

            return saved;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private void recordFallbackMetrics(RequestLog log) {
        String defaultPrimary = "gemini";
        if ("MEDIUM".equalsIgnoreCase(log.getPriority()) || "LOW".equalsIgnoreCase(log.getPriority())) {
            defaultPrimary = "groq";
        }
        if (!defaultPrimary.equalsIgnoreCase(log.getProvider())) {
            metricsService.recordFallback(defaultPrimary, log.getProvider());
        }
    }

    public java.util.List<RequestLog> getAllLogs() {
        return repository.findAll();
    }

    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        // General rule of thumb: ~4 characters per token
        return Math.max(1, text.length() / 4);
    }

    private double calculateCost(String provider, String model, int inputTokens, int outputTokens) {
        if (provider == null || model == null) {
            return 0.0;
        }

        double inputRate = 0.0;
        double outputRate = 0.0;

        String providerLower = provider.toLowerCase();
        String modelLower = model.toLowerCase();

        if (providerLower.contains("cached") || providerLower.contains("(cached)")) {
            inputRate = 0.0;
            outputRate = 0.0;
        } else if (providerLower.contains("gemini")) {
            // gemini-2.5-flash rates: $0.075 / 1M input, $0.30 / 1M output
            inputRate = 0.075 / 1_000_000.0;
            outputRate = 0.30 / 1_000_000.0;
        } else if (providerLower.contains("groq")) {
            if (modelLower.contains("70b")) {
                // llama-3.3-70b-versatile: $0.59 / 1M input, $0.79 / 1M output
                inputRate = 0.59 / 1_000_000.0;
                outputRate = 0.79 / 1_000_000.0;
            } else {
                // llama-3.1-8b-instant: $0.05 / 1M input, $0.08 / 1M output
                inputRate = 0.05 / 1_000_000.0;
                outputRate = 0.08 / 1_000_000.0;
            }
        } else if (providerLower.contains("openai")) {
            if (modelLower.contains("mini")) {
                // gpt-4o-mini: $0.15 / 1M input, $0.60 / 1M output
                inputRate = 0.15 / 1_000_000.0;
                outputRate = 0.60 / 1_000_000.0;
            } else {
                // gpt-4o: $2.50 / 1M input, $10.00 / 1M output
                inputRate = 2.50 / 1_000_000.0;
                outputRate = 10.00 / 1_000_000.0;
            }
        } else if (providerLower.contains("claude") || providerLower.contains("anthropic")) {
            if (modelLower.contains("haiku")) {
                // claude-3-5-haiku: $0.80 / 1M input, $4.00 / 1M output
                inputRate = 0.80 / 1_000_000.0;
                outputRate = 4.00 / 1_000_000.0;
            } else {
                // claude-3-5-sonnet: $3.00 / 1M input, $15.00 / 1M output
                inputRate = 3.00 / 1_000_000.0;
                outputRate = 15.00 / 1_000_000.0;
            }
        } else if (providerLower.contains("ollama")) {
            // Ollama: local running, cost is $0.00
            inputRate = 0.0;
            outputRate = 0.0;
        }

        return (inputTokens * inputRate) + (outputTokens * outputRate);
    }
}
