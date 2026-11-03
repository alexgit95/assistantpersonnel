package com.assistant.auth;

import com.assistant.common.ApiException;
import com.assistant.common.TokenSupport;
import com.assistant.config.AssistantProperties;
import com.assistant.profile.ProfileDeletedEvent;
import java.time.Clock;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Jetons de transfert a usage unique, en memoire seulement (duree de vie de quelques minutes, aucune ecriture sur disque).
 * Chaque QR affiche porte un identifiant opaque (qrId) dont le Pi suit l'etat pour le regenerer des qu'il est consomme.
 */
@Service
public class HandoffService {

    public enum QrState { ACTIVE, CONSUMED, EXPIRED }

    public record IssuedQr(String token, String qrId, long expiresAtMs) {
    }

    public record Redeemed(long profileId, SessionScope scope) {
    }

    static final int MAX_ACTIVE_TOKENS = 100;
    static final int MAX_ACTIVE_PER_PROFILE_AND_SCOPE = 2;
    private static final long STATE_RETENTION_MS = 60_000L;

    private record Token(String qrId, long profileId, SessionScope scope, long expiresAtMs) {
    }

    private static final class Qr {
        final String tokenHash;
        final long expiresAtMs;
        boolean consumed;

        Qr(String tokenHash, long expiresAtMs) {
            this.tokenHash = tokenHash;
            this.expiresAtMs = expiresAtMs;
        }
    }

    private final AssistantProperties properties;
    private final Clock clock;
    private final Map<String, Token> tokens = new LinkedHashMap<>();
    private final Map<String, Qr> qrs = new HashMap<>();

    public HandoffService(AssistantProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** Emet un jeton pour le profil et la portee ; au plus deux jetons actifs par profil et portee (le plus ancien est revoque). */
    public synchronized IssuedQr issue(long profileId, SessionScope scope) {
        long now = clock.millis();
        dropExpired(now);
        if (tokens.size() >= MAX_ACTIVE_TOKENS) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Trop de QR actifs, reessayez dans un instant.");
        }
        revokeOldest(profileId, scope);
        String token = TokenSupport.newToken();
        String hash = TokenSupport.hash(token);
        String qrId = TokenSupport.newId();
        long expiresAt = now + properties.handoff().tokenTtlSeconds() * 1000L;
        tokens.put(hash, new Token(qrId, profileId, scope, expiresAt));
        qrs.put(qrId, new Qr(hash, expiresAt));
        return new IssuedQr(token, qrId, expiresAt);
    }

    /** Consomme le jeton (usage unique) ; vide s'il est inconnu, deja utilise ou expire. */
    public synchronized Optional<Redeemed> redeem(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Token entry = tokens.remove(TokenSupport.hash(token));
        if (entry == null) {
            return Optional.empty();
        }
        Qr qr = qrs.get(entry.qrId());
        if (qr != null) {
            qr.consumed = true;
        }
        if (entry.expiresAtMs() <= clock.millis()) {
            return Optional.empty();
        }
        return Optional.of(new Redeemed(entry.profileId(), entry.scope()));
    }

    public synchronized QrState state(String qrId) {
        Qr qr = qrs.get(qrId);
        if (qr == null) {
            return QrState.EXPIRED;
        }
        if (qr.consumed) {
            return QrState.CONSUMED;
        }
        return qr.expiresAtMs > clock.millis() ? QrState.ACTIVE : QrState.EXPIRED;
    }

    public synchronized void revokeProfile(long profileId) {
        for (Iterator<Map.Entry<String, Token>> it = tokens.entrySet().iterator(); it.hasNext(); ) {
            Token token = it.next().getValue();
            if (token.profileId() == profileId) {
                qrs.remove(token.qrId());
                it.remove();
            }
        }
    }

    public synchronized void revokeAll() {
        tokens.clear();
        qrs.clear();
    }

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    public synchronized void purgeExpired() {
        dropExpired(clock.millis());
    }

    @EventListener
    void onProfileDeleted(ProfileDeletedEvent event) {
        revokeProfile(event.profileId());
    }

    @EventListener
    void onProfileSignedOut(ProfileSignedOutEvent event) {
        revokeProfile(event.profileId());
    }

    @EventListener
    void onSessionsInvalidated(SessionsInvalidatedEvent event) {
        revokeAll();
    }

    public long tokenTtlSeconds() {
        return properties.handoff().tokenTtlSeconds();
    }

    synchronized int activeTokenCount() {
        return tokens.size();
    }

    private void revokeOldest(long profileId, SessionScope scope) {
        long same = tokens.values().stream().filter(t -> t.profileId() == profileId && t.scope() == scope).count();
        for (Iterator<Map.Entry<String, Token>> it = tokens.entrySet().iterator();
                it.hasNext() && same >= MAX_ACTIVE_PER_PROFILE_AND_SCOPE; ) {
            Token token = it.next().getValue();
            if (token.profileId() == profileId && token.scope() == scope) {
                qrs.remove(token.qrId());
                it.remove();
                same--;
            }
        }
    }

    private void dropExpired(long now) {
        tokens.values().removeIf(token -> token.expiresAtMs() <= now);
        qrs.values().removeIf(qr -> qr.expiresAtMs + STATE_RETENTION_MS <= now);
    }
}
