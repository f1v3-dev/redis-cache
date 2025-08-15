package com.f1v3.cache.controller;

import com.f1v3.cache.common.normalizer.QueryNormalizer;
import com.f1v3.cache.dto.SearchBookResponse;
import com.f1v3.cache.service.SearchBookUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 책 검색을 위한 Controller.
 *
 * @author Seungjo, Jeong
 */
@RestController
@RequiredArgsConstructor
public class SearchBookController {

    private final SearchBookUseCase searchBookUseCase;

    @GetMapping("/api/books")
    public ResponseEntity<SearchBookResponse> search(
            @RequestParam String query,
            @RequestParam int page) {

        String normalizedQuery = QueryNormalizer.normalize(query);
        return ResponseEntity.ok(searchBookUseCase.search(normalizedQuery, page));
    }

}
