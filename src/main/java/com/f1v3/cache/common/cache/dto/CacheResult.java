package com.f1v3.cache.common.cache.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CacheResult<T> {
    private final T data;
    private final Integer delta;
    private final Long remainingTtl;
    private final boolean cacheHit;
}
