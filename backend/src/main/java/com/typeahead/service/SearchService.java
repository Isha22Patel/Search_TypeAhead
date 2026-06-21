package com.typeahead.service;

import com.typeahead.cache.ConsistentHashRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Handles POST /search requests.
 *
 * Design principle: "Return immediately, write eventually."
 *   1. Validate and normalize the query.
 *   2. Invalidate the cache entry for this prefix so next /suggest gets fresh results.
 *   3. Hand off to TrendingService (in-memory, O(1)) — no DB call.
 *   4. Hand off to BatchWriteService (buffer increment, O(1)) — no DB call.
 *   5. Return instantly to the caller.
 *
 * This achieves sub-millisecond POST /search latency regardless of DB load.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final BatchWriteService    batchWriteService;
    private final TrendingService      trendingService;
    private final ConsistentHashRouter cacheRouter;

    /**
     * Records a search query.
     *
     * @param rawQuery the raw query string from the request body
     * @return normalized query string (for confirmation in response)
     * @throws IllegalArgumentException if the query is blank or null
     */
    public String recordSearch(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            throw new IllegalArgumentException("Query cannot be blank");
        }

        String query = rawQuery.trim().toLowerCase();

        // ── Cache invalidation ────────────────────────────────────────────────
        // Why invalidate the cache on search submission?
        // The counts in the cache are now stale. The next /suggest call should
        // get fresh data from DB (after the batch flush updates the count).
        // We invalidate ALL prefixes of the query — e.g., "iph", "ipho", "iphon"
        // — because any of them could be cached with stale suggestion lists.
        invalidatePrefixes(query);

        // ── Record in trending engine (in-memory, O(1)) ───────────────────────
        trendingService.recordSearch(query);

        // ── Buffer for batch DB write (in-memory, O(1)) ───────────────────────
        batchWriteService.recordSearch(query);

        log.debug("Search recorded: query='{}' → queued for batch flush + trending", query);
        return query;
    }

    /**
     * Invalidates all prefix cache entries for a query.
     *
     * Example: query = "iphone 15"
     *   Invalidates: "i", "ip", "iph", "ipho", "iphon", "iphone", "iphone ",
     *                "iphone 1", "iphone 15"
     *
     * Why all prefixes?
     *   Any prefix from length 1 to query.length() might be cached.
     *   If we only invalidate the exact query, searching "iphone 15" wouldn't
     *   refresh the suggestions shown when the user has typed "ipho".
     *
     * Trade-off: O(L) invalidations where L = query length.
     *   For typical queries (L < 50) this is negligible.
     *   In production, we'd use a prefix-aware cache that invalidates lazily
     *   by reducing TTL rather than hard invalidation.
     */
    private void invalidatePrefixes(String query) {
        for (int i = 1; i <= query.length(); i++) {
            cacheRouter.invalidate(query.substring(0, i));
        }
    }
}
