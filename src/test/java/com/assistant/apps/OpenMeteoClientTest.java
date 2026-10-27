package com.assistant.apps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestClient;

@ExtendWith(OutputCaptureExtension.class)
class OpenMeteoClientTest {

    @Test
    void parsesGeocodingAndFrenchForecastResponses() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenMeteoClient client = new OpenMeteoClient(builder.build());

        server.expect(requestTo(org.hamcrest.Matchers.containsString("name=Lyon")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"results":[{"id":123,"name":"Lyon","admin1":"Auvergne-Rhône-Alpes",
                        "country":"France","latitude":45.75,"longitude":4.85}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/v1/forecast")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"current":{"temperature_2m":18.2,"apparent_temperature":17.4,
                        "relative_humidity_2m":62,"wind_speed_10m":8.1,"weather_code":2},
                        "daily":{"time":["2026-10-27"],"weather_code":[3],
                        "temperature_2m_min":[11.1],"temperature_2m_max":[19.3]}}
                        """, MediaType.APPLICATION_JSON));

        WeatherLocation location = client.geocode("Lyon").getFirst();
        WeatherForecast forecast = client.forecast(location, 5);

        assertThat(location).isEqualTo(new WeatherLocation(123, "Lyon", "Auvergne-Rhône-Alpes",
                "France", 45.75, 4.85));
        assertThat(forecast.current().temperatureC()).isEqualTo(18.2);
        assertThat(forecast.current().description()).isEqualTo("Partiellement nuageux");
        assertThat(forecast.days()).singleElement().satisfies(day -> {
            assertThat(day.date().toString()).isEqualTo("2026-10-27");
            assertThat(day.description()).isEqualTo("Couvert");
        });
        server.verify();
    }

    @Test
    void logsProviderStatusWithoutLoggingTheCityQuery(CapturedOutput output) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenMeteoClient client = new OpenMeteoClient(builder.build());
        server.expect(requestTo(org.hamcrest.Matchers.containsString("name=Lyon")))
                .andRespond(MockRestResponseCreators.withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.geocode("Lyon")).isInstanceOf(WeatherUnavailableException.class);

        assertThat(output.getOut()).contains("HTTP 503").doesNotContain("name=Lyon");
        server.verify();
    }

    @Test
    void logsNetworkCauseAndRedactsTheRequestUrl(CapturedOutput output) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenMeteoClient client = new OpenMeteoClient(builder.build());
        server.expect(requestTo(org.hamcrest.Matchers.containsString("name=Lyon")))
                .andRespond(MockRestResponseCreators.withException(
                        new java.io.IOException("Could not connect to https://example.test/?name=Lyon")));

        assertThatThrownBy(() -> client.geocode("Lyon")).isInstanceOf(WeatherUnavailableException.class);

        assertThat(output.getOut()).contains("IOException", "[URL REDACTED]").doesNotContain("name=Lyon");
        server.verify();
    }
}