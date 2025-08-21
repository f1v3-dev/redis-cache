from locust import HttpUser, task, between, events
import random
import time
import threading
from datetime import datetime, timedelta

HOST = "http://localhost:8080"

# PER 알고리즘 테스트를 위한 설정
PER_TEST_QUERIES = [
    'springboot',  # PER 테스트 대상 쿼리
    'java',        # PER 테스트 대상 쿼리
    'python',      # 비교 대상 쿼리
]

# 캐시 TTL 설정 (초) - 실제 서버 설정과 맞춰야 함
CACHE_TTL = 10  # 60초로 가정

# PER 테스트를 위한 시나리오별 가중치
per_scenario_weights = {
    'normal_load': 30,      # 정상 부하 (30%)
    'burst_before_ttl': 20, # TTL 만료 직전 버스트 (20%)
    'concurrent_after_ttl': 25,  # TTL 만료 후 동시 요청 (25%)
    'random_access': 25     # 랜덤 접근 (25%)
}

class PERTestStatistics:
    """PER 알고리즘 효과 측정을 위한 통계 수집"""
    def __init__(self):
        self.cache_hits = 0
        self.cache_misses = 0
        self.response_times = []
        self.concurrent_requests = 0
        self.lock = threading.Lock()

    def record_response(self, response_time, is_cache_hit=None):
        with self.lock:
            self.response_times.append(response_time)
            if is_cache_hit is True:
                self.cache_hits += 1
            elif is_cache_hit is False:
                self.cache_misses += 1

    def record_concurrent_request(self):
        with self.lock:
            self.concurrent_requests += 1

    def get_stats(self):
        with self.lock:
            avg_response_time = sum(self.response_times) / len(self.response_times) if self.response_times else 0
            cache_hit_ratio = self.cache_hits / (self.cache_hits + self.cache_misses) if (self.cache_hits + self.cache_misses) > 0 else 0
            return {
                'avg_response_time': avg_response_time,
                'cache_hit_ratio': cache_hit_ratio,
                'total_requests': len(self.response_times),
                'concurrent_requests': self.concurrent_requests,
                'cache_hits': self.cache_hits,
                'cache_misses': self.cache_misses
            }

# 전역 통계 객체
per_stats = PERTestStatistics()

def weighted_scenario_choice(weights_dict):
    """시나리오별 가중치에 따른 랜덤 선택"""
    choices = list(weights_dict.keys())
    weights = list(weights_dict.values())
    return random.choices(choices, weights=weights, k=1)[0]

def simulate_cache_warmup_phase():
    """캐시 워밍업 단계 시뮬레이션"""
    return random.uniform(0, CACHE_TTL * 0.3)  # TTL의 30% 시점까지

def simulate_ttl_near_expiry():
    """TTL 만료 직전 시뮬레이션"""
    return random.uniform(CACHE_TTL * 0.8, CACHE_TTL * 0.95)  # TTL의 80-95% 시점

def simulate_post_ttl_expiry():
    """TTL 만료 직후 시뮬레이션"""
    return random.uniform(CACHE_TTL * 1.0, CACHE_TTL * 1.1)  # TTL의 100-110% 시점

