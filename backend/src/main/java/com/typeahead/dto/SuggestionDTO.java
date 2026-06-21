package com.typeahead.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for a single suggestion returned by /suggest.
 *
 * score = blended ranking score (0.3 * historicalCount + 0.7 * recentCount)
 * When no trending data exists, score equals count.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuggestionDTO {
    private String query;
    private long   count;
    private double score;
}
