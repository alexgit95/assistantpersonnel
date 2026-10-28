package com.assistant.apps;

import com.assistant.integrations.ExternalApiModeService;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class ModeAwareWeatherGateway implements WeatherGateway {

    private final WeatherGateway liveGateway;
    private final TestWeatherGateway testGateway;
    private final ExternalApiModeService mode;

    public ModeAwareWeatherGateway(@Qualifier("openMeteoClient") WeatherGateway liveGateway,
                                   TestWeatherGateway testGateway, ExternalApiModeService mode) {
        this.liveGateway = liveGateway;
        this.testGateway = testGateway;
        this.mode = mode;
    }

    @Override
    public boolean isTestMode() {
        return mode.isTestMode();
    }

    @Override
    public List<WeatherLocation> geocode(String query) {
        return mode.isTestMode() ? testGateway.geocode(query) : liveGateway.geocode(query);
    }

    @Override
    public WeatherForecast forecast(WeatherLocation location, int forecastDays) {
        boolean testMode = mode.isTestMode();
        WeatherForecast forecast = testMode ? testGateway.forecast(location, forecastDays)
            : liveGateway.forecast(location, forecastDays);
        return new WeatherForecast(forecast.location(), forecast.current(), forecast.days(), testMode);
    }
}
