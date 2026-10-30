package com.assistant.guest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assistant.apps.ProfileAppService;
import com.assistant.profile.Profile;
import com.assistant.profile.ProfileRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class GuestServiceTest {

    private final GuestSettingRepository settings = mock(GuestSettingRepository.class);
    private final ProfileRepository profiles = mock(ProfileRepository.class);
    private final ProfileAppService profileApps = mock(ProfileAppService.class);
    private final GuestService service = new GuestService(settings, profiles, profileApps,
            Clock.fixed(Instant.parse("2026-10-30T10:00:00Z"), ZoneOffset.UTC));

    private void savingAssignsId(long id) {
        when(profiles.save(any(Profile.class))).thenAnswer(invocation -> {
            Profile profile = invocation.getArgument(0);
            ReflectionTestUtils.setField(profile, "id", id);
            return profile;
        });
    }

    @Test
    void createsTheGuestWithWeatherTopLeftAndNewsTopRight() {
        when(settings.findById(1L)).thenReturn(Optional.empty());
        savingAssignsId(9L);

        long id = service.ensureGuest();

        assertThat(id).isEqualTo(9L);
        ArgumentCaptor<Profile> profile = ArgumentCaptor.forClass(Profile.class);
        verify(profiles).save(profile.capture());
        assertThat(profile.getValue().getName()).isEqualTo("Invité");
        assertThat(profile.getValue().getAvatar()).isNull();
        ArgumentCaptor<GuestSetting> setting = ArgumentCaptor.forClass(GuestSetting.class);
        verify(settings).save(setting.capture());
        assertThat(setting.getValue().getId()).isEqualTo(1L);
        assertThat(setting.getValue().getProfileId()).isEqualTo(9L);
        verify(profileApps).replaceAll(9L, List.of(
                new ProfileAppService.Selection("weather", true, 1, "{}"),
                new ProfileAppService.Selection("news", true, 3, "{}")));
    }

    @Test
    void doesNothingWhenTheGuestAlreadyExists() {
        when(settings.findById(1L)).thenReturn(Optional.of(new GuestSetting(1L, 5L)));
        when(profiles.existsById(5L)).thenReturn(true);

        assertThat(service.ensureGuest()).isEqualTo(5L);

        verify(profiles, never()).save(any());
        verify(settings, never()).save(any());
        verify(profileApps, never()).replaceAll(any(Long.class), any());
    }

    @Test
    void recreatesTheGuestWhenItsProfileIsGone() {
        GuestSetting dangling = new GuestSetting(1L, 5L);
        when(settings.findById(1L)).thenReturn(Optional.of(dangling));
        when(profiles.existsById(5L)).thenReturn(false);
        savingAssignsId(12L);

        assertThat(service.ensureGuest()).isEqualTo(12L);

        assertThat(dangling.getProfileId()).isEqualTo(12L);
        verify(settings).save(dangling);
        verify(profileApps).replaceAll(eq(12L), any());
    }

    @Test
    void guestProfileIdIsEmptyWithoutSettingOrProfile() {
        when(settings.findById(1L)).thenReturn(Optional.empty());
        assertThat(service.guestProfileId()).isEmpty();

        when(settings.findById(1L)).thenReturn(Optional.of(new GuestSetting(1L, 5L)));
        when(profiles.existsById(5L)).thenReturn(false);
        assertThat(service.guestProfileId()).isEmpty();

        when(profiles.existsById(5L)).thenReturn(true);
        assertThat(service.guestProfileId()).contains(5L);
    }
}
