package com.assistant.apps;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import com.assistant.proxy.OutboundHttp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

@Component
public class OpenMeteoClient implements WeatherGateway {

    private static final Logger LOG = LoggerFactory.getLogger(OpenMeteoClient.class);

    private final RestClient restClient;

    public OpenMeteoClient() {
        HttpClient httpClient = OutboundHttp.clientBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    OpenMeteoClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public List<WeatherLocation> geocode(String query) {
        URI uri = org.springframework.web.util.UriComponentsBuilder
                .fromUriString("https://geocoding-api.open-meteo.com/v1/search")
                .queryParam("name", query)
                .queryParam("count", 10)
                .queryParam("language", "fr")
                .queryParam("format", "json")
                .build().encode().toUri();
        try {
            JsonNode response = restClient.get().uri(uri).retrieve().body(JsonNode.class);
            JsonNode results = response == null ? null : response.get("results");
            if (results == null || !results.isArray()) {
                return List.of();
            }
            List<WeatherLocation> locations = new ArrayList<>();
            for (JsonNode result : results) {
                locations.add(new WeatherLocation(result.path("id").asLong(), result.path("name").asString(),
                        optionalText(result, "admin1"), optionalText(result, "country"),
                        result.path("latitude").asDouble(), result.path("longitude").asDouble()));
            }
            return List.copyOf(locations);
        } catch (RestClientException ex) {
            logProviderFailure("geocodage", ex);
            throw new WeatherUnavailableException(ex);
        }
    }

    @Override
    public WeatherForecast forecast(WeatherLocation location, int forecastDays) {
        URI uri = org.springframework.web.util.UriComponentsBuilder
                .fromUriString("https://api.open-meteo.com/v1/forecast")
                .queryParam("latitude", location.latitude())
                .queryParam("longitude", location.longitude())
                .queryParam("current", "temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m")
                .queryParam("daily", "weather_code,temperature_2m_max,temperature_2m_min")
                .queryParam("forecast_days", forecastDays)
                .queryParam("timezone", "auto")
                .build().encode().toUri();
        try {
            JsonNode response = restClient.get().uri(uri).retrieve().body(JsonNode.class);
            return parseForecast(response, location);
        } catch (RestClientException ex) {
            logProviderFailure("previsions", ex);
            throw new WeatherUnavailableException(ex);
        }
    }

    private static void logProviderFailure(String operation, RestClientException exception) {
        Throwable rootCause = exception;
        StringJoiner causes = new StringJoiner(" -> ");
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            causes.add(cause.getClass().getSimpleName());
            rootCause = cause;
        }
        if (exception instanceof RestClientResponseException responseException) {
            LOG.warn("Open-Meteo failure during {}: HTTP {}, causes={}",
                    operation, responseException.getStatusCode().value(), causes);
            return;
        }
        String detail = rootCause.getMessage();
        if (detail != null) {
            detail = detail.replaceAll("https?://[^\\s\\\"']+", "[URL REDACTED]");
        }
        LOG.warn("Open-Meteo failure during {}: causes={}, detail={}",
                operation, causes, detail == null ? "indisponible" : detail);
    }

    private static WeatherForecast parseForecast(JsonNode response, WeatherLocation location) {
        JsonNode current = response == null ? null : response.get("current");
        JsonNode daily = response == null ? null : response.get("daily");
        if (current == null || daily == null) {
            throw new WeatherUnavailableException(null);
        }
        int currentCode = current.path("weather_code").asInt(-1);
        WeatherForecast.CurrentConditions conditions = new WeatherForecast.CurrentConditions(
                current.path("temperature_2m").asDouble(Double.NaN),
                current.path("apparent_temperature").asDouble(Double.NaN),
                current.path("relative_humidity_2m").asInt(-1),
                current.path("wind_speed_10m").asDouble(Double.NaN), WeatherCode.description(currentCode));
        JsonNode times = daily.path("time");
        JsonNode codes = daily.path("weather_code");
        JsonNode maximums = daily.path("temperature_2m_max");
        JsonNode minimums = daily.path("temperature_2m_min");
        if (!times.isArray() || !codes.isArray() || !maximums.isArray() || !minimums.isArray()) {
            throw new WeatherUnavailableException(null);
        }
        List<WeatherForecast.ForecastDay> days = new ArrayList<>();
        int count = Math.min(Math.min(times.size(), codes.size()), Math.min(maximums.size(), minimums.size()));
        for (int index = 0; index < count; index++) {
            days.add(new WeatherForecast.ForecastDay(LocalDate.parse(times.get(index).asString()),
                    minimums.get(index).asDouble(Double.NaN), maximums.get(index).asDouble(Double.NaN),
                    WeatherCode.description(codes.get(index).asInt(-1))));
        }
        return new WeatherForecast(location, conditions, List.copyOf(days));
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }
}