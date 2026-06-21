package com.typeahead.cache;

import com.typeahead.dto.SuggestionDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ┌──────────────────────────────────────────────────────────────────┐
 * │              CONSISTENT HASH RING IMPLEMENTATION                 │
 * │                                                                  │
 * │  Hash Ring (TreeMap<Integer, CacheNode>)                         │
 * │                                                                  │
 * │      0           node-1-r0 (hash=1823)                          │
 * │      │           node-2-r0 (hash=4921)                          │
 * │      │           node-1-r1 (hash=7341)                          │
 * │      │           node-3-r0 (hash=9182)  ← "iphone" hashes here  │
 * │      │                 ...                                       │
 * │   MAX_INT        node-2-r99 (hash=∞)                            │
 * │                                                                  │
 * │  getNode("iphone"):                                              │
 * │    hash("iphone") = 8900                                         │
 * │    TreeMap.ceilingEntry(8900) = node-3-r0 → returns node-3      │
 * │                                                                  │
 * │  Why virtual nodes?                                              │
 * │    Without virtual nodes, when a node is added/removed,          │
 * │    ALL keys between two adjacent nodes must be remapped.         │
 * │    With R=100 virtual nodes per physical node, only 1/N of       │
 * │    the key space is affected on average. This makes cache         │
 * │    invalidation minimal and load distribution uniform.           │
 * └──────────────────────────────────────────────────────────────────┘
 *
 * Hash function: MD5 of the virtual node key, take first 4 bytes as int.
 * MD5 is fast and provides good distribution (not used for security here).
 *
 * TreeMap is used because:
 *   - Sorted by hash value → O(log N) ceilingEntry for ring lookup.
 *   - N = totalNodes * virtualReplicas (e.g., 3 * 100 = 300 entries).
 *   - O(log 300) ≈ 8 comparisons per lookup — effectively O(1).
 */
@Slf4j
@Component
public class ConsistentHashRouter {

    @Value("${typeahead.cache.node-count:3}")
    private int nodeCount;

    @Value("${typeahead.cache.virtual-replicas:100}")
    private int virtualReplicas;

    @Value("${typeahead.cache.ttl-seconds:60}")
    private long ttlSeconds;

    @Value("${typeahead.cache.max-size:5000}")
    private int maxSizePerNode;

    // The ring: sorted map of hash → CacheNode
    private final TreeMap<Integer, CacheNode> ring = new TreeMap<>();

    // Physical node registry: nodeId → CacheNode
    private final Map<String, CacheNode> nodes = new LinkedHashMap<>();

    // Global counters across all nodes
    private final AtomicLong totalHits   = new AtomicLong(0);
    private final AtomicLong totalMisses = new AtomicLong(0);

    /**
     * Initializes the hash ring after Spring injects all @Value properties.
     * Creates N physical CacheNode instances and places R virtual replicas
     * of each on the ring.
     */
    @PostConstruct
    public void initialize() {
        log.info("Initializing ConsistentHashRing: {} nodes × {} virtual replicas = {} ring entries",
            nodeCount, virtualReplicas, nodeCount * virtualReplicas);

        for (int i = 1; i <= nodeCount; i++) {
            String nodeId = "node-" + i;
            addNode(nodeId);
        }

        log.info("Hash ring ready. Node assignments:");
        nodes.keySet().forEach(id -> log.info("  └─ {}", id));
    }

    /**
     * Adds a new physical node and distributes its virtual replicas on the ring.
     * Called on startup and can be called dynamically to scale out.
     */
    public void addNode(String nodeId) {
        CacheNode node = new LocalCacheNode(nodeId, maxSizePerNode);
        nodes.put(nodeId, node);

        for (int replica = 0; replica < virtualReplicas; replica++) {
            String virtualKey = nodeId + "-replica-" + replica;
            int    hash       = hashKey(virtualKey);
            ring.put(hash, node);
        }
        log.info("Added node '{}' with {} virtual replicas", nodeId, virtualReplicas);
    }

