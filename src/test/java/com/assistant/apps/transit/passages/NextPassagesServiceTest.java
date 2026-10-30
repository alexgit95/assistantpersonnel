package com.assistant.apps.transit.passages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.assistant.Fixtures;
import com.assistant.apps.ProfileAppService;
import com.assistant.apps.transit.LineCatalog;
import com.assistant.apps.transit.PrimKeyService;
import com.assistant.common.ApiException;
import com.assistant.config.AssistantProperties;
import com.assistant.integrations.ExternalApiModeService;
import com.assistant.quota.ApiQuotaService;
import com.assistant.quota.QuietHours;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

class NextPassagesServiceTest {

    private static final String CONFIG = "{\"stops\":[{\"arrid\":\"24377\",\"name\":\"Carnot\","
            + "\"town\":\"Paris\",\"type\":\"bus\"}]}";

    private final PassageGateway liveGateway = mock(PassageGateway.class);
    private final MockPassageGateway testGateway = mock(MockPassageGateway.class);
    private final StopSearchClient search = mock(StopSearchClient.class);
    private final ProfileAppService profileApps = mock(ProfileAppService.class);
    private final ApiQuotaService quota = mock(ApiQuotaService.class);
    private final QuietHours quietHours = mock(QuietHours.class);
    private final ExternalApiModeService mode = mock(ExternalApiModeService.class);
    private final PrimKeyService primKey = mock(PrimKeyService.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-30T08:00:00Z"));
    private final LineCatalog catalog = new LineCatalog(new DefaultResourceLoader(), new ObjectMapper(), clock);
    private final NextPassagesService service = new NextPassagesService(liveGateway, testGateway, search, catalog,
            profileApps, new ObjectMapper(), properties(), primKey, quota, quietHours, mode, clock);

    @BeforeEach
    void setUp() {
        when(mode.isTestMode()).thenReturn(false);
        when(quietHours.paused()).thenReturn(false);
        when(quietHours.resumeLabel()).thenReturn("5 h");
        when(primKey.isConfigured()).thenReturn(true);
        when(quota.tryReserve("prim", 750)).thenReturn(true);
        useConfig(CONFIG);
    }

    @Test
    void returnsTheFourNextFuturePassagesInMinutesWithTheUpdateTime() {
        Instant now = clock.instant();
        when(liveGateway.fetch("24377")).thenReturn(new PassageFeed(List.of(
                passage("C01154", now.minusSeconds(30)), passage("C01154", now.plusSeconds(5 * 60 + 20)),
                passage("C01154", now.plusSeconds(9 * 60)), passage("C01154", now.plusSeconds(15 * 60)),
                passage("C01154", now.plusSeconds(21 * 60)), passage("C01154", now.plusSeconds(33 * 60))),
                now.minusSeconds(40)));

        var overview = service.next(1L);

        assertThat(overview.notice()).isNull();
        var stop = overview.stops().getFirst();
        assertThat(stop.state()).isEqualTo(NextPassagesService.STATE_OK);
        assertThat(stop.updatedAt()).isEqualTo(now.minusSeconds(40));
        assertThat(stop.passages()).extracting(NextPassagesService.PassageView::minutes)
                .containsExactly(5, 9, 15, 21);
    }

    @Test
    void sharesOneCallPerStopWithinTheCacheWindowThenRefreshes() {
        when(liveGateway.fetch("24377")).thenReturn(new PassageFeed(
                List.of(passage("C01154", clock.instant().plusSeconds(600))), clock.instant()));

        service.next(1L);
        service.next(1L);
        verify(liveGateway, times(1)).fetch("24377");

        clock.advance(NextPassagesService.CACHE_TTL.plusSeconds(1));
        service.next(1L);

        verify(liveGateway, times(2)).fetch("24377");
        verify(quota, times(2)).tryReserve("prim", 750);
    }

    @Test
    void asksToConfigureTheKeyWithoutCallingPrimOrReservingBudget() {
        when(primKey.isConfigured()).thenReturn(false);

        var overview = service.next(1L);

        assertThat(overview.notice()).contains("clé PRIM");
        assertThat(overview.stops().getFirst().state()).isEqualTo(NextPassagesService.STATE_NO_DATA);
        verifyNoInteractions(liveGateway, quota);
    }

    @Test
    void pausesWithoutCallingPrimDuringQuietHours() {
        when(quietHours.paused()).thenReturn(true);

        var overview = service.next(1L);

        assertThat(overview.notice()).isEqualTo("Prochains passages en pause jusqu'à 5 h.");
        verifyNoInteractions(liveGateway, quota);
    }

    @Test
    void keepsServingCachedPassagesWhenTheBudgetIsExhausted() {
        when(liveGateway.fetch("24377")).thenReturn(new PassageFeed(
                List.of(passage("C01154", clock.instant().plusSeconds(3600))), clock.instant()));
        service.next(1L);
        clock.advance(Duration.ofSeconds(60));
        when(quota.tryReserve("prim", 750)).thenReturn(false);

        var overview = service.next(1L);

        assertThat(overview.notice()).contains("Budget quotidien PRIM atteint");
        assertThat(overview.stops().getFirst().passages()).hasSize(1);
        verify(liveGateway, times(1)).fetch("24377");
    }

    @Test
    void marksAStopRejectedByPrimAsUnavailable() {
        when(liveGateway.fetch("24377")).thenThrow(new PassageUnavailableException(true, null));

        var stop = service.next(1L).stops().getFirst();

        assertThat(stop.state()).isEqualTo(NextPassagesService.STATE_UNKNOWN_STOP);
        assertThat(stop.message()).isEqualTo("Arrêt indisponible");
        assertThat(stop.passages()).isEmpty();
    }

    @Test
    void keepsTheLastKnownPassagesWhenPrimFailsTemporarily() {
        when(liveGateway.fetch("24377")).thenReturn(new PassageFeed(
                List.of(passage("C01154", clock.instant().plusSeconds(3600))), clock.instant()))
                .thenThrow(new PassageUnavailableException(false, null));
        service.next(1L);
        clock.advance(Duration.ofSeconds(60));

        var overview = service.next(1L);

        assertThat(overview.notice()).contains("Actualisation indisponible");
        assertThat(overview.stops().getFirst().passages()).hasSize(1);
    }

    @Test
    void reportsNoPassageWhenEveryTimeIsAlreadyPast() {
        when(liveGateway.fetch("24377")).thenReturn(new PassageFeed(
                List.of(passage("C01154", clock.instant().minusSeconds(120))), clock.instant()));

        var stop = service.next(1L).stops().getFirst();

        assertThat(stop.state()).isEqualTo(NextPassagesService.STATE_NO_PASSAGE);
    }

    @Test
    void usesSimulatedPassagesInTestModeWithoutKeyQuotaOrExternalCall() {
        when(mode.isTestMode()).thenReturn(true);
        when(primKey.isConfigured()).thenReturn(false);
        Instant now = clock.instant();
        when(testGateway.fetch("24377")).thenReturn(new PassageFeed(List.of(
                passage("C01154", now.plusSeconds(60)), passage("C01154", now.plusSeconds(300)),
                passage("C01154", now.plusSeconds(600)), passage("C01154", now.plusSeconds(900)),
                passage("C01154", now.plusSeconds(1200))), now));

        var first = service.next(1L);
        service.next(1L);

        assertThat(first.testMode()).isTrue();
        assertThat(first.stops().getFirst().passages()).hasSize(4);
        verify(testGateway, times(1)).fetch("24377");
        verifyNoInteractions(liveGateway, quota);
    }

    @Test
    void enrichesPassagesWithTheLineLabelWhenTheCatalogKnowsIt() {
        when(liveGateway.fetch("24377")).thenReturn(new PassageFeed(List.of(
                passage("C01739", clock.instant().plusSeconds(120)),
                passage("INCONNUE", clock.instant().plusSeconds(240))), clock.instant()));

        var passages = service.next(1L).stops().getFirst().passages();

        assertThat(passages.getFirst().line()).isEqualTo(catalog.find("C01739").orElseThrow().label());
        assertThat(passages.get(1).line()).isEqualTo("INCONNUE");
        assertThat(passages.get(1).lineColor()).isNull();
    }

    @Test
    void savesAtMostThreeUniqueValidStopsInTheProfileConfiguration() {
        var saved = service.saveStops(1L, List.of(new TransitStop(" 24377 ", " Carnot ", "Paris", "bus"),
                new TransitStop("2140", "Carnot", "Villetaneuse", "bus")));

        assertThat(saved).extracting(TransitStop::arrid).containsExactly("24377", "2140");
        var json = ArgumentCaptor.forClass(String.class);
        verify(profileApps).updateConfiguration(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("transit-passages"), json.capture());
        assertThat(json.getValue()).contains("\"arrid\":\"24377\"").contains("\"arrid\":\"2140\"");
    }

    @Test
    void rejectsAFourthStopADuplicateAndAnInvalidIdentifier() {
        var one = new TransitStop("1", "A", "", "bus");
        var two = new TransitStop("2", "B", "", "bus");
        var three = new TransitStop("3", "C", "", "bus");
        var four = new TransitStop("4", "D", "", "bus");

        assertBadRequest(() -> service.saveStops(1L, List.of(one, two, three, four)));
        assertBadRequest(() -> service.saveStops(1L, List.of(one, one)));
        assertBadRequest(() -> service.saveStops(1L, List.of(new TransitStop("12a", "A", "", "bus"))));
        assertBadRequest(() -> service.saveStops(1L, List.of(new TransitStop("5", " ", "", "bus"))));
        assertBadRequest(() -> service.saveStops(1L, null));
        verify(profileApps, never()).updateConfiguration(org.mockito.ArgumentMatchers.anyLong(), anyString(),
                anyString());
    }

    @Test
    void readsOnlyValidDistinctStopsLimitedToThreeFromTheConfiguration() {
        useConfig("{\"stops\":[{\"arrid\":\"1\",\"name\":\"A\"},{\"arrid\":\"1\",\"name\":\"A\"},"
                + "{\"arrid\":\"x\",\"name\":\"B\"},{\"arrid\":\"2\",\"name\":\"C\"},{\"arrid\":\"3\",\"name\":\"D\"},"
                + "{\"arrid\":\"4\",\"name\":\"E\"}]}");

        assertThat(service.stops(1L)).extracting(TransitStop::arrid).containsExactly("1", "2", "3");
    }

    @Test
    void delegatesTheStopSearch() {
        when(search.search("carnot")).thenReturn(List.of(new TransitStop("1", "Carnot", "", "bus")));

        assertThat(service.searchStops("carnot")).hasSize(1);
    }

    @Test
    void simulatesTheStopSearchInTestModeWithoutCallingThePublicApi() {
        when(mode.isTestMode()).thenReturn(true);

        assertThat(service.searchStops("carnot")).extracting(TransitStop::arrid)
                .containsExactly("24377", "39713", "2140");
        assertThat(service.searchStops("zzz")).hasSizeGreaterThan(3);
        assertBadRequest(() -> service.searchStops("ab"));
        verifyNoInteractions(search);
    }

    private void assertBadRequest(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private void useConfig(String configJson) {
        when(profileApps.requireEnabled(1L, "transit-passages")).thenReturn(new ProfileAppService.EnabledApp(
                new TransitPassagesAssistantApp(), 1, configJson));
        when(profileApps.listForProfile(1L)).thenReturn(List.of(new ProfileAppService.AppPreference(
                "transit-passages", "Prochains passages", "", "fragments/widgets/transit-passages :: content",
                "/js/widgets/transit-passages.js", true, 1, configJson)));
    }

    private static Passage passage(String lineId, Instant at) {
        return new Passage(lineId, "Porte d'Orléans", at, "onTime");
    }

    private static AssistantProperties properties() {
        var base = Fixtures.properties();
        return new AssistantProperties(base.session(), base.scan(), base.recognition(), base.enrollment(),
                base.weather(), base.ui(), base.backup(), base.faceAssets(),
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
