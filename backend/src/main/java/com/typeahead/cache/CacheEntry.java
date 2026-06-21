package com.typeahead.cache;

import com.typeahead.dto.SuggestionDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/**
 * Immutable wrapper that adds TTL expiry semantics to a cache value.
 *
 * Why store expiresAt instead of a duration?
 * - Storing an absolute Instant means we can check expiry in O(1) at read time
 *   without needing any background thread.
 * - This is exactly how Redis implements EXPIREAT.
 */
@Getter
@AllArgsConstructor
public class CacheEntry {
    private final List<SuggestionDTO> value;
    private final Instant             expiresAt;

    /** Returns true if this entry has passed its TTL. */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /** Seconds remaining before expiry (negative if already expired). */
    public long ttlRemainingSeconds() {
        return expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
    }
}
