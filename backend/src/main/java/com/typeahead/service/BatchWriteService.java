package com.typeahead.service;

import com.typeahead.dto.BatchStatsDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ┌────────────────────────────────────────────────────────────────────┐
 * │                   BATCH WRITE SERVICE                              │
 * │                                                                    │
 * │  Problem: if we write to DB on every search, at 10,000 searches/s  │
 * │  we'd hammer Postgres with 10,000 UPDATEs/s — catastrophic.       │
 * │                                                                    │
 * │  Solution: buffer writes in memory, flush in batches periodically. │
 * │                                                                    │
 * │  Buffer: ConcurrentHashMap<String, AtomicLong>                     │
 * │    key   = normalized query string                                 │
 * │    value = count of times searched since last flush                │
 * │                                                                    │
 * │  Example buffer before flush:                                      │
 * │    "iphone 15"       → 47                                         │
 * │    "python tutorial" → 23                                         │
 * │    "react js"        → 8                                          │
 * │                                                                    │
 * │  On flush: 3 DB writes (one UPSERT per unique query)              │
 * │  vs.  78 individual writes if we wrote on every search.           │
 * │  Write reduction = (78 - 3) / 78 = 96.2%                         │
 * │                                                                    │
 * │  Flush conditions:                                                 │
 * │    a) Every 5 seconds (@Scheduled fixedDelay)                     │
 * │    b) Buffer size exceeds maxBufferSize (force flush)             │
 * └────────────────────────────────────────────────────────────────────┘
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchWriteService {

    private final JdbcTemplate jdbcTemplate;

    @Value("${typeahead.batch.max-buffer-size:1000}")
    private int maxBufferSize;

    @Value("${typeahead.batch.jdbc-batch-size:500}")
    private int jdbcBatchSize;

    /**
     * The write buffer.
     * ConcurrentHashMap + AtomicLong: lock-free increments even under
     * concurrent search submissions from multiple threads.
     *
     * computeIfAbsent is atomic → no race condition when a new query
     * is encountered for the first time.
     */
    private volatile ConcurrentHashMap<String, AtomicLong> buffer =
        new ConcurrentHashMap<>();

    // ── Global metrics ──────────────────────────────────────────────────────
    private final AtomicLong totalSearchesReceived = new AtomicLong(0);
    private final AtomicLong totalDbWrites         = new AtomicLong(0);
    private final AtomicLong totalFlushCount       = new AtomicLong(0);

    private static final String UPSERT_SQL =
        "INSERT INTO search_queries (query, count, last_searched_at, recent_score) " +
        "VALUES (?, ?, NOW(), 0.0) " +
        "ON CONFLICT (query) DO UPDATE " +
        "SET count = search_queries.count + EXCLUDED.count, " +
        "    last_searched_at = NOW()";

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Records a search into the in-memory buffer.
     * Does NOT touch the database — returns immediately.
     *
     * Thread-safety: computeIfAbsent and AtomicLong.incrementAndGet are both
     * lock-free on the critical path. This method can handle 100k+ calls/sec.
     */
    public void recordSearch(String query) {
        totalSearchesReceived.incrementAndGet();
        buffer.computeIfAbsent(query, k -> new AtomicLong(0))
              .incrementAndGet();

        // Force flush if buffer is too large (circuit breaker)
        if (buffer.size() >= maxBufferSize) {
            log.warn("Buffer size {} exceeded max {} — forcing immediate flush", buffer.size(), maxBufferSize);
            flushBatch();
        }
    }

    /**
     * Scheduled batch flush.
     *
     * Atomic swap pattern:
     *   1. Atomically swap current buffer with a new empty map.
     *   2. Work on the drained snapshot — new searches go to the fresh buffer.
     *   3. This means flushBatch() never blocks recordSearch().
     *
     * Without the swap pattern, we'd need to lock the entire buffer during
     * the (potentially slow) DB write — causing HOL blocking on search submissions.
     */
    @Scheduled(fixedDelayString = "${typeahead.batch.flush-interval-ms:5000}")
    public synchronized void flushBatch() {
        if (buffer.isEmpty()) {
            return;
        }

        // ── Atomic swap: drain the live buffer ────────────────────────────────
        ConcurrentHashMap<String, AtomicLong> snapshot = buffer;
        buffer = new ConcurrentHashMap<>();

        int uniqueQueries = snapshot.size();
        if (uniqueQueries == 0) return;

        log.info("Flushing batch: {} unique queries to DB…", uniqueQueries);
        long flushStart = System.currentTimeMillis();

        // ── Build JDBC batch argument lists ───────────────────────────────────
        List<Object[]> batch = new ArrayList<>(Math.min(uniqueQueries, jdbcBatchSize));
        int flushed = 0;

        for (Map.Entry<String, AtomicLong> entry : snapshot.entrySet()) {
            batch.add(new Object[]{entry.getKey(), entry.getValue().get()});

            if (batch.size() >= jdbcBatchSize) {
                jdbcTemplate.batchUpdate(UPSERT_SQL, batch);
                flushed += batch.size();
                batch.clear();
            }
        }

        // Final partial batch
        if (!batch.isEmpty()) {
            jdbcTemplate.batchUpdate(UPSERT_SQL, batch);
            flushed += batch.size();
        }

        totalDbWrites.addAndGet(flushed);
        totalFlushCount.incrementAndGet();

        long elapsedMs = System.currentTimeMillis() - flushStart;
        long searched  = totalSearchesReceived.get();
        long dbWrites  = totalDbWrites.get();
        double reduction = searched > 0
            ? (1.0 - (double) dbWrites / searched) * 100.0
            : 0.0;

        log.info("Batch flush complete: {} queries → {} DB writes in {}ms | " +
                 "Total searches={}, DB writes={}, reduction={}%",
            uniqueQueries, flushed, elapsedMs,
            searched, dbWrites, String.format("%.1f", reduction));
    }

    // ── Stats API ───────────────────────────────────────────────────────────

    public BatchStatsDTO getStats() {
        long searched = totalSearchesReceived.get();
        long dbWrites = totalDbWrites.get();
        double reduction = searched > 0
            ? (1.0 - (double) dbWrites / searched) * 100.0
            : 0.0;

        return BatchStatsDTO.builder()
            .bufferSize(buffer.size())
            .totalFlushed(totalFlushCount.get())
            .totalSearchesReceived(searched)
            .totalDbWrites(dbWrites)
            .writeReductionPercent(reduction)
            .build();
    }
}
