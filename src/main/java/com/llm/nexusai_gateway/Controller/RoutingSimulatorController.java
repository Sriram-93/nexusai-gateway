package com.llm.nexusai_gateway.Controller;

import com.llm.nexusai_gateway.Decision.RoutingSimulatorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/routing")
@CrossOrigin(origins = "*")
public class RoutingSimulatorController {

    private final RoutingSimulatorService simulatorService;

    public RoutingSimulatorController(RoutingSimulatorService simulatorService) {
        this.simulatorService = simulatorService;
    }

    @PostMapping("/simulate")
    public ResponseEntity<RoutingSimulatorService.SimulationResult> simulateRouting(
            @RequestBody RoutingSimulatorService.SimulationRequest request) {
        RoutingSimulatorService.SimulationResult result = simulatorService.simulate(request);
        return ResponseEntity.ok(result);
    }
}
