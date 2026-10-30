package com.assistant.apps;

import java.util.List;
import java.io.IOException;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class TestWeatherGateway {

        static final String FIXTURE = "classpath:integration/response-open-meteo.json";
        private final WeatherLocation location;
        private final WeatherForecast forecast;

        public TestWeatherGateway(ResourceLoader resources, ObjectMapper objectMapper,
                                                          WeatherResponseParser responseParser) {
                try (var input = resources.getResource(FIXTURE).getInputStream()) {
                        JsonNode fixture = objectMapper.readTree(input);
                        this.location = location(fixture.path("location"));
                        List<WeatherLocation> results = responseParser.parseGeocoding(fixture.path("geocodingResponse"));
                        if (results.size() != 1 || !location.equals(results.getFirst())) {
                                throw new IllegalStateException("Le fixture Open-Meteo doit contenir uniquement Paris.");
                        }
                        this.forecast = responseParser.parseForecast(fixture.path("forecastResponse"), location, true);
                } catch (IOException exception) {
                        throw new IllegalStateException("Le fixture Open-Meteo de test est introuvable.", exception);
                }
    }

    public List<WeatherLocation> geocode(String query) {
                return List.of(location);
    }

    public WeatherForecast forecast(WeatherLocation location) {
                return new WeatherForecast(this.location, forecast.current(), forecast.days(), true,
                                forecast.utcOffsetSeconds(), forecast.hourly(), forecast.currentTime());
        }

        private static WeatherLocation location(JsonNode node) {
                WeatherLocation location = new WeatherLocation(node.path("id").asLong(), node.path("name").asString(),
                                text(node, "region"), text(node, "country"), node.path("latitude").asDouble(Double.NaN),
                                node.path("longitude").asDouble(Double.NaN));
                if (location.id() < 1 || location.name().isBlank() || location.country() == null
                                || !Double.isFinite(location.latitude()) || !Double.isFinite(location.longitude())) {
                        throw new IllegalStateException("La localisation Paris du fixture Open-Meteo est invalide.");
                }
                return location;
        }

        private static String text(JsonNode node, String field) {
                JsonNode value = node.get(field);
                return value == null || value.isNull() ? null : value.asString();
    }
}
