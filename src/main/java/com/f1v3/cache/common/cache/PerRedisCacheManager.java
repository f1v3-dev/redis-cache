package com.f1v3.cache.common.cache;

import com.f1v3.cache.common.cache.config.PerCacheProperties;
import com.f1v3.cache.common.cache.dto.CacheResult;
import com.f1v3.cache.common.cache.exception.CacheException;
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

@Component
@RequiredArgsConstructor
public class PerRedisCacheManager {

    private final RedisTemplate<String, Object> redisTemplate;
    private final DefaultRedisScript<List> cacheGetRedisScript;
    private final DefaultRedisScript<String> cacheSetRedisScript;
    private final ObjectMapper objectMapper;
    private final PerCacheProperties cacheProperties;

    private static final ThreadLocalRandom random = ThreadLocalRandom.current();

    public <T> T get(String key, Class<T> clazz, Supplier<T> recomputer) {
        try {
            CacheResult<String> cacheResult = getCacheData(key);

            if (shouldRecompute(cacheResult)) {
                return recomputeAndCache(key, recomputer);
            }
            return deserializeData(cacheResult.getData(), clazz);
        } catch (Exception e) {
            throw new CacheException("캐시 조회 실패", e);
        }
    }

    @SuppressWarnings("unchecked")
    private CacheResult<String> getCacheData(String key) {
        List<Object> result = redisTemplate.execute(
                cacheGetRedisScript,
                List.of(key, getDeltaKey(key))
        );

        if (result == null || result.size() < 2) {
            return new CacheResult<>(null, null, null, false);
        }

        List<Object> valueList = (List<Object>) result.getFirst();
        if (valueList == null || valueList.size() < 2) {
            return new CacheResult<>(null, null, null, false);
        }

        String cachedData = (String) valueList.getFirst();
        Integer delta = (Integer) valueList.get(1);
        Long remainingTtl = (Long) result.get(1);

        return new CacheResult<>(cachedData, delta, remainingTtl, cachedData != null);
    }

    private <T> T recomputeAndCache(String key, Supplier<T> recomputer) {

        long startTime = System.currentTimeMillis();
        T newData = recomputer.get();
        long computationTime = System.currentTimeMillis() - startTime;

        put(key, newData, computationTime);

        return newData;
    }

    private <T> T deserializeData(String cachedData, Class<T> clazz) {
        try {
            return objectMapper.readValue(cachedData, clazz);
        } catch (JsonProcessingException e) {
            throw new CacheException("캐시 데이터 역직렬화 실패", e);
        }
    }

    private <T> void put(String key, T value, long computationTime) {
        try {
            String deltaKey = getDeltaKey(key);
            String serializedValue = serializeValue(value);

            redisTemplate.execute(
                    cacheSetRedisScript,
                    List.of(key, deltaKey),
                    serializedValue,
                    computationTime,
                    cacheProperties.getDefaultTtl()
            );

        } catch (Exception e) {
            throw new CacheException("캐시 저장 실패", e);
        }
    }

    private <T> String serializeValue(T value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new CacheException("데이터 직렬화 실패", e);
        }
    }

    private boolean shouldRecompute(CacheResult<String> cacheResult) {

        if (!cacheResult.isCacheHit() ||
                cacheResult.getData() == null ||
                cacheResult.getDelta() == null ||
                cacheResult.getRemainingTtl() == null) {
            return true;
        }

        double randomValue = random.nextDouble(); // 0~1 사이
        double logRandom = Math.log(randomValue); // 항상 음수값
        double threshold = cacheResult.getDelta() * cacheProperties.getBeta() * (-logRandom); // 음수를 양수로 변환

        boolean shouldRecompute = cacheResult.getRemainingTtl() <= threshold;
        return shouldRecompute;
    }

    private String getDeltaKey(String key) {
        return key + cacheProperties.getDeltaKeySuffix();
    }
}
