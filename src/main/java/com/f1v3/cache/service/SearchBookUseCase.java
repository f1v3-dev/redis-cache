package com.f1v3.cache.service;

import com.f1v3.cache.clients.api.SearchBookAdapter;
import com.f1v3.cache.common.cache.PerRedisCacheManager;
import com.f1v3.cache.config.circuitbreaker.CircuitBreakerProvider;
import com.f1v3.cache.dto.SearchBookResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SearchBookUseCase {

    private final SearchBookAdapter searchBookAdapter;
    private final PerRedisCacheManager perRedisCacheManager;

    public SearchBookUseCase(
            @Qualifier("testSearchBookAdapter") SearchBookAdapter searchBookAdapter,
            PerRedisCacheManager perRedisCacheManager
    ) {
        this.searchBookAdapter = searchBookAdapter;
        this.perRedisCacheManager = perRedisCacheManager;
    }

    @CircuitBreaker(
            name = CircuitBreakerProvider.CIRCUIT_REDIS,
            fallbackMethod = "searchWithoutCache"
    )
    public SearchBookResponse search(String query, int page) {
        String cacheKey = generateCacheKey(query, page);

        return perRedisCacheManager.get(
                cacheKey,
                SearchBookResponse.class,
                () -> SearchBookResponse.from(searchBookAdapter.search(query, page))
        );
    }

    public SearchBookResponse searchWithoutCache(String query, int page, Throwable e) {
//        log.warn("Fallback이 다음과 같은 오류로 인해 활성화됨: {}", e.getMessage());
//        log.info("레디스를 사용할 수 없어 외부 API를 직접 호출. query: {}, page: {}", query, page);
        return SearchBookResponse.from(searchBookAdapter.search(query, page));
    }

    private String generateCacheKey(String query, int page) {
        return "searchBook:" + query + ":" + page;
    }
}
