package com.assistant.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AdminAuthenticationProviderTest {

    private static final String PASSWORD = "integration-admin-password";

    static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private MutableClock clock;
    private AdminLoginGuard guard;
    private AdminAuthenticationProvider provider;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        AdminProperties properties = new AdminProperties("admin", PASSWORD, 3, 1);
        guard = new AdminLoginGuard(properties, clock);
        provider = new AdminAuthenticationProvider(properties, guard, new BCryptPasswordEncoder(4));
    }

    @Test
    void validCredentialsGrantOnlyAdminAuthority() {
        var result = provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("admin", PASSWORD));

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
    }

    @Test
    void invalidCredentialsAreRejectedAndLockAfterConfiguredAttempts() {
        for (int attempt = 0; attempt < 2; attempt++) {
            assertThatThrownBy(() -> provider.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated("admin", "wrong-password")))
                    .isInstanceOf(BadCredentialsException.class);
        }
        assertThat(guard.isLocked()).isFalse();
        assertThatThrownBy(() -> provider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("other", PASSWORD)))
                .isInstanceOf(LockedException.class);
        assertThat(guard.isLocked()).isTrue();
    }

    @Test
    void successfulLoginResetsFailuresAndLockExpires() {
        assertThatThrownBy(() -> provider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("admin", "wrong-password")))
                .isInstanceOf(BadCredentialsException.class);
        provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("admin", PASSWORD));
        assertThat(guard.isLocked()).isFalse();

        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("admin", "wrong-password"));
            } catch (BadCredentialsException | LockedException ignored) {
                // The third failure itself starts the lockout.
            }
        }
        assertThat(guard.isLocked()).isTrue();
        clock.advance(Duration.ofMinutes(1));
        assertThat(guard.isLocked()).isFalse();
    }

    @Test
    void adminPasswordMustBePresentAndAtLeastTwelveCharacters() {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        assertThat(validator.validate(new AdminProperties("admin", "short", 5, 15))).isNotEmpty();
        assertThat(validator.validate(new AdminProperties("admin", "", 5, 15))).isNotEmpty();
        assertThat(validator.validate(new AdminProperties("admin", PASSWORD, 5, 15))).isEmpty();
    }
}
