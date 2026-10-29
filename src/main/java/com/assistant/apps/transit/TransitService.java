package com.assistant.apps.transit;

import com.assistant.apps.ProfileAppService;
import com.assistant.common.ApiException;
import com.assistant.config.AssistantProperties;
import com.assistant.integrations.ExternalApiModeService;
import com.assistant.quota.ApiQuotaService;
import com.assistant.quota.QuietHours;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class TransitService {

    private static final Logger LOG = LoggerFactory.getLogger(TransitService.class);
    private static final String APP_KEY = "transit";
    private static final String QUOTA_KEY = "prim";
    private static final int MAX_FAVORITES = 4;
    // Un tick à 10 min pile arrive juste avant l'échéance du flux précédent : sans marge, il serait ignoré.
    static final Duration SCHEDULE_SLACK = Duration.ofSeconds(5);

    private final TransitGateway liveGateway;
    private final TransitGateway testGateway;
    private final LineCatalog catalog;
    private final ProfileAppService profileApps;
    private final ObjectMapper objectMapper;
    private final AssistantProperties properties;
    private final PrimKeyService primKey;
    private final ApiQuotaService quota;
    private final QuietHours quietHours;
    private final ExternalApiModeService mode;
    private final Clock clock;
    private final Duration refreshInterval;

    private volatile TransitFeed liveFeed;
    private volatile Instant lastAttemptAt;
    private volatile String refreshNotice;

    public TransitService(@Qualifier("primClient") TransitGateway liveGateway, MockTransitGateway testGateway, LineCatalog catalog,
                          ProfileAppService profileApps, ObjectMapper objectMapper, AssistantProperties properties,
                          PrimKeyService primKey, ApiQuotaService quota, QuietHours quietHours,
                          ExternalApiModeService mode, Clock clock) {
        this.liveGateway = liveGateway;
        this.testGateway = testGateway;
        this.catalog = catalog;
        this.profileApps = profileApps;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.primKey = primKey;
        this.quota = quota;
        this.quietHours = quietHours;
        this.mode = mode;
        this.clock = clock;
        this.refreshInterval = Duration.ofMinutes(properties.prim().cacheMinutes()).minus(SCHEDULE_SLACK);
    }

    public List<TransitLine> searchLines(String query) {
        if (query == null || query.isBlank() || query.strip().length() > 100) {
            throw invalid("Saisissez un nom de ligne (100 caractères maximum).");
        }
        return catalog.search(query.strip()).stream().limit(100).toList();
    }

    public List<TransitLine> saveFavorites(long profileId, List<String> lineIds) {
        if (lineIds == null || lineIds.size() > MAX_FAVORITES) {
            throw invalid("Vous pouvez sélectionner au maximum quatre lignes.");
        }
        Set<String> unique = new HashSet<>();
        for (String id : lineIds) {
            if (id == null || !catalog.isActive(id)) {
                throw invalid("Une ligne sélectionnée est inconnue ou inactive.");
            }
            if (!unique.add(id)) {
                throw invalid("Une même ligne ne peut être sélectionnée qu'une fois.");
            }
        }
        ProfileAppService.AppPreference preference = profileApps.listForProfile(profileId).stream()
                .filter(app -> APP_KEY.equals(app.key())).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Application Transports introuvable."));
        ObjectNode config = objectMapper.createObjectNode();
        try {
            JsonNode parsed = objectMapper.readTree(preference.configJson());
            if (parsed instanceof ObjectNode object) {
                config = object.deepCopy();
            }
            config.set("favoriteLineIds", objectMapper.valueToTree(lineIds));
            profileApps.updateConfiguration(profileId, APP_KEY, objectMapper.writeValueAsString(config));
        } catch (JacksonException exception) {
            throw invalid("La configuration du widget Transports est invalide.");
        }
        return catalog.favorites(lineIds);
    }

    public List<TransitLine> favorites(long profileId) {
        ProfileAppService.AppPreference preference = profileApps.listForProfile(profileId).stream()
                .filter(app -> APP_KEY.equals(app.key())).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Application Transports introuvable."));
        return catalog.favorites(readFavoriteIds(preference.configJson()));
    }

    public Overview alerts(long profileId) {
        ProfileAppService.EnabledApp app = profileApps.requireEnabled(profileId, APP_KEY);
        List<TransitLine> favorites = catalog.favorites(readFavoriteIds(app.configJson()));
        boolean testMode = mode.isTestMode();
        boolean paused = !testMode && quietHours.paused();
        TransitFeed feed;
        String notice = null;
        if (testMode) {
            feed = testGateway.fetchDisruptions();
            notice = "MODE TEST · données simulées";
        } else if (paused) {
            feed = liveFeed;
            notice = "Alertes en pause jusqu'à " + quietHours.resumeLabel() + ".";
        } else if (!primKey.isConfigured()) {
            feed = liveFeed;
            notice = "Le service PRIM n'est pas configuré.";
        } else {
            feed = refreshIfDue();
            notice = refreshNotice;
            if (feed == null && (notice == null || notice.isBlank())) {
                throw unavailable();
            }
        }
        Instant fetchedAt = feed == null ? null : feed.fetchedAt();
        return new Overview(testMode, paused, notice, fetchedAt, buildLineAlerts(favorites, feed, clock.instant()));
    }

        @Scheduled(fixedRateString = "PT${assistant.prim.cache-minutes:10}M",
            initialDelayString = "PT${assistant.prim.cache-minutes:10}M")
    public void scheduledRefresh() {
        if (mode.isTestMode() || quietHours.paused() || !primKey.isConfigured()) {
            return;
        }
        try {
            refreshLive();
        } catch (RuntimeException exception) {
            LOG.warn("Actualisation PRIM indisponible: {}", exception.getClass().getSimpleName());
        }
    }

    private TransitFeed refreshIfDue() {
        Instant now = clock.instant();
        TransitFeed current = liveFeed;
        if (current != null && Duration.between(current.fetchedAt(), now).compareTo(refreshInterval) < 0) {
            return current;
        }
        if (quietHours.paused()) {
            refreshNotice = "Alertes en pause jusqu'à " + quietHours.resumeLabel() + ".";
            return current;
        }
        return refreshLive();
    }

    private synchronized TransitFeed refreshLive() {
        Instant now = clock.instant();
        TransitFeed current = liveFeed;
        if (current != null && Duration.between(current.fetchedAt(), now).compareTo(refreshInterval) < 0) {
            return current;
        }
        if (quietHours.paused()) {
            refreshNotice = "Alertes en pause jusqu'à " + quietHours.resumeLabel() + ".";
            return current;
        }
        if (lastAttemptAt != null && Duration.between(lastAttemptAt, now).compareTo(refreshInterval) < 0) {
            return current;
        }
        if (!primKey.isConfigured()) {
            refreshNotice = "Le service PRIM n'est pas configuré.";
            return current;
        }
        if (!quota.tryReserve(QUOTA_KEY, properties.prim().dailyBudget())) {
            refreshNotice = "Budget quotidien PRIM atteint.";
            return current;
        }
        lastAttemptAt = now;
        try {
            liveFeed = liveGateway.fetchDisruptions();
            refreshNotice = null;
            return liveFeed;
        } catch (TransitUnavailableException exception) {
            refreshNotice = "Actualisation indisponible; affichage des dernières données reçues.";
            return current;
        }
    }

    private List<LineAlerts> buildLineAlerts(List<TransitLine> favorites, TransitFeed feed, Instant now) {
        List<TransitDisruption> disruptions = feed == null ? List.of() : feed.disruptions();
        return favorites.stream().map(line -> {
            List<TransitAlert> alerts = disruptions.stream()
                    .filter(disruption -> disruption.activeAt(now) && matches(disruption, line))
                    .map(disruption -> new TransitAlert(disruption.id(),
                            nonBlank(disruption.title(), line.label()),
                            nonBlank(disruption.shortMessage(), "Perturbation du trafic"),
                            disruption.message(),
                            nonBlank(disruption.severity(), "INFORMATION"),
                            nonBlank(disruption.cause(), "")))
                    .sorted(Comparator.comparingInt((TransitAlert alert) -> severityRank(alert.severity()))
                            .thenComparing(TransitAlert::title, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            return new LineAlerts(line, alerts);
        }).sorted(Comparator.comparing((LineAlerts lineAlerts) -> lineAlerts.alerts().isEmpty())).toList();
    }

    private boolean matches(TransitDisruption disruption, TransitLine line) {
        String primId = "line:IDFM:" + line.id();
        if (disruption.lineIds().contains(primId)) {
            return true;
        }
        if (disruption.hasImpactedSections() || disruption.title() == null) {
            return false;
        }
        return titleMatches(disruption.title(), line);
    }

    private static boolean titleMatches(String title, TransitLine line) {
        String normalized = title.strip().toLowerCase(Locale.ROOT);
        List<String> prefixes = new ArrayList<>();
        if (line.networkName() != null && !line.networkName().isBlank()) {
            prefixes.add(line.networkName() + " " + line.shortName());
        }
        if ("Transilien".equalsIgnoreCase(line.networkName())) {
            prefixes.add("Ligne " + line.shortName());
        } else if ("metro".equalsIgnoreCase(line.transportMode())) {
            prefixes.add("Métro " + line.shortName());
        } else if ("bus".equalsIgnoreCase(line.transportMode())) {
            prefixes.add("Bus " + line.shortName());
        }
        return prefixes.stream().anyMatch(prefix -> normalized.startsWith(prefix.toLowerCase(Locale.ROOT)));
    }

    private List<String> readFavoriteIds(String configJson) {
        try {
            JsonNode ids = objectMapper.readTree(configJson).get("favoriteLineIds");
            if (ids == null || !ids.isArray()) {
                return List.of();
            }
            List<String> favorites = new ArrayList<>();
            for (JsonNode id : ids) {
                if (id.isTextual() && catalog.isActive(id.asString())) {
                    favorites.add(id.asString());
                }
            }
            return favorites.stream().distinct().limit(MAX_FAVORITES).toList();
        } catch (JacksonException exception) {
            return List.of();
        }
    }

    private static int severityRank(String severity) {
        return switch (severity.toUpperCase(Locale.ROOT)) {
            case "BLOQUANTE" -> 0;
            case "PERTURBEE", "PERTURBÉE" -> 1;
            default -> 2;
        };
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Le service d'information trafic est indisponible.");
    }

    public record Overview(boolean testMode, boolean paused, String notice, Instant fetchedAt,
                           List<LineAlerts> lines) {
    }

    public record LineAlerts(TransitLine line, List<TransitAlert> alerts) {
    }
}
