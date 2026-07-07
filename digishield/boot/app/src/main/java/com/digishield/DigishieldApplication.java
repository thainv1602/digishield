package com.digishield;

import java.util.Locale;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.modulith.Modulithic;

/**
 * DigiShield application entry point.
 *
 * <p>This is the single Spring Boot bootstrap class for the whole modular
 * monolith. All business modules and shared libraries are wired in as
 * dependencies so Spring Modulith can detect and verify the module structure
 * rooted at this package ({@code com.digishield}).
 */
@Modulithic(systemName = "DigiShield")
@SpringBootApplication
public class DigishieldApplication {

    public static void main(String[] args) {
        // Vietnamese is the product's default language: pin it as the fallback for
        // threads with no request locale (async event listeners, scheduled jobs) so
        // backend-produced text doesn't drift to the JVM's default locale.
        LocaleContextHolder.setDefaultLocale(Locale.forLanguageTag("vi"));
        SpringApplication.run(DigishieldApplication.class, args);
    }

    /**
     * In the {@code flyway} profile the app runs as a one-shot migration job:
     * Flyway has already applied migrations during context startup, so close the
     * context and exit instead of leaving the web server running forever.
     */
    @Bean
    @Profile("flyway")
    ApplicationRunner flywayMigrateAndExit(ConfigurableApplicationContext context) {
        return args -> System.exit(SpringApplication.exit(context, () -> 0));
    }
}
