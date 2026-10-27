package com.assistant.apps;

import com.assistant.common.ApiException;
import com.assistant.profile.Profile;
import com.assistant.profile.ProfileApp;
import com.assistant.profile.ProfileAppRepository;
import com.assistant.profile.ProfileRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileAppService {

    static final int MAX_CONFIG_LENGTH = 10_000;
    static final int UNASSIGNED_SLOT = -1;
    static final int SLOT_COUNT = 5;

    private final ProfileRepository profiles;
    private final ProfileAppRepository profileApps;
    private final AssistantAppRegistry registry;
    private final ObjectMapper objectMapper;

    public ProfileAppService(ProfileRepository profiles, ProfileAppRepository profileApps,
                             AssistantAppRegistry registry, ObjectMapper objectMapper) {
        this.profiles = profiles;
        this.profileApps = profileApps;
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<AppPreference> listForProfile(long profileId) {
        List<ProfileApp> stored = profileApps.findByProfile_IdOrderByPositionAscAppKeyAsc(profileId);
        var byKey = new java.util.HashMap<String, ProfileApp>();
        stored.forEach(preference -> byKey.put(preference.getAppKey(), preference));
        List<AssistantApp> definitions = registry.all();
        var assignedSlots = normalizeStoredSlots(stored);
        Set<Integer> occupiedSlots = new HashSet<>(assignedSlots.values());
        List<AppPreference> result = new ArrayList<>(definitions.size());
        for (AssistantApp definition : definitions) {
            ProfileApp preference = byKey.get(definition.key());
            boolean enabled;
            int position;
            if (preference != null) {
                enabled = assignedSlots.containsKey(definition.key());
                position = enabled ? assignedSlots.get(definition.key()) : UNASSIGNED_SLOT;
            } else if (definition.defaultEnabled()) {
                position = reserveDefaultSlot(definition.defaultPosition(), occupiedSlots);
                enabled = position != UNASSIGNED_SLOT;
            } else {
                enabled = false;
                position = UNASSIGNED_SLOT;
            }
            result.add(new AppPreference(definition.key(), definition.title(), definition.description(),
                    definition.fragmentTemplate(), definition.widgetModule(), enabled, position,
                    preference == null ? definition.defaultConfigJson() : preference.getConfigJson()));
        }
        return result.stream().sorted(java.util.Comparator.comparingInt(AppPreference::position)
                .thenComparing(AppPreference::key)).toList();
    }

    @Transactional(readOnly = true)
    public EnabledApp requireEnabled(long profileId, String appKey) {
        AssistantApp definition = registry.require(appKey);
        AppPreference preference = listForProfile(profileId).stream()
                .filter(app -> app.key().equals(appKey))
                .findFirst().orElseThrow(ProfileAppService::appDisabled);
        if (!preference.enabled()) {
            throw appDisabled();
        }
        return new EnabledApp(definition, preference.position(), preference.configJson());
    }

    @Transactional
    public String updateConfiguration(long profileId, String appKey, String configJson) {
        AssistantApp definition = registry.require(appKey);
        String validated = validateConfig(configJson, definition.defaultConfigJson());
        Profile profile = profiles.findById(profileId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "Profil introuvable."));
        ProfileApp preference = profileApps.findByProfile_IdAndAppKey(profileId, appKey).orElse(null);
        if (preference == null) {
            profile.addApp(new ProfileApp(profile, appKey, UNASSIGNED_SLOT, false, validated));
        } else {
            preference.setConfigJson(validated);
        }
        return validated;
    }

    @Transactional
    public List<AppPreference> replaceAll(long profileId, List<Selection> selections) {
        Profile profile = profiles.findById(profileId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "Profil introuvable."));
        if (selections.size() > 50) {
            throw invalid("Au maximum 50 applications peuvent être configurées.");
        }

        Set<String> keys = new HashSet<>();
        Set<Integer> positions = new HashSet<>();
        List<ValidatedSelection> validated = new ArrayList<>(selections.size());
        for (Selection selection : selections) {
            AssistantApp definition;
            try {
                definition = registry.require(selection.key());
            } catch (ApiException ex) {
                throw invalid("Application inconnue : " + selection.key());
            }
            if (!keys.add(selection.key())) {
                throw invalid("Une application ne peut apparaître qu'une fois.");
            }
            if (selection.enabled()) {
                if (selection.position() < 0 || selection.position() >= SLOT_COUNT) {
                    throw invalid("Un widget activé doit être affecté à l'un des cinq emplacements.");
                }
                if (!positions.add(selection.position())) {
                    throw invalid("Un emplacement ne peut contenir qu'un seul widget.");
                }
            } else if (selection.position() != UNASSIGNED_SLOT) {
                throw invalid("Un widget non affecté doit rester dans la liste des widgets disponibles.");
            }
            validated.add(new ValidatedSelection(definition, selection,
                    validateConfig(selection.configJson(), definition.defaultConfigJson())));
        }

        List<ProfileApp> current = profile.getApps();
        Set<String> selectedKeys = validated.stream().map(selection -> selection.definition().key())
                .collect(Collectors.toSet());
        current.removeIf(existing -> !selectedKeys.contains(existing.getAppKey()));
        var existingByKey = current.stream().collect(Collectors.toMap(ProfileApp::getAppKey, app -> app));
        for (ValidatedSelection selection : validated) {
            String key = selection.definition().key();
            ProfileApp existing = existingByKey.get(key);
            if (existing == null) {
                current.add(new ProfileApp(profile, key, selection.selection().position(),
                        selection.selection().enabled(), selection.configJson()));
            } else {
                existing.setPosition(selection.selection().position());
                existing.setEnabled(selection.selection().enabled());
                existing.setConfigJson(selection.configJson());
            }
        }
        return listForProfile(profileId);
    }

    private String validateConfig(String submitted, String fallback) {
        String json = submitted == null || submitted.isBlank() ? fallback : submitted;
        if (json == null || json.length() > MAX_CONFIG_LENGTH) {
            throw invalid("La configuration JSON est absente ou trop volumineuse.");
        }
        try {
            JsonNode parsed = objectMapper.readTree(json);
            if (parsed == null || !parsed.isObject()) {
                throw invalid("La configuration doit être un objet JSON.");
            }
            return objectMapper.writeValueAsString(parsed);
        } catch (JacksonException ex) {
            throw invalid("La configuration JSON est invalide.");
        }
    }

    private static java.util.Map<String, Integer> normalizeStoredSlots(List<ProfileApp> stored) {
        List<ProfileApp> active = stored.stream().filter(ProfileApp::isEnabled)
                .sorted(java.util.Comparator.comparingInt(ProfileApp::getPosition).thenComparing(ProfileApp::getAppKey))
                .toList();
        var slots = new java.util.HashMap<String, Integer>();
        Set<Integer> occupied = new HashSet<>();
        for (ProfileApp preference : active) {
            int position = preference.getPosition();
            if (position >= 0 && position < SLOT_COUNT && occupied.add(position)) {
                slots.put(preference.getAppKey(), position);
            }
        }
        for (ProfileApp preference : active) {
            if (!slots.containsKey(preference.getAppKey())) {
                int position = firstAvailableSlot(occupied);
                if (position != UNASSIGNED_SLOT) {
                    occupied.add(position);
                    slots.put(preference.getAppKey(), position);
                }
            }
        }
        return slots;
    }

    private static int reserveDefaultSlot(int preferred, Set<Integer> occupied) {
        if (preferred >= 0 && preferred < SLOT_COUNT && !occupied.contains(preferred)) {
            occupied.add(preferred);
            return preferred;
        }
        int position = firstAvailableSlot(occupied);
        if (position != UNASSIGNED_SLOT) {
            occupied.add(position);
        }
        return position;
    }

    private static int firstAvailableSlot(Set<Integer> occupied) {
        for (int position = 0; position < SLOT_COUNT; position++) {
            if (!occupied.contains(position)) {
                return position;
            }
        }
        return UNASSIGNED_SLOT;
    }

    private static ApiException appDisabled() {
        return new ApiException(HttpStatus.NOT_FOUND, "Application désactivée ou introuvable pour ce profil.");
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    public record Selection(String key, boolean enabled, int position, String configJson) {
    }

    public record AppPreference(String key, String title, String description, String fragmentTemplate,
                                String widgetModule,
                                boolean enabled, int position, String configJson) {
    }

    public record EnabledApp(AssistantApp definition, int position, String configJson) {
    }

    private record ValidatedSelection(AssistantApp definition, Selection selection, String configJson) {
    }
}
