package com.assistant.quota;

import static org.assertj.core.api.Assertions.assertThat;

import com.assistant.config.AssistantProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class QuietHoursTest {

    private static QuietHours at(String instant, String from, String to) {
        var prim = new AssistantProperties.Prim(750, 15, from, to);
        var properties = new AssistantProperties(null, null, null, null, null, null, null, null, prim);
        return new QuietHours(properties, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    @Test
    void nightWindowCrossingMidnightIsPausedInParisTime() {
        // 23 h 30 a Paris (UTC+1 en hiver) : en pause.
        assertThat(at("2026-12-10T22:30:00Z", "22:00", "05:00").paused()).isTrue();
        // 4 h 59 a Paris : en pause ; 5 h 00 : reprise.
        assertThat(at("2026-12-10T03:59:00Z", "22:00", "05:00").paused()).isTrue();
        assertThat(at("2026-12-10T04:00:00Z", "22:00", "05:00").paused()).isFalse();
        // 21 h 59 a Paris : actif ; 22 h 00 : pause.
        assertThat(at("2026-12-10T20:59:00Z", "22:00", "05:00").paused()).isFalse();
        assertThat(at("2026-12-10T21:00:00Z", "22:00", "05:00").paused()).isTrue();
    }

    @Test
    void summerTimeShiftsTheWindowByParisOffset() {
        // 4 h 30 a Paris (UTC+2 en ete) : en pause ; 5 h 00 : reprise.
        assertThat(at("2026-07-10T02:30:00Z", "22:00", "05:00").paused()).isTrue();
        assertThat(at("2026-07-10T03:00:00Z", "22:00", "05:00").paused()).isFalse();
    }

    @Test
    void sameDayWindowAndDisabledWindow() {
        assertThat(at("2026-12-10T11:00:00Z", "12:00", "13:00").paused()).isTrue();
        assertThat(at("2026-12-10T13:00:00Z", "12:00", "13:00").paused()).isFalse();
        assertThat(at("2026-12-10T23:00:00Z", "", "").paused()).isFalse();
        assertThat(at("2026-12-10T23:00:00Z", "22:00", "22:00").paused()).isFalse();
    }

    @Test
    void resumeLabelIsShort() {
        assertThat(at("2026-12-10T23:00:00Z", "22:00", "05:00").resumeLabel()).isEqualTo("5 h");
        assertThat(at("2026-12-10T23:00:00Z", "22:00", "05:30").resumeLabel()).isEqualTo("5 h 30");
    }
}
