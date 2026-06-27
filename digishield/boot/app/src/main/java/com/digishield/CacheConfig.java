package com.digishield;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

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
}
