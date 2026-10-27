package com.assistant.apps;

import java.util.List;

public interface WeatherGateway {

    List<WeatherLocation> geocode(String query);

    WeatherForecast forecast(WeatherLocation location, int forecastDays);
}