package com.assistant.auth;

import com.assistant.common.ApiException;
import com.assistant.config.AssistantProperties;
import com.assistant.profile.ProfileDeletedEvent;
import com.assistant.profile.ProfileRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sessions faciales glissantes : expiration = derniere detection du profil + duree configuree. */
@Service
public class SessionService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppSessionRepository sessions;
    private final ProfileRepository profiles;
    private final AssistantProperties properties;
    private final Clock clock;

    public SessionService(AppSessionRepository sessions, ProfileRepository profiles,
                          AssistantProperties properties, Clock clock) {
        this.sessions = sessions;
        this.profiles = profiles;
        this.properties = properties;
        this.clock = clock;
    }

    /** Ouvre une session pour le profil ; l'eventuelle session precedente de l'appareil est fermee (bascule). */
    @Transactional
    public IssuedSession login(long profileId, String previousToken) {
        String name = profiles.findNameById(profileId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Profil introuvable."));
        if (previousToken != null && !previousToken.isBlank()) {
            sessions.deleteById(hash(previousToken));
        }
        String token = newToken();
        long now = clock.millis();
        long expiresAt = now + durationMs();
        sessions.save(new AppSession(hash(token), profileId, now, expiresAt));
        return new IssuedSession(token, profileId, name, expiresAt);
    }

    @Transactional(readOnly = true)
    public Optional<AuthenticatedSession> authenticate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String id = hash(token);
        long now = clock.millis();
        return sessions.findById(id)
                .filter(s -> s.getExpiresAtMs() > now)
                .map(s -> new AuthenticatedSession(s.getId(), s.getProfileId(), s.getExpiresAtMs()));
    }

    /** Prolonge la session du profil courant (appele quand son visage est revu). */
    @Transactional
    public Optional<IssuedSession> heartbeat(String token) {
        return authenticate(token).map(session -> {
            long expiresAt = clock.millis() + durationMs();
            sessions.updateExpiry(session.sessionId(), expiresAt);
            String name = profiles.findNameById(session.profileId()).orElse("");
            return new IssuedSession(token, session.profileId(), name, expiresAt);
        });
    }

    @Transactional
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            sessions.deleteById(hash(token));
        }
    }

    @Transactional
    public int purgeExpired() {
        return sessions.deleteExpired(clock.millis());
    }

    @Transactional
    public void invalidateAll() {
        sessions.deleteAllInBatch();
    }

    @EventListener
    void onProfileDeleted(ProfileDeletedEvent event) {
        sessions.deleteByProfileId(event.profileId());
    }

    @Transactional(readOnly = true)
    public String profileName(long profileId) {
        return profiles.findNameById(profileId).orElse("");
    }

    public long remainingSeconds(long expiresAtMs) {
        return Math.max(0, (expiresAtMs - clock.millis() + 999) / 1000);
    }

    public long durationSeconds() {
        return properties.session().durationMinutes() * 60L;
    }

    private long durationMs() {
        return durationSeconds() * 1000L;
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record IssuedSession(String token, long profileId, String profileName, long expiresAtMs) {
    }
}
