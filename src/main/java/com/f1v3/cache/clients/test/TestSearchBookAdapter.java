package com.f1v3.cache.clients.test;

import com.f1v3.cache.clients.api.SearchBookAdapter;
import com.f1v3.cache.clients.api.response.SearchBookDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class TestSearchBookAdapter implements SearchBookAdapter {

    private final Random random = new Random();
    public static final AtomicInteger REQUEST_COUNT = new AtomicInteger(0);

    // 테스트용 책 데이터
    private static final List<String> BOOK_TITLES = List.of(
            "Effective Java", "Clean Code", "Spring in Action",
            "Java: The Complete Reference", "Head First Design Patterns",
            "Microservices Patterns", "Building Microservices",
            "Spring Boot in Action", "Java Concurrency in Practice",
            "Domain-Driven Design", "Clean Architecture", "Refactoring"
    );

    private static final List<String> AUTHORS = List.of(
            "Joshua Bloch", "Robert C. Martin", "Craig Walls",
            "Herbert Schildt", "Eric Freeman", "Chris Richardson",
            "Sam Newman", "Craig Walls", "Brian Goetz",
            "Eric Evans", "Robert C. Martin", "Martin Fowler"
    );

    @Override
    public SearchBookDTO search(String query, int page) {
        // PER 알고리즘 테스트를 위한 200ms~500ms 응답 시간 시뮬레이션
        long responseTime = 200 + random.nextInt(251); // 200~500ms

        log.info("테스트용 어댑터 호출 - query: {}, page: {}, 예상 응답시간: {}ms",
                 query, page, responseTime);

        try {
            Thread.sleep(responseTime);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Thread was interrupted: {}", e.getMessage());
        }

        REQUEST_COUNT.incrementAndGet();
        return generateTestSearchResult(query, page);
    }

    private SearchBookDTO generateTestSearchResult(String query, int page) {
        // 검색어에 따른 결과 수 결정 (PER 테스트용)
        int totalResults = determineTotalResults(query);
        int pageSize = 10;
        int startIndex = (page - 1) * pageSize;

        // 현재 페이지의 책 목록 생성
        List<SearchBookDTO.Book> books = generateBooks(query, startIndex, pageSize, totalResults);

        // 페이지 정보 생성
        SearchBookDTO.PageInfo pageInfo = new SearchBookDTO.PageInfo(
                startIndex + pageSize >= totalResults, // isEnd
                Math.min(totalResults, 1000), // pageableCount (최대 1000개)
                totalResults, // totalCount
                page // current page
        );

        log.info("테스트 결과 생성 완료 - query: {}, page: {}, books: {}, totalResults: {}",
                 query, page, books.size(), totalResults);

        return new SearchBookDTO(books, pageInfo);
    }

    private int determineTotalResults(String query) {
        // PER 알고리즘 테스트를 위한 쿼리별 결과 수 설정
        return switch (query.toLowerCase()) {
            case "springboot", "spring" -> 150 + random.nextInt(50); // 인기 검색어: 많은 결과
            case "java" -> 200 + random.nextInt(100); // 매우 인기 검색어: 매우 많은 결과
            case "python" -> 80 + random.nextInt(40); // 보통 인기: 중간 결과
            case "redis" -> 50 + random.nextInt(30); // 전문 검색어: 적은 결과
            case "mysql" -> 60 + random.nextInt(25); // 전문 검색어: 적은 결과
            default -> 20 + random.nextInt(30); // 일반 검색어: 적은 결과
        };
    }

    private List<SearchBookDTO.Book> generateBooks(String query, int startIndex, int pageSize, int totalResults) {
        List<SearchBookDTO.Book> books = new ArrayList<>();
        int endIndex = Math.min(startIndex + pageSize, totalResults);

        for (int i = startIndex; i < endIndex; i++) {
            String title = generateTitle(query, i);
            String author = AUTHORS.get(i % AUTHORS.size());
            LocalDate publishedAt = LocalDate.now().minusDays(random.nextInt(3650)); // 최근 10년
            String thumbnail = generateThumbnailUrl(i);

            books.add(new SearchBookDTO.Book(title, author, publishedAt, thumbnail));
        }

        return books;
    }

    private String generateTitle(String query, int index) {
        String baseTitle = BOOK_TITLES.get(index % BOOK_TITLES.size());

        // 검색어가 포함된 제목 생성 (검색 relevance 시뮬레이션)
        if (random.nextDouble() < 0.7) { // 70% 확률로 검색어 포함
            return String.format("%s: %s Edition", baseTitle, capitalizeFirst(query));
        } else {
            return String.format("%s (Vol.%d)", baseTitle, (index % 5) + 1);
        }
    }

    private String generateThumbnailUrl(int index) {
        return String.format("https://via.placeholder.com/120x160?text=Book%d", index + 1);
    }

    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}