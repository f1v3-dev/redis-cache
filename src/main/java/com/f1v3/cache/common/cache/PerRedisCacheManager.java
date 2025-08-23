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
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import static java.time.Duration.ofMillis;

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

    private static final ThreadLocalRandom RANDOM = ThreadLocalRandom.current();

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
            if (shouldRecompute(cacheResult)) {
                T recomputed = tryRecomputeSingleFlight(key, recomputer);
                if (recomputed != null) {
                    return recomputed;
                }
            }

            return deserializeData(cacheResult.getData(), clazz);
        } catch (Exception e) {
            throw new CacheException("캐시 조회 실패", e);
        }
    }

    private <T> T retryGetFromCacheOrFail(String key, Class<T> clazz) {
        int attempts = cacheProperties.getRetryAttempts();
        long backoff = cacheProperties.getBaseBackoffMs();

        for (int i = 0; i < attempts; i++) {
            // Jitter 방식의 Sleep
            sleep(backoff + RANDOM.nextLong(cacheProperties.getMaxJitterMs()));
            CacheResult<String> after = getCacheData(key);
            if (after.isCacheHit() && after.getData() != null) {
                return deserializeData(after.getData(), clazz);
            }

            // todo: 여기서도 못얻으면?
            //      어떻게 대응해야할지 생각하기
        }

        throw new CacheException("캐시 미스 상태에서 동시 갱신 경합으로 값 확보 실패");
    }

    private <T> T tryRecomputeSingleFlight(String key, Supplier<T> recomputer) {

        long lockTimeout = cacheProperties.getDefaultLockTtlMs();
        String token = acquireLock(key, lockTimeout);

        if (token == null) {
            // 팔로워: 절대 원천 호출 금지
            log.debug("Lock acquisition failed for key={}, timeout={}ms", key, lockTimeout);
            return null;
        }

        try {
            long start = System.currentTimeMillis();
            T newData = recomputer.get();
            long computeTime = System.currentTimeMillis() - start;

            put(key, newData, computeTime);
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
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(lockKey, token, ofMillis(ttlMillis));

        return Boolean.TRUE.equals(ok) ? token : null;
    }

    private void releaseLock(String key, String token) {
        try {
            redisTemplate.execute(
                    unlockScript,
                    List.of(buildLockKey(key)),
                    token
            );

        } catch (Exception e) {
            log.warn("Failed to release lock for key={}, err={}", key, e.toString());
        }
    }

    @SuppressWarnings("unchecked")
    private CacheResult<String> getCacheData(String key) {
        List<Object> result = redisTemplate.execute(
                cacheGetRedisScript,
                List.of(key, getDeltaKey(key))
        );

        if (result.size() < 2) {
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

    private boolean shouldRecompute(CacheResult<String> cacheResult) {

        if (!cacheResult.isCacheHit() ||
                cacheResult.getData() == null ||
                cacheResult.getDelta() == null ||
                cacheResult.getRemainingTtl() == null) {
            return true;
        }

        double randomValue = RANDOM.nextDouble(); // 0~1 사이
        double logRandom = Math.log(randomValue); // 항상 음수값
        double threshold = cacheResult.getDelta() * cacheProperties.getBeta() * (-logRandom); // PER Algorithm

        Long remainingTtl = cacheResult.getRemainingTtl();
        return remainingTtl <= threshold;
    }

    private <T> String serializeValue(T value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new CacheException("데이터 직렬화 실패", e);
        }
    }

    private String getDeltaKey(String key) {
        return key + cacheProperties.getDeltaKeySuffix();
    }

    private String buildLockKey(String key) {
        return key + ":lock";
    }


    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
