package com.assistant.admin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class AdminStartupConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AdminConfiguration.class)
            .withPropertyValues(
                    "assistant.admin.username=admin",
                    "assistant.admin.max-failed-attempts=5",
                    "assistant.admin.lockout-minutes=15");

    @Test
    void missingAdminPasswordPreventsStartup() {
        contextRunner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void validAdminPasswordAllowsStartup() {
        contextRunner.withPropertyValues("assistant.admin.password=local-admin-password")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(AdminProperties.class).username()).isEqualTo("admin");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AdminProperties.class)
    static class AdminConfiguration {

        @Bean
        LocalValidatorFactoryBean validator() {
            return new LocalValidatorFactoryBean();
        }
    }
}
