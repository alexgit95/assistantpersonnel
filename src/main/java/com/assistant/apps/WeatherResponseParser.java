package com.assistant.apps;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class WeatherResponseParser {

    private static final int MORNING_START = 6;
    private static final int AFTERNOON_START = 12;
    private static final int AFTERNOON_END = 18;

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
        JsonNode hourly = response == null ? null : response.get("hourly");
        JsonNode daily = response == null ? null : response.get("daily");
        if (current == null || hourly == null || daily == null) {
            throw new WeatherUnavailableException(null);
        }
        WeatherForecast.CurrentConditions conditions = new WeatherForecast.CurrentConditions(
                current.path("temperature_2m").asDouble(Double.NaN),
                current.path("apparent_temperature").asDouble(Double.NaN),
                WeatherCode.description(current.path("weather_code").asInt(-1)));
        JsonNode dates = daily.path("time");
        JsonNode hourTimes = hourly.path("time");
        JsonNode hourTemperatures = hourly.path("temperature_2m");
        JsonNode hourCodes = hourly.path("weather_code");
        if (!dates.isArray() || !hourTimes.isArray() || !hourTemperatures.isArray() || !hourCodes.isArray()) {
            throw new WeatherUnavailableException(null);
        }
        int hourCount = Math.min(hourTimes.size(), Math.min(hourTemperatures.size(), hourCodes.size()));
        List<WeatherForecast.ForecastDay> days = new ArrayList<>();
        int count = Math.min(dates.size(), WeatherGateway.FORECAST_DAYS);
        try {
            for (int index = 0; index < count; index++) {
                days.add(parseDay(dates.get(index).asString(), index, hourly, daily, hourCount));
            }
        } catch (WeatherUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new WeatherUnavailableException(exception);
        }
        if (days.isEmpty()) {
            throw new WeatherUnavailableException(null);
        }
        return new WeatherForecast(location, conditions, List.copyOf(days), testMode,
                response.path("utc_offset_seconds").asInt(0));
    }

    private static WeatherForecast.ForecastDay parseDay(String date, int dayIndex, JsonNode hourly, JsonNode daily,
                                                         int hourCount) {
        LocalDate day = LocalDate.parse(date);
        String prefix = date + "T";
        JsonNode times = hourly.path("time");
        double morningMin = Double.POSITIVE_INFINITY;
        double afternoonMax = Double.NEGATIVE_INFINITY;
        int morningCode = -1;
        int afternoonCode = -1;
        int rainMax = 0;
        double gustMax = 0;
        for (int hourIndex = 0; hourIndex < hourCount; hourIndex++) {
            String time = times.get(hourIndex).asString();
            if (!time.startsWith(prefix)) {
                continue;
            }
            int hour = Integer.parseInt(time.substring(11, 13));
            JsonNode temperature = hourly.path("temperature_2m").get(hourIndex);
            int code = hourly.path("weather_code").get(hourIndex).asInt(-1);
            if (temperature.isNumber()) {
                double value = temperature.asDouble();
                if (hour >= MORNING_START && hour < AFTERNOON_START) {
                    morningMin = Math.min(morningMin, value);
                    morningCode = worst(morningCode, code);
                } else if (hour >= AFTERNOON_START && hour < AFTERNOON_END) {
                    afternoonMax = Math.max(afternoonMax, value);
                    afternoonCode = worst(afternoonCode, code);
                }
            }
            rainMax = Math.max(rainMax, (int) Math.round(valueAt(hourly, "precipitation_probability", hourIndex, 0)));
            gustMax = Math.max(gustMax, valueAt(hourly, "wind_gusts_10m", hourIndex, 0));
        }
        if (Double.isInfinite(morningMin) || Double.isInfinite(afternoonMax)) {
            throw new WeatherUnavailableException(null);
        }
        rainMax = (int) Math.round(valueAt(daily, "precipitation_probability_max", dayIndex, rainMax));
        int gust = (int) Math.round(valueAt(daily, "wind_gusts_10m_max", dayIndex, gustMax));
        WeatherForecast.DayPart morning = new WeatherForecast.DayPart(morningMin, morningCode,
                WeatherCode.description(morningCode));
        WeatherForecast.DayPart afternoon = new WeatherForecast.DayPart(afternoonMax, afternoonCode,
                WeatherCode.description(afternoonCode));
        String advice = WeatherAdvice.advise(Math.min(morningMin, afternoonMax), Math.max(morningMin, afternoonMax),
                rainMax, gust, worst(morningCode, afternoonCode));
        return new WeatherForecast.ForecastDay(day, morning, afternoon, rainMax, gust,
                gust > WeatherAdvice.GUST_ALERT_KMH, advice);
    }

    private static int worst(int first, int second) {
        return WeatherCode.severity(second) > WeatherCode.severity(first) ? second : first;
    }

    private static double valueAt(JsonNode container, String field, int index, double fallback) {
        JsonNode values = container.path(field);
        JsonNode value = values.isArray() && index < values.size() ? values.get(index) : null;
        return value != null && value.isNumber() ? value.asDouble() : fallback;
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }
}