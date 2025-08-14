package com.f1v3.cache.common.normalizer;

import java.util.regex.Pattern;

/**
 * 검색어 정규화를 위한 유틸리티 클래스
 * 효율적인 캐시 적용을 위해 검색어를 일관된 형태로 변환합니다.
 *
 * @author Seungjo, Jeong
 */
public class QueryNormalizer {

    private static final Pattern WHITE_SPACE = Pattern.compile("\\s+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^가-힣a-z0-9]");

    private QueryNormalizer() {
        // 유틸리티 클래스이므로 인스턴스 생성을 방지
    }

    /**
     * 검색어를 정규화합니다.
     * 1. 공백 제거
     * 2. 소문자로 변환
     * 3. 영숫자, 한글을 제외한 특수문자 제거
     *
     * @param query 정규화할 검색어
     * @return 정규화된 검색어
     */
    public static String normalize(String query) {
        if (query == null || query.trim().isEmpty()) {
            return "";
        }

        String normalizedQuery = WHITE_SPACE.matcher(query).replaceAll("");
        normalizedQuery = normalizedQuery.toLowerCase();
        normalizedQuery = NON_ALPHANUMERIC.matcher(normalizedQuery).replaceAll("");

        return normalizedQuery;
    }
}
