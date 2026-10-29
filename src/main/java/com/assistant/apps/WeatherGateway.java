package com.assistant.apps;

import java.util.List;

public interface WeatherGateway {

    /** Duree figee des previsions : aujourd'hui et les trois jours suivants. */
    int FORECAST_DAYS = 4;

    default boolean isTestMode() {
        return false;
    }

    List<WeatherLocation> geocode(String query);

    WeatherForecast forecast(WeatherLocation location);
}