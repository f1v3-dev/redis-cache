package com.f1v3.cache.service;

import com.f1v3.cache.clients.api.SearchBookAdapter;
import com.f1v3.cache.common.cache.BookCacheManager;
import com.f1v3.cache.dto.SearchBookResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Slf4j
@Service
@RequiredArgsConstructor
public class SearchBookUseCase {

    private final BookCacheManager<SearchBookResponse> cacheManager;
    private final SearchBookAdapter searchBookAdapter;

    public SearchBookResponse search(String query, int page) {
        String cacheKey = query + ":" + page;

        try {

            return cacheManager.getFromCache(cacheKey)
                    .orElseGet(() -> {
                        SearchBookResponse response = SearchBookResponse.from(searchBookAdapter.search(query, page));
                        cacheManager.addToCache(cacheKey, response);
                        cacheManager.incrementCount(cacheKey);

                        return response;
                    });
        } catch (Exception e) {
            // RedisSystemException 같은 예외가 발생할 수 있음. (master/slave 모두 장애 발생 등)
            // 이러한 예외가 발생하더라도 정상적으로 처리될 수 있는 처리
            log.warn("Error during book search for query: {}, page: {}\nError message: {}", query, page, e.getMessage());
            return SearchBookResponse.from(searchBookAdapter.search(query, page));
        }
    }
}
