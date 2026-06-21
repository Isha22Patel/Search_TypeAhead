package com.typeahead.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Aggregated performance metrics exposed at GET /metrics.
 *
 * Latency percentiles are computed over a rolling window of the last 1000 requests.
 * cacheHitRate = cacheHits / (cacheHits + cacheMisses)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricsDTO {
    private LatencyStats suggest;
    private LatencyStats search;
    private double       cacheHitRate;
    private long         cacheHits;
    private long         cacheMisses;
    private long         totalRequests;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LatencyStats {
        private double p50Ms;
        private double p95Ms;
        private double p99Ms;
        private long   sampleCount;
    }
}
