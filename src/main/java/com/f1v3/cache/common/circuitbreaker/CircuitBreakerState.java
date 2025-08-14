package com.f1v3.cache.common.circuitbreaker;

/**
 * 서킷 브레이커의 상태를 나타내는 ENUM
 *
 * @author Seungjo, Jeong
 */
public enum CircuitBreakerState {
    CLOSED,    // 정상 상태 - 모든 요청이 통과
    OPEN,      // 차단 상태 - 모든 요청이 fallback으로 처리
    HALF_OPEN  // 반개방 상태 - 제한적으로 요청을 허용하여 복구 시도
}
