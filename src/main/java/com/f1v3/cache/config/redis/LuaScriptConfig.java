package com.f1v3.cache.config.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import java.util.List;

/**
 * Redis Lua Script Configuration for Cache Stampede Prevention.
 *
 * @author Seungjo, Jeong
 */
@Configuration
public class LuaScriptConfig {

    @Bean
    public DefaultRedisScript<List> cacheGetRedisScript() {
        DefaultRedisScript<List> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/per_cache_get.lua")));
        redisScript.setResultType(List.class);
        return redisScript;
    }

    @Bean
    public DefaultRedisScript<String> cacheSetRedisScript() {
        DefaultRedisScript<String> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/per_cache_set.lua")));
        redisScript.setResultType(String.class);
        return redisScript;
    }

    @Bean
    public DefaultRedisScript<Long> unlockScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/unlock.lua")));
        script.setResultType(Long.class);
        return script;
    }

}
