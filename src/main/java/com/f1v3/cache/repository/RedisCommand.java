package com.f1v3.cache.repository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

/**
 * 레디스 커맨드를 실행하는 클래스 (Master Redis 사용).
 *
 * @author Seungjo, Jeong
 */
@Repository
public class RedisCommand {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisCommand(
            @Qualifier("masterRedisTemplate") RedisTemplate<String, Object> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    public void setData(String key, String value, Duration expiredTime) {
        redisTemplate.opsForValue().set(key, value, expiredTime);
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }

    public void incrementWithExpire(String countKey, Duration expiredTime) {
        redisTemplate.opsForValue().increment(countKey);
        redisTemplate.expire(countKey, expiredTime);
    }
}
