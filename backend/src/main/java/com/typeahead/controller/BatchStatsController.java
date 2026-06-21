package com.typeahead.controller;

import com.typeahead.dto.BatchStatsDTO;
import com.typeahead.service.BatchWriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * GET /batch/stats
 *
 * Returns live batch write buffer statistics.
 * Demonstrates write reduction achieved by batching.
 *
 * Example at steady state (100 searches/sec, flush every 5s):
 *   totalSearchesReceived = 5000
 *   totalDbWrites         = 50   (50 unique queries)
 *   writeReductionPercent = 99.0%
 */
@RestController
@RequestMapping("/batch/stats")
@RequiredArgsConstructor
public class BatchStatsController {

    private final BatchWriteService batchWriteService;

    @GetMapping
    public ResponseEntity<BatchStatsDTO> stats() {
        return ResponseEntity.ok(batchWriteService.getStats());
    }
}
