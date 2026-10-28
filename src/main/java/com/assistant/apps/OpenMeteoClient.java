package com.assistant.apps;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.StringJoiner;
import com.assistant.proxy.OutboundHttp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class OpenMeteoClient implements WeatherGateway {

    private static final Logger LOG = LoggerFactory.getLogger(OpenMeteoClient.class);

    private final RestClient restClient;
    private final WeatherResponseParser responseParser;

    @Autowired
    public OpenMeteoClient(WeatherResponseParser responseParser) {
        this(defaultClient(), responseParser);
    }

    OpenMeteoClient(RestClient restClient) {
        this(restClient, new WeatherResponseParser());
    }

    OpenMeteoClient(RestClient restClient, WeatherResponseParser responseParser) {
        this.responseParser = responseParser;
        this.restClient = restClient;
    }

    private static RestClient defaultClient() {
        HttpClient httpClient = OutboundHttp.clientBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public List<WeatherLocation> geocode(String query) {
        URI uri = org.springframework.web.util.UriComponentsBuilder
                .fromUriString("https://geocoding-api.open-meteo.com/v1/search")
                .queryParam("name", query)
                .queryParam("count", 10)
                .queryParam("language", "fr")
                .queryParam("format", "json")
                .build().encode().toUri();
        try {
            return responseParser.parseGeocoding(restClient.get().uri(uri).retrieve().body(tools.jackson.databind.JsonNode.class));
        } catch (RestClientException ex) {
            logProviderFailure("geocodage", ex);
            throw new WeatherUnavailableException(ex);
        }
    }

    @Override
    public WeatherForecast forecast(WeatherLocation location, int forecastDays) {
        URI uri = org.springframework.web.util.UriComponentsBuilder
                .fromUriString("https://api.open-meteo.com/v1/forecast")
                .queryParam("latitude", location.latitude())
                .queryParam("longitude", location.longitude())
                .queryParam("current", "temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m")
                .queryParam("daily", "weather_code,temperature_2m_max,temperature_2m_min")
                .queryParam("forecast_days", forecastDays)
                .queryParam("timezone", "auto")
                .build().encode().toUri();
        try {
            var response = restClient.get().uri(uri).retrieve().body(tools.jackson.databind.JsonNode.class);
            return responseParser.parseForecast(response, location, false);
        } catch (RestClientException ex) {
            logProviderFailure("previsions", ex);
            throw new WeatherUnavailableException(ex);
        }
    }

    private static void logProviderFailure(String operation, RestClientException exception) {
        Throwable rootCause = exception;
        StringJoiner causes = new StringJoiner(" -> ");
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            causes.add(cause.getClass().getSimpleName());
            rootCause = cause;
        }
        if (exception instanceof RestClientResponseException responseException) {
            LOG.warn("Open-Meteo failure during {}: HTTP {}, causes={}",
                    operation, responseException.getStatusCode().value(), causes);
            return;
        }
        String detail = rootCause.getMessage();
        if (detail != null) {
            detail = detail.replaceAll("https?://[^\\s\\\"']+", "[URL REDACTED]");
        }
        LOG.warn("Open-Meteo failure during {}: causes={}, detail={}",
                operation, causes, detail == null ? "indisponible" : detail);
    }

}