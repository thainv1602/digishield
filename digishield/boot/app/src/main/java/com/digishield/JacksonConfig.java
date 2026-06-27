package com.digishield;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes a shared {@link ObjectMapper} bean for injection. Spring Boot 4 no
 * longer registers a general-purpose ObjectMapper bean by default, but several
 * modules (tenancy, learning) inject one to (de)serialize JSON columns such as
 * {@code rule_json} and tenant settings.
 */
@Configuration
class JacksonConfig {

    @Bean
    @ConditionalOnMissingBean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
