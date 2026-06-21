package com.typeahead.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for a trending search result from /trending.
 *
 * score = historicalWeight * historicalCount + recencyWeight * recentCount
 *       = 0.3 * historicalCount + 0.7 * recentCount  (configurable weights)
 *
 * recentCount = number of searches within the sliding time window (default: 60 min).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrendingDTO {
    private String query;
    private double score;
    private long   recentCount;
    private long   historicalCount;
}
