package searchengine.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.SearchService;
import searchengine.services.StatisticsService;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api")

public class ApiController {
    @Autowired
    private final StatisticsService statisticsService;
    @Autowired
    private final IndexingService indexingService;
    @Autowired
    private final SearchService searchService;

    @Autowired
    public ApiController(StatisticsService statisticsService, IndexingService indexingService, SearchService searchService) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
        this.searchService = searchService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<?> startIndexing() {
        synchronized (indexingService) {
            if (indexingService.isIndexing()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "result", false,
                        "error", "Indexing has already started"
                ));
            }
            indexingService.startIndexing();
            return ResponseEntity.ok(Map.of("result", true));
        }
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<?> stopIndexing() {
        synchronized (indexingService) {
            if (indexingService.isIndexing()) {
                indexingService.stopIndexing();
                return ResponseEntity.ok(Map.of("result", true));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "result", false,
                        "error", "Indexing is not running"
                ));
            }
        }
    }

    @PostMapping("/indexPage")
    public ResponseEntity<?> indexPage(@RequestParam String url) {
        try {
            boolean result = indexingService.indexPage(url);
            if (result) {
                return ResponseEntity.ok(Map.of("result", true));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "result", false,
                        "error", "Page not found"
                ));
            }
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "result", false,
                    "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam String query,
                                    @RequestParam(required = false) String site,
                                    @RequestParam(defaultValue = "0") int offset,
                                    @RequestParam(defaultValue = "20") int limit) {
        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "result", false,
                    "error", "Query is empty"
            ));
        }

        SearchResponse response = searchService.search(query, site, offset, limit);
        if (response == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "result", false,
                    "error", "Search failed"
            ));
        }

        return ResponseEntity.ok(response);

    }

}
