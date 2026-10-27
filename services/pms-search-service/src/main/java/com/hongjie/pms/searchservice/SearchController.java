package com.hongjie.pms.searchservice;

import com.hongjie.pms.contracts.search.*;
import com.hongjie.pms.searchengine.SearchEngine;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.io.IOException;

@RestController
public class SearchController {
    private final SearchEngine engine;

    public SearchController(SearchEngine engine) {
        this.engine = engine;
    }

    @PostMapping(SearchProtocol.PATH)
    public SearchResult query(@RequestBody SearchQuery query) throws IOException {
        return engine.search(query);
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    ResponseEntity<Void> invalid() {
        return ResponseEntity.badRequest().build();
    }

    @ExceptionHandler({
        IOException.class,
        co.elastic.clients.elasticsearch._types.ElasticsearchException.class
    })
    ResponseEntity<Void> unavailable() {
        return ResponseEntity.status(503).build();
    }
}
