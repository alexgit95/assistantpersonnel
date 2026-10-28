package com.assistant.apps.transit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class TransitResponseParserTest {

    @Test
    void parsesStructuredLineIdsAndParisLocalValidityPeriods() throws Exception {
        var parser = new TransitResponseParser();
        var root = new ObjectMapper().readTree("""
                {"disruptions":[{"id":"d1","title":"RER A : interruption","severity":"BLOQUANTE",
                "message":"<p>Détail HTML PRIM</p>",
                "applicationPeriods":[{"begin":"20261028T120000","end":"20261028T130000"}],
                "impactedSections":[{"lineId":"line:IDFM:C01742"}]}]}
                """);

        var feed = parser.parse(root, Instant.parse("2026-10-28T11:00:00Z"));
        var disruption = feed.disruptions().getFirst();

        assertThat(disruption.lineIds()).containsExactly("line:IDFM:C01742");
        assertThat(disruption.message()).isEqualTo("<p>Détail HTML PRIM</p>");
        assertThat(disruption.hasImpactedSections()).isTrue();
        assertThat(disruption.activeAt(Instant.parse("2026-10-28T11:30:00Z"))).isTrue();
        assertThat(disruption.activeAt(Instant.parse("2026-10-28T12:00:00Z"))).isFalse();
        assertThat(disruption.periods().getFirst().begin())
                .isEqualTo(java.time.LocalDateTime.of(2026, 10, 28, 12, 0).atZone(ZoneId.of("Europe/Paris")).toInstant());
    }

    @Test
    void marksMissingImpactedSectionsForTitleOnlyFallback() throws Exception {
        var root = new ObjectMapper().readTree("""
                {"disruptions":[{"id":"d1","title":"Ligne J : travaux","applicationPeriods":[]}]}
                """);

        var disruption = new TransitResponseParser().parse(root, Instant.EPOCH).disruptions().getFirst();

        assertThat(disruption.hasImpactedSections()).isFalse();
        assertThat(disruption.lineIds()).isEmpty();
    }
}
