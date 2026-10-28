package com.assistant.quota;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Budget persiste sur SQLite reel : reservation atomique, redemarrage, changement de jour, purge. */
@SpringBootTest
@ActiveProfiles("local")
class ApiQuotaServiceTest {

    static final String DB_FILE = "target/it-quota-" + UUID.randomUUID() + ".db";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB_FILE + "?foreign_keys=on");
        registry.add("assistant.admin.password", () -> "quota-admin-password");
    }

    @Autowired
    ExternalApiQuotaRepository repository;

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now;
        private final ZoneId zone;

        MutableClock(AtomicReference<Instant> now, ZoneId zone) {
            this.now = now;
            this.zone = zone;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId newZone) {
            return new MutableClock(now, newZone);
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }

    private static String apiName() {
        return "t" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void neverExceedsTheBudgetAndSurvivesARestart() {
        var instant = new AtomicReference<>(Instant.parse("2026-10-27T10:00:00Z"));
        String api = apiName();
        var service = new ApiQuotaService(repository, new MutableClock(instant, ZoneId.of("UTC")));

        assertThat(service.tryReserve(api, 2)).isTrue();
        assertThat(service.tryReserve(api, 2)).isTrue();
        assertThat(service.tryReserve(api, 2)).isFalse();

        var restarted = new ApiQuotaService(repository, new MutableClock(instant, ZoneId.of("UTC")));
        assertThat(restarted.used(api)).isEqualTo(2);
        assertThat(restarted.tryReserve(api, 2)).isFalse();
    }

    @Test
    void quotaDayFollowsParisTimeAndOldDaysArePurged() {
        var instant = new AtomicReference<>(Instant.parse("2026-10-27T10:00:00Z"));
        String api = apiName();
        var service = new ApiQuotaService(repository, new MutableClock(instant, ZoneId.of("UTC")));
        repository.save(new ExternalApiQuota(api, "2026-10-01", 3));

        assertThat(service.tryReserve(api, 1)).isTrue();
        assertThat(service.tryReserve(api, 1)).isFalse();
        assertThat(repository.findByApiNameAndQuotaDay(api, "2026-10-01")).isEmpty();

        instant.set(Instant.parse("2026-10-27T23:30:00Z"));
        assertThat(service.tryReserve(api, 1)).isTrue();
        assertThat(repository.findByApiNameAndQuotaDay(api, "2026-10-28")).isPresent();
    }

    @Test
    void concurrentReservationsNeverExceedTheBudget() throws Exception {
        var instant = new AtomicReference<>(Instant.parse("2026-10-27T10:00:00Z"));
        String api = apiName();
        var service = new ApiQuotaService(repository, new MutableClock(instant, ZoneId.of("UTC")));
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                results.add(executor.submit(() -> service.tryReserve(api, 5)));
            }
            int granted = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    granted++;
                }
            }
            assertThat(granted).isEqualTo(5);
            assertThat(service.used(api)).isEqualTo(5);
        } finally {
            executor.shutdownNow();
        }
    }
}
