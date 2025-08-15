package com.f1v3.cache.config.circuitbreaker;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Circuit Breaker Configuration.
 *
 * @author Seungjo, Jeong
 */
@Configuration
@RequiredArgsConstructor
public class CircuitBreakerProvider {

    public static final String CIRCUIT_REDIS = "CB_REDIS";

    private final CircuitBreakerProperties circuitBreakerProperties;

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        return CircuitBreakerRegistry.ofDefaults();
    }

    @Bean
    public CircuitBreaker redisCircuitBreaker(CircuitBreakerRegistry circuitBreakerRegistry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .failureRateThreshold(circuitBreakerProperties.getFailureRateThreshold())
                .slowCallDurationThreshold(Duration.ofMillis(circuitBreakerProperties.getSlowCallDurationThreshold()))
                .slowCallRateThreshold(circuitBreakerProperties.getSlowCallRateThreshold())
                .waitDurationInOpenState(Duration.ofMillis(circuitBreakerProperties.getWaitDurationInOpenState()))
                .minimumNumberOfCalls(circuitBreakerProperties.getMinimumNumberOfCalls())
                .slidingWindowSize(circuitBreakerProperties.getSlidingWindowSize())
                .permittedNumberOfCallsInHalfOpenState(circuitBreakerProperties.getPermittedNumberOfCallsInHalfOpenState())
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        return circuitBreakerRegistry.circuitBreaker(CIRCUIT_REDIS, config);
    }
}