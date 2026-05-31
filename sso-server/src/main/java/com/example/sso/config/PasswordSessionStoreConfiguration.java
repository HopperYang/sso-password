package com.example.sso.config;

import com.example.sso.crypto.InMemoryPasswordSessionStore;
import com.example.sso.crypto.PasswordSessionStore;
import com.example.sso.crypto.RedisPasswordSessionStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class PasswordSessionStoreConfiguration {

    @Bean
    @ConditionalOnProperty(name = "sso.password-session.redis-enabled", havingValue = "false", matchIfMissing = true)
    PasswordSessionStore inMemoryPasswordSessionStore(SsoProperties props) {
        return new InMemoryPasswordSessionStore(props);
    }

    @Bean
    @ConditionalOnProperty(name = "sso.password-session.redis-enabled", havingValue = "true")
    PasswordSessionStore redisPasswordSessionStore(StringRedisTemplate redis, SsoProperties props) {
        return new RedisPasswordSessionStore(redis, props);
    }
}
