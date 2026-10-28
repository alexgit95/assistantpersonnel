package com.assistant.apps;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class WeatherResponseParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WeatherResponseParser parser = new WeatherResponseParser();

    @Test
    void parsesTheBundledRawOpenMeteoFixtureAsParisForFiveDays() throws IOException {
        JsonNode fixture;
        try (InputStream input = getClass().getResourceAsStream("/integration/response-open-meteo.json")) {
            assertThat(input).isNotNull();
            fixture = objectMapper.readTree(input);
        }
        var locations = parser.parseGeocoding(fixture.path("geocodingResponse"));
        var forecast = parser.parseForecast(fixture.path("forecastResponse"),
                locations.getFirst(), true);

        assertThat(locations).singleElement().extracting(WeatherLocation::name).isEqualTo("Paris");
        assertThat(forecast.location()).isEqualTo(locations.getFirst());
        assertThat(forecast.current().temperatureC()).isEqualTo(18.4);
        assertThat(forecast.current().description()).isEqualTo("Partiellement nuageux");
        assertThat(forecast.days()).hasSize(5);
        assertThat(forecast.days().getFirst().date().toString()).isEqualTo("2026-10-28");
        assertThat(forecast.testMode()).isTrue();
    }
}
