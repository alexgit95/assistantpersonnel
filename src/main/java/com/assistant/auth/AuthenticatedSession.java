package com.assistant.auth;

/** Session valide resolue pour la requete courante ; le profil ne vient jamais du client. */
public record AuthenticatedSession(String sessionId, long profileId, long expiresAtMs) {

    public static final String REQUEST_ATTRIBUTE = AuthenticatedSession.class.getName();
    static final String GUEST_SESSION_ID = "guest";

    /** Contexte sans session reelle : uniquement pour les lectures annotees @AllowGuest. */
    public static AuthenticatedSession guest(long profileId) {
        return new AuthenticatedSession(GUEST_SESSION_ID, profileId, 0L);
    }

    public boolean isGuest() {
        return GUEST_SESSION_ID.equals(sessionId);
    }
}
