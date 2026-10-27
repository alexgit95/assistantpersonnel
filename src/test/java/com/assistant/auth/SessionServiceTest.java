package com.assistant.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assistant.Fixtures;
import com.assistant.common.ApiException;
import com.assistant.profile.ProfileDeletedEvent;
import com.assistant.profile.ProfileRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T10:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
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

    @Mock
    AppSessionRepository sessions;
    @Mock
    ProfileRepository profiles;

    MutableClock clock;
    SessionService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        service = new SessionService(sessions, profiles, Fixtures.properties(), clock);
    }

    private AppSession stored(String token, long expiresAtMs) {
        return new AppSession(SessionService.hash(token), 5L, 0L, expiresAtMs);
    }

    @Test
    void loginStoresOnlyTheHashOfTheTokenWithSlidingExpiry() {
        when(profiles.findNameById(5L)).thenReturn(Optional.of("Alice"));

        SessionService.IssuedSession issued = service.login(5L, null);

        ArgumentCaptor<AppSession> captor = ArgumentCaptor.forClass(AppSession.class);
        verify(sessions).save(captor.capture());
        AppSession saved = captor.getValue();
        assertThat(saved.getId()).isNotEqualTo(issued.token()).hasSize(64);
        assertThat(saved.getId()).isEqualTo(SessionService.hash(issued.token()));
        assertThat(saved.getProfileId()).isEqualTo(5L);
        assertThat(issued.expiresAtMs()).isEqualTo(clock.millis() + Duration.ofMinutes(10).toMillis());
        assertThat(issued.profileName()).isEqualTo("Alice");
        verify(sessions, never()).deleteById(any());
    }

    @Test
    void loginGeneratesDistinctTokens() {
        when(profiles.findNameById(5L)).thenReturn(Optional.of("Alice"));
        assertThat(service.login(5L, null).token()).isNotEqualTo(service.login(5L, null).token());
    }

    @Test
    void loginReplacesThePreviousSessionOfTheDevice() {
        when(profiles.findNameById(6L)).thenReturn(Optional.of("Bob"));
        service.login(6L, "old-token");
        verify(sessions).deleteById(SessionService.hash("old-token"));
    }

    @Test
    void loginUnknownProfileIsNotFound() {
        when(profiles.findNameById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.login(9L, null)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(sessions, never()).save(any());
    }

    @Test
    void authenticateAcceptsAValidSession() {
        when(sessions.findById(SessionService.hash("tok"))).thenReturn(Optional.of(stored("tok", clock.millis() + 1000)));
        assertThat(service.authenticate("tok")).hasValueSatisfying(s -> assertThat(s.profileId()).isEqualTo(5L));
    }

    @Test
    void authenticateRejectsExpiredUnknownAndMissingTokens() {
        when(sessions.findById(SessionService.hash("old"))).thenReturn(Optional.of(stored("old", clock.millis())));
        when(sessions.findById(SessionService.hash("none"))).thenReturn(Optional.empty());

        assertThat(service.authenticate("old")).isEmpty();
        assertThat(service.authenticate("none")).isEmpty();
        assertThat(service.authenticate(null)).isEmpty();
        assertThat(service.authenticate(" ")).isEmpty();
    }

    @Test
    void heartbeatExtendsExpiryFromNow() {
        long initialExpiry = clock.millis() + Duration.ofMinutes(10).toMillis();
        when(sessions.findById(SessionService.hash("tok"))).thenReturn(Optional.of(stored("tok", initialExpiry)));
        when(profiles.findNameById(5L)).thenReturn(Optional.of("Alice"));
        clock.advance(Duration.ofMinutes(4));

        Optional<SessionService.IssuedSession> issued = service.heartbeat("tok");

        long expected = clock.millis() + Duration.ofMinutes(10).toMillis();
        assertThat(issued).hasValueSatisfying(s -> assertThat(s.expiresAtMs()).isEqualTo(expected));
        verify(sessions).updateExpiry(SessionService.hash("tok"), expected);
    }

    @Test
    void heartbeatOnExpiredSessionDoesNothing() {
        when(sessions.findById(SessionService.hash("tok"))).thenReturn(Optional.of(stored("tok", clock.millis() - 1)));
        assertThat(service.heartbeat("tok")).isEmpty();
        verify(sessions, never()).updateExpiry(any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void sessionExpiresTenMinutesAfterTheLastHeartbeat() {
        long expiry = clock.millis() + Duration.ofMinutes(10).toMillis();
        when(sessions.findById(SessionService.hash("tok"))).thenReturn(Optional.of(stored("tok", expiry)));

        clock.advance(Duration.ofMinutes(9));
        assertThat(service.authenticate("tok")).isPresent();
        clock.advance(Duration.ofMinutes(1));
        assertThat(service.authenticate("tok")).isEmpty();
    }

    @Test
    void logoutDeletesTheSessionAndIgnoresEmptyTokens() {
        service.logout("tok");
        verify(sessions).deleteById(SessionService.hash("tok"));
        service.logout(null);
        service.logout("");
        verify(sessions, org.mockito.Mockito.times(1)).deleteById(any());
    }

    @Test
    void purgeDeletesExpiredSessions() {
        when(sessions.deleteExpired(clock.millis())).thenReturn(3);
        assertThat(service.purgeExpired()).isEqualTo(3);
    }

    @Test
    void deletedProfileInvalidatesItsSessions() {
        service.onProfileDeleted(new ProfileDeletedEvent(5L));
        verify(sessions).deleteByProfileId(5L);
    }

    @Test
    void invalidateAllClearsEverySession() {
        service.invalidateAll();
        verify(sessions).deleteAllInBatch();
    }

    @Test
    void remainingSecondsNeverGoesNegative() {
        assertThat(service.remainingSeconds(clock.millis() + 1500)).isEqualTo(2);
        assertThat(service.remainingSeconds(clock.millis() - 5000)).isZero();
        assertThat(service.durationSeconds()).isEqualTo(600);
    }
}
