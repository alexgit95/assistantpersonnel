package com.assistant.apps;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class TestWeatherGateway {

    private static final List<WeatherLocation> LOCATIONS = List.of(
            new WeatherLocation(2988507, "Paris", "Île-de-France", "France", 48.8566, 2.3522),
            new WeatherLocation(2996944, "Lyon", "Auvergne-Rhône-Alpes", "France", 45.75, 4.85));

    private final Clock clock;

    public TestWeatherGateway(Clock clock) {
        this.clock = clock;
    }

    public List<WeatherLocation> geocode(String query) {
        String normalized = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return LOCATIONS.stream().filter(location -> location.name().toLowerCase(Locale.ROOT).contains(normalized))
                .toList();
    }

    public WeatherForecast forecast(WeatherLocation location, int forecastDays) {
        List<WeatherForecast.ForecastDay> days = java.util.stream.IntStream.range(0, forecastDays)
                .mapToObj(index -> new WeatherForecast.ForecastDay(LocalDate.now(clock).plusDays(index),
                        12 + index, 18 + index, index % 2 == 0 ? "Éclaircies" : "Ciel dégagé"))
                .toList();
        return new WeatherForecast(location,
                new WeatherForecast.CurrentConditions(18, 17, 58, 9, "Éclaircies"), days);
    }
}
