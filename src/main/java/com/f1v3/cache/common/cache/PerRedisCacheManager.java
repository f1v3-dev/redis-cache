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

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class PerRedisCacheManager {

    private final RedisTemplate<String, Object> redisTemplate;
    private final DefaultRedisScript<List> cacheGetRedisScript;
    private final DefaultRedisScript<String> cacheSetRedisScript;
    private final DefaultRedisScript<Long> unlockScript;

    private final ObjectMapper objectMapper;
    private final PerCacheProperties cacheProperties;

    private static final ThreadLocalRandom random = ThreadLocalRandom.current();

    public <T> T get(String key, Class<T> clazz, Supplier<T> recomputer) {
        try {
            CacheResult<String> cacheResult = getCacheData(key);

            // 1. 캐시 미스: Single Flight Pattern을 통해 1개의 요청만 재게산
            if (!cacheResult.isCacheHit() || cacheResult.getData() == null) {
                T recomputed = tryRecomputeSingleFlight(key, recomputer);
                if (recomputed != null) {
                    return recomputed;
                }

                return retryGetFromCacheOrFail(key, clazz);
            }

            // 2. 캐시 히트: PER로 조기 갱신 필요 여부 판단
            boolean needEarlyRefresh = shouldRecompute(cacheResult);

            // 기본 정책: 현재 요청은 캐시값 반환(지연 최소화). 갱신은 다음 요청에서(동기).
            if (!needEarlyRefresh) {
                return deserializeData(cacheResult.getData(), clazz);
            }

            return deserializeData(cacheResult.getData(), clazz);
        } catch (Exception e) {
            throw new CacheException("캐시 조회 실패", e);
        }
    }

    // 팔로워 경로: 재조회 2회(총 3회 기회: 최초 미스 + 2회 재조회)
    private <T> T retryGetFromCacheOrFail(String key, Class<T> clazz) {
        int attempts = Math.max(0, 2); // 기대: 2
        long backoff = Math.max(0, 100); // 50~100 권장

        for (int i = 0; i < attempts; i++) {
            sleep(backoff + random.nextLong(30)); // 지터 약간
            CacheResult<String> after = getCacheData(key);
            if (after.isCacheHit() && after.getData() != null) {
                return deserializeData(after.getData(), clazz);
            }
        }

        // 여전히 미스면 정책적으로 실패 처리(또는 부분 데이터/기본값 반환으로 디그레이드 가능)
        throw new CacheException("캐시 미스 상태에서 동시 갱신 경합으로 값 확보 실패");
    }

    // 리더만 recomputer 실행. 실패 시 null 반환(팔로워 경로로 위임)
    private <T> T tryRecomputeSingleFlight(String key, Supplier<T> recomputer) {
        String token = acquireLock(key, 5000);
        if (token == null) {
            // 팔로워: 절대 원천 호출 금지
            return null;
        }

        try {
            long start = System.currentTimeMillis();
            T newData = recomputer.get();
            long comp = System.currentTimeMillis() - start;

            put(key, newData, comp);
            return newData;
        } catch (Exception ex) {
            log.warn("Recompute failed for key={}", key, ex);
            return null;
        } finally {
            releaseLock(key, token);
        }
    }

    private String acquireLock(String key, long ttlMillis) {
        String lockKey = buildLockKey(key);
        String token = UUID.randomUUID().toString();
        Boolean ok = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, token, Duration.ofMillis(ttlMillis));
        return Boolean.TRUE.equals(ok) ? token : null;
    }

    private boolean releaseLock(String key, String token) {
        try {
            Long res = redisTemplate.execute(
                    unlockScript,
                    List.of(buildLockKey(key)),
                    token
            );

            return res != null && res > 0;
        } catch (Exception e) {
            log.warn("Failed to release lock for key={}, err={}", key, e.toString());
            return false;
        }
    }

    private String buildLockKey(String key) {
        return key + ":lock";
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

        Long remainingTtl = cacheResult.getRemainingTtl();
        log.info("threshold = {}, remainingTtl = {}", threshold, remainingTtl);

        return remainingTtl <= threshold;
    }

    private String getDeltaKey(String key) {
        return key + cacheProperties.getDeltaKeySuffix();
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(Math.max(0, ms));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
