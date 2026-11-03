package com.assistant.enrollment;

import com.assistant.auth.SessionScope;
import com.assistant.auth.SessionService;
import com.assistant.auth.ProfileSignedOutEvent;
import com.assistant.auth.SessionsInvalidatedEvent;
import com.assistant.common.ApiException;
import com.assistant.common.TokenSupport;
import com.assistant.config.AssistantProperties;
import com.assistant.profile.ProfileDeletedEvent;
import com.assistant.profile.ProfileService;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Session de capture a distance : le smartphone pilote, la camera du Pi prend les photos. Une seule session active
 * (un seul Pi), uniquement en memoire : empreintes et miniatures n'atteignent jamais le disque et disparaissent a la
 * fin ou a l'expiration.
 */
@Service
public class EnrollmentSessionService {

    public enum Role { NONE, PAIRED, CLAIMED }

    public record Opened(String id, EnrollmentMode mode, EnrollmentState state, String pairingToken) {
    }

    public record Recapture(String id, String claimToken) {
    }

    /** Vue d'une session ; le nom et le profil ne sont renseignes que pour le Pi et le smartphone qui a pris la main. */
    public record Snapshot(String id, EnrollmentMode mode, EnrollmentState state, String name, Long profileId,
                           long serverNowMs, long countdownStartsAtMs, long nextShotAtMs, int done, int total,
                           int guidanceIndex, String lastRejection, boolean claimed) {
    }

    public record Submission(boolean accepted, String reason, float[] embedding, String avatar) {
    }

    public record Confirmation(ProfileService.ProfileView profile, SessionService.IssuedSession session) {
    }

    private static final Set<String> REJECTION_REASONS = Set.of("none", "tooSmall", "duplicate", "ambiguous", "multiple");
    private static final long MIN_GAP_BETWEEN_SHOTS_MS = 500L;

    private final AssistantProperties properties;
    private final Clock clock;
    private final ProfileService profiles;
    private final SessionService sessions;
    private EnrollmentSession current;

    public EnrollmentSessionService(AssistantProperties properties, Clock clock, ProfileService profiles,
                                    SessionService sessions) {
        this.properties = properties;
        this.clock = clock;
        this.profiles = profiles;
        this.sessions = sessions;
    }

    /** Cree la session de capture d'un nouveau profil, ou reprend celle en cours (rechargement du Pi, recapture demandee). */
    public synchronized Opened openOrResume() {
        expireIfNeeded();
        if (current == null || current.state.terminal()) {
            current = new EnrollmentSession(EnrollmentMode.CREATE, null, TokenSupport.newToken(), expiry());
        }
        return new Opened(current.id, current.mode, current.state, current.pairingToken);
    }

    /** Demande de recapture du profil de la session du smartphone ; remplace une attente d'enrolement non entamee. */
    public synchronized Recapture createRecapture(long profileId) {
        expireIfNeeded();
        boolean idleEnrollment = current != null && current.mode == EnrollmentMode.CREATE
                && current.state == EnrollmentState.WAITING && current.claimTokenHash == null;
        if (current != null && !current.state.terminal() && !idleEnrollment) {
            throw conflict("Une capture est deja en cours.");
        }
        String claim = TokenSupport.newToken();
        current = new EnrollmentSession(EnrollmentMode.RECAPTURE, profileId, null, expiry());
        current.claimTokenHash = TokenSupport.hash(claim);
        return new Recapture(current.id, claim);
    }

    /** Le QR invite ne porte que le jeton d'appairage : le smartphone retrouve ainsi l'identifiant de la session en attente. */
    public synchronized Snapshot findByPairing(String pairingToken) {
        expireIfNeeded();
        if (current == null || current.state.terminal() || current.mode != EnrollmentMode.CREATE
                || !TokenSupport.matches(pairingToken, current.pairingToken)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Session de capture introuvable ou expiree.");
        }
        return snapshot(current, false);
    }

    public synchronized Snapshot state(String id, String pairingToken, String claimToken) {
        EnrollmentSession session = require(id);
        Role role = roleOf(session, pairingToken, claimToken);
        if (role == Role.NONE) {
            throw forbidden();
        }
        if (role == Role.CLAIMED) {
            touch(session);
        }
        return snapshot(session, role == Role.CLAIMED);
    }

