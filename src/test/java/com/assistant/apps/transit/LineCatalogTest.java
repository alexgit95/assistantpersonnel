package com.assistant.apps.transit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.ObjectMapper;

class LineCatalogTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LineCatalog catalog = new LineCatalog(new DefaultResourceLoader(), objectMapper,
            Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void servesActiveLinesFromTheBundledLocalReferential() {
        TransitLine rerA = catalog.find("C01742").orElseThrow();
        TransitLine transilienJ = catalog.find("C01739").orElseThrow();

        assertThat(rerA.label()).isEqualTo("RER A");
        assertThat(transilienJ.label()).isEqualTo("Transilien J");
        assertThat(catalog.search("RER A")).contains(rerA);
        assertThat(catalog.search("ligne j")).contains(transilienJ);
        assertThat(catalog.search("RER")).isNotEmpty();
    }

    @Test
    void allStructuredLineIdsInThePrimFixtureResolveToTheReferential() throws Exception {
        try (var input = getClass().getResourceAsStream("/integration/response_info-traffic.json")) {
            var root = objectMapper.readTree(input);
            Set<String> ids = new java.util.HashSet<>();
            for (var disruption : root.path("disruptions")) {
                for (var section : disruption.path("impactedSections")) {
                    String primId = section.path("lineId").asString();
                    if (primId.startsWith("line:IDFM:")) {
                        ids.add(primId.substring("line:IDFM:".length()));
                    }
                }
            }
            assertThat(ids).hasSize(114);
            assertThat(ids).allSatisfy(id -> assertThat(catalog.isActive(id)).isTrue());
        }
    }
}
