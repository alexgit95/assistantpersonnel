package com.assistant.kiosk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assistant.Fixtures;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class KioskStartupCheckTest {

    private static MockEnvironment environment(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return environment;
    }

    @Test
    void productionWithoutKeyPreventsStartup() {
        assertThatThrownBy(() -> new KioskStartupCheck(Fixtures.withKioskKey(""), environment("prod")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ASSISTANT_KIOSK_KEY");
    }

    @Test
    void productionWithBlankKeyPreventsStartup() {
        assertThatThrownBy(() -> new KioskStartupCheck(Fixtures.withKioskKey("   "), environment("prod")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void productionWithKeyStarts() {
        assertThatCode(() -> new KioskStartupCheck(Fixtures.withKioskKey("secret"), environment("prod")))
                .doesNotThrowAnyException();
    }

    @Test
    void localWithoutKeyStartsWithWarning() {
        assertThatCode(() -> new KioskStartupCheck(Fixtures.withKioskKey(""), environment("local")))
                .doesNotThrowAnyException();
    }

    @Test
    void localWithKeyStarts() {
        assertThat(new KioskStartupCheck(Fixtures.withKioskKey("secret"), environment("local"))).isNotNull();
    }
}
