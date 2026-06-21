package com.typeahead.service;

import com.typeahead.dto.TrendingDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │                  TRENDING SEARCH ENGINE                             │
 * │                                                                     │
 * │  Data structure per query:                                          │
 * │    RecentActivity {                                                  │
 * │      totalSearches: AtomicLong           ← historical count         │
 * │      recentSearches: ConcurrentLinkedDeque<Instant>  ← time window  │
 * │    }                                                                │
 * │                                                                     │
 * │  Score formula (configurable weights in application.yml):           │
 * │    score = historicalWeight * totalSearches                         │
 * │           + recencyWeight   * recentCount                           │
 * │         = 0.3 * totalSearches + 0.7 * recentCount                  │
 * │                                                                     │
 * │  Sliding window: last 60 minutes (configurable)                     │
 * │    - recentCount = deque entries with timestamp > now - 60min       │
 * │    - Old entries pruned by @Scheduled cleanup every 5 minutes       │
 * │                                                                     │
 * │  Why recency-aware ranking?                                         │
 * │    - A query with 10M total searches from 5 years ago should not    │
 * │      dominate over a query that 1000 people searched in last hour.  │
 * │    - recencyWeight = 0.7 means recent spikes boost ranking quickly. │
 * │    - historicalWeight = 0.3 prevents brand-new one-off queries      │
 * │      from immediately dominating trending.                          │
 * └─────────────────────────────────────────────────────────────────────┘
 */
@Slf4j
@Service
public class TrendingService {

    @Value("${typeahead.trending.window-minutes:60}")
    private long windowMinutes;

    @Value("${typeahead.trending.top-limit:10}")
    private int topLimit;

    @Value("${typeahead.trending.history-weight:0.3}")
    private double historyWeight;

    @Value("${typeahead.trending.recency-weight:0.7}")
    private double recencyWeight;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Main data store: query → RecentActivity.
     *
     * ConcurrentHashMap is thread-safe for concurrent search submissions.
     * computeIfAbsent is atomic — no race condition on first-time inserts.
     */
    private final ConcurrentHashMap<String, RecentActivity> activityMap =
        new ConcurrentHashMap<>();

    // ── Inner class for per-query activity ─────────────────────────────────

    /**
     * Holds both historical (total) and recent search data for a query.
     * ConcurrentLinkedDeque allows concurrent addLast() from multiple threads.
     */
    private static class RecentActivity {
        final AtomicLong                  totalSearches  = new AtomicLong(0);
        final ConcurrentLinkedDeque<Instant> recentSearches = new ConcurrentLinkedDeque<>();
    }

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Seeds the top historical queries from the database on application startup.
     * This ensures the UI "Trending Searches" isn't completely empty before
     * users actually start searching for things.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void seedFromDatabase() {
        log.info("Seeding TrendingService with top historical searches from database...");
        try {
            jdbcTemplate.query("SELECT query, count FROM search_queries ORDER BY count DESC LIMIT 100",
                rs -> {
                    String query = rs.getString("query");
                    long count = rs.getLong("count");
                    
                    RecentActivity activity = new RecentActivity();
                    activity.totalSearches.set(count);
                    // We don't add to recentSearches deque since these are historical
                    activityMap.put(query, activity);
                });
            log.info("Successfully seeded TrendingService with top {} historical searches.", activityMap.size());
        } catch (Exception e) {
            log.warn("Failed to seed TrendingService from DB. Database might be empty or unavailable.", e);
        }
    }

    /**
     * Records a search event for the given query.
     * Called by SearchService on every POST /search.
     *
     * Operations:
     * 1. Increment totalSearches (historical counter).
     * 2. Add current timestamp to recentSearches deque (for window scoring).
     */
    public void recordSearch(String query) {
        RecentActivity activity = activityMap.computeIfAbsent(query, k -> new RecentActivity());
        activity.totalSearches.incrementAndGet();
        activity.recentSearches.addLast(Instant.now());
    }

    /**
     * Returns top N trending queries sorted by blended score.
     *
     * Steps:
     * 1. Compute window cutoff = now - windowMinutes.
     * 2. For each query, count how many deque entries are after cutoff (recentCount).
     * 3. Compute blended score.
     * 4. Sort descending, take top N.
     */
    public List<TrendingDTO> getTopTrending(int limit) {
        Instant cutoff = Instant.now().minusSeconds(windowMinutes * 60);

        return activityMap.entrySet().stream()
            .map(entry -> {
                String         query    = entry.getKey();
                RecentActivity activity = entry.getValue();
                long historical  = activity.totalSearches.get();
                long recentCount = activity.recentSearches.stream()
                    .filter(t -> t.isAfter(cutoff))
                    .count();
                double score = historyWeight * historical + recencyWeight * recentCount;
                return TrendingDTO.builder()
                    .query(query)
                    .score(score)
                    .recentCount(recentCount)
                    .historicalCount(historical)
                    .build();
            })
            .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
            .limit(limit)
            .collect(Collectors.toList());
    }

    /**
     * Computes a blended score for a specific query.
     * Used by SuggestionService to re-rank DB results with trending data.
     *
     * If the query has no trending activity yet, falls back to historicalCount.
     */
    public double getBlendedScore(String query, long historicalCount) {
        RecentActivity activity = activityMap.get(query);
        if (activity == null) {
            // No trending data → score is purely historical
            return historicalCount;
        }
        Instant cutoff      = Instant.now().minusSeconds(windowMinutes * 60);
        long    recentCount = activity.recentSearches.stream()
            .filter(t -> t.isAfter(cutoff))
            .count();
        return historyWeight * historicalCount + recencyWeight * recentCount;
    }

    /**
     * Scheduled cleanup: removes timestamps older than the sliding window.
     * Runs every 5 minutes (configurable via trending.cleanup-interval-ms).
     *
     * Why periodic cleanup?
     * - ConcurrentLinkedDeque grows unboundedly if never pruned.
     * - We prune from the front (oldest entries) until we find one within the window.
     * - This is O(pruned entries) per query — amortized O(1) per search event.
     */
    @Scheduled(fixedDelayString = "${typeahead.trending.cleanup-interval-ms:300000}")
    public void cleanOldEntries() {
        Instant cutoff   = Instant.now().minusSeconds(windowMinutes * 60);
        int     pruned   = 0;

        for (RecentActivity activity : activityMap.values()) {
            // Poll from front while oldest entry is before cutoff
            while (!activity.recentSearches.isEmpty()
                    && activity.recentSearches.peekFirst().isBefore(cutoff)) {
                activity.recentSearches.pollFirst();
                pruned++;
            }
        }

        if (pruned > 0) {
            log.info("TrendingService cleanup: pruned {} old timestamps from sliding windows", pruned);
        }
    }
}
