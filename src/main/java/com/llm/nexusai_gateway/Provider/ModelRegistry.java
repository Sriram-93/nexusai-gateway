package com.llm.nexusai_gateway.Provider;

import com.llm.nexusai_gateway.Repository.RegisteredModelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Central catalogue of all routable LLM model arms.
 *
 * <h3>Source priority (highest to lowest):</h3>
 * <ol>
 *   <li><strong>Database ({@code registered_models} table)</strong> — populated by
 *       {@link ModelDiscoveryService} from provider APIs and customer configurations
 *       via the management REST API. This is the live, runtime-authoritative source.</li>
 *   <li><strong>{@code application.properties} ({@code nexusai.models.*})</strong> —
 *       used only as a seed/bootstrap mechanism. On first startup, the config entries
 *       are imported into the DB. Subsequently, the DB is the single source of truth.</li>
 * </ol>
 *
 * <p>No model name, price, or capability value should ever be hardcoded anywhere
 * in the Java codebase. All data flows from config → DB → here → routing engine.</p>
 */
@Component
@ConfigurationProperties(prefix = "nexusai")
public class ModelRegistry {

    private static final Logger log = LoggerFactory.getLogger(ModelRegistry.class);

    // Injected via Spring: reads from RegisteredModelRepository (DB primary source)
    private final RegisteredModelRepository modelRepository;

    // Raw properties from application.properties — used for seeding only
    private Map<String, ModelProperties> models = new LinkedHashMap<>();

    public ModelRegistry(RegisteredModelRepository modelRepository) {
        this.modelRepository = modelRepository;
    }

    // ─── Spring binding setter ─────────────────────────────────────────────────

    public void setModels(Map<String, ModelProperties> models) {
        this.models = models;
    }

