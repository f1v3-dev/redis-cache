package com.f1v3.cache.service;

import com.f1v3.cache.clients.api.SearchBookAdapter;
import com.f1v3.cache.config.circuitbreaker.CircuitBreakerProvider;
import com.f1v3.cache.dto.SearchBookResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchBookUseCase {

    private final SearchBookAdapter searchBookAdapter;

    @CircuitBreaker(
            name = CircuitBreakerProvider.CIRCUIT_REDIS,
            fallbackMethod = "searchWithoutCache"
    )
    @Cacheable(
            value = "bookSearch",
            key = "#query + ':' + #page",
            unless = "#result == null or #result.books == null or #result.books.isEmpty()",
            cacheManager = "cacheManager"
    )
    public SearchBookResponse search(String query, int page) {
        log.info("Searching books with cache for query: {}, page: {}", query, page);
        return SearchBookResponse.from(searchBookAdapter.search(query, page));
    }

    public SearchBookResponse searchWithoutCache(String query, int page, Throwable e) {
        log.warn("Fallback triggered for search without cache due to: {}", e.getMessage());
        log.info("Searching books without cache for query: {}, page: {}", query, page);
        return SearchBookResponse.from(searchBookAdapter.search(query, page));
    }
}
