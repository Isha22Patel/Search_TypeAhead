package com.typeahead.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Stats emitted by BatchWriteService at GET /batch/stats.
 *
 * writeReductionPercent = (1 - totalDbWrites / totalSearches) * 100
 * Shows how much DB I/O was saved by batching.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchStatsDTO {
    private int    bufferSize;
    private long   totalFlushed;
    private long   totalSearchesReceived;
    private long   totalDbWrites;
    private double writeReductionPercent;
}
