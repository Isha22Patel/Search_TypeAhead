package com.typeahead.cache;

import com.typeahead.dto.SuggestionDTO;

import java.util.List;
import java.util.Optional;

/**
 * Contract for a single cache node in the distributed cache simulation.
 *
 * In a real production system this would be a Redis node, Memcached shard,
 * or any key-value store with TTL support.
 *
 * Here we implement it as an in-memory Java class to avoid requiring an
 * external Redis installation during the viva demo.
 * The semantics are identical: get/put/invalidate + TTL-based expiry.
 */
public interface CacheNode {

    /**
     * Retrieves suggestions for the given prefix key.
     *
     * @param key cache key (normalized prefix string)
     * @return Optional.empty() on miss or expired entry; suggestions on hit
     */
    Optional<List<SuggestionDTO>> get(String key);

    /**
     * Stores suggestions for the given prefix key with a TTL.
     *
     * @param key        cache key
     * @param value      suggestions to store
     * @param ttlSeconds how long this entry should remain valid
     */
    void put(String key, List<SuggestionDTO> value, long ttlSeconds);

    /**
     * Removes the entry for this key (cache invalidation on search submission).
     *
     * @param key cache key to remove
     */
    void invalidate(String key);

    /**
     * Returns a snapshot of current node statistics (hits, misses, size).
     */
    CacheStats stats();

    /**
     * Returns the node's unique identifier for this node (e.g., "node-1").
     */
    String getNodeId();

    /**
     * Returns the remaining TTL in seconds for the given key.
     *
     * Mirrors Redis's {@code TTL <key>} command semantics:
     * <ul>
     *   <li>Positive value  → seconds remaining before expiry</li>
     *   <li>0              → key is missing or already expired</li>
     * </ul>
     *
     * @param key cache key to inspect
     * @return seconds remaining, or 0 if absent/expired
     */
    long getTtl(String key);
}
