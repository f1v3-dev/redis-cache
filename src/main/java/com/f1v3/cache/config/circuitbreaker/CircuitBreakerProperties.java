package com.f1v3.cache.config.circuitbreaker;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Resilience4j Circuit Breaker Properties.
 *
 * @author Seungjo, Jeong
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "resilience4j.circuitbreaker")
public class CircuitBreakerProperties {

    private String slidingWindowType;
    private int failureRateThreshold;
    private int slowCallDurationThreshold;
    private int slowCallRateThreshold;
    private int waitDurationInOpenState;
    private int minimumNumberOfCalls;
    private int slidingWindowSize;
    private int permittedNumberOfCallsInHalfOpenState;
    private List<String> recordExceptions;
}
