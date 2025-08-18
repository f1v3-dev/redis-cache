package com.f1v3.cache.config.circuitbreaker;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

/**
 * Circuit Breaker Configuration.
 *
 * @author Seungjo, Jeong
 */
@Configuration
@RequiredArgsConstructor
public class CircuitBreakerProvider {

    public static final String CIRCUIT_REDIS = "CIRCUIT_REDIS";

}