package com.llm.nexusai_gateway.Decision;

import com.llm.nexusai_gateway.Context.RequestContext;
import com.llm.nexusai_gateway.Reputation.ProviderReputation;
import com.llm.nexusai_gateway.Reputation.ReputationService;
import com.llm.nexusai_gateway.Tenant.TenantConfig;
import com.llm.nexusai_gateway.Tenant.TenantRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import jakarta.annotation.PostConstruct;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Federated Multi-Tenant Contextual Bandit with Reward Decomposition (FT-LinUCB).
 *
 * Research Novelty:
 * 1. Federated Transfer: New tenants warm-start using a Global Policy.
 * 2. Reward Decomposition: Learns independent components (Quality, Latency, Cost, Availability)
 *    and scalarizes at inference time based on per-tenant weight vectors.
 */
public class FederatedLinUcbEngine implements DecisionEngine {

    private static final Logger log = LoggerFactory.getLogger(FederatedLinUcbEngine.class);

    private volatile double explorationAlpha;

    public double getAlpha() {
        return this.explorationAlpha;
    }

    public void setAlpha(double alpha) {
        this.explorationAlpha = alpha;
        log.info("FederatedLinUCB exploration alpha parameter updated to {}", alpha);
    }
    private final int dimension;
    private final int numComponents = 4; // Quality, Latency, Cost, Availability

    // Global Policy (d x d) and (d x 4)
    private final ConcurrentHashMap<String, double[][]> globalArmA;
    private final ConcurrentHashMap<String, double[][]> globalArmB;

    // Tenant-Specific Policies: TenantId -> Provider -> Matrix
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, double[][]>> tenantArmA;
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, double[][]>> tenantArmB;

    private final ConcurrentHashMap<String, Integer> tenantRequestCount;

    private final ReputationService reputationService;
    private final TenantRegistry tenantRegistry;
    private final LinUcbStateRepository stateRepository;
    private final ObjectMapper objectMapper;

    private long totalSelections;

    public FederatedLinUcbEngine(double explorationAlpha, 
                                 ReputationService reputationService, 
                                 TenantRegistry tenantRegistry,
                                 LinUcbStateRepository stateRepository,
                                 ObjectMapper objectMapper) {
        this.explorationAlpha = explorationAlpha;
        this.dimension = RequestContext.FEATURE_DIMENSION;
        this.globalArmA = new ConcurrentHashMap<>();
        this.globalArmB = new ConcurrentHashMap<>();
        this.tenantArmA = new ConcurrentHashMap<>();
        this.tenantArmB = new ConcurrentHashMap<>();
        this.tenantRequestCount = new ConcurrentHashMap<>();
        this.reputationService = reputationService;
        this.tenantRegistry = tenantRegistry;
        this.stateRepository = stateRepository;
        this.objectMapper = objectMapper;
        this.totalSelections = 0;
    }

    @PostConstruct
    public void loadState() {
        log.info("FederatedLinUcbEngine: Loading LinUCB matrices from DB...");
        List<LinUcbState> states = stateRepository.findAll();
        for (LinUcbState state : states) {
            try {
                double[][] matrix = objectMapper.readValue(state.getMatrixData(), double[][].class);
                if ("GLOBAL".equals(state.getScopeId())) {
                    if ("A".equals(state.getMatrixType())) globalArmA.put(state.getProvider(), matrix);
                    else if ("B".equals(state.getMatrixType())) globalArmB.put(state.getProvider(), matrix);
                } else {
                    String tenant = state.getScopeId();
                    tenantArmA.putIfAbsent(tenant, new ConcurrentHashMap<>());
                    tenantArmB.putIfAbsent(tenant, new ConcurrentHashMap<>());
                    if ("A".equals(state.getMatrixType())) tenantArmA.get(tenant).put(state.getProvider(), matrix);
                    else if ("B".equals(state.getMatrixType())) tenantArmB.get(tenant).put(state.getProvider(), matrix);
                }
            } catch (Exception e) {
                log.error("Failed to load matrix for scope={}, provider={}", state.getScopeId(), state.getProvider(), e);
            }
        }
        log.info("FederatedLinUcbEngine: Loaded {} matrix states from DB.", states.size());
    }

    @Scheduled(fixedDelay = 60000) // Every 1 minute
    public void saveState() {
        log.info("FederatedLinUcbEngine: Persisting LinUCB matrices to DB...");
        int count = 0;
        
        // Save Global
        for (Map.Entry<String, double[][]> entry : globalArmA.entrySet()) {
            saveMatrix("GLOBAL", entry.getKey(), "A", entry.getValue());
            count++;
        }
        for (Map.Entry<String, double[][]> entry : globalArmB.entrySet()) {
            saveMatrix("GLOBAL", entry.getKey(), "B", entry.getValue());
            count++;
        }
        
        // Save Tenants
        for (Map.Entry<String, ConcurrentHashMap<String, double[][]>> tenantEntry : tenantArmA.entrySet()) {
            for (Map.Entry<String, double[][]> entry : tenantEntry.getValue().entrySet()) {
                saveMatrix(tenantEntry.getKey(), entry.getKey(), "A", entry.getValue());
                count++;
            }
        }
        for (Map.Entry<String, ConcurrentHashMap<String, double[][]>> tenantEntry : tenantArmB.entrySet()) {
            for (Map.Entry<String, double[][]> entry : tenantEntry.getValue().entrySet()) {
                saveMatrix(tenantEntry.getKey(), entry.getKey(), "B", entry.getValue());
                count++;
            }
        }
        
        log.info("FederatedLinUcbEngine: Persisted {} matrix states.", count);
    }

