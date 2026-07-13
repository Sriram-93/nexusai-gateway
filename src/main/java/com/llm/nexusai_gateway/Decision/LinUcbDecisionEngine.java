package com.llm.nexusai_gateway.Decision;

import com.llm.nexusai_gateway.Context.RequestContext;
import com.llm.nexusai_gateway.Reputation.ProviderReputation;
import com.llm.nexusai_gateway.Reputation.ReputationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LinUCB Contextual Bandit Decision Engine — the core research contribution.
 *
 * Implements the LinUCB algorithm (Li et al., 2010) for contextual multi-armed bandits.
 * Each LLM provider is an "arm". The context vector is extracted from each request.
 * The algorithm learns a linear reward model per arm and uses Upper Confidence Bound
 * exploration to balance exploitation of known-good providers with exploration of
 * uncertain ones.
 *
 * Doc 05 Gap 2: "Introduce online learning that continuously updates routing decisions."
 * Doc 06 Novelty 1: "Closed-loop adaptive routing."
 * Doc 07 H1: "Adaptive routing improves routing decision quality."
 *
 * Mathematical formulation:
 *   For arm a, context x ∈ R^d:
 *     A_a ← d×d matrix (initialized to I_d)
 *     b_a ← d×1 vector (initialized to 0)
 *     θ_a = A_a^{-1} * b_a
 *     UCB_a = θ_a^T * x + α * sqrt(x^T * A_a^{-1} * x)
 *
 *   Select: a* = argmax_a UCB_a
 *   After reward r:
 *     A_{a*} ← A_{a*} + x * x^T
 *     b_{a*} ← b_{a*} + r * x
 */
public class LinUcbDecisionEngine implements DecisionEngine {

    private static final Logger log = LoggerFactory.getLogger(LinUcbDecisionEngine.class);

    /** Exploration parameter — controls exploration vs exploitation tradeoff */
    private final double alpha;

    /** Feature dimension (from RequestContext.FEATURE_DIMENSION) */
    private final int dimension;

    /** Per-arm parameters: A matrix (d×d) */
    private final ConcurrentHashMap<String, double[][]> armA;

    /** Per-arm parameters: b vector (d×1) */
    private final ConcurrentHashMap<String, double[]> armB;

    /** Provider model mapping */
    private final Map<String, String> providerModelMap;

    private final ReputationService reputationService;

    /** Total selections across all arms (for logging) */
    private long totalSelections;

    public LinUcbDecisionEngine(double alpha, ReputationService reputationService) {
        this.alpha = alpha;
        this.dimension = RequestContext.FEATURE_DIMENSION;
        this.armA = new ConcurrentHashMap<>();
        this.armB = new ConcurrentHashMap<>();
        this.reputationService = reputationService;
        this.totalSelections = 0;
        this.providerModelMap = Map.of(
            "gemini", "gemini-2.5-flash",
            "groq", "llama-3.3-70b-versatile"
        );
    }

    @Override
    public ExplainedDecision select(RequestContext context, List<String> eligibleProviders) {
        double[] x = context.toFeatureVector();

        String bestArm = null;
        double bestUcb = Double.NEGATIVE_INFINITY;
        Map<String, Double> allScores = new LinkedHashMap<>();

        for (String provider : eligibleProviders) {
            initArmIfAbsent(provider);

            double[][] A = armA.get(provider);
            double[] b = armB.get(provider);

            // θ = A^{-1} * b
            double[][] Ainv = invertMatrix(A);
            double[] theta = matVecMul(Ainv, b);

            // UCB = θ^T * x + α * sqrt(x^T * A^{-1} * x)
            double expectedReward = dotProduct(theta, x);
            double uncertainty = alpha * Math.sqrt(quadForm(x, Ainv));
            double ucbScore = expectedReward + uncertainty;

            allScores.put(provider, ucbScore);

            if (ucbScore > bestUcb) {
                bestUcb = ucbScore;
                bestArm = provider;
            }
        }

        totalSelections++;

        ProviderReputation rep = reputationService.get(bestArm);
        String finalProvider = bestArm.contains(":") ? bestArm.split(":")[0] : bestArm;
        String finalModel = bestArm.contains(":") ? bestArm.split(":")[1] : providerModelMap.getOrDefault(finalProvider, "default");

        log.info("LinUCB selected {} (UCB={:.4f}) from {} candidates [selection #{}]",
                 bestArm, bestUcb, eligibleProviders.size(), totalSelections);

        return new ExplainedDecision(
            finalProvider, finalModel, bestUcb,
            rep.getAvgQuality(),
            rep.getAvgLatencyMs(),
            rep.getHealthScore(),
            String.format("LinUCB: %s scored %.4f (expected=%.4f + exploration=%.4f)",
                          bestArm, bestUcb,
                          bestUcb - alpha * Math.sqrt(quadForm(x, invertMatrix(armA.get(bestArm)))),
                          alpha * Math.sqrt(quadForm(x, invertMatrix(armA.get(bestArm))))),
            allScores,
            RoutingStrategy.ADAPTIVE
        );
    }

