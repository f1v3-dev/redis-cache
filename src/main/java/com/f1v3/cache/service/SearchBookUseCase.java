package com.f1v3.cache.service;

import com.f1v3.cache.clients.api.SearchBookAdapter;
import com.f1v3.cache.dto.SearchBookResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;


@Slf4j
@Service
@RequiredArgsConstructor
public class SearchBookUseCase {

    private final SearchBookAdapter searchBookAdapter;

    @Cacheable(
            value = "bookSearch",
            key = "#query + ':' + #page",
            unless = "#result == null or #result.books == null or #result.books.isEmpty()",
            cacheManager = "cacheManager"
    )
    public SearchBookResponse search(String query, int page) {
        return SearchBookResponse.from(searchBookAdapter.search(query, page));
    }

}

