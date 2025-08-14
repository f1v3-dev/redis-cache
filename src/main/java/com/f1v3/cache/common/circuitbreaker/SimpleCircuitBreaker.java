package com.f1v3.cache.common.circuitbreaker;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * 간단한 서킷 브레이커
 */
@Slf4j
public class SimpleCircuitBreaker {

    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicReference<CircuitBreakerState> state = new AtomicReference<>(CircuitBreakerState.CLOSED);
    private final int maxFailures;

    public SimpleCircuitBreaker() {
        this.maxFailures = 3; // 기본값 3번 실패
    }

    public SimpleCircuitBreaker(int maxFailures) {
        this.maxFailures = maxFailures;
    }

    /**
     * 서킷 브레이커를 통해 작업 실행
     */
    public <T> T execute(Supplier<T> mainOperation, Supplier<T> fallbackOperation) {
        CircuitBreakerState currentState = state.get();

        // OPEN 상태면 바로 fallback 실행
        if (currentState == CircuitBreakerState.OPEN) {
            log.warn("Circuit breaker is OPEN, using fallback");
            return fallbackOperation.get();
        }

        try {
            // 메인 작업 실행
            T result = mainOperation.get();

            // 성공하면 상태를 CLOSED로 변경
            onSuccess();
            return result;

        } catch (Exception e) {
            // 실패하면 실패 카운트 증가 및 상태 확인
            onFailure();
            log.warn("Main operation failed, using fallback. Current state: {}", state.get(), e);
            return fallbackOperation.get();
        }
    }

    private void onSuccess() {
        failureCount.set(0);
        state.set(CircuitBreakerState.CLOSED);
        log.debug("Circuit breaker state: CLOSED");
    }

    private void onFailure() {
        int failures = failureCount.incrementAndGet();
        log.warn("Failure count: {}", failures);

        if (failures > maxFailures) {
            state.set(CircuitBreakerState.OPEN);
            log.warn("Circuit breaker state changed to OPEN due to {} failures", failures);
        }
    }

    // 상태 확인용 메서드들
    public CircuitBreakerState getState() {
        return state.get();
    }

    public int getFailureCount() {
        return failureCount.get();
    }

    public boolean isOpen() {
        return state.get() == CircuitBreakerState.OPEN;
    }

    public boolean isClosed() {
        return state.get() == CircuitBreakerState.CLOSED;
    }
}