class PERTestUser(HttpUser):
    host = HOST

    def on_start(self):
        """사용자 시작 시 초기화"""
        self.start_time = time.time()
        self.user_stats = {
            'requests_made': 0,
            'scenarios_executed': {},
            'query_distribution': {}
        }

    @task(3)  # 30% - 정상 부하 테스트
    def normal_load_test(self):
        """정상적인 부하 패턴으로 PER 알고리즘 테스트"""
        query = random.choice(PER_TEST_QUERIES)
        page = random.randint(1, 4)

        start_time = time.time()
        response = self.client.get(
            f'/api/books?query={query}&page={page}',
            name=f'per-normal-{query}'
        )
        end_time = time.time()

        # 통계 수집
        response_time = (end_time - start_time) * 1000  # ms 단위
        per_stats.record_response(response_time)

        self._update_user_stats('normal_load', query)

    @task(2)  # 20% - TTL 만료 직전 버스트 테스트
    def ttl_expiry_burst_test(self):
        """TTL 만료 직전 버스트 상황에서 PER 알고리즘 효과 테스트"""
        query = random.choice(PER_TEST_QUERIES[:2])  # 인기 쿼리만 대상
        page = random.randint(1, 4)

        # TTL 만료 직전 시뮬레이션을 위한 대기
        wait_time = random.uniform(0.1, 0.5)
        time.sleep(wait_time)

        start_time = time.time()
        response = self.client.get(
            f'/api/books?query={query}&page={page}',
            name=f'per-ttl-burst-{query}'
        )
        end_time = time.time()

        response_time = (end_time - start_time) * 1000
        per_stats.record_response(response_time)

        self._update_user_stats('burst_before_ttl', query)

    @task(3)  # 25% - TTL 만료 후 동시 요청 테스트
    def concurrent_post_ttl_test(self):
        """TTL 만료 후 동시 요청 상황에서 PER 알고리즘 효과 테스트"""
        query = random.choice(PER_TEST_QUERIES[:2])  # 인기 쿼리만 대상
        page = random.randint(1, 4)

        # 동시 요청 시뮬레이션
        per_stats.record_concurrent_request()

        start_time = time.time()
        response = self.client.get(
            f'/api/books?query={query}&page={page}',
            name=f'per-concurrent-{query}'
        )
        end_time = time.time()

        response_time = (end_time - start_time) * 1000
        per_stats.record_response(response_time)

        self._update_user_stats('concurrent_after_ttl', query)

    @task(2)  # 25% - 랜덤 접근 패턴 테스트
    def random_access_test(self):
        """랜덤한 접근 패턴으로 PER 알고리즘 기본 동작 테스트"""
        query = random.choice(PER_TEST_QUERIES)
        page = random.randint(1, 4)

        # 랜덤 대기 시간
        wait_time = random.uniform(0.1, 2.0)
        time.sleep(wait_time)

        start_time = time.time()
        response = self.client.get(
            f'/api/books?query={query}&page={page}',
            name=f'per-random-{query}'
        )
        end_time = time.time()

        response_time = (end_time - start_time) * 1000
        per_stats.record_response(response_time)

        self._update_user_stats('random_access', query)

    @task(1)  # 추가 테스트: 캐시 미스 유발을 위한 새로운 쿼리
    def cache_miss_inducer(self):
        """캐시 미스를 의도적으로 유발하여 PER 알고리즘 동작 확인"""
        # 타임스탬프를 포함한 유니크한 쿼리 생성
        unique_query = f"test_{int(time.time() * 1000) % 10000}"
        page = random.randint(1, 4)

        start_time = time.time()
        response = self.client.get(
            f'/api/books?query={unique_query}&page={page}',
            name='per-cache-miss'
        )
        end_time = time.time()

        response_time = (end_time - start_time) * 1000
        per_stats.record_response(response_time, is_cache_hit=False)

        self._update_user_stats('cache_miss_test', unique_query)

    def _update_user_stats(self, scenario, query):
        """사용자별 통계 업데이트"""
        self.user_stats['requests_made'] += 1

        if scenario not in self.user_stats['scenarios_executed']:
            self.user_stats['scenarios_executed'][scenario] = 0
        self.user_stats['scenarios_executed'][scenario] += 1

        if query not in self.user_stats['query_distribution']:
            self.user_stats['query_distribution'][query] = 0
        self.user_stats['query_distribution'][query] += 1

    wait_time = between(0.5, 2)  # PER 알고리즘 테스트를 위한 다양한 간격

# Locust 이벤트 핸들러
@events.test_start.add_listener
def on_test_start(environment, **kwargs):
    """테스트 시작 시 실행"""
    print("=== PER 알고리즘 캐시 효과 테스트 시작 ===")
    print(f"테스트 대상 쿼리: {PER_TEST_QUERIES}")
    print(f"캐시 TTL 설정: {CACHE_TTL}초")
    print(f"테스트 시나리오: {list(per_scenario_weights.keys())}")

@events.test_stop.add_listener
def on_test_stop(environment, **kwargs):
    """테스트 종료 시 PER 알고리즘 효과 통계 출력"""
    stats = per_stats.get_stats()

    print("\n=== PER 알고리즘 테스트 결과 ===")
    print(f"전체 요청 수: {stats['total_requests']}")
    print(f"평균 응답 시간: {stats['avg_response_time']:.2f}ms")
    print(f"캐시 히트율: {stats['cache_hit_ratio']:.2%}")
    print(f"동시 요청 수: {stats['concurrent_requests']}")
    print(f"캐시 히트: {stats['cache_hits']}")
    print(f"캐시 미스: {stats['cache_misses']}")

    # PER 알고리즘 효과 분석
    if stats['avg_response_time'] < 100:  # 100ms 미만
        print("✅ PER 알고리즘이 효과적으로 동작하고 있습니다 (빠른 응답시간)")
    elif stats['avg_response_time'] < 300:  # 300ms 미만
        print("⚠️  PER 알고리즘 효과가 제한적입니다 (보통 응답시간)")
    else:
        print("❌ PER 알고리즘 최적화가 필요합니다 (느린 응답시간)")

    if stats['cache_hit_ratio'] > 0.8:  # 80% 이상
        print("✅ 캐시 히트율이 우수합니다")
    elif stats['cache_hit_ratio'] > 0.6:  # 60% 이상
        print("⚠️  캐시 히트율이 보통입니다")
    else:
        print("❌ 캐시 히트율 개선이 필요합니다")

# 실행 예시 명령어:
# locust -f per-test.py --host=http://localhost:8080 -u 50 -r 10 -t 300s
# -u 50: 50명의 동시 사용자
# -r 10: 초당 10명씩 사용자 증가
# -t 300s: 5분간 테스트 실행
