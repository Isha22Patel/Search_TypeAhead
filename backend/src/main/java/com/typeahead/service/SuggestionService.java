package com.typeahead.service;

import com.typeahead.cache.ConsistentHashRouter;
import com.typeahead.dto.SuggestionDTO;
import com.typeahead.model.SearchQuery;
import com.typeahead.repository.SearchQueryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Core service for typeahead suggestions.
 *
 * Cache flow:
 *   prefix → ConsistentHashRouter.get(prefix)
 *          → Hit?  return cached list
 *          → Miss? query DB → store in cache → return list
 *
 * Ranking:
 *   Results are sorted by blended score from TrendingService.
 *   If no trending data exists, score = count (pure popularity).
 *
 * Why prefix normalization (trim + lowercase)?
 *   "iPhone " and "iphone" should return identical suggestions.
 *   Cache key must be deterministic — "iPhone" and "iphone" should
 *   map to the same cache node and same DB query.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuggestionService {

    private final SearchQueryRepository repository;
    private final ConsistentHashRouter  cacheRouter;
    private final TrendingService       trendingService;

    /**
     * Returns up to 10 suggestions for the given prefix.
     *
     * @param rawPrefix the raw prefix from the request parameter (may be null/blank)
     * @return sorted list of suggestions (empty list on blank/null prefix)
     */
    public List<SuggestionDTO> getSuggestions(String rawPrefix) {
        // ── Guard: null or blank prefix returns no suggestions ─────────────────
        if (rawPrefix == null || rawPrefix.isBlank()) {
            return Collections.emptyList();
        }

        // ── Normalize: trim whitespace + lowercase for consistent cache keys ────
        String prefix = rawPrefix.trim().toLowerCase();

        // ── Step 1: Check consistent hash cache ────────────────────────────────
        var cached = cacheRouter.get(prefix);
        if (cached.isPresent()) {
            log.debug("Cache HIT for prefix='{}' (node={})",
                prefix, cacheRouter.getNode(prefix).getNodeId());
            return cached.get();
        }

        // ── Step 2: Cache miss → query PostgreSQL ──────────────────────────────
        log.debug("Cache MISS for prefix='{}' → querying DB", prefix);

        List<SearchQuery> dbResults = repository.findTop10ByPrefixNative(prefix);

        if (dbResults.isEmpty()) {
            return Collections.emptyList();
        }

        // ── Step 3: Convert to DTOs with blended trending score ────────────────
        List<SuggestionDTO> suggestions = dbResults.stream()
            .map(sq -> {
                double blendedScore = trendingService.getBlendedScore(sq.getQuery(), sq.getCount());
                return SuggestionDTO.builder()
                    .query(sq.getQuery())
                    .count(sq.getCount())
                    .score(blendedScore)
                    .build();
            })
            .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
            .toList();

        // ── Step 4: Store in cache for subsequent requests ─────────────────────
        cacheRouter.put(prefix, suggestions);

        return suggestions;
    }
}
