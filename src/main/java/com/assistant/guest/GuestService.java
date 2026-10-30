package com.assistant.guest;

import com.assistant.apps.ProfileAppService;
import com.assistant.profile.Profile;
import com.assistant.profile.ProfileRepository;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Profil invite global : affiche ses widgets quand aucune session faciale n'est ouverte. */
@Service
public class GuestService {

    static final long SETTING_ID = 1L;
    static final String NAME = "Invité";
    static final int WEATHER_SLOT = 1;
    static final int NEWS_SLOT = 3;

    private final GuestSettingRepository settings;
    private final ProfileRepository profiles;
    private final ProfileAppService profileApps;
    private final Clock clock;

    public GuestService(GuestSettingRepository settings, ProfileRepository profiles,
                        ProfileAppService profileApps, Clock clock) {
        this.settings = settings;
        this.profiles = profiles;
        this.profileApps = profileApps;
        this.clock = clock;
    }

    /** Lu a chaque requete anonyme : la table ne contient qu'une ligne. */
    @Transactional(readOnly = true)
    public Optional<Long> guestProfileId() {
        return settings.findById(SETTING_ID).map(GuestSetting::getProfileId).filter(profiles::existsById);
    }

    /** Cree l'invite et ses widgets par defaut s'il manque ; sans effet s'il existe deja. */
    @Transactional
    public long ensureGuest() {
        Optional<Long> existing = guestProfileId();
        if (existing.isPresent()) {
            return existing.get();
        }
        Profile profile = profiles.save(new Profile(NAME, null, clock.millis()));
        GuestSetting setting = settings.findById(SETTING_ID).orElseGet(() -> new GuestSetting(SETTING_ID, null));
        setting.setProfileId(profile.getId());
        settings.save(setting);
        profileApps.replaceAll(profile.getId(), List.of(
                new ProfileAppService.Selection("weather", true, WEATHER_SLOT, "{}"),
                new ProfileAppService.Selection("news", true, NEWS_SLOT, "{}")));
        return profile.getId();
    }
}
