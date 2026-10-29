package com.assistant.apps.transit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PrimClientTest {

    @Test
    void callsTheGlobalEndpointWithTheApiKeyHeader() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var client = new PrimClient(builder.build(), new TransitResponseParser(), keys("prim-secret"),
                Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), ZoneOffset.UTC));
        server.expect(requestTo(PrimClient.ENDPOINT.toString()))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("apikey", "prim-secret"))
                .andRespond(withSuccess("""
                        {"disruptions":[{"id":"d1","title":"RER A : interruption",
                        "severity":"BLOQUANTE","applicationPeriods":[],"impactedSections":[
                        {"lineId":"line:IDFM:C01742"}]}]}
                        """, MediaType.APPLICATION_JSON));

        TransitFeed feed = client.fetchDisruptions();

        assertThat(feed.disruptions()).hasSize(1);
        assertThat(feed.disruptions().getFirst().lineIds()).containsExactly("line:IDFM:C01742");
        assertThat(feed.fetchedAt()).isEqualTo(Instant.parse("2026-10-28T12:00:00Z"));
        server.verify();
    }

    @Test
    void rejectsMissingApiKeyWithoutSendingARequest() {
        var client = new PrimClient(RestClient.builder().build(), new TransitResponseParser(), keys(null),
                Clock.systemUTC());

        assertThatThrownBy(client::fetchDisruptions).isInstanceOf(TransitUnavailableException.class);
    }

    private static PrimKeyService keys(String apiKey) {
        PrimKeyService keys = mock(PrimKeyService.class);
        when(keys.currentKey()).thenReturn(Optional.ofNullable(apiKey));
        return keys;
    }
}
