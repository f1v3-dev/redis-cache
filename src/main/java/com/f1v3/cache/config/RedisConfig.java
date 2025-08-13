package com.f1v3.cache.config;

import io.lettuce.core.ReadFrom;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStaticMasterReplicaConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@EnableCaching
@Configuration
@RequiredArgsConstructor
public class RedisConfig {

    private final RedisProperties redisProperties; // master/slave 정보 담는 설정 클래스

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {


        // 슬레이브 우선 읽기 설정
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .readFrom(ReadFrom.REPLICA_PREFERRED)
                .build();

        // Master / Replica 구성
        RedisStaticMasterReplicaConfiguration redisConfig =
                new RedisStaticMasterReplicaConfiguration(
                        redisProperties.getMaster().getHost(),
                        redisProperties.getMaster().getPort()
                );

        // 마스터/슬레이브 공통 패스워드 설정
        redisConfig.setPassword(RedisPassword.of(redisProperties.getPassword()));

        // 슬레이브 노드 등록
        redisProperties.getSlaves().forEach(slave ->
                redisConfig.addNode(slave.getHost(), slave.getPort())
        );

        return new LettuceConnectionFactory(redisConfig, clientConfig);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(LettuceConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());

        return template;
    }
}
