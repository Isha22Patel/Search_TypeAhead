package com.typeahead.controller;

import com.typeahead.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * POST /search
 *
 * Accepts a search query, records it asynchronously, returns immediately.
 * Response time goal: < 5ms (pure in-memory operations, no DB call).
 */
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @PostMapping
    public ResponseEntity<Map<String, String>> search(
            @RequestBody Map<String, String> body) {

        String raw = body.get("query");
        try {
            String normalized = searchService.recordSearch(raw);
            return ResponseEntity.ok(Map.of(
                "message", "Searched",
                "query",   normalized
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }
}
