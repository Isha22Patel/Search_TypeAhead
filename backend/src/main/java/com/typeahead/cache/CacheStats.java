package com.typeahead.cache;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Live statistics for a single cache node.
 * Exposed via CacheDebugController for viva demonstration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CacheStats {
    private String nodeId;
    private long   hits;
    private long   misses;
    private int    currentSize;
    private int    maxSize;

    public double hitRate() {
        long total = hits + misses;
        return total == 0 ? 0.0 : (double) hits / total * 100.0;
    }
}
