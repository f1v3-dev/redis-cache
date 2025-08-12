package com.f1v3.cache.common.cache;

import com.f1v3.cache.dto.SearchBookResponse;
import com.f1v3.cache.repository.RedisCommand;
import com.f1v3.cache.repository.RedisQuery;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis 기반 캐시 매니저
 *
 * @author Seungjo, Jeong
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisCacheManager implements BookCacheManager<SearchBookResponse> {

    private final RedisCommand redisCommand;
    private final RedisQuery redisQuery;
    private final ObjectMapper objectMapper;

    private static final Duration COUNT_TTL = Duration.ofHours(1L);
    private static final Duration DATA_TTL = Duration.ofMinutes(30L);

    private static final String COUNT_CACHE_PREFIX = "search:count:";
    private static final String DATA_CACHE_PREFIX = "search:data:";

    @Override
    public Optional<SearchBookResponse> getFromCache(String key) {
        try {
            if (!isThresholdReached(key)) {
                return Optional.empty();
            }

            SearchBookResponse response = getBookData(key);
            return Optional.of(response);

        } catch (Exception e) {
            log.error("Redis cache retrieval failed for key: {}", key, e);
            return Optional.empty();
        }
    }

    @Override
    public void incrementCount(String key) {
        String countKey = buildCountKey(key);
        redisCommand.incrementWithExpire(countKey, COUNT_TTL);
    }

    @Override
    public void addToCache(String key, SearchBookResponse value) {
        if (!isCacheable(value) || !isThresholdReached(key)) {
            return;
        }

        try {
            saveToCache(key, value);
            log.info("Data cached for key: {} (access count: {})", key, getCount(key));
        } catch (Exception e) {
            log.error("Failed to cache data for key: {}", key, e);
        }
    }

    @Override
    public void clear() {
        // TODO: 캐시 전체 삭제 로직 구현
        log.warn("Cache clear operation is not implemented yet.");
    }

    private SearchBookResponse getBookData(String key) throws JsonProcessingException {
        String dataKey = buildDataKey(key);
        String data = redisQuery.getData(dataKey);
        return objectMapper.readValue(data, SearchBookResponse.class);
    }

    private long getCount(String key) {
        String countKey = buildCountKey(key);
        String countData = redisQuery.getData(countKey);
        return countData != null ? Long.parseLong(countData) : 0;
    }

    private void saveToCache(String key, SearchBookResponse value) throws JsonProcessingException {
        String dataKey = buildDataKey(key);
        String data = objectMapper.writeValueAsString(value);
        redisCommand.setData(dataKey, data, DATA_TTL);
    }

    private boolean isThresholdReached(String key) {
        return getCount(key) >= THRESHOLD;
    }

    private boolean isCacheable(SearchBookResponse response) {
        return response.books() != null && !response.books().isEmpty();
    }

    private String buildCountKey(String key) {
        return COUNT_CACHE_PREFIX + key;
    }

    private String buildDataKey(String key) {
        return DATA_CACHE_PREFIX + key;
    }
}
