package com.typeahead.repository;

import com.typeahead.model.SearchQuery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for search query persistence.
 *
 * Two approaches for prefix matching are provided:
 *
 * 1. Spring Data derived method: uses JPQL LIKE under the hood.
 *    Good for dev/testing; Hibernate generates the LIKE clause automatically.
 *
 * 2. Native ILIKE query: faster in production because it directly uses the
 *    PostgreSQL text_pattern_ops index and avoids Hibernate overhead.
 *    ILIKE = case-insensitive LIKE in PostgreSQL.
 */
@Repository
public interface SearchQueryRepository extends JpaRepository<SearchQuery, Long> {

    /**
     * Spring Data derived method – finds top 10 queries starting with prefix,
     * sorted by count descending. Used for comparison / fallback.
     */
    List<SearchQuery> findTop10ByQueryStartingWithIgnoreCaseOrderByCountDesc(String prefix);

    /**
     * Native query using ILIKE + text_pattern_ops index.
     * :prefix || '%' appends a wildcard for prefix matching.
     * LIMIT 10 prevents full-table scan results.
     *
     * Performance note: With the idx_query_pattern index this runs in O(log N + K)
     * where N = total rows, K = matching rows (up to 10).
     */
    @Query(
        value = "SELECT * FROM search_queries WHERE query ILIKE :prefix || '%' ORDER BY count DESC LIMIT 10",
        nativeQuery = true
    )
    List<SearchQuery> findTop10ByPrefixNative(@Param("prefix") String prefix);

    /**
     * Used by DatasetLoader to check existence before insert (avoid duplicates).
     */
    Optional<SearchQuery> findByQuery(String query);

    /**
     * Fetch top N by count – used as base for trending calculation.
     */
    @Query("SELECT s FROM SearchQuery s ORDER BY s.count DESC")
    List<SearchQuery> findTopByCount(org.springframework.data.domain.Pageable pageable);
}
