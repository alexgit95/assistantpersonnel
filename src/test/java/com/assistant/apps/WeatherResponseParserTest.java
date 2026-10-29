package com.assistant.apps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class WeatherResponseParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WeatherResponseParser parser = new WeatherResponseParser();

    @Test
    void parsesTheBundledRawOpenMeteoFixtureAsParisForFourDays() throws IOException {
        JsonNode fixture = fixture();
        var locations = parser.parseGeocoding(fixture.path("geocodingResponse"));
        var forecast = parser.parseForecast(fixture.path("forecastResponse"),
                locations.getFirst(), true);

        assertThat(locations).singleElement().extracting(WeatherLocation::name).isEqualTo("Paris");
        assertThat(forecast.location()).isEqualTo(locations.getFirst());
        assertThat(forecast.current().temperatureC()).isEqualTo(18.4);
        assertThat(forecast.current().feelsLikeC()).isEqualTo(17.2);
        assertThat(forecast.current().description()).isEqualTo("Partiellement nuageux");
        assertThat(forecast.days()).hasSize(4);
        assertThat(forecast.days().getFirst().date().toString()).isEqualTo("2026-10-28");
        assertThat(forecast.testMode()).isTrue();
        assertThat(forecast.utcOffsetSeconds()).isEqualTo(3600);
    }

    @Test
    void aggregatesMorningMinimumAndAfternoonMaximumWithTheMostSevereCode() throws IOException {
        var days = parseFixtureDays();

        var rainyDay = days.get(1);
        assertThat(rainyDay.morning().temperatureC()).isEqualTo(11.8);
        assertThat(rainyDay.morning().code()).isEqualTo(3);
        assertThat(rainyDay.afternoon().temperatureC()).isEqualTo(17.6);
        assertThat(rainyDay.afternoon().code()).isEqualTo(61);
        assertThat(rainyDay.afternoon().description()).isEqualTo("Pluie");
        assertThat(rainyDay.rainChancePercent()).isEqualTo(70);
        assertThat(rainyDay.advice()).isEqualTo("Prends un parapluie et une veste.");
    }

    @Test
    void flagsGustsAboveFiftyKmhOnly() throws IOException {
        var days = parseFixtureDays();

        assertThat(days.get(2).maxGustKmh()).isEqualTo(62);
        assertThat(days.get(2).windAlert()).isTrue();
        assertThat(days.get(2).advice()).contains("Rafales jusqu'à 62 km/h");
        assertThat(days.get(0).windAlert()).isFalse();
        assertThat(days.get(3).windAlert()).isFalse();
    }

    @Test
    void calmDayGetsTheNeutralAdvice() throws IOException {
        assertThat(parseFixtureDays().getFirst().advice()).isEqualTo("Rien de particulier, profite de ta journée.");
    }

    @Test
    void rejectsAResponseWithoutHourlyDataForAPart() throws IOException {
        JsonNode response = fixture().path("forecastResponse").deepCopy();
        var hourly = (tools.jackson.databind.node.ObjectNode) response.path("hourly");
        hourly.putArray("time").add("2026-10-28T00:00");
        hourly.putArray("temperature_2m").add(10.0);
        hourly.putArray("weather_code").add(1);

        assertThatThrownBy(() -> parser.parseForecast(response, parisLocation(), true))
                .isInstanceOf(WeatherUnavailableException.class);
    }

    @Test
    void rejectsAResponseWithoutHourlyBlock() throws IOException {
        var response = (tools.jackson.databind.node.ObjectNode) fixture().path("forecastResponse").deepCopy();
        response.remove("hourly");

        assertThatThrownBy(() -> parser.parseForecast(response, parisLocation(), true))
                .isInstanceOf(WeatherUnavailableException.class);
    }

    private java.util.List<WeatherForecast.ForecastDay> parseFixtureDays() throws IOException {
        return parser.parseForecast(fixture().path("forecastResponse"), parisLocation(), true).days();
    }

    private WeatherLocation parisLocation() throws IOException {
        return parser.parseGeocoding(fixture().path("geocodingResponse")).getFirst();
    }

    private JsonNode fixture() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/integration/response-open-meteo.json")) {
            assertThat(input).isNotNull();
            return objectMapper.readTree(input);
        }
    }
}
