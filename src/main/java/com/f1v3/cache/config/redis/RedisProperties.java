package com.f1v3.cache.config.redis;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Redis Properties Class.
 *
 * @author Seungjo, Jeong
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "redis")
public class RedisProperties {

    private Sentinel sentinel;
    private String password;

    @Data
    public static class Sentinel {
        private String master;
        private List<Node> nodes;

        @Data
        public static class Node {
            private String host;
            private int port;
        }

    }
}
