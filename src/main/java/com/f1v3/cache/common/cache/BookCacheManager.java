package com.f1v3.cache.common.cache;

import java.util.Optional;

/**
 * Cache Manager interface for BookSearch.
 *
 * @author Seungjo, Jeong
 */
public interface BookCacheManager<T> {

    // FIXME: 캐시 임계값을 여기서 정의해도 되나?
    long THRESHOLD = 5L;

    Optional<T> getFromCache(String key);

    void incrementCount(String key);

    void addToCache(String key, T value);

    void clear();
}
