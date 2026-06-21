package com.typeahead.cache;

import com.typeahead.dto.SuggestionDTO;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory implementation of CacheNode.
 *
 * Data structure: ConcurrentHashMap<String, CacheEntry>
 *   - Thread-safe for concurrent reads/writes without external synchronization.
 *   - O(1) get/put/remove.
 *
 * TTL expiry: Lazy expiration — entries are checked at read time.
 *   - This mirrors Redis's default lazy eviction strategy.
 *   - No background eviction thread needed for correctness (only for memory reclaim).
 *
 * Eviction on overflow: When size > maxSize, evict the oldest entry
 *   (approximated by insertion order using an auxiliary deque).
 *   This is an LRU approximation — good enough for demo purposes.
 *   Production systems use proper LRU (LinkedHashMap or Caffeine).
 *
 * Why ConcurrentHashMap over synchronized HashMap?
 *   - ConcurrentHashMap uses segment/bucket locking (Java 8+: CAS + bin-level locks).
 *   - Allows concurrent reads to proceed without blocking each other.
 *   - Perfect for a high-read, low-write cache workload.
 */
@Slf4j
public class LocalCacheNode implements CacheNode {

    private final String                              nodeId;
    private final int                                 maxSize;
    private final ConcurrentHashMap<String, CacheEntry> store;

    // Insertion-order deque for approximating LRU eviction
    private final Deque<String>  insertionOrder;
    private final AtomicLong     hitCounter  = new AtomicLong(0);
    private final AtomicLong     missCounter = new AtomicLong(0);

    public LocalCacheNode(String nodeId, int maxSize) {
        this.nodeId         = nodeId;
        this.maxSize        = maxSize;
        this.store          = new ConcurrentHashMap<>(maxSize);
        this.insertionOrder = new ArrayDeque<>(maxSize);
    }

    /**
     * Cache GET operation.
     *
     * Returns empty if:
     * a) Key doesn't exist (cache miss)
     * b) Entry exists but TTL has expired (lazy eviction + miss)
     */
    @Override
    public Optional<List<SuggestionDTO>> get(String key) {
        CacheEntry entry = store.get(key);

        if (entry == null) {
            missCounter.incrementAndGet();
            return Optional.empty();
        }

        if (entry.isExpired()) {
            // Lazy eviction: remove expired entry on access
            store.remove(key);
            synchronized (insertionOrder) {
                insertionOrder.remove(key);
            }
            missCounter.incrementAndGet();
            log.debug("[{}] Cache EXPIRED: key={}", nodeId, key);
            return Optional.empty();
        }

        hitCounter.incrementAndGet();
        log.debug("[{}] Cache HIT: key={}, ttl={}s remaining",
            nodeId, key, entry.ttlRemainingSeconds());
        return Optional.of(entry.getValue());
    }

    /**
     * Cache PUT operation.
     *
     * Creates a CacheEntry with expiresAt = now + ttlSeconds.
     * Evicts oldest entry if we've exceeded maxSize.
     */
    @Override
    public void put(String key, List<SuggestionDTO> value, long ttlSeconds) {
        // Evict if at capacity
        if (store.size() >= maxSize) {
            evictOldest();
        }

        Instant expiresAt = Instant.now().plusSeconds(ttlSeconds);
        store.put(key, new CacheEntry(value, expiresAt));

        synchronized (insertionOrder) {
            insertionOrder.remove(key); // Remove old position if re-inserting
            insertionOrder.addLast(key);
        }
        log.debug("[{}] Cache PUT: key={}, ttl={}s, size={}", nodeId, key, ttlSeconds, store.size());
    }

    /**
     * Explicit cache invalidation — called when a query is searched
     * so the next /suggest call gets fresh results from DB.
     */
    @Override
    public void invalidate(String key) {
        store.remove(key);
        synchronized (insertionOrder) {
            insertionOrder.remove(key);
        }
        log.debug("[{}] Cache INVALIDATED: key={}", nodeId, key);
    }

    /** Evicts the oldest inserted entry (LRU approximation). */
    private void evictOldest() {
        String oldest;
        synchronized (insertionOrder) {
            oldest = insertionOrder.pollFirst();
        }
        if (oldest != null) {
            store.remove(oldest);
            log.debug("[{}] Cache EVICTED (overflow): key={}", nodeId, oldest);
        }
    }

    @Override
    public CacheStats stats() {
        return CacheStats.builder()
            .nodeId(nodeId)
            .hits(hitCounter.get())
            .misses(missCounter.get())
            .currentSize(store.size())
            .maxSize(maxSize)
            .build();
    }

    @Override
    public String getNodeId() {
        return nodeId;
    }

    /**
     * Returns the real remaining TTL for the key by directly inspecting
     * the internal CacheEntry's expiresAt timestamp.
     *
     * Equivalent to Redis's TTL command: reads the stored expiry without
     * touching hit/miss counters (read-only introspection).
     *
     * @param key cache key to inspect
     * @return seconds remaining before expiry, or 0 if absent/expired
     */
    @Override
    public long getTtl(String key) {
        CacheEntry entry = store.get(key);
        if (entry == null || entry.isExpired()) {
            return 0L;
        }
        return Math.max(0L, entry.ttlRemainingSeconds());
    }
}
