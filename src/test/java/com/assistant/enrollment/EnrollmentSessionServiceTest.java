package com.assistant.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.assistant.Fixtures;
import com.assistant.TestClock;
import com.assistant.auth.ProfileSignedOutEvent;
import com.assistant.auth.SessionScope;
import com.assistant.auth.SessionService;
import com.assistant.auth.SessionsInvalidatedEvent;
import com.assistant.common.ApiException;
import com.assistant.profile.ProfileDeletedEvent;
import com.assistant.profile.ProfileService;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class EnrollmentSessionServiceTest {

    private static final int PHOTOS = 5;

    @Mock
    ProfileService profiles;
    @Mock
    SessionService sessions;

    TestClock clock;
    EnrollmentSessionService service;

    @BeforeEach
    void setUp() {
        clock = new TestClock();
        service = new EnrollmentSessionService(Fixtures.properties(), clock, profiles, sessions);
    }

    private static EnrollmentSessionService.Submission accepted(int index) {
        return new EnrollmentSessionService.Submission(true, null, Fixtures.vector(index), Fixtures.avatar());
    }

    private static EnrollmentSessionService.Submission rejected(String reason) {
        return new EnrollmentSessionService.Submission(false, reason, null, null);
    }

    private static void assertStatus(Runnable action, HttpStatus status) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(status));
    }

    /** Session CREATE en attente, prise en main par un smartphone et lancee : renvoie (open, claimToken). */
    private record Started(EnrollmentSessionService.Opened opened, String claim) {
    }

    private Started started() {
        EnrollmentSessionService.Opened opened = service.openOrResume();
        String claim = service.start(opened.id(), opened.pairingToken(), null, "Alice");
        return new Started(opened, claim);
    }

    private Started reviewed() {
        Started started = started();
        for (int i = 0; i < PHOTOS; i++) {
            service.submit(started.opened().id(), accepted(i), null);
        }
        return started;
    }

    @Test
    void openCreatesAWaitingSessionAndResumesItAfterwards() {
        EnrollmentSessionService.Opened first = service.openOrResume();
        EnrollmentSessionService.Opened second = service.openOrResume();

        assertThat(first.state()).isEqualTo(EnrollmentState.WAITING);
        assertThat(first.mode()).isEqualTo(EnrollmentMode.CREATE);
        assertThat(first.pairingToken()).isNotBlank();
        assertThat(second).isEqualTo(first);
    }

    @Test
    void openCreatesANewSessionOnceTheCurrentOneIsOver() {
        EnrollmentSessionService.Opened first = service.openOrResume();
        service.cancel(first.id(), true, null);

        EnrollmentSessionService.Opened second = service.openOrResume();

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(second.pairingToken()).isNotEqualTo(first.pairingToken());
    }

    @Test
    void sessionExpiresAfterTheConfiguredIdleTimeAndIsReplaced() {
        EnrollmentSessionService.Opened first = service.openOrResume();
        clock.advance(Duration.ofMinutes(10));

        assertThat(service.kioskView()).hasValueSatisfying(
                s -> assertThat(s.state()).isEqualTo(EnrollmentState.EXPIRED));
        assertThat(service.openOrResume().id()).isNotEqualTo(first.id());
    }

    @Test
    void theQrTokenAloneFindsTheWaitingSession() {
        EnrollmentSessionService.Opened opened = service.openOrResume();

        EnrollmentSessionService.Snapshot found = service.findByPairing(opened.pairingToken());

        assertThat(found.id()).isEqualTo(opened.id());
        assertThat(found.state()).isEqualTo(EnrollmentState.WAITING);
        assertThat(found.name()).isNull();
    }

    @Test
    void theQrTokenIsUselessWithoutAMatchingLiveCreationSession() {
        EnrollmentSessionService.Opened opened = service.openOrResume();

        assertStatus(() -> service.findByPairing("wrong"), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.findByPairing(null), HttpStatus.NOT_FOUND);
        service.cancel(opened.id(), true, null);
        assertStatus(() -> service.findByPairing(opened.pairingToken()), HttpStatus.NOT_FOUND);
    }

    @Test
    void theQrTokenStopsWorkingOnceTheSessionExpires() {
        EnrollmentSessionService.Opened opened = service.openOrResume();
        clock.advance(Duration.ofMinutes(10));

        assertStatus(() -> service.findByPairing(opened.pairingToken()), HttpStatus.NOT_FOUND);
    }

    @Test
    void startRequiresAValidTokenAndName() {
        EnrollmentSessionService.Opened opened = service.openOrResume();

        assertStatus(() -> service.start(opened.id(), "wrong", null, "Alice"), HttpStatus.FORBIDDEN);
        assertStatus(() -> service.start(opened.id(), null, null, "Alice"), HttpStatus.FORBIDDEN);
        assertStatus(() -> service.start(opened.id(), opened.pairingToken(), null, "  "), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.start(opened.id(), opened.pairingToken(), null, "<script>"), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.start("other-id", opened.pairingToken(), null, "Alice"), HttpStatus.NOT_FOUND);
    }

    @Test
    void firstSmartphoneClaimsTheSessionAndStartsTheCountdown() {
        Started started = started();

        assertThat(started.claim()).isNotBlank();
        EnrollmentSessionService.Snapshot view = service.state(started.opened().id(), null, started.claim());
        assertThat(view.state()).isEqualTo(EnrollmentState.COUNTDOWN);
        assertThat(view.name()).isEqualTo("Alice");
        assertThat(view.nextShotAtMs() - view.countdownStartsAtMs()).isEqualTo(5_000);
        assertThat(view.done()).isZero();
        assertThat(view.total()).isEqualTo(PHOTOS);
    }

    @Test
    void aSecondSmartphoneIsRefusedOnceTheSessionIsClaimed() {
        Started started = started();
        assertStatus(() -> service.start(started.opened().id(), started.opened().pairingToken(), null, "Bob"),
                HttpStatus.CONFLICT);
    }

    @Test
    void theSmartphoneWithoutClaimSeesTheStateButNotTheName() {
        Started started = started();
        EnrollmentSessionService.Snapshot view = service.state(started.opened().id(),
                started.opened().pairingToken(), null);
        assertThat(view.name()).isNull();
        assertThat(view.claimed()).isTrue();
        assertThat(view.state()).isEqualTo(EnrollmentState.COUNTDOWN);
    }

    @Test
    void stateRefusesCallersWithoutAnyValidToken() {
        EnrollmentSessionService.Opened opened = service.openOrResume();
        assertStatus(() -> service.state(opened.id(), null, null), HttpStatus.FORBIDDEN);
        assertStatus(() -> service.state(opened.id(), "bad", "bad"), HttpStatus.FORBIDDEN);
    }

    @Test
    void validCapturesAdvanceTheScheduleUntilReview() {
        Started started = started();
        String id = started.opened().id();
        long firstShot = service.state(id, null, started.claim()).nextShotAtMs();

        EnrollmentSessionService.Snapshot afterOne = service.submit(id, accepted(0), null);

        assertThat(afterOne.state()).isEqualTo(EnrollmentState.CAPTURING);
        assertThat(afterOne.done()).isEqualTo(1);
        assertThat(afterOne.guidanceIndex()).isEqualTo(1);
        assertThat(afterOne.nextShotAtMs()).isEqualTo(firstShot + 4_000);

        for (int i = 1; i < PHOTOS - 1; i++) {
            service.submit(id, accepted(i), null);
        }
        EnrollmentSessionService.Snapshot last = service.submit(id, accepted(PHOTOS - 1), null);
        assertThat(last.state()).isEqualTo(EnrollmentState.REVIEW);
        assertThat(last.done()).isEqualTo(PHOTOS);
    }

    @Test
    void aSlowPiNeverGetsAShotInThePast() {
        Started started = started();
        String id = started.opened().id();
        clock.advance(Duration.ofSeconds(30));

        EnrollmentSessionService.Snapshot view = service.submit(id, accepted(0), null);

        assertThat(view.nextShotAtMs()).isGreaterThan(clock.millis());
    }

    @Test
    void rejectionsAreRetriedAndAbandonedAfterThreeInARow() {
        Started started = started();
        String id = started.opened().id();

        EnrollmentSessionService.Snapshot first = service.submit(id, rejected("none"), null);
        assertThat(first.state()).isEqualTo(EnrollmentState.CAPTURING);
        assertThat(first.lastRejection()).isEqualTo("none");
        service.submit(id, rejected("tooSmall"), null);
        EnrollmentSessionService.Snapshot third = service.submit(id, rejected("duplicate"), null);

        assertThat(third.state()).isEqualTo(EnrollmentState.WAITING);
        assertThat(third.lastRejection()).isEqualTo("abandoned");
        assertThat(third.done()).isZero();
    }

    @Test
    void anAcceptedCaptureResetsTheFailureStreak() {
        Started started = started();
        String id = started.opened().id();
        service.submit(id, rejected("none"), null);
        service.submit(id, rejected("none"), null);
        service.submit(id, accepted(0), null);
        service.submit(id, rejected("none"), null);
        EnrollmentSessionService.Snapshot view = service.submit(id, rejected("none"), null);

        assertThat(view.state()).isEqualTo(EnrollmentState.CAPTURING);
    }

    @Test
    void unknownRejectionReasonsAreNormalized() {
        Started started = started();
        EnrollmentSessionService.Snapshot view = service.submit(started.opened().id(), rejected("<b>x</b>"), null);
        assertThat(view.lastRejection()).isEqualTo("other");
    }

    @Test
    void theSmartphoneCanRestartAfterAnAbandonedAttempt() {
        Started started = started();
        String id = started.opened().id();
        service.submit(id, rejected("none"), null);
        service.submit(id, rejected("none"), null);
        service.submit(id, rejected("none"), null);

        String again = service.start(id, started.opened().pairingToken(), started.claim(), "Alice");

        assertThat(again).isNull();
        assertThat(service.state(id, null, started.claim()).state()).isEqualTo(EnrollmentState.COUNTDOWN);
    }

    @Test
    void submitIsRefusedWhenNoCaptureIsExpected() {
        EnrollmentSessionService.Opened opened = service.openOrResume();
        assertStatus(() -> service.submit(opened.id(), accepted(0), null), HttpStatus.CONFLICT);
    }

    @Test
    void anAcceptedCaptureNeedsAnEmbeddingAndAnAvatar() {
        Started started = started();
        String id = started.opened().id();
        assertStatus(() -> service.submit(id, new EnrollmentSessionService.Submission(true, null, null,
                Fixtures.avatar()), null), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.submit(id, new EnrollmentSessionService.Submission(true, null,
                Fixtures.vector(0), null), null), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.submit(id, new EnrollmentSessionService.Submission(true, null,
                Fixtures.vector(0), "http://evil/x.png"), null), HttpStatus.BAD_REQUEST);
    }

    @Test
    void avatarsAreOnlyAvailableToTheClaimantDuringReview() {
        Started started = started();
        String id = started.opened().id();
        assertStatus(() -> service.avatars(id, started.claim()), HttpStatus.CONFLICT);

        for (int i = 0; i < PHOTOS; i++) {
            service.submit(id, accepted(i), null);
        }

        assertThat(service.avatars(id, started.claim())).hasSize(PHOTOS);
        assertStatus(() -> service.avatars(id, "nope"), HttpStatus.FORBIDDEN);
    }

    @Test
    void restartClearsTheCapturesAndRestartsTheCountdown() {
        Started reviewed = reviewed();
        String id = reviewed.opened().id();

        service.restart(id, reviewed.claim());

        EnrollmentSessionService.Snapshot view = service.state(id, null, reviewed.claim());
        assertThat(view.state()).isEqualTo(EnrollmentState.COUNTDOWN);
        assertThat(view.done()).isZero();
        assertStatus(() -> service.restart(id, reviewed.claim()), HttpStatus.CONFLICT);
    }

    @Test
    void confirmCreatesTheProfileOpensASessionAndDropsTheCaptures() {
        Started reviewed = reviewed();
        String id = reviewed.opened().id();
        ProfileService.ProfileView view = new ProfileService.ProfileView(7L, "Alice", Fixtures.avatar(), PHOTOS);
        var issued = new SessionService.IssuedSession("tok", 7L, "Alice", 1L);
        when(profiles.create(eq("Alice"), anyList(), eq(Fixtures.avatar()))).thenReturn(view);
        when(sessions.openTransientSession(7L, SessionScope.FULL, "previous")).thenReturn(issued);

        EnrollmentSessionService.Confirmation confirmation = service.confirm(id, reviewed.claim(), 2, false, "previous");

        assertThat(confirmation.profile()).isEqualTo(view);
        assertThat(confirmation.session()).isEqualTo(issued);
        ArgumentCaptor<List<float[]>> embeddings = ArgumentCaptor.forClass(List.class);
        verify(profiles).create(eq("Alice"), embeddings.capture(), eq(Fixtures.avatar()));
        assertThat(embeddings.getValue()).hasSize(PHOTOS);
        assertThat(service.state(id, null, reviewed.claim()).state()).isEqualTo(EnrollmentState.DONE);
        assertStatus(() -> service.avatars(id, reviewed.claim()), HttpStatus.CONFLICT);
    }

    @Test
    void confirmRejectsABadAvatarIndexAndAStrangerButKeepsTheReview() {
        Started reviewed = reviewed();
        String id = reviewed.opened().id();

        assertStatus(() -> service.confirm(id, reviewed.claim(), 9, false, null), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.confirm(id, reviewed.claim(), -1, false, null), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.confirm(id, "stranger", 0, false, null), HttpStatus.FORBIDDEN);
        assertThat(service.state(id, null, reviewed.claim()).state()).isEqualTo(EnrollmentState.REVIEW);
        verifyNoInteractions(profiles);
    }

    @Test
    void aRefusedProfileKeepsTheSessionInReview() {
        Started reviewed = reviewed();
        String id = reviewed.opened().id();
        when(profiles.create(eq("Alice"), anyList(), eq(Fixtures.avatar())))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "Ce visage correspond deja a un profil existant."));

        assertStatus(() -> service.confirm(id, reviewed.claim(), 0, false, null), HttpStatus.CONFLICT);

        assertThat(service.state(id, null, reviewed.claim()).state()).isEqualTo(EnrollmentState.REVIEW);
        verify(sessions, never()).openTransientSession(anyLong(), eq(SessionScope.FULL), eq(null));
    }

    @Test
    void recaptureIsBoundToTheProfileConnectedOnThePi() {
        EnrollmentSessionService.Recapture recapture = service.createRecapture(5L);
        service.start(recapture.id(), null, recapture.claimToken(), null);

        assertStatus(() -> service.submit(recapture.id(), accepted(0), null), HttpStatus.FORBIDDEN);
        assertStatus(() -> service.submit(recapture.id(), accepted(0), 6L), HttpStatus.FORBIDDEN);
        assertThat(service.submit(recapture.id(), accepted(0), 5L).done()).isEqualTo(1);
    }

    @Test
    void recaptureConfirmUpdatesTheEmbeddingsWithoutOpeningASession() {
        EnrollmentSessionService.Recapture recapture = service.createRecapture(5L);
        service.start(recapture.id(), null, recapture.claimToken(), null);
        for (int i = 0; i < PHOTOS; i++) {
            service.submit(recapture.id(), accepted(i), 5L);
        }
        ProfileService.ProfileView view = new ProfileService.ProfileView(5L, "Alice", Fixtures.avatar(), PHOTOS);
        when(profiles.updateEmbeddings(eq(5L), anyList(), eq(true), eq(Fixtures.avatar()))).thenReturn(view);

        EnrollmentSessionService.Confirmation confirmation = service.confirm(recapture.id(),
                recapture.claimToken(), 0, true, null);

        assertThat(confirmation.profile()).isEqualTo(view);
        assertThat(confirmation.session()).isNull();
        verify(profiles, never()).create(org.mockito.ArgumentMatchers.any(), anyList(), org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(sessions);
    }

    @Test
    void recaptureNeedsTheClaimTokenAndCannotBeStartedByAnyone() {
        EnrollmentSessionService.Recapture recapture = service.createRecapture(5L);
        assertStatus(() -> service.start(recapture.id(), "anything", null, null), HttpStatus.FORBIDDEN);
        assertStatus(() -> service.state(recapture.id(), "anything", null), HttpStatus.FORBIDDEN);
    }

    @Test
    void recaptureReplacesAnIdleEnrollmentButNotAnotherRecapture() {
        service.openOrResume();
        EnrollmentSessionService.Recapture first = service.createRecapture(5L);
        assertThat(first.claimToken()).isNotBlank();

        assertStatus(() -> service.createRecapture(6L), HttpStatus.CONFLICT);
    }

    @Test
    void aClaimedEnrollmentCannotBeReplacedByARecapture() {
        started();
        assertStatus(() -> service.createRecapture(5L), HttpStatus.CONFLICT);
    }

    @Test
    void cancelByThePiOrTheClaimantButNotByAStranger() {
        Started started = started();
        String id = started.opened().id();

        assertStatus(() -> service.cancel(id, false, null), HttpStatus.FORBIDDEN);
        assertStatus(() -> service.cancel(id, false, "stranger"), HttpStatus.FORBIDDEN);
        service.cancel(id, false, started.claim());

        assertThat(service.state(id, null, started.claim()).state()).isEqualTo(EnrollmentState.CANCELLED);
        service.cancel(id, true, null);
    }

    @Test
    void piCancelDropsTheCapturesAndFreesTheSlot() {
        Started started = started();
        service.submit(started.opened().id(), accepted(0), null);

        service.cancel(started.opened().id(), true, null);

        assertThat(service.kioskView()).hasValueSatisfying(s -> {
            assertThat(s.state()).isEqualTo(EnrollmentState.CANCELLED);
            assertThat(s.done()).isZero();
        });
    }

    @Test
    void theClaimantsActivityKeepsTheSessionAlive() {
        Started started = started();
        String id = started.opened().id();
        clock.advance(Duration.ofMinutes(9));
        service.state(id, null, started.claim());
        clock.advance(Duration.ofMinutes(9));

        assertThat(service.state(id, null, started.claim()).state()).isEqualTo(EnrollmentState.COUNTDOWN);
    }

    @Test
    void kioskViewIsEmptyWithoutSessionAndDoesNotExtendTheSession() {
        assertThat(service.kioskView()).isEmpty();
        EnrollmentSessionService.Opened opened = service.openOrResume();
        clock.advance(Duration.ofMinutes(9));
        service.kioskView();
        clock.advance(Duration.ofMinutes(2));
        assertThat(service.kioskView()).hasValueSatisfying(
                s -> assertThat(s.state()).isEqualTo(EnrollmentState.EXPIRED));
        assertThat(opened).isNotNull();
    }

    @Test
    void signOutOfTheRecapturedProfileCancelsTheRecapture() {
        EnrollmentSessionService.Recapture recapture = service.createRecapture(5L);
        service.onProfileSignedOut(new ProfileSignedOutEvent(6L));
        assertThat(service.kioskView()).hasValueSatisfying(s -> assertThat(s.state()).isEqualTo(EnrollmentState.WAITING));

        service.onProfileSignedOut(new ProfileSignedOutEvent(5L));

        assertThat(service.kioskView()).hasValueSatisfying(s -> assertThat(s.state()).isEqualTo(EnrollmentState.CANCELLED));
        assertThat(recapture).isNotNull();
    }

    @Test
    void deletingTheProfileAndGlobalInvalidationCancelTheSession() {
        service.createRecapture(5L);
        service.onProfileDeleted(new ProfileDeletedEvent(5L));
        assertThat(service.kioskView()).hasValueSatisfying(s -> assertThat(s.state()).isEqualTo(EnrollmentState.CANCELLED));

        service.openOrResume();
        service.onSessionsInvalidated(new SessionsInvalidatedEvent());
        assertThat(service.kioskView()).hasValueSatisfying(s -> assertThat(s.state()).isEqualTo(EnrollmentState.CANCELLED));
    }

    @Test
    void unknownSessionIdsAreNotFound() {
        service.openOrResume();
        assertStatus(() -> service.restart("nope", "x"), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.cancel("nope", true, null), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.submit("nope", accepted(0), null), HttpStatus.NOT_FOUND);
    }

    @Test
    void confirmFlagsAreForwardedForReplaceMode() {
        EnrollmentSessionService.Recapture recapture = service.createRecapture(5L);
        service.start(recapture.id(), null, recapture.claimToken(), null);
        for (int i = 0; i < PHOTOS; i++) {
            service.submit(recapture.id(), accepted(i), 5L);
        }
        when(profiles.updateEmbeddings(eq(5L), anyList(), anyBoolean(), eq(Fixtures.avatar())))
                .thenReturn(new ProfileService.ProfileView(5L, "Alice", Fixtures.avatar(), 2));

        service.confirm(recapture.id(), recapture.claimToken(), 1, false, null);

        verify(profiles).updateEmbeddings(eq(5L), anyList(), eq(false), eq(Fixtures.avatar()));
    }
}
