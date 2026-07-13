package com.llm.nexusai_gateway.Controller;

import com.llm.nexusai_gateway.Experiment.ExperimentResult;
import com.llm.nexusai_gateway.Experiment.ExperimentRunner;
import com.llm.nexusai_gateway.Model.ChatRequest;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Controller to trigger experimental evaluation.
 *
 * Exposes an endpoint to run a benchmark dataset through all 4
 * routing strategies and return comparative metrics.
 */
@RestController
@RequestMapping("/api/experiment")
public class ExperimentController {

    private final ExperimentRunner experimentRunner;

    public ExperimentController(ExperimentRunner experimentRunner) {
        this.experimentRunner = experimentRunner;
    }

    @PostMapping("/run")
    public Mono<List<ExperimentResult>> runExperiment(@RequestBody List<ChatRequest> dataset) {
        return experimentRunner.runFullExperiment(dataset);
    }
}
