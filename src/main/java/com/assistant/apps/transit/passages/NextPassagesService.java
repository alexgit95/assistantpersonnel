package com.assistant.apps.transit.passages;

import com.assistant.apps.ProfileAppService;
import com.assistant.apps.transit.LineCatalog;
import com.assistant.apps.transit.PrimKeyService;
import com.assistant.apps.transit.TransitLine;
import com.assistant.common.ApiException;
import com.assistant.config.AssistantProperties;
import com.assistant.integrations.ExternalApiModeService;
import com.assistant.quota.ApiQuotaService;
import com.assistant.quota.QuietHours;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class NextPassagesService {

    static final String APP_KEY = "transit-passages";
    static final String QUOTA_KEY = "prim";
    static final int MAX_STOPS = 3;
    static final int PASSAGES_PER_LINE = 3;
    static final int LINES_ONE_STOP = 4;
    static final int LINES_TWO_STOPS = 2;
    static final int LINES_THREE_STOPS = 1;
    // Un poll à 60 s pile arrive juste avant l'échéance du cache : sans marge il serait servi périmé d'une minute.
    static final Duration CACHE_TTL = Duration.ofSeconds(55);
    private static final int MAX_CACHE = 100;
    private static final List<TransitStop> TEST_STOPS = List.of(
            new TransitStop("24377", "Carnot", "Paris", "bus"),
            new TransitStop("39713", "René Alazard - Sadi Carnot", "Bagnolet", "bus"),
            new TransitStop("2140", "Jean-Baptiste Clément - Carnot", "Villetaneuse", "bus"),
            new TransitStop("473921", "Châtelet-Les Halles", "Paris", "bus"),
            new TransitStop("22421", "Porte d'Orléans", "Paris", "bus"));

    public static final String STATE_OK = "OK";
    public static final String STATE_NO_PASSAGE = "NO_PASSAGE";
    public static final String STATE_UNKNOWN_STOP = "UNKNOWN_STOP";
    public static final String STATE_NO_DATA = "NO_DATA";

    private final PassageGateway liveGateway;
    private final PassageGateway testGateway;
    private final StopSearchClient search;
    private final LineCatalog catalog;
    private final ProfileAppService profileApps;
    private final ObjectMapper objectMapper;
    private final AssistantProperties properties;
    private final PrimKeyService primKey;
    private final ApiQuotaService quota;
    private final QuietHours quietHours;
    private final ExternalApiModeService mode;
    private final Clock clock;
    private final Map<String, CacheEntry> cache = Collections.synchronizedMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
            return size() > MAX_CACHE;
        }
    });

    public NextPassagesService(@Qualifier("primPassageClient") PassageGateway liveGateway,
                               MockPassageGateway testGateway, StopSearchClient search, LineCatalog catalog,
                               ProfileAppService profileApps, ObjectMapper objectMapper,
                               AssistantProperties properties, PrimKeyService primKey, ApiQuotaService quota,
                               QuietHours quietHours, ExternalApiModeService mode, Clock clock) {
        this.liveGateway = liveGateway;
        this.testGateway = testGateway;
        this.search = search;
        this.catalog = catalog;
        this.profileApps = profileApps;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.primKey = primKey;
        this.quota = quota;
        this.quietHours = quietHours;
        this.mode = mode;
        this.clock = clock;
    }

    public List<TransitStop> searchStops(String query) {
        if (mode.isTestMode()) {
            String text = StopSearchClient.validate(query).toLowerCase(java.util.Locale.ROOT);
            List<TransitStop> matches = TEST_STOPS.stream().filter(stop ->
                    stop.name().toLowerCase(java.util.Locale.ROOT).contains(text)
                            || stop.town().toLowerCase(java.util.Locale.ROOT).contains(text)).toList();
            // Sans correspondance, tout le jeu de test est proposé pour que le mode reste utilisable.
            return matches.isEmpty() ? TEST_STOPS : matches;
        }
        return search.search(query);
    }

    public List<TransitStop> stops(long profileId) {
        return readStops(preference(profileId).configJson());
    }

    public List<TransitStop> saveStops(long profileId, List<TransitStop> requested) {
        if (requested == null || requested.size() > MAX_STOPS) {
            throw invalid("Vous pouvez sélectionner au maximum trois arrêts.");
        }
        List<TransitStop> stops = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (TransitStop stop : requested) {
            TransitStop clean = normalize(stop);
            if (clean == null) {
                throw invalid("Un arrêt sélectionné est invalide.");
            }
            if (!unique.add(clean.arrid())) {
                throw invalid("Un même arrêt ne peut être sélectionné qu'une fois.");
            }
            stops.add(clean);
        }
        ProfileAppService.AppPreference preference = preference(profileId);
        try {
            JsonNode parsed = objectMapper.readTree(preference.configJson());
            ObjectNode config = parsed instanceof ObjectNode object ? object.deepCopy() : objectMapper.createObjectNode();
            config.set("stops", objectMapper.valueToTree(stops));
            profileApps.updateConfiguration(profileId, APP_KEY, objectMapper.writeValueAsString(config));
        } catch (JacksonException exception) {
            throw invalid("La configuration du widget Prochains passages est invalide.");
        }
        return List.copyOf(stops);
    }

    public Overview next(long profileId) {
        ProfileAppService.EnabledApp app = profileApps.requireEnabled(profileId, APP_KEY);
        List<TransitStop> stops = readStops(app.configJson());
        Instant now = clock.instant();
        int maxLines = linesPerStop(stops.size());
        if (mode.isTestMode()) {
            return overview(true, "MODE TEST · données simulées",
                    stops.stream().map(stop -> view(stop, testEntry(stop, now, maxLines), now, maxLines)).toList());
        }
        String notice = null;
        boolean canCall = true;
        if (!primKey.isConfigured()) {
            notice = "Renseignez la clé PRIM dans l'administration pour afficher les prochains passages.";
            canCall = false;
        } else if (quietHours.paused()) {
            notice = "Prochains passages en pause jusqu'à " + quietHours.resumeLabel() + ".";
            canCall = false;
        }
        AtomicReference<String> refreshNotice = new AtomicReference<>();
        List<StopPassages> views = new ArrayList<>(stops.size());
        for (TransitStop stop : stops) {
            CacheEntry entry = cache.get(stop.arrid());
            if (canCall && (entry == null || stale(entry, now))) {
                entry = refresh(stop, refreshNotice);
            }
            views.add(view(stop, entry, now, maxLines));
        }
        return overview(false, notice != null ? notice : refreshNotice.get(), views);
    }

    static int linesPerStop(int stopCount) {
        if (stopCount <= 1) {
            return LINES_ONE_STOP;
        }
        return stopCount == 2 ? LINES_TWO_STOPS : LINES_THREE_STOPS;
    }

    /** La plus ancienne heure de mise à jour des arrêts qui ont des données : elle ne promet pas plus de fraîcheur qu'il n'y en a. */
    private static Overview overview(boolean testMode, String notice, List<StopPassages> stops) {
        Instant oldest = stops.stream().map(StopPassages::updatedAt).filter(java.util.Objects::nonNull)
                .min(java.util.Comparator.naturalOrder()).orElse(null);
        return new Overview(testMode, notice, stops, oldest);
    }

    /** Les 3 prochains passages de chaque ligne, par blocs de ligne dans l'ordre de leur premier passage, limités à maxLines lignes. */
    static List<Passage> selectPassages(PassageFeed feed, Instant now, int maxLines) {
        Map<String, List<Passage>> byLine = new LinkedHashMap<>();
        for (Passage passage : futurePassages(feed, now)) {
            List<Passage> group = byLine.computeIfAbsent(passage.lineId() == null ? "" : passage.lineId(),
                    key -> new ArrayList<>());
            if (group.size() < PASSAGES_PER_LINE) {
                group.add(passage);
            }
        }
        return byLine.values().stream().limit(maxLines).flatMap(List::stream).toList();
    }

    private CacheEntry testEntry(TransitStop stop, Instant now, int maxLines) {
        String key = "test:" + stop.arrid();
        CacheEntry entry = cache.get(key);
        // Les horaires simulés sont conservés tant qu'ils suffisent à remplir toutes les lignes affichées.
        if (entry == null || entry.feed() == null
                || selectPassages(entry.feed(), now, maxLines).size() < maxLines * PASSAGES_PER_LINE) {
            entry = new CacheEntry(now, testGateway.fetch(stop.arrid()), false);
            cache.put(key, entry);
        }
        return entry;
    }

    private synchronized CacheEntry refresh(TransitStop stop, AtomicReference<String> notice) {
        Instant now = clock.instant();
        CacheEntry current = cache.get(stop.arrid());
        if (current != null && !stale(current, now)) {
            return current;
        }
        if (!quota.tryReserve(QUOTA_KEY, properties.prim().dailyBudget())) {
            notice.compareAndSet(null, "Budget quotidien PRIM atteint.");
            return current;
        }
        CacheEntry entry;
        try {
            entry = new CacheEntry(now, liveGateway.fetch(stop.arrid()), false);
        } catch (PassageUnavailableException exception) {
            if (exception.unknownStop()) {
                entry = new CacheEntry(now, null, true);
            } else {
                notice.compareAndSet(null, "Actualisation indisponible; affichage des dernières données reçues.");
                entry = new CacheEntry(now, current == null ? null : current.feed(), false);
            }
        }
        cache.put(stop.arrid(), entry);
        return entry;
    }

    private static boolean stale(CacheEntry entry, Instant now) {
        return Duration.between(entry.attemptedAt(), now).compareTo(CACHE_TTL) >= 0;
    }

    private StopPassages view(TransitStop stop, CacheEntry entry, Instant now, int maxLines) {
        if (entry != null && entry.unknownStop()) {
            return new StopPassages(stop, STATE_UNKNOWN_STOP, "Arrêt indisponible", null, List.of());
        }
        if (entry == null || entry.feed() == null) {
            return new StopPassages(stop, STATE_NO_DATA, "Données indisponibles", null, List.of());
        }
        List<PassageView> passages = selectPassages(entry.feed(), now, maxLines).stream()
                .map(passage -> toView(passage, now)).toList();
        if (passages.isEmpty()) {
            return new StopPassages(stop, STATE_NO_PASSAGE, "Aucun passage prévu", entry.feed().updatedAt(), passages);
        }
        return new StopPassages(stop, STATE_OK, null, entry.feed().updatedAt(), passages);
    }

    private static List<Passage> futurePassages(PassageFeed feed, Instant now) {
        return feed.passages().stream().filter(passage -> !passage.expectedAt().isBefore(now)).toList();
    }

    private PassageView toView(Passage passage, Instant now) {
        TransitLine line = passage.lineId() == null ? null : catalog.find(passage.lineId()).orElse(null);
        // Pastille : lettre du train ou numéro du bus, sans préfixe de mode ni de réseau.
        String label = line != null ? line.shortName() : passage.lineId() == null ? "" : passage.lineId();
        String color = line != null && line.lineColor() != null && line.lineColor().matches("(?i)[0-9a-f]{6}")
                ? line.lineColor() : null;
        return new PassageView(label, color, passage.destination() == null ? "" : passage.destination(),
                (int) Duration.between(now, passage.expectedAt()).toMinutes(), passage.expectedAt(),
                passage.status());
    }

    private ProfileAppService.AppPreference preference(long profileId) {
        return profileApps.listForProfile(profileId).stream().filter(app -> APP_KEY.equals(app.key())).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Application Prochains passages introuvable."));
    }

    private List<TransitStop> readStops(String configJson) {
        try {
            JsonNode array = objectMapper.readTree(configJson).get("stops");
            if (array == null || !array.isArray()) {
                return List.of();
            }
            List<TransitStop> stops = new ArrayList<>();
            Set<String> unique = new HashSet<>();
            for (JsonNode node : array) {
                TransitStop stop = normalize(new TransitStop(text(node, "arrid"), text(node, "name"),
                        text(node, "town"), text(node, "type")));
                if (stop != null && unique.add(stop.arrid())) {
                    stops.add(stop);
                }
            }
            return stops.stream().limit(MAX_STOPS).toList();
        } catch (JacksonException exception) {
            return List.of();
        }
    }

    private static TransitStop normalize(TransitStop stop) {
        if (stop == null || stop.arrid() == null || !stop.arrid().strip().matches("\\d{1,12}")
                || stop.name() == null || stop.name().isBlank() || stop.name().strip().length() > 100) {
            return null;
        }
        return new TransitStop(stop.arrid().strip(), stop.name().strip(), clip(stop.town(), 100),
                clip(stop.type(), 30));
    }

    private static String clip(String value, int max) {
        String result = value == null ? "" : value.strip();
        return result.length() > max ? result.substring(0, max) : result;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || !value.isTextual() ? null : value.asString();
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    private record CacheEntry(Instant attemptedAt, PassageFeed feed, boolean unknownStop) {
    }

    public record Overview(boolean testMode, String notice, List<StopPassages> stops, Instant updatedAt) {
    }

    public record StopPassages(TransitStop stop, String state, String message, Instant updatedAt,
                               List<PassageView> passages) {
    }

    public record PassageView(String line, String lineColor, String destination, int minutes, Instant expectedAt,
                              String status) {
    }
}
