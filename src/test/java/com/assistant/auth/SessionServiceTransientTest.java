package com.assistant.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assistant.Fixtures;
import com.assistant.TestClock;
import com.assistant.profile.ProfileRepository;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** Sessions transitoires du smartphone, portee et evenements de fermeture. */
@ExtendWith(MockitoExtension.class)
class SessionServiceTransientTest {

    @Mock
    AppSessionRepository sessions;
    @Mock
    ProfileRepository profiles;
    @Mock
    ApplicationEventPublisher events;

    TestClock clock;
    SessionService service;

    @BeforeEach
    void setUp() {
        clock = new TestClock();
        service = new SessionService(sessions, profiles, Fixtures.properties(), clock, events);
    }

    private AppSession stored(String token, long profileId, SessionSource source, long expiresAtMs) {
        return new AppSession(SessionService.hash(token), profileId, 0L, expiresAtMs, SessionScope.FULL, source);
    }

    @Test
    void fullTransientSessionLastsThirtyMinutesAndIsMarkedAsHandoff() {
        when(profiles.findNameById(5L)).thenReturn(Optional.of("Alice"));

        SessionService.IssuedSession issued = service.openTransientSession(5L, SessionScope.FULL, null);

        ArgumentCaptor<AppSession> captor = ArgumentCaptor.forClass(AppSession.class);
        verify(sessions).save(captor.capture());
        AppSession saved = captor.getValue();
        assertThat(saved.getScope()).isEqualTo(SessionScope.FULL);
        assertThat(saved.getSource()).isEqualTo(SessionSource.HANDOFF);
        assertThat(saved.getId()).isEqualTo(SessionService.hash(issued.token()));
        assertThat(issued.expiresAtMs()).isEqualTo(clock.millis() + Duration.ofMinutes(30).toMillis());
        assertThat(service.transientDurationSeconds(SessionScope.FULL)).isEqualTo(1800);
    }

    @Test
    void weightTransientSessionLastsTenMinutes() {
        when(profiles.findNameById(5L)).thenReturn(Optional.of("Alice"));

        SessionService.IssuedSession issued = service.openTransientSession(5L, SessionScope.WEIGHT, null);

        ArgumentCaptor<AppSession> captor = ArgumentCaptor.forClass(AppSession.class);
        verify(sessions).save(captor.capture());
        assertThat(captor.getValue().getScope()).isEqualTo(SessionScope.WEIGHT);
        assertThat(issued.expiresAtMs()).isEqualTo(clock.millis() + Duration.ofMinutes(10).toMillis());
    }

    @Test
    void transientSessionReplacesThePreviousCookieWithoutSigningTheProfileOut() {
        when(profiles.findNameById(5L)).thenReturn(Optional.of("Alice"));
        when(sessions.findById(SessionService.hash("old"))).thenReturn(Optional.of(stored("old", 5L,
                SessionSource.HANDOFF, clock.millis() + 1000)));

        service.openTransientSession(5L, SessionScope.FULL, "old");

        verify(sessions).deleteById(SessionService.hash("old"));
        verify(events, never()).publishEvent(any(ProfileSignedOutEvent.class));
    }

    @Test
    void transientSessionIsRefusedForTheGuestProfile() {
        when(profiles.findNameById(1L)).thenReturn(Optional.of("Invite"));
        when(profiles.isGuest(1L)).thenReturn(true);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.openTransientSession(1L, SessionScope.FULL, null))
                .isInstanceOf(com.assistant.common.ApiException.class);
        verify(sessions, never()).save(any());
    }

    @Test
    void authenticateExposesTheScopeOfTheSession() {
        AppSession weight = new AppSession(SessionService.hash("tok"), 5L, 0L, clock.millis() + 1000,
                SessionScope.WEIGHT, SessionSource.HANDOFF);
        when(sessions.findById(SessionService.hash("tok"))).thenReturn(Optional.of(weight));

        assertThat(service.authenticate("tok")).hasValueSatisfying(s -> assertThat(s.scope()).isEqualTo(SessionScope.WEIGHT));
    }

    @Test
    void heartbeatNeverExtendsASmartphoneSession() {
        long expiry = clock.millis() + Duration.ofMinutes(30).toMillis();
        when(sessions.findById(SessionService.hash("tok"))).thenReturn(Optional.of(stored("tok", 5L,
                SessionSource.HANDOFF, expiry)));
        when(profiles.findNameById(5L)).thenReturn(Optional.of("Alice"));
        clock.advance(Duration.ofMinutes(5));

        Optional<SessionService.IssuedSession> issued = service.heartbeat("tok");

        assertThat(issued).hasValueSatisfying(s -> assertThat(s.expiresAtMs()).isEqualTo(expiry));
        verify(sessions, never()).updateExpiry(any(), anyLong());
    }

    @Test
    void switchingProfileOnTheSameDevicePublishesTheSignOutOfThePreviousOne() {
        when(profiles.findNameById(6L)).thenReturn(Optional.of("Bob"));
        when(sessions.findById(SessionService.hash("old"))).thenReturn(Optional.of(stored("old", 5L,
                SessionSource.FACE, clock.millis() + 1000)));

        service.login(6L, "old");

        verify(events).publishEvent(new ProfileSignedOutEvent(5L));
    }

    @Test
    void renewingTheSameProfileDoesNotSignItOut() {
        when(profiles.findNameById(5L)).thenReturn(Optional.of("Alice"));
        when(sessions.findById(SessionService.hash("old"))).thenReturn(Optional.of(stored("old", 5L,
                SessionSource.FACE, clock.millis() + 1000)));

        service.login(5L, "old");

        verify(events, never()).publishEvent(any(ProfileSignedOutEvent.class));
    }

    @Test
    void logoutPublishesTheSignOutOfTheProfile() {
        when(sessions.findById(SessionService.hash("tok"))).thenReturn(Optional.of(stored("tok", 5L,
                SessionSource.FACE, clock.millis() + 1000)));

        service.logout("tok");

        verify(sessions).deleteById(SessionService.hash("tok"));
        verify(events).publishEvent(new ProfileSignedOutEvent(5L));
    }

    @Test
    void sessionsCreatedWithoutScopeDefaultToFullFromTheFace() {
        AppSession legacy = new AppSession("id", 5L, 0L, 1L);
        assertThat(legacy.getScope()).isEqualTo(SessionScope.FULL);
        assertThat(legacy.getSource()).isEqualTo(SessionSource.FACE);
    }

    @Test
    void invalidateAllPublishesAGlobalInvalidation() {
        service.invalidateAll();
        verify(events).publishEvent(new SessionsInvalidatedEvent());
    }
}
