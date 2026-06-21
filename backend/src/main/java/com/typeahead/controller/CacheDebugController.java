package com.typeahead.controller;

import com.typeahead.cache.CacheNode;
import com.typeahead.cache.ConsistentHashRouter;
import com.typeahead.dto.CacheDebugDTO;
import com.typeahead.dto.SuggestionDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * GET /cache/debug?prefix=<p>
 *
 * Diagnostic endpoint for viva demonstration.
 * Shows which hash ring node owns the prefix, whether it's cached,
 * and how much TTL remains.
 *
 * Example response:
 * {
 *   "prefix":             "iphone",
 *   "nodeId":             "node-2",
 *   "ringHash":           192837,
 *   "isHit":              true,
 *   "ttlRemainingSeconds": 43,
 *   "cachedSuggestions":  [...]
 * }
 */
@RestController
@RequestMapping("/cache/debug")
@RequiredArgsConstructor
public class CacheDebugController {

    private final ConsistentHashRouter cacheRouter;

    @GetMapping
    public ResponseEntity<CacheDebugDTO> debug(
            @RequestParam(name = "prefix", required = false, defaultValue = "") String rawPrefix) {

        String prefix = rawPrefix.trim().toLowerCase();

        // Find the responsible node
        CacheNode node    = cacheRouter.getNode(prefix.isBlank() ? "_empty_" : prefix);
        int       hash    = cacheRouter.getHashForKey(prefix.isBlank() ? "_empty_" : prefix);

        // Probe cache without updating hit/miss counters (read-only debug)
        Optional<List<SuggestionDTO>> cached = node.get(prefix);

        long ttlRemaining = 0;
        if (cached.isPresent()) {
            // Read the real remaining TTL from the CacheEntry's expiresAt timestamp.
            // Equivalent to Redis's TTL command — no approximation needed.
            ttlRemaining = node.getTtl(prefix);
        }

        CacheDebugDTO debugInfo = CacheDebugDTO.builder()
            .prefix(prefix)
            .nodeId(node.getNodeId())
            .ringHash(hash)
            .isHit(cached.isPresent())
            .ttlRemainingSeconds(ttlRemaining)
            .cachedSuggestions(cached.orElse(List.of()))
            .build();

        return ResponseEntity.ok(debugInfo);
    }
}
