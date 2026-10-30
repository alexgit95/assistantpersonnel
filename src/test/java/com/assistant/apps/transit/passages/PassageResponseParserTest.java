package com.assistant.apps.transit.passages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class PassageResponseParserTest {

    private final PassageResponseParser parser = new PassageResponseParser();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void sortsPassagesAndPrefersArrivalOverDepartureTime() throws Exception {
        var feed = parser.parse(mapper.readTree("""
                {"Siri":{"ServiceDelivery":{"ResponseTimestamp":"2026-10-30T08:12:30.210Z",
                "StopMonitoringDelivery":[{"ResponseTimestamp":"2026-10-30T08:12:30.213Z","MonitoredStopVisit":[
                {"MonitoredVehicleJourney":{"LineRef":{"value":"STIF:Line::C01154:"},
                 "DestinationName":[{"value":"Porte d'Orléans"}],
                 "MonitoredCall":{"ExpectedDepartureTime":"2026-10-30T08:35:28.000Z","DepartureStatus":"onTime"}}},
                {"MonitoredVehicleJourney":{"LineRef":{"value":"STIF:Line::C01400:"},
                 "MonitoredCall":{"ExpectedArrivalTime":"2026-10-30T08:20:00.000Z",
                  "ExpectedDepartureTime":"2026-10-30T08:50:00.000Z",
                  "DestinationDisplay":[{"value":"Gare"}]}}}]}]}}}
                """), Instant.parse("2026-10-30T09:00:00Z"));

        assertThat(feed.updatedAt()).isEqualTo(Instant.parse("2026-10-30T08:12:30.213Z"));
        assertThat(feed.passages()).extracting(Passage::lineId).containsExactly("C01400", "C01154");
        assertThat(feed.passages().getFirst().expectedAt()).isEqualTo(Instant.parse("2026-10-30T08:20:00Z"));
        assertThat(feed.passages().getFirst().destination()).isEqualTo("Gare");
        assertThat(feed.passages().get(1).destination()).isEqualTo("Porte d'Orléans");
        assertThat(feed.passages().get(1).status()).isEqualTo("onTime");
    }

    @Test
    void ignoresVisitsWithoutAUsableTimeAndFallsBackToFetchTime() throws Exception {
        var feed = parser.parse(mapper.readTree("""
                {"Siri":{"ServiceDelivery":{"StopMonitoringDelivery":[{"MonitoredStopVisit":[
                {"MonitoredVehicleJourney":{"MonitoredCall":{"ExpectedDepartureTime":"pas une date"}}},
                {"MonitoredVehicleJourney":{"MonitoredCall":{}}}]}]}}}
                """), Instant.parse("2026-10-30T09:00:00Z"));

        assertThat(feed.passages()).isEmpty();
        assertThat(feed.updatedAt()).isEqualTo(Instant.parse("2026-10-30T09:00:00Z"));
    }

    @Test
    void rejectsAResponseWithoutStopMonitoringDelivery() throws Exception {
        assertThatThrownBy(() -> parser.parse(mapper.readTree("{\"Siri\":{}}"), Instant.now()))
                .isInstanceOf(PassageUnavailableException.class);
        assertThatThrownBy(() -> parser.parse(null, Instant.now()))
                .isInstanceOf(PassageUnavailableException.class);
    }

    @Test
    void extractsTheCodifLigneFromTheLineRef() {
        assertThat(PassageResponseParser.lineId("STIF:Line::C01154:")).isEqualTo("C01154");
        assertThat(PassageResponseParser.lineId(null)).isNull();
        assertThat(PassageResponseParser.lineId("::")).isNull();
    }
}
