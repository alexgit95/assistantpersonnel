package com.assistant.apps;

import java.time.LocalDate;
import java.util.List;

public record WeatherForecast(WeatherLocation location, CurrentConditions current, List<ForecastDay> days,
                              boolean testMode, int utcOffsetSeconds) {

    public WeatherForecast(WeatherLocation location, CurrentConditions current, List<ForecastDay> days) {
        this(location, current, days, false, 0);
    }

    public WeatherForecast(WeatherLocation location, CurrentConditions current, List<ForecastDay> days,
                           boolean testMode) {
        this(location, current, days, testMode, 0);
    }

    public record CurrentConditions(double temperatureC, double feelsLikeC, String description) {
    }

    /** Temperature et code WMO representatifs d'une plage (matin 6-12 h, apres-midi 12-18 h). */
    public record DayPart(double temperatureC, int code, String description) {
    }

    public record ForecastDay(LocalDate date, DayPart morning, DayPart afternoon, int rainChancePercent,
                              int maxGustKmh, boolean windAlert, String advice) {
    }
}