package com.f1v3.cache.service;

import com.f1v3.cache.clients.api.SearchBookAdapter;
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

    private final SearchBookUseCase searchBookUseCase;
    private final SearchBookAdapter searchBookAdapter;

    public SearchBookResponse search(String query, int page) {
        log.info("Searching books with cache for query: {}, page: {}", query, page);

        try {
            return searchBookUseCase.search(query, page);
        } catch (Exception e) {
            log.warn("Cache search failed, using fallback for query: {}, page: {}", query, page, e);
            return fallbackSearch(query, page);
        }
    }

    /**
     * 책 검색에 실패했을 경우 직접 검색을 수행하는 fallback 메서드.
     */
    private SearchBookResponse fallbackSearch(String query, int page) {
        log.warn("Fallback search for query: {}, page: {}", query, page);
        return SearchBookResponse.from(searchBookAdapter.search(query, page));
    }
}



