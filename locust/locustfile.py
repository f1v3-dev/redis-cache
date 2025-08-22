import random
import threading
import time
from math import floor
from locust import HttpUser, task, between, events

# 서버 호스트와 엔드포인트
HOST = "http://localhost:8080"
ENDPOINT = "/api/books"

# 트래픽 구성(스파이크 유발용)
HOT_KEYS = ["springboot"]      # 스파이크 극대화: 단일 핫키
HOT_RATIO = 0.95               # 95% 트래픽을 핫키에 집중
TAIL_SIZE = 10                 # 롱테일 축소
TAIL_KEYS = [f"tail_{i}" for i in range(TAIL_SIZE)]

# 캐시/측정 파라미터
CACHE_TTL = 5                  # 서버 TTL과 맞춤(초)
EXPIRE_WINDOW = 0.5            # TTL 경계 ±0.5초만 집계(더 날카롭게)
WARMUP_SECONDS = 60            # 첫 60초는 워밍업으로 간주(표시에만 참고)

# 만료 경계 정렬: 테스트 시작 시각을 기준으로 TTL 배수 지점에 경계를 둔다
def next_anchor_since(start_ts, ttl):
    now = time.time()
    if now <= start_ts:
        return start_ts
    # start_ts에서 ttl 간격으로 가장 가까운 과거 경계 찾은 뒤 필요시 한 칸 앞으로
    elapsed = now - start_ts
    steps = int(elapsed // ttl)
    return start_ts + steps * ttl

class RollingStats:
    def __init__(self):
        self.lock = threading.Lock()
        self.response_times = []      # ms(성공만)
        self.inflight = 0
        self.rps = {}                 # {sec: count}
        self.start_ts = time.time()
        self.expire_anchor = next_anchor_since(self.start_ts, CACHE_TTL)
        self.expire_window_hits = 0
        self.total_requests = 0
        self.errors = 0

    def _sec_now(self):
        return int(time.time())

    def request_start(self):
        with self.lock:
            self.inflight += 1

    def request_end(self, rt_ms, ok=True):
        with self.lock:
            self.inflight -= 1
            self.total_requests += 1
            if ok:
                self.response_times.append(rt_ms)
            else:
                self.errors += 1
            s = self._sec_now()
            self.rps[s] = self.rps.get(s, 0) + 1

    def advance_anchor_if_needed(self):
        with self.lock:
            now = time.time()
            # TTL 배수 경계를 초과하면 다음 경계로 이동(여러 스텝 건너뛸 수 있음)
            while now - self.expire_anchor >= CACHE_TTL:
                self.expire_anchor += CACHE_TTL

    def mark_if_in_expire_window(self):
        with self.lock:
            delta = abs(time.time() - self.expire_anchor)
            if delta <= EXPIRE_WINDOW:
                self.expire_window_hits += 1

    def percentile(self, p):
        with self.lock:
            n = len(self.response_times)
            if n == 0:
                return 0.0
            arr = sorted(self.response_times)
        # 선형 보간
        k = (n - 1) * (p / 100.0)
        f = int(floor(k))
        c = min(f + 1, n - 1)
        if f == c:
            return float(arr[int(k)])
        d0 = arr[f] * (c - k)
        d1 = arr[c] * (k - f)
        return float(d0 + d1)

    def summary(self):
        with self.lock:
            n = len(self.response_times)
            if n == 0:
                return {}
            avg = sum(self.response_times) / n
            duration = max(1e-9, time.time() - self.start_ts)
            max_rps = max(self.rps.values()) if self.rps else 0
            return {
                "total_requests": self.total_requests,
                "success_count": n,
                "error_count": self.errors,
                "avg_ms": avg,
                "min_ms": min(self.response_times),
                "max_ms": max(self.response_times),
                "p50_ms": self.percentile(50),
                "p90_ms": self.percentile(90),
                "p95_ms": self.percentile(95),
                "p99_ms": self.percentile(99),
                "inflight_now": self.inflight,
                "expire_window_hits": self.expire_window_hits,
                "max_rps": max_rps,
                "avg_rps": self.total_requests / duration,
                "warmup_s": WARMUP_SECONDS
            }

stats = RollingStats()

class CacheStampedeUser(HttpUser):
    host = HOST
    # think time 더 짧게: 동시성/QPS 상승
    wait_time = between(0.01, 0.04)

    def on_start(self):
        self.user_id = random.randint(1000, 9999)

    @task(1)
    def get_books(self):
        # TTL 경계를 TTL 배수(anchor) 기반으로 갱신
        stats.advance_anchor_if_needed()

        # 95% 핫키, 5% 롱테일
        if random.random() < HOT_RATIO:
            key = random.choice(HOT_KEYS)
        else:
            key = random.choice(TAIL_KEYS)

        params = {"query": key, "page": 1}

        stats.request_start()
        start = time.time()
        try:
            with self.client.get(
                    ENDPOINT,
                    params=params,
                    name="/api/books?q={variable}",
                    catch_response=True
            ) as resp:
                rt_ms = (time.time() - start) * 1000.0
                # 만료 윈도우 집계
                stats.mark_if_in_expire_window()

                if resp.status_code == 200:
                    resp.success()
                    stats.request_end(rt_ms, ok=True)
                    # 너무 잦은 print는 부하기 CPU를 잡아먹으므로 주석 처리 권장
                    # if rt_ms > 1000:
                    #     print(f"⚠️ slow: {rt_ms:.0f}ms key={key}")
                else:
                    resp.failure(f"HTTP {resp.status_code}")
                    stats.request_end(rt_ms, ok=False)
        except Exception as e:
            rt_ms = (time.time() - start) * 1000.0
            stats.request_end(rt_ms, ok=False)
            # print(f"❌ req failed: {e}")

@events.test_start.add_listener
def on_test_start(environment, **kwargs):
    print("=" * 60)
    print("🚀 Spike 유발 집중 테스트 시작 (핫키 95%, TTL 만료 동시성 극대화)")
    print(f"🔥 HOT_RATIO={HOT_RATIO}, HOT_KEYS={HOT_KEYS}, TAIL_SIZE={TAIL_SIZE}")
    print(f"⏰ CACHE_TTL={CACHE_TTL}s, EXPIRE_WINDOW=±{EXPIRE_WINDOW}s, WARMUP={WARMUP_SECONDS}s")
    print(f"🔗 ENDPOINT: {HOST}{ENDPOINT}?query=...&page=1")
    print("🎯 목표: 만료 직전/직후 p95/p99 스파이크를 before에서 크게, after에서 완화 확인")
    print("=" * 60)

@events.test_stop.add_listener
def on_test_stop(environment, **kwargs):
    s = stats.summary()
    if not s:
        print("❌ 통계 없음")
        return

    print("\n" + "=" * 60)
    print("📊 결과 요약")
    print("=" * 60)
    print(f"- 총 요청: {s['total_requests']:,} (성공 {s['success_count']:,}, 실패 {s['error_count']:,})")
    print(f"- RPS: max {s['max_rps']:.1f}, avg {s['avg_rps']:.1f}")
    print(f"- 응답시간(ms): avg {s['avg_ms']:.1f}, min {s['min_ms']:.1f}, max {s['max_ms']:.1f}")
    print(f"  p50 {s['p50_ms']:.1f} | p90 {s['p90_ms']:.1f} | p95 {s['p95_ms']:.1f} | p99 {s['p99_ms']:.1f}")
    print(f"- 만료 윈도우(±{EXPIRE_WINDOW}s) 히트 수: {s['expire_window_hits']:,}")
    print(f"- 워밍업 참고: 처음 {s['warmup_s']}초는 그래프 해석에서 제외 권장")
    print("=" * 60)