    /** Lance le compte a rebours ; renvoie le jeton de prise en main (premier smartphone) ou null s'il le detient deja. */
    public synchronized String start(String id, String pairingToken, String claimToken, String name) {
        EnrollmentSession session = require(id);
        Role role = roleOf(session, pairingToken, claimToken);
        if (role == Role.NONE) {
            throw forbidden();
        }
        if (session.state != EnrollmentState.WAITING
                || (session.claimTokenHash != null && role != Role.CLAIMED)) {
            throw conflict("Enrolement deja en cours.");
        }
        if (session.mode == EnrollmentMode.CREATE) {
            session.name = ProfileService.validateName(name);
        }
        String issued = null;
        if (session.claimTokenHash == null) {
            issued = TokenSupport.newToken();
            session.claimTokenHash = TokenSupport.hash(issued);
        }
        beginCountdown(session);
        touch(session);
        return issued;
    }

    /** Capture (ou rejet) deposee par le Pi ; piProfileId est le profil connecte sur le Pi (controle en recapture). */
    public synchronized Snapshot submit(String id, Submission submission, Long piProfileId) {
        EnrollmentSession session = require(id);
        if (session.state != EnrollmentState.COUNTDOWN && session.state != EnrollmentState.CAPTURING) {
            throw conflict("Aucune capture n'est attendue.");
        }
        if (session.mode == EnrollmentMode.RECAPTURE && !session.profileId.equals(piProfileId)) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Le profil connecte sur l'ecran n'est pas celui de la recapture.");
        }
        long now = clock.millis();
        session.state = EnrollmentState.CAPTURING;
        if (submission.accepted()) {
            String avatar = ProfileService.validateAvatar(submission.avatar());
            if (avatar == null || submission.embedding() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Capture incomplete : empreinte et miniature attendues.");
            }
            session.captures.add(new EnrollmentSession.Capture(submission.embedding(), avatar));
            session.consecutiveFailures = 0;
            session.lastRejection = null;
            session.guidanceIndex = session.captures.size();
            if (session.captures.size() >= properties.enrollment().photos()) {
                session.state = EnrollmentState.REVIEW;
            } else {
                session.nextShotAtMs = nextShot(session, now);
            }
        } else {
            session.consecutiveFailures++;
            session.lastRejection = REJECTION_REASONS.contains(submission.reason()) ? submission.reason() : "other";
            if (session.consecutiveFailures >= properties.enrollment().remote().maxConsecutiveFailures()) {
                session.captures.clear();
                session.consecutiveFailures = 0;
                session.guidanceIndex = 0;
                session.lastRejection = "abandoned";
                session.state = EnrollmentState.WAITING;
            } else {
                session.nextShotAtMs = nextShot(session, now);
            }
        }
        touch(session);
        return snapshot(session, true);
    }

    public synchronized List<String> avatars(String id, String claimToken) {
        EnrollmentSession session = requireClaimed(id, claimToken);
        if (session.state != EnrollmentState.REVIEW) {
            throw conflict("Les miniatures ne sont disponibles qu'en revue.");
        }
        touch(session);
        return session.captures.stream().map(EnrollmentSession.Capture::avatar).toList();
    }

    public synchronized void restart(String id, String claimToken) {
        EnrollmentSession session = requireClaimed(id, claimToken);
        if (session.state != EnrollmentState.REVIEW) {
            throw conflict("La relance n'est possible qu'en revue.");
        }
        beginCountdown(session);
        touch(session);
    }

    public synchronized Confirmation confirm(String id, String claimToken, int avatarIndex, boolean replace,
                                             String previousSessionToken) {
        EnrollmentSession session = requireClaimed(id, claimToken);
        if (session.state != EnrollmentState.REVIEW) {
            throw conflict("La validation n'est possible qu'en revue.");
        }
        if (avatarIndex < 0 || avatarIndex >= session.captures.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choisissez une des captures comme photo de profil.");
        }
        List<float[]> embeddings = new ArrayList<>();
        session.captures.forEach(capture -> embeddings.add(capture.embedding()));
        String avatar = session.captures.get(avatarIndex).avatar();
        ProfileService.ProfileView view;
        SessionService.IssuedSession issued = null;
        if (session.mode == EnrollmentMode.CREATE) {
            view = profiles.create(session.name, embeddings, avatar);
            issued = sessions.openTransientSession(view.id(), SessionScope.FULL, previousSessionToken);
        } else {
            view = profiles.updateEmbeddings(session.profileId, embeddings, replace, avatar);
        }
        session.state = EnrollmentState.DONE;
        session.captures.clear();
        return new Confirmation(view, issued);
    }

    /** Annulation par le Pi (kiosk = true, cle deja verifiee) ou par le smartphone qui a pris la main. */
    public synchronized void cancel(String id, boolean kiosk, String claimToken) {
        EnrollmentSession session = require(id);
        if (!kiosk && roleOf(session, null, claimToken) != Role.CLAIMED) {
            throw forbidden();
        }
        if (!session.state.terminal()) {
            end(session, EnrollmentState.CANCELLED);
        }
    }

    /** Session courante pour le Pi (y compris terminee, pour qu'il reprenne la reconnaissance), sans la prolonger. */
    public synchronized Optional<Snapshot> kioskView() {
        expireIfNeeded();
        return Optional.ofNullable(current).map(session -> snapshot(session, true));
    }

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    synchronized void purgeExpired() {
        expireIfNeeded();
    }

    @EventListener
    synchronized void onProfileDeleted(ProfileDeletedEvent event) {
        cancelRecaptureOf(event.profileId());
    }

    @EventListener
    synchronized void onProfileSignedOut(ProfileSignedOutEvent event) {
        cancelRecaptureOf(event.profileId());
    }

    @EventListener
    synchronized void onSessionsInvalidated(SessionsInvalidatedEvent event) {
        if (current != null && !current.state.terminal()) {
            end(current, EnrollmentState.CANCELLED);
        }
    }

    private void cancelRecaptureOf(long profileId) {
        if (current != null && !current.state.terminal() && current.mode == EnrollmentMode.RECAPTURE
                && current.profileId == profileId) {
            end(current, EnrollmentState.CANCELLED);
        }
    }

    private EnrollmentSession requireClaimed(String id, String claimToken) {
        EnrollmentSession session = require(id);
        if (roleOf(session, null, claimToken) != Role.CLAIMED) {
            throw forbidden();
        }
        return session;
    }

    private EnrollmentSession require(String id) {
        expireIfNeeded();
        if (current == null || !current.id.equals(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Session de capture introuvable ou expiree.");
        }
        return current;
    }

    private Role roleOf(EnrollmentSession session, String pairingToken, String claimToken) {
        if (claimToken != null && session.claimTokenHash != null
                && TokenSupport.matches(TokenSupport.hash(claimToken), session.claimTokenHash)) {
            return Role.CLAIMED;
        }
        if (session.mode == EnrollmentMode.CREATE && TokenSupport.matches(pairingToken, session.pairingToken)) {
            return Role.PAIRED;
        }
        return Role.NONE;
    }

    private void beginCountdown(EnrollmentSession session) {
        long now = clock.millis();
        session.captures.clear();
        session.state = EnrollmentState.COUNTDOWN;
        session.countdownStartsAtMs = now;
        session.nextShotAtMs = now + properties.enrollment().remote().countdownSeconds() * 1000L;
        session.consecutiveFailures = 0;
        session.guidanceIndex = 0;
        session.lastRejection = null;
    }

    private long nextShot(EnrollmentSession session, long now) {
        long interval = properties.enrollment().remote().intervalSeconds() * 1000L;
        return Math.max(session.nextShotAtMs + interval, now + MIN_GAP_BETWEEN_SHOTS_MS);
    }

    private void end(EnrollmentSession session, EnrollmentState state) {
        session.state = state;
        session.captures.clear();
    }

    private void expireIfNeeded() {
        if (current != null && !current.state.terminal() && clock.millis() >= current.expiresAtMs) {
            end(current, EnrollmentState.EXPIRED);
        }
    }

    private void touch(EnrollmentSession session) {
        session.expiresAtMs = expiry();
    }

    private long expiry() {
        return clock.millis() + properties.enrollment().remote().sessionTtlMinutes() * 60_000L;
    }

    private Snapshot snapshot(EnrollmentSession session, boolean details) {
        int total = properties.enrollment().photos();
        int done = session.state == EnrollmentState.DONE ? total : session.captures.size();
        return new Snapshot(session.id, session.mode, session.state, details ? session.name : null,
                details ? session.profileId : null, clock.millis(), session.countdownStartsAtMs,
                session.nextShotAtMs, done, total, session.guidanceIndex, session.lastRejection,
                session.claimTokenHash != null);
    }

    private static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }

    private static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "Acces refuse a cette session de capture.");
    }
}
