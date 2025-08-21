from locust import HttpUser, task, between, events
import random
import time
import threading
from datetime import datetime

HOST = "http://localhost:8080"

# 핫 키 설정 - Cache Stampede 테스트용
HOT_KEY = "springboot"  # 모든 사용자가 동일한 키로 요청
HOT_PAGE = 1           # 동일한 페이지

# 캐시 TTL 설정 (초) - 실제 서버 설정과 맞춰야 함
CACHE_TTL = 5  # 5초로 설정하여 빠른 Cache Stampede 유발

class PERTestStatistics:
    """PER 알고리즘 Cache Stampede 효과 측정"""
    def __init__(self):
        self.response_times = []
        self.concurrent_requests = 0
        self.requests_per_second = {}
        self.cache_stampede_events = 0
        self.lock = threading.Lock()
        self.start_time = time.time()

    def record_response(self, response_time):
        with self.lock:
            current_time = int(time.time())
            self.response_times.append(response_time)

            # 초당 요청 수 기록
            if current_time not in self.requests_per_second:
                self.requests_per_second[current_time] = 0
            self.requests_per_second[current_time] += 1

    def record_concurrent_request(self):
        with self.lock:
            self.concurrent_requests += 1

    def record_cache_stampede(self):
        with self.lock:
            self.cache_stampede_events += 1

    def get_stats(self):
        with self.lock:
            if not self.response_times:
                return {}

            sorted_times = sorted(self.response_times)
            total_requests = len(self.response_times)

            stats = {
                'total_requests': total_requests,
                'avg_response_time': sum(self.response_times) / total_requests,
                'min_response_time': min(self.response_times),
                'max_response_time': max(self.response_times),
                'p50_response_time': sorted_times[int(total_requests * 0.5)],
                'p90_response_time': sorted_times[int(total_requests * 0.9)],
                'p95_response_time': sorted_times[int(total_requests * 0.95)],
                'p99_response_time': sorted_times[int(total_requests * 0.99)],
                'concurrent_requests': self.concurrent_requests,
                'cache_stampede_events': self.cache_stampede_events,
                'max_rps': max(self.requests_per_second.values()) if self.requests_per_second else 0,
                'avg_rps': total_requests / max(1, time.time() - self.start_time)
            }
            return stats

# 전역 통계 객체
per_stats = PERTestStatistics()

class CacheStampedeTestUser(HttpUser):
    host = HOST

    def on_start(self):
        """사용자 시작 시 초기화"""
        self.user_id = random.randint(1000, 9999)
        print(f"사용자 {self.user_id} 시작 - 핫 키 '{HOT_KEY}' 집중 테스트")

    @task(1)  # 100% - 핫 키에만 집중적으로 요청 (Cache Stampede 유발)
    def hot_key_stampede_test(self):
        """핫 키에 대한 동시 요청으로 Cache Stampede 상황 시뮬레이션"""

        # 동시 요청 카운트
        per_stats.record_concurrent_request()

        # Cache Stampede 상황 감지를 위한 이벤트 기록
        if random.random() < 0.2:  # 20% 확률로 Cache Stampede 이벤트 기록
            per_stats.record_cache_stampede()

        start_time = time.time()

        try:
            with self.client.get(
                    f'/api/books?query={HOT_KEY}&page={HOT_PAGE}',
                    name=f'hot-key-{HOT_KEY}',
                    catch_response=True
            ) as response:
                end_time = time.time()
                response_time = (end_time - start_time) * 1000  # ms 단위

                if response.status_code == 200:
                    response.success()
                    per_stats.record_response(response_time)

                    # 응답 시간이 비정상적으로 길면 로그 출력
                    if response_time > 800:  # 800ms 초과
                        print(f"⚠️  사용자 {self.user_id}: 긴 응답시간 감지 - {response_time:.0f}ms (PER 효과 확인 필요)")
                else:
                    response.failure(f"HTTP {response.status_code}")

        except Exception as e:
            end_time = time.time()
            response_time = (end_time - start_time) * 1000
            print(f"❌ 사용자 {self.user_id}: 요청 실패 - {str(e)}")

    # Cache Stampede 효과를 극대화하기 위해 매우 짧은 대기 시간
    wait_time = between(0.05, 0.2)  # 0.05~0.2초 대기 (더 집중적인 요청)

