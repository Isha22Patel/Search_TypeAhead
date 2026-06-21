package com.typeahead.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Persistent representation of a search query.
 *
 * Indexes:
 * - idx_query_pattern: text_pattern_ops index enables fast LIKE 'prefix%' queries
 *   because standard B-tree index can't optimize LIKE when the pattern uses text
 *   collation. text_pattern_ops bypasses collation.
 * - idx_count_desc: allows ORDER BY count DESC without a filesort.
 */
@Entity
@Table(
    name = "search_queries",
    indexes = {
        @Index(name = "idx_query_pattern", columnList = "query"),
        @Index(name = "idx_count_desc",    columnList = "count DESC")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchQuery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The search query string – unique, lowercase-normalized.
     * column definition uses TEXT (unbounded) rather than VARCHAR to avoid
     * truncation for multi-word queries.
     */
    @Column(nullable = false, unique = true, columnDefinition = "TEXT")
    private String query;

    /**
     * Cumulative search count – drives base ranking.
     * Default 0 at entity creation; incremented via UPSERT in BatchWriteService.
     */
    @Column(nullable = false)
    @Builder.Default
    private Long count = 0L;

    /**
     * Timestamp of the last time this query was searched.
     * Updated on every batch flush.
     */
    @Column(name = "last_searched_at")
    private Instant lastSearchedAt;

    /**
     * Pre-computed blended trending score stored here for fast read.
     * Refreshed by TrendingService on each trending cleanup cycle.
     */
    @Column(name = "recent_score")
    @Builder.Default
    private Double recentScore = 0.0;
}
