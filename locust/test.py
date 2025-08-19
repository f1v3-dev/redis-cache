from locust import HttpUser, task, between
import random

HOST = "http://localhost:8080"

# 인기 검색어 (높은 비율로 호출됨) - cache stampede 테스트용
popular_queries = [
    'springboot',  # 가장 인기 있는 검색어
    'java',        # 두 번째로 인기 있는 검색어
]

# 일반 검색어 (낮은 비율로 호출됨)
normal_queries = [
    'python',
    'redis',
    'mysql'
]

# 가중치 설정 - 인기 검색어에 높은 가중치 부여
query_weights = {
    'springboot': 40,  # 40% 확률
    'java': 30,        # 30% 확률
    'python': 10,      # 10% 확률
    'redis': 10,       # 10% 확률
    'mysql': 10        # 10% 확률
}

# page list = 1 ~ 4
page_list = list(range(1, 5))

def weighted_random_choice(weights_dict):
    """가중치에 따른 랜덤 선택"""
    choices = list(weights_dict.keys())
    weights = list(weights_dict.values())
    return random.choices(choices, weights=weights, k=1)[0]

class WebsiteUser(HttpUser):
    host = HOST

    @task(8)  # 일반 검색 - 80% 비율
    def search_with_weighted_queries(self):
        """가중치가 적용된 쿼리로 검색 - cache stampede 테스트용"""
        query = weighted_random_choice(query_weights)
        page = random.choice(page_list)
        self.client.get(
            f'/api/books?query={query}&page={page}',
            name=f'weighted-search-{query}')

    @task(2)  # 균등 분포 검색 - 20% 비율
    def search_with_uniform_queries(self):
        """균등하게 분포된 쿼리로 검색"""
        all_queries = list(query_weights.keys())
        query = random.choice(all_queries)
        page = random.choice(page_list)
        self.client.get(
            f'/api/books?query={query}&page={page}',
            name=f'uniform-search-{query}')

    wait_time = between(1, 1)