    @Override
    public synchronized void update(RequestContext context, String provider, double reward) {
        String key = provider.toLowerCase();
        initArmIfAbsent(key);

        double[] x = context.toFeatureVector();
        double[][] A = armA.get(key);
        double[] b = armB.get(key);

        // A_a ← A_a + x * x^T
        for (int i = 0; i < dimension; i++) {
            for (int j = 0; j < dimension; j++) {
                A[i][j] += x[i] * x[j];
            }
        }

        // b_a ← b_a + r * x
        for (int i = 0; i < dimension; i++) {
            b[i] += reward * x[i];
        }

        log.debug("LinUCB updated arm {} with reward {:.4f}", key, reward);
    }

    @Override
    public RoutingStrategy getStrategy() {
        return RoutingStrategy.ADAPTIVE;
    }

    @Override
    public synchronized void reset() {
        armA.clear();
        armB.clear();
        totalSelections = 0;
        log.info("LinUCB engine reset — all arm parameters cleared");
    }

    private void initArmIfAbsent(String provider) {
        armA.computeIfAbsent(provider, k -> identityMatrix(dimension));
        armB.computeIfAbsent(provider, k -> {
            double[] b = new double[dimension];
            // Optimistic Initialization: assume expected reward is 1.0 to guarantee exploration
            for (int i = 0; i < dimension; i++) {
                b[i] = 1.0; 
            }
            return b;
        });
    }

    // --- Linear Algebra Utilities ---

    private double[][] identityMatrix(int d) {
        double[][] I = new double[d][d];
        for (int i = 0; i < d; i++) {
            I[i][i] = 1.0;
        }
        return I;
    }

    private double dotProduct(double[] a, double[] b) {
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    /** x^T * M * x — the quadratic form used for uncertainty estimation */
    private double quadForm(double[] x, double[][] M) {
        double result = 0.0;
        for (int i = 0; i < x.length; i++) {
            for (int j = 0; j < x.length; j++) {
                result += x[i] * M[i][j] * x[j];
            }
        }
        return Math.max(0.0, result); // Ensure non-negative for sqrt
    }

    /** Matrix × vector multiplication */
    private double[] matVecMul(double[][] M, double[] v) {
        double[] result = new double[M.length];
        for (int i = 0; i < M.length; i++) {
            for (int j = 0; j < v.length; j++) {
                result[i] += M[i][j] * v[j];
            }
        }
        return result;
    }

    /**
     * Invert a small d×d matrix using Gauss-Jordan elimination.
     * For d=7 (our feature dimension), this is computationally trivial.
     */
    private double[][] invertMatrix(double[][] matrix) {
        int n = matrix.length;
        double[][] augmented = new double[n][2 * n];

        // Build [A | I]
        for (int i = 0; i < n; i++) {
            System.arraycopy(matrix[i], 0, augmented[i], 0, n);
            augmented[i][n + i] = 1.0;
        }

        // Forward elimination with partial pivoting
        for (int col = 0; col < n; col++) {
            // Find pivot
            int maxRow = col;
            for (int row = col + 1; row < n; row++) {
                if (Math.abs(augmented[row][col]) > Math.abs(augmented[maxRow][col])) {
                    maxRow = row;
                }
            }
            double[] temp = augmented[col];
            augmented[col] = augmented[maxRow];
            augmented[maxRow] = temp;

            double pivot = augmented[col][col];
            if (Math.abs(pivot) < 1e-12) {
                // Singular or near-singular — return identity as safe fallback
                return identityMatrix(n);
            }

            // Scale pivot row
            for (int j = 0; j < 2 * n; j++) {
                augmented[col][j] /= pivot;
            }

            // Eliminate column
            for (int row = 0; row < n; row++) {
                if (row != col) {
                    double factor = augmented[row][col];
                    for (int j = 0; j < 2 * n; j++) {
                        augmented[row][j] -= factor * augmented[col][j];
                    }
                }
            }
        }

        // Extract inverse
        double[][] inverse = new double[n][n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(augmented[i], n, inverse[i], 0, n);
        }
        return inverse;
    }
}