    private void saveMatrix(String scope, String provider, String type, double[][] matrix) {
        try {
            String json = objectMapper.writeValueAsString(matrix);
            LinUcbState state = stateRepository.findByScopeIdAndProviderAndMatrixType(scope, provider, type)
                    .orElse(new LinUcbState(scope, provider, type, ""));
            state.setMatrixData(json);
            stateRepository.save(state);
        } catch (Exception e) {
            log.error("Failed to save matrix scope={}, provider={}", scope, provider, e);
        }
    }

    @Override
    public ExplainedDecision select(RequestContext context, List<String> eligibleProviders) {
        double[] x = context.toFeatureVector();
        String tenantId = context.tenantId() != null ? context.tenantId() : "default";
        TenantConfig tenantConfig = tenantRegistry.get(tenantId).orElse(null);
        double[] weights = (tenantConfig != null) ? tenantConfig.getRewardWeights() : new double[]{0.25, 0.25, 0.25, 0.25};

        String bestArm = null;
        double bestUcb = Double.NEGATIVE_INFINITY;
        Map<String, Double> allScores = new LinkedHashMap<>();
        
        // Track the chosen alpha for logging
        double finalTransferAlpha = 0.0;

        for (String provider : eligibleProviders) {
            initArmIfAbsent(tenantId, provider);

            // 1. Global Policy Estimate
            double[][] A_g = globalArmA.get(provider);
            double[][] B_g = globalArmB.get(provider);
            double[][] Ainv_g = invertMatrix(A_g);
            double[][] Theta_g = matMatMul(Ainv_g, B_g);
            double[] r_hat_g = vecMatMul(x, Theta_g);

            // 2. Tenant Policy Estimate
            double[][] A_t = tenantArmA.get(tenantId).get(provider);
            double[][] B_t = tenantArmB.get(tenantId).get(provider);
            double[][] Ainv_t = invertMatrix(A_t);
            double[][] Theta_t = matMatMul(Ainv_t, B_t);
            double[] r_hat_t = vecMatMul(x, Theta_t);

            // 3. Calculate Uncertainties (Variance)
            double uncertaintyGlobal = Math.sqrt(quadForm(x, Ainv_g));
            double uncertaintyTenant = Math.sqrt(quadForm(x, Ainv_t));

            // 4. Dynamic Cold-Start Decay (Phase 4)
            double transferAlpha = uncertaintyTenant / (uncertaintyTenant + uncertaintyGlobal + 1e-9);

            // 5. Federated Interpolation for expected reward components
            double[] r_hat_interpolated = new double[numComponents];
            for (int c = 0; c < numComponents; c++) {
                r_hat_interpolated[c] = transferAlpha * r_hat_g[c] + (1 - transferAlpha) * r_hat_t[c];
            }

            // 6. Scalarize with Tenant Weights
            double expectedReward = dotProduct(r_hat_interpolated, weights);

            // 7. Calculate Final UCB Score
            double uncertainty = explorationAlpha * (transferAlpha * uncertaintyGlobal + (1 - transferAlpha) * uncertaintyTenant);
            double ucbScore = expectedReward + uncertainty;
            allScores.put(provider, ucbScore);

            if (ucbScore > bestUcb) {
                bestUcb = ucbScore;
                bestArm = provider;
                finalTransferAlpha = transferAlpha;
            }
        }

        tenantRequestCount.put(tenantId, tenantRequestCount.getOrDefault(tenantId, 0) + 1);
        totalSelections++;

        ProviderReputation rep = reputationService.get(bestArm);
        double avgQual = rep != null ? rep.getAvgQuality() : 0.0;
        double avgLat = rep != null ? rep.getAvgLatencyMs() : 0.0;
        double health = rep != null ? rep.getHealthScore() : 1.0;

        String finalProvider = bestArm.contains(":") ? bestArm.split(":")[0] : bestArm;
        String finalModel = bestArm.contains(":") ? bestArm.split(":")[1] : "default";

        return new ExplainedDecision(
            finalProvider, finalModel, bestUcb,
            avgQual, avgLat, health,
            String.format("FederatedLinUCB: %s scored %.4f (Dynamic α_transfer=%.2f)", bestArm, bestUcb, finalTransferAlpha),
            allScores, RoutingStrategy.FEDERATED
        );
    }

    @Override
    public void update(RequestContext context, String provider, double reward) {
        updateWithComponents(context, provider, reward, new double[]{reward, reward, reward, reward});
    }

