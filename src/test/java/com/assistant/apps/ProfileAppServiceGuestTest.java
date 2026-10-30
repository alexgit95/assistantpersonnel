package com.assistant.apps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.assistant.common.ApiException;
import com.assistant.profile.ProfileAppRepository;
import com.assistant.profile.ProfileRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ProfileAppServiceGuestTest {

    private static AssistantApp app(String key, boolean guestAllowed, boolean defaultEnabled) {
        return new AssistantApp() {
            @Override
            public String key() {
                return key;
            }

            @Override
            public String title() {
                return key;
            }

            @Override
            public String description() {
                return "";
            }

            @Override
            public String fragmentTemplate() {
                return "fragments/widgets/" + key + " :: content";
            }

            @Override
            public boolean guestAllowed() {
                return guestAllowed;
            }

            @Override
            public boolean defaultEnabled() {
                return defaultEnabled;
            }

            @Override
            public int defaultPosition() {
                return 0;
            }
        };
    }

    private final ProfileAppRepository stored = mock(ProfileAppRepository.class);
    private final ProfileAppService service = new ProfileAppService(mock(ProfileRepository.class), stored,
            new AssistantAppRegistry(List.of(app("weather", true, false), app("weight", false, true))),
            new ObjectMapper());

    @Test
    void anAppEnabledByDefaultForProfilesIsNeverListedForTheGuest() {
        when(stored.findByProfile_IdOrderByPositionAscAppKeyAsc(5L)).thenReturn(List.of());

        assertThat(service.listForProfile(5L)).extracting(ProfileAppService.AppPreference::key)
                .contains("weight");
        assertThat(service.listForGuest(5L)).isEmpty();
    }

    @Test
    void requireEnabledForGuestRefusesNonWhitelistedApplications() {
        assertThatThrownBy(() -> service.requireEnabledForGuest(5L, "weight"))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getStatus().value()).isEqualTo(404));
    }

    @Test
    void requireEnabledForGuestRefusesAnUnknownApplication() {
        assertThatThrownBy(() -> service.requireEnabledForGuest(5L, "inconnue")).isInstanceOf(ApiException.class);
    }
}
