package com.assistant.apps;

import java.time.LocalDate;
import java.util.List;

public record WeatherForecast(WeatherLocation location, CurrentConditions current, List<ForecastDay> days,
                              boolean testMode) {

    public WeatherForecast(WeatherLocation location, CurrentConditions current, List<ForecastDay> days) {
        this(location, current, days, false);
    }

    public record CurrentConditions(double temperatureC, double feelsLikeC, int humidityPercent,
                                    double windKmh, String description) {
    }

    public record ForecastDay(LocalDate date, double minC, double maxC, String description) {
    }
}