package com.assistant.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.assistant.Fixtures;
import com.assistant.common.ApiException;
import com.assistant.profile.ProfileRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class SessionServiceGuestTest {

    private final AppSessionRepository sessions = mock(AppSessionRepository.class);
    private final ProfileRepository profiles = mock(ProfileRepository.class);
    private final SessionService service = new SessionService(sessions, profiles, Fixtures.properties(),
            Clock.fixed(Instant.parse("2026-10-30T10:00:00Z"), ZoneOffset.UTC));

    @Test
    void loginIsRefusedForTheGuestProfileWithoutCreatingASession() {
        when(profiles.findNameById(9L)).thenReturn(Optional.of("Invité"));
        when(profiles.isGuest(9L)).thenReturn(true);

        assertThatThrownBy(() -> service.login(9L, null)).isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(sessions);
    }
}
