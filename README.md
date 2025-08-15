# Redis Cache 프로젝트

## 프로젝트 개요

- 레디스를 활용한 캐시 시스템 구현 및 장애 대응 방안 학습
- 외부 시스템의 장애가 애플리케이션까지의 장애로 전파되지 않도록 구성하는 것이 목표
- 가용성을 높이기 위한 구조는 어떻게 만들어야 할까?

## 아키텍처 및 흐름

**현재 구성:**

```
Controller → UseCase (Circuit Breaker + Cache) → Adapter → External API
```

```mermaid
graph LR
    subgraph "Application Layer"
        Controller --> UseCase
    end
    
    subgraph "Infrastructure"
        UseCase --> Cache[(Redis)]
        UseCase --> API[External API]
    end
    
    subgraph "Resilience"
        UseCase -.-> CB[Circuit Breaker]
        CB -.-> Fallback
    end
```


### 전체 시스템 플로우

```mermaid
flowchart LR
    A[Client] --> B[Controller]
    B --> C[UseCase]
    C --> D{Circuit<br/>Breaker}
    
    D -->|CLOSED| E{Cache}
    D -->|OPEN| F[Fallback]
    
    E -->|Hit| G[Return]
    E -->|Miss| H[API Call]
    
    H --> I[Kakao API]
    I --> J{Cache<br/>Condition}
    J -->|Save| K[Store Cache]
    J -->|Skip| G
    K --> G
    F --> G
```



### 서킷브레이커 상태 전환

```mermaid
stateDiagram-v2
    [*] --> CLOSED
    CLOSED --> OPEN: 실패율 임계값 초과
    OPEN --> HALF_OPEN: 대기 시간 경과/호출 횟수 충족
    HALF_OPEN --> CLOSED: 성공적인 호출
    HALF_OPEN --> OPEN: 실패 발생
    
    note right of CLOSED
        정상 상태
        @Cacheable 동작
    end note
    
    note right of OPEN
        차단 상태
        fallback 메서드 호출
    end note
    
    note right of HALF_OPEN
        반열림 상태
        제한된 요청만 처리
    end note
```

### Redis 장애 대응 시나리오

```mermaid
flowchart TD
    A[Redis 장애 발생] --> B{장애 유형}
    
    B -->|연결 실패| C[애플리케이션 레벨 처리]
    B -->|마스터 다운| D[인프라 레벨 처리]
    
    C --> E[fallback 처리]
    E --> G[캐시 없이 정상 처리]
    
    D --> H[Sentinel 감지]
    H --> I[자동 Failover]
    I --> J[새로운 마스터 선출]
    J --> K[서비스 복구]
    
    G --> L[사용자에게 정상 응답]
    K --> L
```

## 학습 목표

### Redis 장애 대응 방안

Redis 서버 장애가 발생해도 서비스가 문제없이 동작하는 구성 

#### 1. 장애 처리 (애플리케이션 레벨, Fallback)

- 가장 간단하게 `try-catch`로 예외 처리하는 방법 
- 서킷브레이커를 통한 장애 전파 차단
- 외부 API 직접 호출을 통한 Fallback 처리

#### 2. 장애 대응 (인프라 레벨, HA)

- Master-Slave 구성을 통한 읽기 성능 향상
- Sentinel을 통한 자동 장애 감지 및 failover
- Cluster 구성을 통한 분산 처리 및 고가용성 확보

## 주요 기능

### 1. 캐시 시스템

- Redis를 활용한 도서 검색 결과 캐시
- TTL 30분 설정으로 메모리 효율적 관리 
- 캐시 키: `query:page` 형태로 구성

### 2. 서킷브레이커

- Redis 캐시 장애 시 자동 fallback 처리
- 장애 전파 차단을 통한 시스템 안정성 확보
- AOP 기반 어노테이션으로 간결한 구현
