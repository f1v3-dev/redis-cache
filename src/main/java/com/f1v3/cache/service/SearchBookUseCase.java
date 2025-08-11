package com.f1v3.cache.service;

import com.f1v3.cache.clients.api.SearchBookAdapter;
import com.f1v3.cache.clients.api.response.SearchBookDTO;
import com.f1v3.cache.dto.SearchBookResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * {class name}.
 *
 * @author Seungjo, Jeong
 */
@Service
@RequiredArgsConstructor
public class SearchBookUseCase {

    private final SearchBookAdapter searchBookAdapter;


    @Cacheable(
            value = "searchBookCache",
            key = "#query + ':' + #page",
            condition = "#query != null && #query.length() > 0 && #page >= 1",
            unless = "#result == null || #result.books.isEmpty()"
    )
    public SearchBookResponse search(String query, int page) {
        SearchBookDTO response = searchBookAdapter.search(query, page);
        return SearchBookResponse.from(response);
    }

}
