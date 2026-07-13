package com.example.forklift_erp.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.boot.SpringApplication;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSafetyValidatorTests {

    @Test
    void environmentPostProcessorRejectsUnsafeProdConfigurationEarly() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("jwt.secret", ProductionSafetyValidator.DEV_JWT_SECRET)
                .withProperty("jwt.expiration", String.valueOf(ProductionSafetyValidator.DEV_JWT_EXPIRATION_MS))
                .withProperty("spring.datasource.password", "")
                .withProperty("forklift.admin.bootstrap.password", ProductionSafetyValidator.DEV_ADMIN_PASSWORD)
                .withProperty("forklift.seed-demo-data.enabled", "true")
                .withProperty("forklift.admin.business-data-reset.enabled", "true")
                .withProperty("forklift.admin.data-restore.enabled", "true");
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> new ProductionSafetyEnvironmentPostProcessor()
                .postProcessEnvironment(environment, new SpringApplication()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsafe production configuration");
    }

    @Test
    void environmentPostProcessorDoesNothingOutsideProd() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("jwt.secret", ProductionSafetyValidator.DEV_JWT_SECRET);

        assertThatCode(() -> new ProductionSafetyEnvironmentPostProcessor()
                .postProcessEnvironment(environment, new SpringApplication()))
                .doesNotThrowAnyException();
    }

    @Test
    void prodProfileRejectsDevelopmentSecretsAndDangerousSwitches() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.profiles.active", "prod")
                .withProperty("jwt.secret", ProductionSafetyValidator.DEV_JWT_SECRET)
                .withProperty("jwt.expiration", String.valueOf(ProductionSafetyValidator.DEV_JWT_EXPIRATION_MS))
                .withProperty("spring.datasource.password", "")
                .withProperty("forklift.admin.bootstrap.password", ProductionSafetyValidator.DEV_ADMIN_PASSWORD)
                .withProperty("forklift.seed-demo-data.enabled", "true")
                .withProperty("forklift.admin.business-data-reset.enabled", "true")
                .withProperty("forklift.admin.data-restore.enabled", "true");
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> new ProductionSafetyValidator(environment).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsafe production configuration")
                .hasMessageContaining("FORKLIFT_ERP_JWT_SECRET")
                .hasMessageContaining("FORKLIFT_ERP_DB_PASSWORD")
                .hasMessageContaining("FORKLIFT_ERP_ADMIN_PASSWORD")
                .hasMessageContaining("FORKLIFT_ERP_SEED_DEMO_DATA")
                .hasMessageContaining("FORKLIFT_ERP_BUSINESS_DATA_RESET_ENABLED")
                .hasMessageContaining("FORKLIFT_ERP_DATA_RESTORE_ENABLED");
    }

    @Test
    void prodProfileAcceptsExplicitSafeConfiguration() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("jwt.secret", "prod-jwt-secret-value-that-is-long-enough-123456")
                .withProperty("jwt.expiration", "86400000")
                .withProperty("spring.datasource.password", "not-the-legacy-password")
                .withProperty("forklift.admin.bootstrap.password", "not-the-default-admin-password")
                .withProperty("forklift.seed-demo-data.enabled", "false")
                .withProperty("forklift.admin.business-data-reset.enabled", "false")
                .withProperty("forklift.admin.data-restore.enabled", "false");
        environment.setActiveProfiles("prod");

        assertThatCode(() -> new ProductionSafetyValidator(environment).validate()).doesNotThrowAnyException();
    }

    @Test
    void prodProfileAcceptsExplicitLongJwtExpiration() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("jwt.secret", "prod-jwt-secret-value-that-is-long-enough-123456")
                .withProperty("jwt.expiration", String.valueOf(ProductionSafetyValidator.DEV_JWT_EXPIRATION_MS))
                .withProperty("FORKLIFT_ERP_JWT_EXPIRATION", String.valueOf(ProductionSafetyValidator.DEV_JWT_EXPIRATION_MS))
                .withProperty("spring.datasource.password", "not-the-legacy-password")
                .withProperty("forklift.admin.bootstrap.password", "not-the-default-admin-password")
                .withProperty("forklift.seed-demo-data.enabled", "false")
                .withProperty("forklift.admin.business-data-reset.enabled", "false")
                .withProperty("forklift.admin.data-restore.enabled", "false");
        environment.setActiveProfiles("prod");

        assertThatCode(() -> new ProductionSafetyValidator(environment).validate()).doesNotThrowAnyException();
    }

    @Test
    void nonProdProfileAllowsDevelopmentDefaults() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("jwt.secret", ProductionSafetyValidator.DEV_JWT_SECRET)
                .withProperty("forklift.admin.bootstrap.password", ProductionSafetyValidator.DEV_ADMIN_PASSWORD);

        assertThatCode(() -> new ProductionSafetyValidator(environment).validate()).doesNotThrowAnyException();
    }
}
