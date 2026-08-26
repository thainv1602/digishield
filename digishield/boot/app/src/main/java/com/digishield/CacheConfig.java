package com.digishield;

import com.digishield.analytics.api.AnalyticsService;
import com.digishield.analytics.api.dto.DashboardDto;
import com.digishield.shared.tenantcontext.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

/**
 * Enables annotation-driven caching (@Cacheable, ...). The backing CacheManager
 * is auto-configured from {@code spring.cache.type}: Redis in prod/default,
 * none (no-op) in the dev profile and in slice tests.
 *
 * <p>Kept as a separate {@code @Configuration} (not on the application class) so
 * web slice tests ({@code @WebMvcTest}) — which don't load arbitrary
 * configuration — aren't forced to provide a CacheManager.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** How long a cached dashboard may lag the database. */
    public static final Duration DASHBOARD_TTL = Duration.ofSeconds(60);

    /**
     * Keys every cached entry by tenant <em>and</em> request locale.
     *
     * <p>The tenant half is not optional: {@link TenantContext} is a
     * ThreadLocal, so a method's own arguments say nothing about whose data is
     * being read, and a key without it would serve one tenant the other's
     * numbers. The locale half matters because the cached values carry
     * translated labels — keyed by tenant alone, whoever loaded the dashboard
     * first would decide everyone else's language.
     */
    @Bean
    KeyGenerator tenantLocaleKeyGenerator() {
        return (target, method, params) -> {
            StringBuilder key = new StringBuilder(TenantContext.requireUuid().toString())
                    .append(':').append(LocaleContextHolder.getLocale().toLanguageTag())
                    .append(':').append(method.getName());
            for (Object param : params) {
                key.append(':').append(param);
            }
            return key.toString();
        };
    }

    /**
     * Serialises cached dashboards as JSON bound to {@link DashboardDto} rather
     * than with Java serialisation or polymorphic typing. A concrete target type
     * means the DTO needs no {@code Serializable}, entries stay readable with
     * {@code redis-cli}, and nothing in the cache can name the class to
     * instantiate on the way back — which is how polymorphic deserialisation
     * turns a writable cache into remote code execution.
     */
    @Bean
    RedisCacheManagerBuilderCustomizer digishieldCacheCustomizer(ObjectMapper objectMapper) {
        RedisCacheConfiguration dashboard = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(DASHBOARD_TTL)
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new Jackson2JsonRedisSerializer<>(objectMapper, DashboardDto.class)));
        return builder -> builder.withCacheConfiguration(AnalyticsService.DASHBOARD_CACHE, dashboard);
    }
}
