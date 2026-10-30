package com.assistant.apps.transit.passages;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.ObjectMapper;

class MockPassageGatewayTest {

    private final Instant now = Instant.parse("2026-11-02T10:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    private MockPassageGateway gateway(long seed) {
        return new MockPassageGateway(new DefaultResourceLoader(), new PassageResponseParser(), new ObjectMapper(),
                clock, new Random(seed));
    }

    @Test
    void rebasesTheFixtureOnFutureSortedTimes() {
        PassageFeed feed = gateway(7).fetch("24377");

        assertThat(feed.updatedAt()).isEqualTo(now);
        assertThat(feed.passages()).hasSize(MockPassageGateway.VISITS);
        assertThat(feed.passages()).extracting(Passage::expectedAt).allMatch(time -> time.isAfter(now))
                .isSorted();
        assertThat(feed.passages()).extracting(Passage::lineId).containsOnly("C01154");
    }

    @Test
    void variesTheTimesWithTheRandomSourceButNotAcrossTheSameSeed() {
        assertThat(gateway(1).fetch("1").passages()).isEqualTo(gateway(1).fetch("2").passages());
        assertThat(gateway(1).fetch("1").passages()).isNotEqualTo(gateway(2).fetch("1").passages());
    }
}
