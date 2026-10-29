package com.assistant.apps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assistant.integrations.ExternalApiModeService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class ModeAwareWeatherGatewayTest {

    @Test
    void testModeUsesLocalWeatherDataWithoutCallingOpenMeteo() {
        WeatherGateway live = mock(WeatherGateway.class);
        ExternalApiModeService mode = mock(ExternalApiModeService.class);
        when(mode.isTestMode()).thenReturn(true);
        var mockGateway = new TestWeatherGateway(new DefaultResourceLoader(), new ObjectMapper(),
            new WeatherResponseParser());
        var gateway = new ModeAwareWeatherGateway(live, mockGateway, mode);

        var cities = gateway.geocode("Tokyo");
        var storedProfileLocation = new WeatherLocation(2996944, "Lyon", "Auvergne-Rhône-Alpes", "France",
            45.75, 4.85);
        var forecast = gateway.forecast(storedProfileLocation);

        assertThat(cities).hasSize(1);
        assertThat(cities.getFirst().name()).isEqualTo("Paris");
        assertThat(forecast.location().name()).isEqualTo("Paris");
        assertThat(forecast.current().description()).isEqualTo("Partiellement nuageux");
        assertThat(forecast.days()).hasSize(4);
        assertThat(forecast.testMode()).isTrue();
        verifyNoInteractions(live);
    }

    @Test
    void liveModeUsesOpenMeteoWithoutSubstitutingTheFixture() {
        WeatherGateway live = mock(WeatherGateway.class);
        ExternalApiModeService mode = mock(ExternalApiModeService.class);
        when(mode.isTestMode()).thenReturn(false);
        var lyon = new WeatherLocation(2996944, "Lyon", "Auvergne-Rhône-Alpes", "France", 45.75, 4.85);
        var expected = new WeatherForecast(lyon,
                new WeatherForecast.CurrentConditions(12, 10, "Pluie"), java.util.List.of());
        when(live.geocode("Lyon")).thenReturn(java.util.List.of(lyon));
        when(live.forecast(lyon)).thenReturn(expected);
        var mockGateway = new TestWeatherGateway(new DefaultResourceLoader(), new ObjectMapper(),
                new WeatherResponseParser());
        var gateway = new ModeAwareWeatherGateway(live, mockGateway, mode);

        assertThat(gateway.geocode("Lyon")).containsExactly(lyon);
        assertThat(gateway.forecast(lyon)).isEqualTo(expected);

        verify(live).geocode("Lyon");
        verify(live).forecast(lyon);
    }
}
