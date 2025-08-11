package com.f1v3.cache.controller;

import com.f1v3.cache.service.SearchBookUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.regex.Pattern;

/**
 * 책 검색을 위한 Controller.
 *
 * @author Seungjo, Jeong
 */
@RestController
@RequiredArgsConstructor
public class SearchBookController {

    private static final Pattern WHITE_SPACE = Pattern.compile("\\s+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^가-힣a-z0-9]");

    private final SearchBookUseCase searchBookUseCase;

    @GetMapping("/api/books")
    public ResponseEntity<?> search(
            @RequestParam String query,
            @RequestParam int page) {

        String normalizedQuery = normalizeQuery(query);
        return ResponseEntity.ok(searchBookUseCase.search(normalizedQuery, page));
    }

    /**
     * 검색어 정규화 메서드 (효율적인 캐시 적용을 위해 사용)
     */
    private String normalizeQuery(String query) {
        String normalizedQuery = WHITE_SPACE.matcher(query).replaceAll("");
        normalizedQuery = normalizedQuery.toLowerCase();
        normalizedQuery = NON_ALPHANUMERIC.matcher(normalizedQuery).replaceAll("");
        return normalizedQuery;
    }

}
