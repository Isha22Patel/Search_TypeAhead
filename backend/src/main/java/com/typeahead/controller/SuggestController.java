package com.typeahead.controller;

import com.typeahead.dto.SuggestionDTO;
import com.typeahead.service.SuggestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * GET /suggest?q=<prefix>
 *
 * Returns up to 10 suggestions sorted by blended score.
 * Empty list (not 404) for unrecognized prefixes — consistent API contract.
 *
 * Performance target: < 20ms on cache hit, < 100ms on cache miss.
 */
@RestController
@RequestMapping("/suggest")
@RequiredArgsConstructor
public class SuggestController {

    private final SuggestionService suggestionService;

    @GetMapping
    public ResponseEntity<Map<String, List<SuggestionDTO>>> suggest(
            @RequestParam(name = "q", required = false) String prefix) {

        List<SuggestionDTO> suggestions = suggestionService.getSuggestions(prefix);
        return ResponseEntity.ok(Map.of("suggestions", suggestions));
    }
}
