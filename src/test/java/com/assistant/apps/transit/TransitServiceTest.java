package com.assistant.apps.transit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.assistant.Fixtures;
import com.assistant.apps.ProfileAppService;
import com.assistant.config.AssistantProperties;
import com.assistant.integrations.ExternalApiModeService;
import com.assistant.quota.ApiQuotaService;
import com.assistant.quota.QuietHours;
import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import org.springframework.core.io.DefaultResourceLoader;

class TransitServiceTest {

    private final TransitGateway liveGateway = mock(TransitGateway.class);
    private final MockTransitGateway testGateway = mock(MockTransitGateway.class);
    private final ProfileAppService profileApps = mock(ProfileAppService.class);
    private final ApiQuotaService quota = mock(ApiQuotaService.class);
    private final QuietHours quietHours = mock(QuietHours.class);
    private final ExternalApiModeService mode = mock(ExternalApiModeService.class);
    private final PrimKeyService primKey = mock(PrimKeyService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), ZoneOffset.UTC);
    private final LineCatalog catalog = new LineCatalog(new DefaultResourceLoader(), new ObjectMapper(), clock);
    private final TransitService service = new TransitService(liveGateway, testGateway, catalog, profileApps,
            new ObjectMapper(), properties(), primKey, quota, quietHours, mode, clock);

    @BeforeEach
    void setUp() {
        when(mode.isTestMode()).thenReturn(false);
        when(quietHours.paused()).thenReturn(false);
        when(primKey.isConfigured()).thenReturn(true);
        when(quietHours.resumeLabel()).thenReturn("5 h");
        when(quota.tryReserve("prim", 750)).thenReturn(true);
        when(profileApps.requireEnabled(1L, "transit")).thenReturn(new ProfileAppService.EnabledApp(
                new TransitAssistantApp(), 1, "{\"favoriteLineIds\":[\"C01739\"]}"));
    }

    @Test
    void refreshesOneGlobalFeedAndSharesItAcrossLineRequests() {
        TransitDisruption alert = new TransitDisruption("d1", "Ligne J : trafic interrompu", "Interruption",
            "<p>Le trafic est interrompu.</p>", "BLOQUANTE", "TRAVAUX",
            Set.of("line:IDFM:C01739"), true, List.of());
        when(liveGateway.fetchDisruptions()).thenReturn(new TransitFeed(List.of(alert), clock.instant()));

        var first = service.alerts(1L);
        var second = service.alerts(1L);

        assertThat(first.lines()).hasSize(1);
        assertThat(first.lines().getFirst().line().label()).isEqualTo("Transilien J");
        assertThat(first.lines().getFirst().alerts()).extracting(TransitAlert::title)
                .containsExactly("Ligne J : trafic interrompu");
        assertThat(first.lines().getFirst().alerts().getFirst().message()).contains("Le trafic est interrompu");
        assertThat(second.fetchedAt()).isEqualTo(first.fetchedAt());
        verify(liveGateway).fetchDisruptions();
        verify(quota).tryReserve("prim", 750);
    }

        @Test
        void refreshesAfterTenMinutesAndKeepsTheLastSuccessfulSnapshotOnFailure() {
        MutableClock mutableClock = new MutableClock(Instant.parse("2026-10-28T12:00:00Z"));
        TransitDisruption alert = new TransitDisruption("d1", "Ligne J : trafic perturbé", "Perturbée",
            "Détails", "PERTURBEE", "TRAVAUX", Set.of("line:IDFM:C01739"), true, List.of());
        TransitFeed initial = new TransitFeed(List.of(alert), mutableClock.instant());
        when(liveGateway.fetchDisruptions()).thenReturn(initial)
            .thenThrow(new TransitUnavailableException(new IllegalStateException("offline")));
        when(quota.tryReserve("prim", 750)).thenReturn(true, true);
        TransitService timed = new TransitService(liveGateway, testGateway, catalog, profileApps,
            new ObjectMapper(), properties(), primKey, quota, quietHours, mode, mutableClock);

        var first = timed.alerts(1L);
        assertThat(first.fetchedAt()).isEqualTo(initial.fetchedAt());
        mutableClock.advance(Duration.ofMinutes(11));
        var stale = timed.alerts(1L);

        assertThat(stale.fetchedAt()).isEqualTo(initial.fetchedAt());
        assertThat(stale.notice()).contains("Actualisation indisponible");
        assertThat(stale.lines().getFirst().alerts()).hasSize(1);
        verify(liveGateway, org.mockito.Mockito.times(2)).fetchDisruptions();
        }

    @Test
    void usesExactTitlePrefixOnlyWhenThereAreNoStructuredSections() {
        TransitDisruption titleOnly = new TransitDisruption("d1", "Ligne J : travaux", "Travaux", "Détails",
            "PERTURBEE", "TRAVAUX", Set.of(), false, List.of());
        when(liveGateway.fetchDisruptions()).thenReturn(new TransitFeed(List.of(titleOnly), clock.instant()));

        assertThat(service.alerts(1L).lines().getFirst().alerts()).hasSize(1);

        TransitDisruption unrelatedStructured = new TransitDisruption("d2", "Ligne J : travaux", "Travaux", "Détails",
            "PERTURBEE", "TRAVAUX", Set.of(), true, List.of());
        when(liveGateway.fetchDisruptions()).thenReturn(new TransitFeed(List.of(unrelatedStructured), clock.instant()));
        var another = new TransitService(liveGateway, testGateway, catalog, profileApps, new ObjectMapper(),
                properties(), primKey, quota, quietHours, mode, clock);
        when(quota.tryReserve("prim", 750)).thenReturn(true);

        assertThat(another.alerts(1L).lines().getFirst().alerts()).isEmpty();
    }

    @Test
    void testModeServesTheFixtureWithoutCallingPrimOrReservingQuota() {
        when(mode.isTestMode()).thenReturn(true);
        when(testGateway.fetchDisruptions()).thenReturn(new TransitFeed(List.of(), clock.instant()));

        var overview = service.alerts(1L);

        assertThat(overview.testMode()).isTrue();
        assertThat(overview.notice()).contains("TEST");
        verify(testGateway).fetchDisruptions();
        verifyNoInteractions(quota);
        verifyNoInteractions(liveGateway);
    }

        @Test
        void testFixtureShowsActiveRerAAndLineJButNoBus1433Alert() {
        Clock fixtureClock = Clock.fixed(Instant.parse("2026-10-28T14:00:00Z"), ZoneOffset.UTC);
        LineCatalog fixtureCatalog = new LineCatalog(new DefaultResourceLoader(), new ObjectMapper(), fixtureClock);
        MockTransitGateway fixtureGateway = new MockTransitGateway(new DefaultResourceLoader(),
            new TransitResponseParser(), new ObjectMapper(), fixtureClock);
        when(mode.isTestMode()).thenReturn(true);
        when(profileApps.requireEnabled(1L, "transit")).thenReturn(new ProfileAppService.EnabledApp(
            new TransitAssistantApp(), 1,
            "{\"favoriteLineIds\":[\"C01742\",\"C01739\",\"C02795\"]}"));
        TransitService fixtureService = new TransitService(liveGateway, fixtureGateway, fixtureCatalog,
            profileApps, new ObjectMapper(), properties(), primKey, quota, quietHours, mode, fixtureClock);

        var overview = fixtureService.alerts(1L);

        assertThat(overview.lines().stream().filter(line -> "C01742".equals(line.line().id()))
            .findFirst().orElseThrow().alerts()).isNotEmpty();
        assertThat(overview.lines().stream().filter(line -> "C01739".equals(line.line().id()))
            .findFirst().orElseThrow().alerts()).isNotEmpty();
        assertThat(overview.lines().stream().filter(line -> "C02795".equals(line.line().id()))
            .findFirst().orElseThrow().alerts()).isEmpty();
        verifyNoInteractions(liveGateway);
        verifyNoInteractions(quota);
        }

    @Test
    void withoutKeyNeitherCallsPrimNorReservesQuota() {
        when(primKey.isConfigured()).thenReturn(false);

        var overview = service.alerts(1L);
        service.scheduledRefresh();

        assertThat(overview.notice()).contains("n'est pas configuré");
        assertThat(overview.fetchedAt()).isNull();
        verifyNoInteractions(liveGateway);
        verifyNoInteractions(quota);
    }

    @Test
    void keyEnteredLaterIsUsedWithoutRestart() {
        when(primKey.isConfigured()).thenReturn(false, true);
        when(liveGateway.fetchDisruptions()).thenReturn(new TransitFeed(List.of(), clock.instant()));

        assertThat(service.alerts(1L).fetchedAt()).isNull();
        assertThat(service.alerts(1L).fetchedAt()).isEqualTo(clock.instant());

        verify(liveGateway).fetchDisruptions();
    }

    @Test
    void pausedWindowUsesCachedDataWithoutReservingQuotaOrCallingPrim() {
        when(quietHours.paused()).thenReturn(true);

        var overview = service.alerts(1L);

        assertThat(overview.paused()).isTrue();
        assertThat(overview.notice()).contains("pause jusqu'à 5 h");
        verifyNoInteractions(quota);
        verifyNoInteractions(liveGateway);
    }

    @Test
    void exhaustedQuotaDoesNotCallPrimAndExplainsTheFallbackState() {
        when(quota.tryReserve("prim", 750)).thenReturn(false);

        var overview = service.alerts(1L);

        assertThat(overview.notice()).contains("Budget quotidien PRIM atteint");
        assertThat(overview.fetchedAt()).isNull();
        verifyNoInteractions(liveGateway);
    }

    private static AssistantProperties properties() {
        var base = Fixtures.properties();
        return new AssistantProperties(base.session(), base.scan(), base.recognition(), base.enrollment(), base.weather(),
                base.ui(), base.backup(), base.faceAssets(),
                new AssistantProperties.Prim(750, 10, "22:00", "05:00"));
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
