package com.f1v3.cache.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RedisController {

    private final RedisTemplate<String, Object> redisTemplate;

    @PostMapping("/api/redis")
    public ResponseEntity<Void> setData(
            @RequestParam String key,
            @RequestParam String value) {

        redisTemplate.opsForValue().set(key, value);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/api/redis")
    public ResponseEntity<String> getData(
            @RequestParam String key
    ) {

        String data = (String) redisTemplate.opsForValue().get(key);
        return ResponseEntity.ok().body(data);
    }

    @GetMapping("/api/redis/test2")
    public ResponseEntity<Void> testConnection() {
        return ResponseEntity.ok().build();
    }
}
