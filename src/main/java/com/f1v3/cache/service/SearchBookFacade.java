package com.f1v3.cache.service;

import com.f1v3.cache.clients.api.SearchBookAdapter;
import com.f1v3.cache.common.circuitbreaker.SimpleCircuitBreaker;
import com.f1v3.cache.dto.SearchBookResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 책 검색을 위한 Facade 클래스
 *
 * @author Seungjo, Jeong
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchBookFacade {

    private final SimpleCircuitBreaker circuitBreaker = new SimpleCircuitBreaker(3);

    private final SearchBookUseCase searchBookUseCase;
    private final SearchBookAdapter searchBookAdapter;

    public SearchBookResponse search(String query, int page) {
        log.info("Searching books with cache for query: {}, page: {}", query, page);

        return circuitBreaker.execute(
                // 메인 작업: 캐시를 포함한 검색
                () -> {
                    log.debug("Executing main search operation");
                    return searchBookUseCase.search(query, page);
                },
                // 폴백 작업: 직접 API 호출
                () -> {
                    log.warn("Fallback search for query: {}, page: {}", query, page);
                    return SearchBookResponse.from(searchBookAdapter.search(query, page));
                }
        );
    }
}
