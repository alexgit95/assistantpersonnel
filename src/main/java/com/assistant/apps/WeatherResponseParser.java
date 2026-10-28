package com.assistant.apps;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class WeatherResponseParser {

    public List<WeatherLocation> parseGeocoding(JsonNode response) {
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
    }

    public WeatherForecast parseForecast(JsonNode response, WeatherLocation location, boolean testMode) {
        JsonNode current = response == null ? null : response.get("current");
        JsonNode daily = response == null ? null : response.get("daily");
        if (current == null || daily == null) {
            throw new WeatherUnavailableException(null);
        }
        WeatherForecast.CurrentConditions conditions = new WeatherForecast.CurrentConditions(
                current.path("temperature_2m").asDouble(Double.NaN),
                current.path("apparent_temperature").asDouble(Double.NaN),
                current.path("relative_humidity_2m").asInt(-1),
                current.path("wind_speed_10m").asDouble(Double.NaN),
                WeatherCode.description(current.path("weather_code").asInt(-1)));
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
            try {
                days.add(new WeatherForecast.ForecastDay(LocalDate.parse(times.get(index).asString()),
                        minimums.get(index).asDouble(Double.NaN), maximums.get(index).asDouble(Double.NaN),
                        WeatherCode.description(codes.get(index).asInt(-1))));
            } catch (RuntimeException exception) {
                throw new WeatherUnavailableException(exception);
            }
        }
        if (days.isEmpty()) {
            throw new WeatherUnavailableException(null);
        }
        return new WeatherForecast(location, conditions, List.copyOf(days), testMode);
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }
}