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
                .andExpect(requestTo(org.hamcrest.Matchers.containsString("forecast_days=4")))
                .andExpect(requestTo(org.hamcrest.Matchers.containsString("hourly=temperature_2m")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"current":{"temperature_2m":18.2,"apparent_temperature":17.4,"weather_code":2},
                        "hourly":{"time":["2026-10-27T06:00","2026-10-27T07:00","2026-10-27T08:00","2026-10-27T09:00",
                        "2026-10-27T10:00","2026-10-27T11:00","2026-10-27T12:00","2026-10-27T13:00","2026-10-27T14:00",
                        "2026-10-27T15:00","2026-10-27T16:00","2026-10-27T17:00"],
                        "temperature_2m":[11.1,12,13,14,15,16,17,18,19.3,18,17,16],
                        "weather_code":[2,2,2,2,2,2,3,3,3,3,3,61],
                        "precipitation_probability":[10,10,10,10,10,10,20,20,30,60,60,60],
                        "wind_gusts_10m":[20,20,20,20,20,20,20,20,20,20,20,20]},
                        "daily":{"time":["2026-10-27"],"precipitation_probability_max":[60],
                        "wind_gusts_10m_max":[35]}}
                        """, MediaType.APPLICATION_JSON));

        WeatherLocation location = client.geocode("Lyon").getFirst();
        WeatherForecast forecast = client.forecast(location);

        assertThat(location).isEqualTo(new WeatherLocation(123, "Lyon", "Auvergne-Rhône-Alpes",
                "France", 45.75, 4.85));
        assertThat(forecast.current().temperatureC()).isEqualTo(18.2);
        assertThat(forecast.current().feelsLikeC()).isEqualTo(17.4);
        assertThat(forecast.current().description()).isEqualTo("Partiellement nuageux");
        assertThat(forecast.days()).singleElement().satisfies(day -> {
            assertThat(day.date().toString()).isEqualTo("2026-10-27");
            assertThat(day.morning().temperatureC()).isEqualTo(11.1);
            assertThat(day.morning().code()).isEqualTo(2);
            assertThat(day.afternoon().temperatureC()).isEqualTo(19.3);
            assertThat(day.afternoon().description()).isEqualTo("Pluie");
            assertThat(day.rainChancePercent()).isEqualTo(60);
            assertThat(day.maxGustKmh()).isEqualTo(35);
            assertThat(day.windAlert()).isFalse();
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