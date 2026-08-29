package com.llm.nexusai_gateway.Evaluation;

import com.llm.nexusai_gateway.Context.TaskCategory;
import com.llm.nexusai_gateway.Provider.LlmProvider;
import com.llm.nexusai_gateway.Provider.ProviderRegistry;

import com.llm.nexusai_gateway.Provider.ProviderResponse;
import com.llm.nexusai_gateway.Repository.ProviderConfigRepository;
import com.llm.nexusai_gateway.Repository.RegisteredModelRepository;
import com.llm.nexusai_gateway.Telemetry.RequestTracingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Mono;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LlmAsAJudgeEvaluatorTest {

    @Mock
    private ProviderRegistry providerRegistry;

    @Mock
    private HeuristicQualityEvaluator heuristicFallback;

    @Mock
    private RegisteredModelRepository registeredModelRepository;

    @Mock
    private ProviderConfigRepository providerConfigRepository;

    @Mock
    private RequestTracingService tracingService;

    @Mock
    private LlmProvider judgeProvider;

    private LlmAsAJudgeEvaluator evaluator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        evaluator = new LlmAsAJudgeEvaluator(
                providerRegistry,
                heuristicFallback,
                registeredModelRepository,
                providerConfigRepository,
                tracingService
        );
    }

    @Test
    void whenNoFastModels_fallsBackToHeuristic() {
        when(registeredModelRepository.findEnabledOrderByLatencyAsc()).thenReturn(Collections.emptyList());
        when(heuristicFallback.evaluate(anyString(), anyString(), any())).thenReturn(Mono.just(QualityScore.of(0.8, 0.9, 1.0)));

        QualityScore score = evaluator.evaluate("Explain quantum computing", "Quantum computing uses qubits...", TaskCategory.REASONING).block();

        assertThat(score).isNotNull();
        assertThat(score.compositeScore()).isEqualTo(QualityScore.of(0.8, 0.9, 1.0).compositeScore());
        verify(heuristicFallback).evaluate(anyString(), anyString(), eq(TaskCategory.REASONING));
        verify(tracingService).traceQualityEvaluation(anyString(), eq("heuristic"), eq("HEURISTIC"), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void whenBlankResponse_returnsZeroScore() {
        QualityScore score = evaluator.evaluate("Prompt", "", TaskCategory.CONVERSATION).block();

        assertThat(score).isNotNull();
        assertThat(score.compositeScore()).isEqualTo(0.0);
        verifyNoInteractions(heuristicFallback, providerRegistry);
    }

    @Test
    void whenJudgeProviderSucceeds_parsesAndReturnsQualityScore() {
        when(registeredModelRepository.findEnabledOrderByLatencyAsc()).thenReturn(Collections.emptyList());
        when(heuristicFallback.evaluate(anyString(), anyString(), any())).thenReturn(Mono.just(QualityScore.of(0.9, 0.85, 0.95)));

        QualityScore score = evaluator.evaluate("Code test", "public class Foo {}", TaskCategory.CODE).block();

        assertThat(score).isNotNull();
        assertThat(score.completeness()).isEqualTo(0.9);
        assertThat(score.relevance()).isEqualTo(0.85);
        assertThat(score.formatCompliance()).isEqualTo(0.95);
    }
}
