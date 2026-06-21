package com.typeahead.controller;

import com.typeahead.dto.TrendingDTO;
import com.typeahead.service.TrendingService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * GET /trending
 *
 * Returns top 10 trending queries using recency-aware blended scoring.
 * Score = 0.3 * historicalCount + 0.7 * recentCount (last 60 min).
 */
@RestController
@RequestMapping("/trending")
@RequiredArgsConstructor
public class TrendingController {

    private final TrendingService trendingService;

    @Value("${typeahead.trending.top-limit:10}")
    private int topLimit;

    @GetMapping
    public ResponseEntity<Map<String, List<TrendingDTO>>> trending() {
        List<TrendingDTO> results = trendingService.getTopTrending(topLimit);
        return ResponseEntity.ok(Map.of("trending", results));
    }
}
