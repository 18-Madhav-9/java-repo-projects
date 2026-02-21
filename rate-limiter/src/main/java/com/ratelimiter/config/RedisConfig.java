package com.ratelimiter.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Spring Data Redis configuration — only active when {@code rate-limit.store=REDIS}.
 *
 * <p>Provides a {@link StringRedisTemplate} bean configured with the standard
 * Spring Boot Redis auto-configuration (host, port, timeout from
 * {@code spring.data.redis.*} properties).</p>
 *
 * <p>Lettuce is the default client; no additional configuration needed for
 * a single-node Redis setup. For clustering, add
 * {@code spring.data.redis.cluster.*} properties.</p>
 */
@Configuration
@ConditionalOnProperty(name = "rate-limit.store", havingValue = "REDIS")
public class RedisConfig {

    /**
     * Creates a {@link StringRedisTemplate} that uses the auto-configured
     * {@link RedisConnectionFactory} (Lettuce by default).
     *
     * <p>Using {@code StringRedisTemplate} because {@link com.ratelimiter.store.RedisStore}
     * passes all values as strings to Lua scripts — no Java serialization is needed.</p>
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
