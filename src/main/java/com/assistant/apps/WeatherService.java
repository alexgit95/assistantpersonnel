package com.assistant.apps;

import com.assistant.apps.ProfileAppService.EnabledApp;
import com.assistant.common.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class WeatherService {

    private static final String APP_KEY = "weather";
    private static final int MAX_CACHE_ENTRIES = 256;
    private final WeatherGateway gateway;
    private final ProfileAppService profileApps;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration cacheTtl;
    private final int forecastDays;
    private final Map<String, CacheEntry<List<WeatherLocation>>> locationCache = lruCache();
    private final Map<Coordinates, CacheEntry<WeatherForecast>> forecastCache = lruCache();

    public WeatherService(WeatherGateway gateway, ProfileAppService profileApps, ObjectMapper objectMapper,
                          Clock clock, com.assistant.config.AssistantProperties properties) {
        this.gateway = gateway;
        this.profileApps = profileApps;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.cacheTtl = Duration.ofMinutes(properties.weather().cacheMinutes());
        this.forecastDays = properties.weather().forecastDays();
    }

    public List<WeatherLocation> searchCities(String query) {
        if (query == null || query.strip().length() < 2) {
            throw invalid("Saisissez au moins deux caractères pour rechercher une ville.");
        }
        String normalized = query.strip().toLowerCase(Locale.ROOT);
        List<WeatherLocation> cached = readLocationCache(normalized);
        if (cached != null) {
            return cached;
        }
        try {
            List<WeatherLocation> results = List.copyOf(gateway.geocode(query.strip()));
            writeLocationCache(normalized, results);
            return results;
        } catch (WeatherUnavailableException ex) {
            throw unavailable();
        }
    }

    @Transactional
    public WeatherLocation saveLocation(long profileId, WeatherLocation location) {
        ProfileAppService.AppPreference preference = profileApps.listForProfile(profileId).stream()
            .filter(app -> app.key().equals(APP_KEY))
            .findFirst().orElseThrow(() -> invalid("Application météo introuvable."));
        validateLocation(location);
        try {
            JsonNode parsed = objectMapper.readTree(preference.configJson());
            ObjectNode config = parsed instanceof ObjectNode object ? object : objectMapper.createObjectNode();
            config.set("location", objectMapper.valueToTree(location));
            profileApps.updateConfiguration(profileId, APP_KEY, objectMapper.writeValueAsString(config));
            return location;
        } catch (JacksonException ex) {
            throw invalid("La configuration météo du profil est invalide.");
        }
    }

    public WeatherForecast forecast(long profileId) {
        EnabledApp app = profileApps.requireEnabled(profileId, APP_KEY);
        WeatherLocation location = configuredLocation(app.configJson());
        Coordinates key = new Coordinates(location.latitude(), location.longitude());
        WeatherForecast cached = readForecastCache(key);
        if (cached != null) {
            return cached;
        }
        try {
            WeatherForecast forecast = gateway.forecast(location, forecastDays);
            writeForecastCache(key, forecast);
            return forecast;
        } catch (WeatherUnavailableException ex) {
            throw unavailable();
        }
    }

    private WeatherLocation configuredLocation(String configJson) {
        try {
            JsonNode location = objectMapper.readTree(configJson).get("location");
            if (location == null || !location.isObject()) {
                throw invalid("Choisissez une ville pour configurer la météo.");
            }
            return new WeatherLocation(location.path("id").asLong(), location.path("name").asString(),
                    textOrNull(location.get("region")), textOrNull(location.get("country")),
                    location.path("latitude").asDouble(Double.NaN),
                    location.path("longitude").asDouble(Double.NaN));
        } catch (JacksonException ex) {
            throw invalid("La configuration météo du profil est invalide.");
        }
    }

    private static String textOrNull(JsonNode value) {
        return value == null || value.isNull() ? null : value.asString();
    }

    private static void validateLocation(WeatherLocation location) {
        if (location == null || location.id() < 1 || location.name() == null || location.name().isBlank()
                || location.country() == null || location.country().isBlank()
                || !Double.isFinite(location.latitude()) || location.latitude() < -90 || location.latitude() > 90
                || !Double.isFinite(location.longitude()) || location.longitude() < -180 || location.longitude() > 180) {
            throw invalid("La ville sélectionnée est invalide.");
        }
    }

    private synchronized List<WeatherLocation> readLocationCache(String key) {
        CacheEntry<List<WeatherLocation>> entry = locationCache.get(key);
        if (entry == null || expired(entry)) {
            locationCache.remove(key);
            return null;
        }
        return entry.value();
    }

    private synchronized void writeLocationCache(String key, List<WeatherLocation> value) {
        locationCache.put(key, new CacheEntry<>(clock.instant(), value));
    }

    private synchronized WeatherForecast readForecastCache(Coordinates key) {
        CacheEntry<WeatherForecast> entry = forecastCache.get(key);
        if (entry == null || expired(entry)) {
            forecastCache.remove(key);
            return null;
        }
        return entry.value();
    }

    private synchronized void writeForecastCache(Coordinates key, WeatherForecast value) {
        forecastCache.put(key, new CacheEntry<>(clock.instant(), value));
    }

    private boolean expired(CacheEntry<?> entry) {
        return !entry.createdAt().plus(cacheTtl).isAfter(clock.instant());
    }

    private static <K, V> Map<K, V> lruCache() {
        return new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > MAX_CACHE_ENTRIES;
            }
        };
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "Le service météo est momentanément indisponible. Réessayez plus tard.");
    }

    private record CacheEntry<T>(Instant createdAt, T value) {
    }

    private record Coordinates(double latitude, double longitude) {
    }
}