    public Map<String, ModelProperties> getModels() {
        return models;
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Returns all enabled arm keys from the DB.
     * This is what the routing engine uses to build the eligible provider list.
     */
    public List<String> getEnabledArmKeys() {
        List<String> dbArms = modelRepository.findByEnabledTrue().stream()
            .map(RegisteredModel::getArmKey)
            .collect(Collectors.toList());

        if (!dbArms.isEmpty()) {
            return dbArms;
        }

        // Fallback to config-file entries if DB is empty (first-run before seeding completes)
        log.warn("No enabled models found in DB. Falling back to application.properties catalogue.");
        return models.values().stream()
            .filter(ModelProperties::isEnabled)
            .map(p -> p.getProvider() + ":" + p.getModelId())
            .collect(Collectors.toList());
    }

    /**
     * Looks up a model by its arm key.
     * Reads from DB first; falls back to config if not found.
     */
    public Optional<ModelCatalog> findByArmKey(String armKey) {
        // Primary: DB lookup
        Optional<RegisteredModel> dbModel = modelRepository.findByArmKey(armKey);
        if (dbModel.isPresent()) {
            return dbModel.map(this::toCatalog);
        }

        // Fallback: scan application.properties entries
        return models.values().stream()
            .filter(p -> (p.getProvider() + ":" + p.getModelId()).equals(armKey))
            .map(p -> new ModelCatalog(
                armKey, p.getProvider(), p.getModelId(),
                p.getInputPricePer1M(), p.getOutputPricePer1M(),
                p.getEstimatedLatencyMs(), p.getContextWindowTokens(), p.isEnabled()
            ))
            .findFirst();
    }

    /**
     * Computes exact cost from DB pricing data.
     * Returns 0.0 if the arm is not registered (free/unknown).
     */
    public double computeCostUsd(String armKey, int inputTokens, int outputTokens) {
        return findByArmKey(armKey)
            .map(m -> m.computeCostUsd(inputTokens, outputTokens))
            .orElse(0.0);
    }

    /**
     * Returns enabled arms sorted by input price ascending (cheapest-first routing).
     * Reads from the DB which has exact prices from LiteLLM sync.
     */
    public List<String> getEnabledArmKeysSortedByCost() {
        List<String> dbSorted = modelRepository.findEnabledOrderByPriceAsc().stream()
            .map(RegisteredModel::getArmKey)
            .collect(Collectors.toList());
        if (!dbSorted.isEmpty()) return dbSorted;
        // Fallback
        return models.values().stream()
            .filter(ModelProperties::isEnabled)
            .sorted(java.util.Comparator.comparingDouble(ModelProperties::getInputPricePer1M))
            .map(p -> p.getProvider() + ":" + p.getModelId())
            .collect(Collectors.toList());
    }

    /**
     * Returns enabled arms sorted by estimated latency ascending (fastest-first routing).
     */
    public List<String> getEnabledArmKeysSortedByLatency() {
        List<String> dbSorted = modelRepository.findEnabledOrderByLatencyAsc().stream()
            .map(RegisteredModel::getArmKey)
            .collect(Collectors.toList());
        if (!dbSorted.isEmpty()) return dbSorted;
        return models.values().stream()
            .filter(ModelProperties::isEnabled)
            .sorted(java.util.Comparator.comparingInt(ModelProperties::getEstimatedLatencyMs))
            .map(p -> p.getProvider() + ":" + p.getModelId())
            .collect(Collectors.toList());
    }

    // ─── Bootstrap: seed DB from application.properties on first run ───────────

    /**
     * Called by {@link ProviderBootstrapService} on startup.
     * Imports any application.properties model entries that do not yet exist in the DB.
     * This is a one-time migration — subsequent runs are entirely DB-driven.
     */
    public int seedFromConfig() {
        int seeded = 0;
        for (ModelProperties props : models.values()) {
            String armKey = props.getProvider() + ":" + props.getModelId();
            if (!modelRepository.existsByArmKey(armKey)) {
                RegisteredModel model = new RegisteredModel(props.getProvider(), props.getModelId());
                model.setInputPricePer1M(props.getInputPricePer1M());
                model.setOutputPricePer1M(props.getOutputPricePer1M());
                model.setEstimatedLatencyMs(props.getEstimatedLatencyMs());
                model.setContextWindowTokens(props.getContextWindowTokens());
                model.setEnabled(props.isEnabled());
                model.setPricingVerified(props.getInputPricePer1M() > 0);
                modelRepository.save(model);
                seeded++;
                log.info("Seeded model from config: {}", armKey);
            }
        }
        return seeded;
    }

    // ─── Private helpers ───────────────────────────────────────────────────────

    private ModelCatalog toCatalog(RegisteredModel m) {
        return new ModelCatalog(
            m.getArmKey(), m.getProviderSlug(), m.getModelId(),
            m.getInputPricePer1M(), m.getOutputPricePer1M(),
            m.getEstimatedLatencyMs(), m.getContextWindowTokens(), m.isEnabled()
        );
    }

    // ─── Inner property class (for Spring @ConfigurationProperties binding) ────

    public static class ModelProperties {
        private String provider;
        private String modelId;
        private double inputPricePer1M;
        private double outputPricePer1M;
        private int estimatedLatencyMs = 500;
        private int contextWindowTokens = 8192;
        private boolean enabled = true;

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getModelId() { return modelId; }
        public void setModelId(String modelId) { this.modelId = modelId; }
        public double getInputPricePer1M() { return inputPricePer1M; }
        public void setInputPricePer1M(double v) { this.inputPricePer1M = v; }
        public double getOutputPricePer1M() { return outputPricePer1M; }
        public void setOutputPricePer1M(double v) { this.outputPricePer1M = v; }
        public int getEstimatedLatencyMs() { return estimatedLatencyMs; }
        public void setEstimatedLatencyMs(int v) { this.estimatedLatencyMs = v; }
        public int getContextWindowTokens() { return contextWindowTokens; }
        public void setContextWindowTokens(int v) { this.contextWindowTokens = v; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