    /**
     * Removes a physical node and all its virtual replicas from the ring.
     * Keys previously served by this node wrap around to the next node.
     * Only 1/N of all keys are affected — this is the key benefit of
     * consistent hashing over simple modulo hashing.
     */
    public void removeNode(String nodeId) {
        nodes.remove(nodeId);
        for (int replica = 0; replica < virtualReplicas; replica++) {
            String virtualKey = nodeId + "-replica-" + replica;
            int    hash       = hashKey(virtualKey);
            ring.remove(hash);
        }
        log.warn("Removed node '{}' from hash ring", nodeId);
    }

    /**
     * Core routing logic: maps a prefix string to a CacheNode.
     *
     * Algorithm:
     *   1. Compute hash of the prefix.
     *   2. Find the smallest hash in the ring that is ≥ prefix hash (ceiling).
     *   3. If no such entry exists (prefix hash > all ring entries), wrap around
     *      to the first (smallest) entry — this is the "ring" behavior.
     *
     * @param prefix the cache key (normalized search prefix)
     * @return the CacheNode responsible for this prefix
     */
    public CacheNode getNode(String prefix) {
        if (ring.isEmpty()) {
            throw new IllegalStateException("Hash ring is empty – no cache nodes available");
        }

        int keyHash = hashKey(prefix);

        // ceilingEntry = first entry with hash >= keyHash
        Map.Entry<Integer, CacheNode> entry = ring.ceilingEntry(keyHash);

        // Wrap around the ring if we're past the last node
        if (entry == null) {
            entry = ring.firstEntry();
        }

        log.debug("Routing: prefix='{}' → hash={} → {}", prefix, keyHash, entry.getValue().getNodeId());
        return entry.getValue();
    }

    /**
     * Returns the hash value that would be used to route a given prefix.
     * Exposed for the /cache/debug endpoint.
     */
    public int getHashForKey(String key) {
        return hashKey(key);
    }

    // ── Cache operations (delegate to routed node) ─────────────────────────

    public Optional<List<SuggestionDTO>> get(String prefix) {
        CacheNode node  = getNode(prefix);
        var       result = node.get(prefix);
        if (result.isPresent()) totalHits.incrementAndGet();
        else                    totalMisses.incrementAndGet();
        return result;
    }

    public void put(String prefix, List<SuggestionDTO> suggestions) {
        getNode(prefix).put(prefix, suggestions, ttlSeconds);
    }

    public void invalidate(String prefix) {
        getNode(prefix).invalidate(prefix);
    }

    // ── Statistics ─────────────────────────────────────────────────────────

    public long getTotalHits()   { return totalHits.get();   }
    public long getTotalMisses() { return totalMisses.get(); }

    public Map<String, CacheNode> getNodes() {
        return Collections.unmodifiableMap(nodes);
    }

    // ── MD5 Hash Function ──────────────────────────────────────────────────

    /**
     * Hashes a string key to an integer using MD5.
     *
     * Why MD5 here (not security-sensitive)?
     *   - Fast to compute.
     *   - Excellent avalanche effect: small input change → very different hash.
     *   - Good uniform distribution across the ring → balanced load.
     *
     * Alternative: Murmur3 (even faster, same distribution quality).
     * We use MD5 because it's in the JDK standard library — no extra dependency.
     */
    private int hashKey(String key) {
        try {
            MessageDigest md    = MessageDigest.getInstance("MD5");
            byte[]        bytes = md.digest(key.getBytes(StandardCharsets.UTF_8));
            // Take first 4 bytes as a signed int (gives full int range)
            return ((bytes[0] & 0xFF) << 24)
                 | ((bytes[1] & 0xFF) << 16)
                 | ((bytes[2] & 0xFF) << 8)
                 |  (bytes[3] & 0xFF);
        } catch (Exception e) {
            // MD5 is always available in the JDK — this never throws
            throw new RuntimeException("MD5 not available", e);
        }
    }
}
