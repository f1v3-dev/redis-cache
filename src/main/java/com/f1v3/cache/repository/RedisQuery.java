package com.f1v3.cache.repository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 레디스 쿼리를 실행하는 클래스 (Slave Redis 사용).
 *
 * @author Seungjo, Jeong
 */
@Repository
public class RedisQuery {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisQuery(
            @Qualifier("slaveRedisTemplate") RedisTemplate<String, Object> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    public String getData(String key) {
        // TODO: Slave 장애시 Master Redis로 조회하는 방법 생각해보기
        return (String) redisTemplate.opsForValue().get(key);
    }
}
