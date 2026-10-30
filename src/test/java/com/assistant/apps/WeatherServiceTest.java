package com.assistant.apps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assistant.apps.ProfileAppService.EnabledApp;
import com.assistant.common.ApiException;
import com.assistant.config.AssistantProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

class WeatherServiceTest {

    private final WeatherGateway gateway = mock(WeatherGateway.class);
    private final ProfileAppService profileApps = mock(ProfileAppService.class);
    private MutableClock clock;
    private WeatherService service;

    @BeforeEach
    void setUp() {
        var properties = new AssistantProperties.Weather(30);
        clock = new MutableClock(Instant.parse("2026-10-27T10:00:00Z"));
        service = new WeatherService(gateway, profileApps, new ObjectMapper(),
            clock, new AssistantProperties(null, null, null, null, properties, null, null, null, null,
                null, null, null, null));
    }

    @Test
    void sharesForecastCacheBetweenProfilesAtTheSameCoordinates() {
        WeatherLocation location = new WeatherLocation(1, "Lyon", "Auvergne-Rhône-Alpes", "France", 45.75, 4.85);
        var config = "{\"location\":{\"id\":1,\"name\":\"Lyon\",\"region\":\"Auvergne-Rhône-Alpes\","
                + "\"country\":\"France\",\"latitude\":45.75,\"longitude\":4.85}}";
        when(profileApps.requireEnabled(1L, "weather")).thenReturn(new EnabledApp(new WeatherAssistantApp(), 0, config));
        when(profileApps.requireEnabled(2L, "weather")).thenReturn(new EnabledApp(new WeatherAssistantApp(), 0, config));
        WeatherForecast forecast = new WeatherForecast(location,
            new WeatherForecast.CurrentConditions(15, 14, "Ciel dégagé"), List.of(), false, 3600,
            List.of(new WeatherForecast.HourlyTemperature("2026-10-27T11:00", 13.5)),
            "2026-10-27T10:00");
        when(gateway.forecast(location)).thenReturn(forecast);

        assertThat(service.forecast(1L)).isEqualTo(forecast);
        assertThat(service.forecast(2L)).isEqualTo(forecast);

        verify(gateway).forecast(location);
    }

    @Test
    void cachesCitySearchesWithoutCaseOrWhitespaceDifferences() {
        WeatherLocation lyon = new WeatherLocation(1, "Lyon", null, "France", 45.75, 4.85);
        when(gateway.geocode("Lyon")).thenReturn(List.of(lyon));

        assertThat(service.searchCities("Lyon")).containsExactly(lyon);
        assertThat(service.searchCities(" lyon ")).containsExactly(lyon);

        verify(gateway).geocode("Lyon");
    }

    @Test
    void reportsUnavailableProviderAsFrenchServiceUnavailableError() {
        when(gateway.geocode("Lyon")).thenThrow(new WeatherUnavailableException(new IllegalStateException()));

        assertThatThrownBy(() -> service.searchCities("Lyon"))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void refreshesForecastWhenTheThirtyMinuteCacheExpires() {
        WeatherLocation location = new WeatherLocation(1, "Lyon", null, "France", 45.75, 4.85);
        String config = "{\"location\":{\"id\":1,\"name\":\"Lyon\",\"country\":\"France\","
                + "\"latitude\":45.75,\"longitude\":4.85}}";
        when(profileApps.requireEnabled(1L, "weather"))
                .thenReturn(new EnabledApp(new WeatherAssistantApp(), 0, config));
        when(gateway.forecast(location)).thenReturn(new WeatherForecast(location,
                new WeatherForecast.CurrentConditions(15, 14, "Ciel dégagé"), List.of()));

        service.forecast(1L);
        clock.advance(Duration.ofMinutes(30));
        service.forecast(1L);

        org.mockito.Mockito.verify(gateway, org.mockito.Mockito.times(2)).forecast(location);
    }

        @Test
        void dropsACachedForecastWhoseFirstDayIsBeforeTheLocalToday() {
        var longCache = new WeatherService(gateway, profileApps, new ObjectMapper(), clock,
            new AssistantProperties(null, null, null, null, new AssistantProperties.Weather(1440), null, null, null, null,
                null, null, null, null));
        WeatherLocation location = new WeatherLocation(1, "Lyon", null, "France", 45.75, 4.85);
        String config = "{\"location\":{\"id\":1,\"name\":\"Lyon\",\"country\":\"France\","
            + "\"latitude\":45.75,\"longitude\":4.85}}";
        when(profileApps.requireEnabled(1L, "weather"))
            .thenReturn(new EnabledApp(new WeatherAssistantApp(), 0, config));
        var part = new WeatherForecast.DayPart(10, 1, "Principalement dégagé");
        var yesterday = new WeatherForecast.ForecastDay(java.time.LocalDate.parse("2026-10-27"), part, part, 0, 0,
            false, "Rien de particulier, profite de ta journée.");
        when(gateway.forecast(location)).thenReturn(new WeatherForecast(location,
            new WeatherForecast.CurrentConditions(10, 9, "Ciel dégagé"), List.of(yesterday), false, 0));

        longCache.forecast(1L);
        longCache.forecast(1L);
        org.mockito.Mockito.verify(gateway).forecast(location);

        clock.advance(Duration.ofHours(14));
        longCache.forecast(1L);

        org.mockito.Mockito.verify(gateway, org.mockito.Mockito.times(2)).forecast(location);
        }

        @Test
        void doesNotReuseTestForecastAfterSwitchingBackToLiveMode() {
        WeatherLocation location = new WeatherLocation(1, "Lyon", null, "France", 45.75, 4.85);
        String config = "{\"location\":{\"id\":1,\"name\":\"Lyon\",\"country\":\"France\","
            + "\"latitude\":45.75,\"longitude\":4.85}}";
        when(profileApps.requireEnabled(1L, "weather"))
            .thenReturn(new EnabledApp(new WeatherAssistantApp(), 0, config));
        when(gateway.isTestMode()).thenReturn(true, false);
        WeatherForecast simulated = new WeatherForecast(location,
            new WeatherForecast.CurrentConditions(18, 17, "Éclaircies"), List.of(), true);
        WeatherForecast live = new WeatherForecast(location,
            new WeatherForecast.CurrentConditions(12, 10, "Pluie"), List.of());
        when(gateway.forecast(location)).thenReturn(simulated, live);

        assertThat(service.forecast(1L).testMode()).isTrue();
        assertThat(service.forecast(1L).testMode()).isFalse();

        org.mockito.Mockito.verify(gateway, org.mockito.Mockito.times(2)).forecast(location);
        }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }

        void advance(Duration duration) {
            current = current.plus(duration);
        }
    }
}