package com.assistant.apps;

import java.util.List;

public interface WeatherGateway {

    default boolean isTestMode() {
        return false;
    }

    List<WeatherLocation> geocode(String query);

    WeatherForecast forecast(WeatherLocation location, int forecastDays);
}