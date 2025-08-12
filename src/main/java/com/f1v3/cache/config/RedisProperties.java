package com.f1v3.cache.config;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis Properties Class.
 *
 * @author Seungjo, Jeong
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "redis")
public class RedisProperties {

    private Master master;
    private Slave slave;

    @Data
    public static class Master {
        private String host;
        private int port;
        private String password;
    }

    @Data
    public static class Slave {
        private String host;
        private int port;
        private String password;
    }
}
