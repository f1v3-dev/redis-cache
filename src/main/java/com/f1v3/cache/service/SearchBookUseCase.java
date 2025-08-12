package com.f1v3.cache.service;

import com.f1v3.cache.clients.api.SearchBookAdapter;
import com.f1v3.cache.common.cache.BookCacheManager;
import com.f1v3.cache.dto.SearchBookResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class SearchBookUseCase {

    private final BookCacheManager<SearchBookResponse> cacheManager;
    private final SearchBookAdapter searchBookAdapter;

    public SearchBookResponse search(String query, int page) {
        String cacheKey = query + ":" + page;

        return cacheManager.getFromCache(cacheKey)
                .orElseGet(() -> {
                    SearchBookResponse response = SearchBookResponse.from(searchBookAdapter.search(query, page));

                    cacheManager.incrementCount(cacheKey);
                    cacheManager.addToCache(cacheKey, response);

                    return response;
                });
    }
}
