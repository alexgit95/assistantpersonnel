package com.assistant.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assistant.Fixtures;
import com.assistant.TestClock;
import com.assistant.common.ApiException;
import com.assistant.profile.ProfileDeletedEvent;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class HandoffServiceTest {

    private final TestClock clock = new TestClock();
    private final HandoffService service = new HandoffService(Fixtures.properties(), clock);

    @Test
    void redeemConsumesTheTokenOnce() {
        HandoffService.IssuedQr qr = service.issue(5L, SessionScope.FULL);
        assertThat(service.state(qr.qrId())).isEqualTo(HandoffService.QrState.ACTIVE);

        Optional<HandoffService.Redeemed> redeemed = service.redeem(qr.token());

        assertThat(redeemed).hasValueSatisfying(r -> {
            assertThat(r.profileId()).isEqualTo(5L);
            assertThat(r.scope()).isEqualTo(SessionScope.FULL);
        });
        assertThat(service.state(qr.qrId())).isEqualTo(HandoffService.QrState.CONSUMED);
        assertThat(service.redeem(qr.token())).isEmpty();
    }

    @Test
    void tokenExpiresAfterTheConfiguredLifetime() {
        HandoffService.IssuedQr qr = service.issue(5L, SessionScope.WEIGHT);
        clock.advance(Duration.ofSeconds(119));
        assertThat(service.state(qr.qrId())).isEqualTo(HandoffService.QrState.ACTIVE);
        clock.advance(Duration.ofSeconds(1));
        assertThat(service.state(qr.qrId())).isEqualTo(HandoffService.QrState.EXPIRED);
        assertThat(service.redeem(qr.token())).isEmpty();
    }

    @Test
    void unknownOrBlankTokensAreRejected() {
        assertThat(service.redeem("nope")).isEmpty();
        assertThat(service.redeem(null)).isEmpty();
        assertThat(service.redeem("  ")).isEmpty();
        assertThat(service.state("unknown")).isEqualTo(HandoffService.QrState.EXPIRED);
    }

    @Test
    void tokensAndQrIdsAreDistinctAndUnguessable() {
        HandoffService.IssuedQr first = service.issue(5L, SessionScope.FULL);
        HandoffService.IssuedQr second = service.issue(5L, SessionScope.FULL);
        assertThat(first.token()).isNotEqualTo(second.token()).hasSizeGreaterThanOrEqualTo(43);
        assertThat(first.qrId()).isNotEqualTo(second.qrId()).isNotEqualTo(first.token());
    }

    @Test
    void keepsAtMostTwoActiveTokensPerProfileAndScope() {
        HandoffService.IssuedQr first = service.issue(5L, SessionScope.FULL);
        HandoffService.IssuedQr second = service.issue(5L, SessionScope.FULL);
        HandoffService.IssuedQr third = service.issue(5L, SessionScope.FULL);

        assertThat(service.state(first.qrId())).isEqualTo(HandoffService.QrState.EXPIRED);
        assertThat(service.redeem(first.token())).isEmpty();
        assertThat(service.state(second.qrId())).isEqualTo(HandoffService.QrState.ACTIVE);
        assertThat(service.state(third.qrId())).isEqualTo(HandoffService.QrState.ACTIVE);
    }

    @Test
    void scopesAndProfilesAreCountedIndependently() {
        var qrs = java.util.List.of(
                service.issue(5L, SessionScope.FULL), service.issue(5L, SessionScope.FULL),
                service.issue(5L, SessionScope.WEIGHT), service.issue(5L, SessionScope.WEIGHT),
                service.issue(6L, SessionScope.FULL));
        assertThat(qrs).allSatisfy(qr -> assertThat(service.state(qr.qrId())).isEqualTo(HandoffService.QrState.ACTIVE));
    }

    @Test
    void refusesNewTokensWhenTheGlobalCapIsReached() {
        for (long profile = 1; profile <= HandoffService.MAX_ACTIVE_TOKENS; profile++) {
            service.issue(profile, SessionScope.FULL);
        }
        assertThatThrownBy(() -> service.issue(999L, SessionScope.FULL))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void revokingAProfileInvalidatesItsTokensOnly() {
        HandoffService.IssuedQr mine = service.issue(5L, SessionScope.FULL);
        HandoffService.IssuedQr other = service.issue(6L, SessionScope.FULL);

        service.onProfileSignedOut(new ProfileSignedOutEvent(5L));

        assertThat(service.state(mine.qrId())).isEqualTo(HandoffService.QrState.EXPIRED);
        assertThat(service.redeem(mine.token())).isEmpty();
        assertThat(service.state(other.qrId())).isEqualTo(HandoffService.QrState.ACTIVE);
    }

    @Test
    void deletedProfileAndGlobalInvalidationRevokeTokens() {
        HandoffService.IssuedQr first = service.issue(5L, SessionScope.FULL);
        service.onProfileDeleted(new ProfileDeletedEvent(5L));
        assertThat(service.redeem(first.token())).isEmpty();

        HandoffService.IssuedQr second = service.issue(6L, SessionScope.WEIGHT);
        service.onSessionsInvalidated(new SessionsInvalidatedEvent());
        assertThat(service.redeem(second.token())).isEmpty();
        assertThat(service.activeTokenCount()).isZero();
    }

    @Test
    void purgeDropsExpiredTokens() {
        service.issue(5L, SessionScope.FULL);
        clock.advance(Duration.ofMinutes(3));
        service.purgeExpired();
        assertThat(service.activeTokenCount()).isZero();
    }

    @Test
    void ttlComesFromConfiguration() {
        assertThat(service.tokenTtlSeconds()).isEqualTo(120);
    }

    @Test
    void onlyOneOfManySimultaneousScansWins() throws Exception {
        HandoffService.IssuedQr qr = service.issue(5L, SessionScope.FULL);
        int scans = 20;
        var pool = java.util.concurrent.Executors.newFixedThreadPool(scans);
        var start = new java.util.concurrent.CountDownLatch(1);
        java.util.List<java.util.concurrent.Future<Boolean>> results = new java.util.ArrayList<>();
        for (int i = 0; i < scans; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return service.redeem(qr.token()).isPresent();
            }));
        }
        start.countDown();
        long winners = 0;
        for (var result : results) {
            if (result.get()) {
                winners++;
            }
        }
        pool.shutdown();

        assertThat(winners).isEqualTo(1);
    }
}