    @Override
    public synchronized void updateWithComponents(RequestContext context, String provider, double scalarReward, double[] rewardComponents) {
        String key = provider.toLowerCase();
        String tenantId = context.tenantId() != null ? context.tenantId() : "default";
        initArmIfAbsent(tenantId, key);

        double[] x = context.toFeatureVector();

        // 1. Update Global Policy
        updateBandit(globalArmA.get(key), globalArmB.get(key), x, rewardComponents);

        // 2. Update Tenant Policy
        updateBandit(tenantArmA.get(tenantId).get(key), tenantArmB.get(tenantId).get(key), x, rewardComponents);
    }

    private void updateBandit(double[][] A, double[][] B, double[] x, double[] rewards) {
        // A ← A + x * x^T
        for (int i = 0; i < dimension; i++) {
            for (int j = 0; j < dimension; j++) {
                A[i][j] += x[i] * x[j];
            }
        }
        // B ← B + x * r^T
        for (int i = 0; i < dimension; i++) {
            for (int c = 0; c < numComponents; c++) {
                B[i][c] += x[i] * rewards[c];
            }
        }
    }

    @Override
    public RoutingStrategy getStrategy() {
        return RoutingStrategy.FEDERATED;
    }

    @Override
    public synchronized void reset() {
        globalArmA.clear();
        globalArmB.clear();
        tenantArmA.clear();
        tenantArmB.clear();
        tenantRequestCount.clear();
        totalSelections = 0;
        log.info("FederatedLinUcbEngine reset — all parameters cleared");
    }

    private void initArmIfAbsent(String tenantId, String provider) {
        // Global init
        globalArmA.computeIfAbsent(provider, k -> identityMatrix(dimension));
        globalArmB.computeIfAbsent(provider, k -> optimisticBMatrix(dimension, numComponents));

        // Tenant init
        tenantArmA.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>());
        tenantArmB.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>());
        
        tenantArmA.get(tenantId).computeIfAbsent(provider, k -> identityMatrix(dimension));
        tenantArmB.get(tenantId).computeIfAbsent(provider, k -> optimisticBMatrix(dimension, numComponents));
    }

    // --- Linear Algebra Utilities ---

    private double[][] identityMatrix(int d) {
        double[][] I = new double[d][d];
        for (int i = 0; i < d; i++) { I[i][i] = 1.0; }
        return I;
    }

    private double[][] optimisticBMatrix(int d, int c) {
        double[][] B = new double[d][c];
        for (int i = 0; i < d; i++) {
            for (int j = 0; j < c; j++) {
                B[i][j] = 1.0;
            }
        }
        return B;
    }

    private double dotProduct(double[] a, double[] b) {
        double sum = 0.0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) { sum += a[i] * b[i]; }
        return sum;
    }

    private double quadForm(double[] x, double[][] M) {
        double result = 0.0;
        for (int i = 0; i < x.length; i++) {
            for (int j = 0; j < x.length; j++) {
                result += x[i] * M[i][j] * x[j];
            }
        }
        return Math.max(0.0, result);
    }

    // Multiply Ainv (d x d) by B (d x c) -> Theta (d x c)
    private double[][] matMatMul(double[][] M1, double[][] M2) {
        int r1 = M1.length;
        int c1 = M1[0].length;
        int c2 = M2[0].length;
        double[][] res = new double[r1][c2];
        for (int i = 0; i < r1; i++) {
            for (int j = 0; j < c2; j++) {
                for (int k = 0; k < c1; k++) {
                    res[i][j] += M1[i][k] * M2[k][j];
                }
            }
        }
        return res;
    }

    // Multiply row vector x (1 x d) by Theta (d x c) -> r (1 x c)
    private double[] vecMatMul(double[] v, double[][] M) {
        int c = M[0].length;
        double[] res = new double[c];
        for (int j = 0; j < c; j++) {
            for (int i = 0; i < v.length; i++) {
                res[j] += v[i] * M[i][j];
            }
        }
        return res;
    }

    private double[][] invertMatrix(double[][] matrix) {
        int n = matrix.length;
        double[][] augmented = new double[n][2 * n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(matrix[i], 0, augmented[i], 0, n);
            augmented[i][n + i] = 1.0;
        }

        for (int col = 0; col < n; col++) {
            int maxRow = col;
            for (int row = col + 1; row < n; row++) {
                if (Math.abs(augmented[row][col]) > Math.abs(augmented[maxRow][col])) { maxRow = row; }
            }
            double[] temp = augmented[col];
            augmented[col] = augmented[maxRow];
            augmented[maxRow] = temp;

            double pivot = augmented[col][col];
            if (Math.abs(pivot) < 1e-12) return identityMatrix(n);

            for (int j = 0; j < 2 * n; j++) augmented[col][j] /= pivot;

            for (int row = 0; row < n; row++) {
                if (row != col) {
                    double factor = augmented[row][col];
                    for (int j = 0; j < 2 * n; j++) augmented[row][j] -= factor * augmented[col][j];
                }
            }
        }

        double[][] inverse = new double[n][n];
        for (int i = 0; i < n; i++) System.arraycopy(augmented[i], n, inverse[i], 0, n);
        return inverse;
    }
}
