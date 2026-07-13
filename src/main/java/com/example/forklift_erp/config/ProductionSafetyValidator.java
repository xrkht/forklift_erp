package com.example.forklift_erp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProductionSafetyValidator implements ApplicationRunner {
    static final String DEV_JWT_SECRET = "forklift-erp-development-jwt-secret-change-me-32-bytes-minimum";
    static final String DEV_ADMIN_PASSWORD = "admin123";
    static final long DEV_JWT_EXPIRATION_MS = 31_536_000_000L;
    static final long MAX_DEFAULT_PROD_JWT_EXPIRATION_MS = 30L * 24 * 60 * 60 * 1000;

    private final Environment environment;

    public ProductionSafetyValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        validate();
    }

    void validate() {
        if (!isProdProfile()) {
            warnForNonProductionDefaults();
            return;
        }

        List<String> failures = new ArrayList<>();
        requireSecret(failures, "FORKLIFT_ERP_JWT_SECRET", "jwt.secret", DEV_JWT_SECRET);
        requireJwtExpiration(failures);
        requireSecret(failures, "FORKLIFT_ERP_DB_PASSWORD", "spring.datasource.password", null);
        requireSecret(failures, "FORKLIFT_ERP_ADMIN_PASSWORD", "forklift.admin.bootstrap.password", DEV_ADMIN_PASSWORD);
        requireDisabled(failures, "FORKLIFT_ERP_SEED_DEMO_DATA", "forklift.seed-demo-data.enabled");
        requireDisabled(failures, "FORKLIFT_ERP_BUSINESS_DATA_RESET_ENABLED", "forklift.admin.business-data-reset.enabled");
        requireDisabled(failures, "FORKLIFT_ERP_DATA_RESTORE_ENABLED", "forklift.admin.data-restore.enabled");

        if (!failures.isEmpty()) {
            throw new IllegalStateException("Unsafe production configuration:\n- " + String.join("\n- ", failures));
        }
    }

    private boolean isProdProfile() {
        return Arrays.stream(environment.getActiveProfiles()).anyMatch("prod"::equalsIgnoreCase);
    }

    private void warnForNonProductionDefaults() {
        if (DEV_JWT_SECRET.equals(property("jwt.secret"))) {
            log.warn("Using the development JWT secret. Set FORKLIFT_ERP_JWT_SECRET before running shared environments.");
        }
        if (DEV_JWT_EXPIRATION_MS == parseLong(property("jwt.expiration"), 0L)) {
            log.warn("Using the development JWT expiration. Set FORKLIFT_ERP_JWT_EXPIRATION before running shared environments.");
        }
        if (DEV_ADMIN_PASSWORD.equals(property("forklift.admin.bootstrap.password"))) {
            log.warn("Using the development bootstrap admin password. Set FORKLIFT_ERP_ADMIN_PASSWORD outside local development.");
        }
    }

    private void requireSecret(List<String> failures, String envName, String propertyName, String disallowedValue) {
        String value = property(propertyName);
        if (isBlankOrPlaceholder(value)) {
            failures.add(envName + " must be configured");
            return;
        }
        if (disallowedValue != null && disallowedValue.equals(value)) {
            failures.add(envName + " must not use the checked-in development/default value");
            return;
        }
        if ("jwt.secret".equals(propertyName) && value.getBytes(StandardCharsets.UTF_8).length < 32) {
            failures.add(envName + " must be at least 32 bytes for HS256 signing");
        }
    }

    private void requireDisabled(List<String> failures, String envName, String propertyName) {
        if (Boolean.parseBoolean(property(propertyName))) {
            failures.add(envName + " must be false in prod");
        }
    }

    private void requireJwtExpiration(List<String> failures) {
        String value = property("jwt.expiration");
        if (isBlankOrPlaceholder(value)) {
            failures.add("FORKLIFT_ERP_JWT_EXPIRATION must be configured or use the prod default");
            return;
        }
        long expiration;
        try {
            expiration = Long.parseLong(value);
        } catch (NumberFormatException ex) {
            failures.add("FORKLIFT_ERP_JWT_EXPIRATION must be a millisecond value");
            return;
        }
        if (expiration <= 0) {
            failures.add("FORKLIFT_ERP_JWT_EXPIRATION must be greater than zero");
            return;
        }
        boolean explicitEnvOverride = !isBlankOrPlaceholder(property("FORKLIFT_ERP_JWT_EXPIRATION"));
        if (!explicitEnvOverride && expiration > MAX_DEFAULT_PROD_JWT_EXPIRATION_MS) {
            failures.add("FORKLIFT_ERP_JWT_EXPIRATION must be explicit for expirations longer than 30 days");
        }
    }

    private boolean isBlankOrPlaceholder(String value) {
        return value == null || value.isBlank() || value.contains("${");
    }

    private String property(String name) {
        try {
            return environment.getProperty(name);
        } catch (IllegalArgumentException unresolvedPlaceholder) {
            return null;
        }
    }

    private long parseLong(String value, long fallback) {
        try {
            return value == null ? fallback : Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
