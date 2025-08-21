package com.f1v3.cache.common.cache;

import com.f1v3.cache.dto.SearchBookResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class PerRedisCacheManager {

    private final RedisTemplate<String, Object> redisTemplate;
    private final DefaultRedisScript<List> cacheGetRedisScript;
    private final DefaultRedisScript<String> cacheSetRedisScript;
    private final ObjectMapper objectMapper;

    private static final double BETA = 1.0;
    private static final long DEFAULT_TTL = 3600;
    private static final ThreadLocalRandom random = ThreadLocalRandom.current();


    public SearchBookResponse preGet(String key, Supplier<SearchBookResponse> recomputer) {

        List<Object> result = redisTemplate.execute(
                cacheGetRedisScript,
                List.of(key, getDeltaKey(key))
        );

        List<Object> valueList = (List<Object>) result.get(0);
        Object cachedData = valueList.get(0);
        Integer delta = (Integer) valueList.get(1);
        Long remainingTtl = (Long) result.get(1);

        boolean isShouldRecomputation = shouldRecompute(cachedData, delta, remainingTtl);

        if (isShouldRecomputation) {
            log.info("캐시 재계산 필요 - key: {}, delta: {}, remainingTtl: {}", key, delta, remainingTtl);
            long startTime = System.currentTimeMillis();
            SearchBookResponse freshData = recomputer.get();
            long computationTime = System.currentTimeMillis() - startTime;

            put(key, freshData, computationTime);
            return freshData;
        }

        try {
            return objectMapper.readValue((String) cachedData, SearchBookResponse.class);
        } catch (JsonProcessingException e) {
            log.warn("캐시 데이터 역직렬화 실패 - key: {}, error: {}", key, e.getMessage());
            throw new RuntimeException("캐시 데이터 역직렬화 실패", e);
        }
    }

    private void put(String key, SearchBookResponse value, long computationTime) {
        try {
            String deltaKey = getDeltaKey(key);
            String serializedValue = objectMapper.writeValueAsString(value);

            redisTemplate.execute(
                    cacheSetRedisScript,
                    List.of(key, deltaKey),
                    serializedValue,
                    computationTime,
                    DEFAULT_TTL
            );

            log.info("캐시 저장 성공 - key: {}, computationTime: {}ms, ttl: {}초", key, computationTime, DEFAULT_TTL);
        } catch (Exception e) {
            log.error("캐시 저장 실패 - key: {}, error: {}", key, e.getMessage(), e);
        }
    }

    private boolean shouldRecompute(Object cachedData, Integer delta, Long remainingTtl) {
        if (cachedData == null || delta == null || remainingTtl == null) {
            return true;
        }

        double randomValue = random.nextDouble();
        return -delta * BETA * Math.log(randomValue) >= remainingTtl;
    }


    private String getDeltaKey(String key) {
        return key + ":delta";
    }
}
