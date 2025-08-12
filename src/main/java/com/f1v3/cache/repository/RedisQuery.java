package com.f1v3.cache.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 레디스 쿼리를 실행하는 클래스.
 *
 * @author Seungjo, Jeong
 */
@Repository
@RequiredArgsConstructor
public class RedisQuery {

    private final RedisTemplate<String, Object> redisTemplate;

    public String getData(String key) {
        return (String) redisTemplate.opsForValue().get(key);
    }
}
