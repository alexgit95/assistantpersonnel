package com.assistant.auth;

/** Session valide resolue pour la requete courante ; le profil ne vient jamais du client. */
public record AuthenticatedSession(String sessionId, long profileId, long expiresAtMs) {

    public static final String REQUEST_ATTRIBUTE = AuthenticatedSession.class.getName();
}
