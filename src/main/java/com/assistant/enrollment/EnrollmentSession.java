package com.assistant.enrollment;

import com.assistant.common.TokenSupport;
import java.util.ArrayList;
import java.util.List;

/** Etat d'une session de capture ; accede uniquement sous le verrou d'{@link EnrollmentSessionService}. */
final class EnrollmentSession {

    record Capture(float[] embedding, String avatar) {
    }

    final String id = TokenSupport.newId();
    final EnrollmentMode mode;
    final Long profileId;
    /** Secret du QR invite, garde en clair en memoire pour que le Pi puisse reafficher le QR apres un rechargement. */
    final String pairingToken;
    String claimTokenHash;
    EnrollmentState state = EnrollmentState.WAITING;
    String name;
    final List<Capture> captures = new ArrayList<>();
    long countdownStartsAtMs;
    long nextShotAtMs;
    long expiresAtMs;
    int consecutiveFailures;
    int guidanceIndex;
    String lastRejection;

    EnrollmentSession(EnrollmentMode mode, Long profileId, String pairingToken, long expiresAtMs) {
        this.mode = mode;
        this.profileId = profileId;
        this.pairingToken = pairingToken;
        this.expiresAtMs = expiresAtMs;
    }
}
