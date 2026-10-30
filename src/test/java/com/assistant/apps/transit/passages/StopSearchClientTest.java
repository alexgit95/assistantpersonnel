package com.assistant.apps.transit.passages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.assistant.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

class StopSearchClientTest {

    private static final String BODY = """
            {"total_count":2,"results":[
            {"arrid":"39713","arrname":"René Alazard - Sadi Carnot","arrtown":"Bagnolet","arrtype":"bus"},
            {"arrid":"abc","arrname":"Identifiant invalide","arrtown":"X","arrtype":"bus"},
            {"arrid":"2140","arrname":"Carnot","arrtown":null,"arrtype":"bus"}]}
            """;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-30T09:00:00Z"));
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final StopSearchClient client = new StopSearchClient(builder.build(), clock);

    @Test
    void searchesByNameAndKeepsOnlyStopsWithANumericIdentifier() {
        server.expect(query("where=search(arrname,\"carnot\")")).andExpect(method(HttpMethod.GET))
                .andExpect(query("select=arrid,arrname,arrtown,arrtype")).andExpect(query("limit=20"))
                .andRespond(withSuccess(BODY, MediaType.APPLICATION_JSON));

        var stops = client.search("  carnot ");

        assertThat(stops).containsExactly(
                new TransitStop("39713", "René Alazard - Sadi Carnot", "Bagnolet", "bus"),
                new TransitStop("2140", "Carnot", "", "bus"));
        server.verify();
    }

    @Test
    void reusesTheCachedAnswerUntilItExpires() {
        server.expect(query("search(arrname")).andRespond(withSuccess(BODY, MediaType.APPLICATION_JSON));
        server.expect(query("search(arrname")).andRespond(withSuccess(BODY, MediaType.APPLICATION_JSON));

        client.search("carnot");
        client.search("CARNOT");
        clock.advance(StopSearchClient.CACHE_TTL.plusSeconds(1));
        client.search("carnot");

        server.verify();
    }

    @Test
    void escapesQuotesAndBackslashesInTheFilter() {
        server.expect(query("where=search(arrname,\"a\\\"b\\\\c\")"))
                .andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));

        assertThat(client.search("a\"b\\c")).isEmpty();
        assertThat(StopSearchClient.escape("\"\\")).isEqualTo("\\\"\\\\");
        server.verify();
    }

    @Test
    void rejectsTooShortOrTooLongQueriesWithoutCallingTheApi() {
        assertThatThrownBy(() -> client.search("ab")).isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> client.search(null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> client.search("x".repeat(101))).isInstanceOf(ApiException.class);
        server.verify();
    }

    @Test
    void reportsUnavailabilityWhenThePublicApiFails() {
        server.expect(query("search(arrname")).andRespond(withServerError());

        assertThatThrownBy(() -> client.search("carnot")).isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    private static RequestMatcher query(String expected) {
        return request -> assertThat(UriUtils.decode(request.getURI().getRawQuery(), StandardCharsets.UTF_8))
                .contains(expected);
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        MutableClock(Instant current) {
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
