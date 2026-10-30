package com.assistant.apps.transit.passages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.assistant.apps.transit.PrimKeyService;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

class PrimPassageClientTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-30T09:00:00Z"), ZoneOffset.UTC);

    @Test
    void callsStopMonitoringWithTheStopPointRefAndTheApiKeyHeader() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var client = new PrimPassageClient(builder.build(), new PassageResponseParser(), keys("prim-secret"), clock);
        server.expect(queryContains("MonitoringRef=STIF:StopPoint:Q:24377:"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("apikey", "prim-secret"))
                .andRespond(withSuccess("""
                        {"Siri":{"ServiceDelivery":{"StopMonitoringDelivery":[{"MonitoredStopVisit":[
                        {"MonitoredVehicleJourney":{"LineRef":{"value":"STIF:Line::C01154:"},
                        "MonitoredCall":{"ExpectedDepartureTime":"2026-10-30T09:05:00.000Z"}}}]}]}}}
                        """, MediaType.APPLICATION_JSON));

        PassageFeed feed = client.fetch("24377");

        assertThat(feed.passages()).hasSize(1);
        assertThat(feed.passages().getFirst().lineId()).isEqualTo("C01154");
        server.verify();
    }

    @Test
    void flagsAnUnknownStopOnBadRequestAndATransientFailureOtherwise() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var client = new PrimPassageClient(builder.build(), new PassageResponseParser(), keys("k"), clock);
        server.expect(queryContains("MonitoringRef")).andRespond(withStatus(HttpStatus.BAD_REQUEST));
        server.expect(queryContains("MonitoringRef")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.fetch("1")).isInstanceOfSatisfying(PassageUnavailableException.class,
                exception -> assertThat(exception.unknownStop()).isTrue());
        assertThatThrownBy(() -> client.fetch("1")).isInstanceOfSatisfying(PassageUnavailableException.class,
                exception -> assertThat(exception.unknownStop()).isFalse());
    }

    @Test
    void rejectsMissingApiKeyWithoutSendingARequest() {
        var client = new PrimPassageClient(RestClient.builder().build(), new PassageResponseParser(), keys(null), clock);

        assertThatThrownBy(() -> client.fetch("1")).isInstanceOf(PassageUnavailableException.class);
    }

    private static RequestMatcher queryContains(String expected) {
        return request -> assertThat(UriUtils.decode(request.getURI().getRawQuery(), StandardCharsets.UTF_8))
                .contains(expected);
    }

    private static PrimKeyService keys(String apiKey) {
        PrimKeyService keys = mock(PrimKeyService.class);
        when(keys.currentKey()).thenReturn(Optional.ofNullable(apiKey));
        return keys;
    }
}
