package com.visualsearch.indexing;

import com.visualsearch.indexing.resilience.AiCircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class WorkerHealthController {

    private final AiCircuitBreaker aiCircuitBreaker;

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "status", "UP",
                "service", "indexing-service",
                "aiCircuit", aiCircuitBreaker.state().name());
    }
}
