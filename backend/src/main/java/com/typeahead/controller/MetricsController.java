package com.typeahead.controller;

import com.typeahead.aop.LatencyAspect;
import com.typeahead.cache.ConsistentHashRouter;
import com.typeahead.dto.MetricsDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * GET /metrics
 *
 * Returns aggregated system-wide performance metrics.
 *
 * Includes:
 * - p50/p95/p99 latency per endpoint (measured by LatencyAspect)
 * - Cache hit/miss rate across all nodes in the consistent hash ring
 * - Total request count
 */
@RestController
@RequestMapping("/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final LatencyAspect        latencyAspect;
    private final ConsistentHashRouter cacheRouter;

    @GetMapping
    public ResponseEntity<MetricsDTO> metrics() {
        long hits   = cacheRouter.getTotalHits();
        long misses = cacheRouter.getTotalMisses();
        long total  = hits + misses;
        double hitRate = total > 0 ? (double) hits / total * 100.0 : 0.0;

        MetricsDTO dto = MetricsDTO.builder()
            .suggest(latencyAspect.getStats("SuggestController"))
            .search(latencyAspect.getStats("SearchController"))
            .cacheHitRate(hitRate)
            .cacheHits(hits)
            .cacheMisses(misses)
            .totalRequests(latencyAspect.getTotalRequests())
            .build();

        return ResponseEntity.ok(dto);
    }
}
