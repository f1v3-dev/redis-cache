package com.f1v3.cache.common.cache.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "cache.per")
public class PerCacheProperties {

    private double beta = 1.0;
    private long defaultTtl = 3600;
    private String deltaKeySuffix = ":delta";
}
