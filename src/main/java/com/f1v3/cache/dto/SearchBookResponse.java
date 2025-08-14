package com.f1v3.cache.dto;

import com.f1v3.cache.clients.api.response.SearchBookDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchBookResponse {
    private List<Book> books;
    private PageInfo pageInfo;

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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Book {
        private String title;
        private String author;
        private LocalDate publishedAt;
        private String thumbnail;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PageInfo {
        private boolean isEnd;
        private int pageableCount;
        private int totalCount;
        private int page;
    }
}
