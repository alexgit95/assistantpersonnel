package com.assistant.apps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.assistant.integrations.ExternalApiModeService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ModeAwareWeatherGatewayTest {

    @Test
    void testModeUsesLocalWeatherDataWithoutCallingOpenMeteo() {
        WeatherGateway live = mock(WeatherGateway.class);
        ExternalApiModeService mode = mock(ExternalApiModeService.class);
        when(mode.isTestMode()).thenReturn(true);
        var mockGateway = new TestWeatherGateway(
                Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), ZoneOffset.UTC));
        var gateway = new ModeAwareWeatherGateway(live, mockGateway, mode);

        var cities = gateway.geocode("paris");
        var forecast = gateway.forecast(cities.getFirst(), 2);

        assertThat(cities).hasSize(1);
        assertThat(cities.getFirst().name()).isEqualTo("Paris");
        assertThat(forecast.current().description()).isEqualTo("Éclaircies");
        assertThat(forecast.days()).hasSize(2);
        verifyNoInteractions(live);
    }
}
