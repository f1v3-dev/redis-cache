# Redis 고가용성 전략

Redis 캐시 시스템에서 장애 상황에 대응하기 위한 두 가지 핵심 전략
1. **장애 대처 (Fallback)** - 장애 발생 시 서비스 연속성 보장 (애플리케이션 레벨)
2. **장애 대응 (Failover)** - 장애 발생 시 자동 복구 및 고가용성 확보 (인프라 레벨)

---

## 1. 장애 대처 (Fallback) 전략

### 1.1 try-catch

```java
try {
    return redisTemplate.opsForValue().get(key);
} catch (Exception e) {
    // 원본 데이터 소스에서 조회
    return repository.findById(key);
}
```

**문제점:**
- Redis 장애 시에도 매번 Redis에 요청을 시도
- 불필요한 네트워크 오버헤드 발생
- 응답 시간 지연
- 시스템 리소스 낭비

### 1.2 Circuit Breaker 패턴 도입

> **서킷 브레이커에서의 3가지 상태**
>
>- **CLOSED**: 정상 상태, Redis 요청 허용
>- **OPEN**: 장애 상태, Redis 요청 차단, 즉시 fallback 실행
>- **HALF-OPEN**: 복구 확인 상태, 제한적 요청 허용

- 장애 감지 후 즉시 fallback으로 전환
- 불필요한 요청 차단으로 성능 향상
- 자동 복구 감지 및 상태 전환
- 시스템 전체 안정성 향상

---

## 2. 장애 대응 (Failover) 전략

### 2.1 Stand-Alone의 문제점

**SPOF(Single Point of Failure)**
- 마스터 1대가 모든 읽기/쓰기 담당
- 서버 장애 시 전체 캐시 시스템 마비
- 수동 복구 필요

### 2.2 Master-Slave 구조

- Master: 쓰기 담당
- Slave: 읽기 담당 (복제본, readonly)

**장점**
- 읽기/쓰기 부하 분산
- 장애 발생 빈도 감소
- 읽기 성능 향상

**문제점 및 한계**
- Master 장애 시 쓰기 불가능 (살아있는 Slave를 통해 읽기만 가능)
- 수동 Slave → Master 승격 필요, 개발자가 직접 대응

### 2.3 Redis Sentinel을 통한 고가용성

1. **모니터링**: Master/Slave 상태 지속 감시
2. **알림**: 장애 발생 시 개발자에게 통지
3. **자동 Fail-over**: 정족수 기반 투표로 새로운 Master 선출
4. **서비스 디스커버리**: 클라이언트에게 현재 Master 정보 제공

**Failover 프로세스:**
1. Sentinel들이 Master 장애 감지
2. 정족수(Quorum) 기반 투표 실시
3. 적합한 Slave를 새로운 Master로 승격
4. 다른 Slave들을 새로운 Master로 재구성
5. 클라이언트에게 새로운 Master 정보 전달

---

## 3. 통합적 접근

### 3.1 다층 방어 전략

```
Client Request
     ↓
Circuit Breaker (Fallback)
     ↓
Redis Sentinel (Fail-over)
     ↓
Master-Slave Cluster
     ↓
Database (Final Fallback)
```

### 3.2 모니터링 및 알림

**슬랙 알림 설정:**
- Sentinel 이벤트 감지
- Master 전환 알림
- Circuit Breaker 상태 변경 알림
- 성능 메트릭 모니터링

---

## 4. 결론

**완전한 고가용성을 위한 필수 요소**

1. **Circuit Breaker**: 즉각적인 장애 대처
2. **Redis Sentinel**: 자동화된 장애 대응
3. **Master-Slave**: 부하 분산 및 데이터 복제
4. **모니터링**: 실시간 상태 감시
5. **알림 시스템**: 신속한 문제 인지

이러한 전략들을 통해 Redis 장애 상황에서도 서비스 중단 없이 안정적인 캐시 시스템을 운영할 수 있습니다.
