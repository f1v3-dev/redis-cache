package com.f1v3.cache.dto;

import com.f1v3.cache.clients.api.response.SearchBookDTO;

import java.time.LocalDate;
import java.util.List;

public record SearchBookResponse(
        List<Book> books,
        PageInfo pageInfo
) {

    public static SearchBookResponse from(SearchBookDTO dto) {
        List<Book> books = dto.books().stream()
                .map(book -> new Book(
                        book.title(),
                        book.author(),
                        book.publishedAt(),
                        book.thumbnail()))
                .toList();

        PageInfo pageInfo = new PageInfo(
                dto.pageInfo().isEnd(),
                dto.pageInfo().pageableCount(),
                dto.pageInfo().totalCount(),
                dto.pageInfo().page());

        return new SearchBookResponse(books, pageInfo);
    }

    public record Book(
            String title,
            String author,
            LocalDate publishedAt,
            String thumbnail) {

    }

    public record PageInfo(
            boolean isEnd,
            int pageableCount,
            int totalCount,
            int page) {
    }
}
