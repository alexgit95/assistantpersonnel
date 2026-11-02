package com.assistant.kiosk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assistant.Fixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

class KioskStartupCheckTest {

    private static MockEnvironment environment(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return environment;
    }

    @ParameterizedTest
    @ValueSource(strings = {"onprem", "remote", "prod"})
    void strictModeWithoutKeyPreventsStartup(String profile) {
        assertThatThrownBy(() -> new KioskStartupCheck(Fixtures.withKioskKey(""), environment(profile)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ASSISTANT_KIOSK_KEY");
    }

    @Test
    void strictModeWithBlankKeyPreventsStartup() {
        assertThatThrownBy(() -> new KioskStartupCheck(Fixtures.withKioskKey("   "), environment("onprem")))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"onprem", "remote", "prod"})
    void strictModeWithKeyStarts(String profile) {
        assertThatCode(() -> new KioskStartupCheck(Fixtures.withKioskKey("secret"), environment(profile)))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "local"})
    void devWithoutKeyStartsWithWarning(String profile) {
        assertThatCode(() -> new KioskStartupCheck(Fixtures.withKioskKey(""), environment(profile)))
                .doesNotThrowAnyException();
    }

    @Test
    void devWithKeyStarts() {
        assertThat(new KioskStartupCheck(Fixtures.withKioskKey("secret"), environment("dev"))).isNotNull();
    }
}
