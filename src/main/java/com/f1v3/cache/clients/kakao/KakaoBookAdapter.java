package com.f1v3.cache.clients.kakao;

import com.f1v3.cache.clients.api.SearchBookAdapter;
import com.f1v3.cache.clients.api.response.SearchBookDTO;
import com.f1v3.cache.clients.kakao.response.KakaoSearchBookDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class KakaoBookAdapter implements SearchBookAdapter {

    private final KakaoApiClient kakaoApiClient;

    private static final int DEFAULT_SIZE = 10;
    private static final String DEFAULT_SORT = "accuracy";


    @Override
    public SearchBookDTO search(String query, int page) {
        KakaoSearchBookDTO response = kakaoApiClient.searchBooks(
                query,
                page,
                DEFAULT_SIZE,
                DEFAULT_SORT);

        return convertResponse(response, page);
    }

    private SearchBookDTO convertResponse(
            KakaoSearchBookDTO dto, int page) {

        return new SearchBookDTO(
                convertBooks(dto.documents()),
                convertPageInfo(dto.meta(), page));
    }

    private List<SearchBookDTO.Book> convertBooks(
            List<KakaoSearchBookDTO.Document> documents) {
        return documents.stream()
                .map(document -> new SearchBookDTO.Book(
                        document.title(),
                        convertAuthor(document.authors()),
                        convertPublishedDate(document.datetime()),
                        document.extractThumbnailFileName()))
                .toList();
    }

    private SearchBookDTO.PageInfo convertPageInfo(
            KakaoSearchBookDTO.Meta meta, int currentPage) {
        return new SearchBookDTO.PageInfo(
                meta.is_end(),
                meta.pageable_count(),
                meta.total_count(),
                currentPage
        );
    }

    private String convertAuthor(List<String> authors) {
        return authors.isEmpty() ? "작자 미상" : authors.get(0);
    }

    private LocalDate convertPublishedDate(OffsetDateTime publishedDate) {
        return publishedDate == null ? LocalDate.EPOCH : publishedDate.toLocalDate();
    }
}