# Locust 이벤트 핸들러
@events.test_start.add_listener
def on_test_start(environment, **kwargs):
    """테스트 시작 시 실행"""
    print("=" * 60)
    print("🚀 PER 알고리즘 Cache Stampede 테스트 시작")
    print(f"🔥 핫 키: '{HOT_KEY}' (페이지 {HOT_PAGE})")
    print(f"⏰ 캐시 TTL: {CACHE_TTL}초")
    print(f"👥 테스트 시나리오: 다수 사용자가 동일한 키로 동시 요청")
    print(f"🎯 목표: Cache Stampede 상황에서 PER 알고리즘 효과 측정")
    print("=" * 60)

@events.test_stop.add_listener
def on_test_stop(environment, **kwargs):
    """테스트 종료 시 Cache Stampede 및 PER 알고리즘 효과 분석"""
    stats = per_stats.get_stats()

    if not stats:
        print("❌ 통계 데이터가 없습니다.")
        return

    print("\n" + "=" * 60)
    print("📊 PER 알고리즘 Cache Stampede 테스트 결과")
    print("=" * 60)

    # 기본 통계
    print(f"📈 전체 요청 수: {stats['total_requests']:,}")
    print(f"🔥 Cache Stampede 이벤트: {stats['cache_stampede_events']:,}")
    print(f"👥 동시 요청 수: {stats['concurrent_requests']:,}")
    print(f"🚀 최대 RPS: {stats['max_rps']:.1f}")
    print(f"📊 평균 RPS: {stats['avg_rps']:.1f}")

    print(f"\n⏱️  응답 시간 분석:")
    print(f"   평균: {stats['avg_response_time']:.1f}ms")
    print(f"   최소: {stats['min_response_time']:.1f}ms")
    print(f"   최대: {stats['max_response_time']:.1f}ms")
    print(f"   P50:  {stats['p50_response_time']:.1f}ms")
    print(f"   P90:  {stats['p90_response_time']:.1f}ms")
    print(f"   P95:  {stats['p95_response_time']:.1f}ms")
    print(f"   P99:  {stats['p99_response_time']:.1f}ms")

    # PER 알고리즘 효과 분석
    print(f"\n🔍 PER 알고리즘 효과 분석:")

    # 응답 시간 기준 평가
    avg_time = stats['avg_response_time']
    p95_time = stats['p95_response_time']
    max_time = stats['max_response_time']

    if avg_time < 100 and p95_time < 200:
        print("✅ 우수: PER 알고리즘이 매우 효과적으로 동작")
        print("   Cache Stampede 상황에서도 안정적인 응답시간 유지")
    elif avg_time < 300 and p95_time < 500:
        print("⚠️  보통: PER 알고리즘이 부분적으로 효과적")
        print("   일부 개선 여지 존재")
    else:
        print("❌ 개선 필요: PER 알고리즘 최적화 권장")
        print("   Cache Stampede 상황에서 성능 저하 발생")

    # Cache Stampede 영향 분석
    stampede_ratio = stats['cache_stampede_events'] / max(1, stats['total_requests'])
    if stampede_ratio > 0.1:  # 10% 이상
        print(f"🔥 높은 Cache Stampede 발생률: {stampede_ratio:.1%}")
        if max_time > 1000:
            print("   PER 알고리즘으로 인한 부하 분산 효과 확인 필요")

    # 권장사항
    print(f"\n💡 권장사항:")
    if p95_time > 500:
        print("   - 캐시 TTL 조정 고려")
        print("   - PER 확률 파라미터 튜닝")
    if stats['max_rps'] > 100:
        print("   - 높은 부하 상황에서 안정성 확인됨")
    if stampede_ratio > 0.05:
        print("   - Cache Stampede 상황에서 PER 알고리즘 동작 모니터링 권장")

    print("=" * 60)

# 실행 예시 명령어:
# Cache Stampede 시뮬레이션 (100명 동시 사용자)
# locust -f per-test.py --host=http://localhost:8080 -u 100 -r 50 -t 180s
#
# 고강도 테스트 (200명 동시 사용자)
# locust -f per-test.py --host=http://localhost:8080 -u 200 -r 100 -t 300s