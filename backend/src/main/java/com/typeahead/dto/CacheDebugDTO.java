package com.typeahead.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response from GET /cache/debug?prefix=<p>
 * Demonstrates which consistent-hash ring node owns this prefix.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CacheDebugDTO {
    private String             prefix;
    private String             nodeId;
    private int                ringHash;
    private boolean            isHit;
    private long               ttlRemainingSeconds;
    private List<SuggestionDTO> cachedSuggestions;
}
