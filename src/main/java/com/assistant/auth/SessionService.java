package com.assistant.auth;

import com.assistant.common.ApiException;
import com.assistant.common.TokenSupport;
import com.assistant.config.AssistantProperties;
import com.assistant.profile.ProfileDeletedEvent;
import com.assistant.profile.ProfileRepository;
import java.time.Clock;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sessions faciales glissantes (Pi) et sessions transitoires a duree fixe (smartphone, issues d'un transfert). */
@Service
public class SessionService {

    private final AppSessionRepository sessions;
    private final ProfileRepository profiles;
    private final AssistantProperties properties;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    @Autowired
    public SessionService(AppSessionRepository sessions, ProfileRepository profiles,
                          AssistantProperties properties, Clock clock, ApplicationEventPublisher events) {
        this.sessions = sessions;
        this.profiles = profiles;
        this.properties = properties;
        this.clock = clock;
        this.events = events;
    }

    public SessionService(AppSessionRepository sessions, ProfileRepository profiles,
                          AssistantProperties properties, Clock clock) {
        this(sessions, profiles, properties, clock, event -> { });
    }

    /** Ouvre une session pour le profil ; l'eventuelle session precedente de l'appareil est fermee (bascule). */
    @Transactional
    public IssuedSession login(long profileId, String previousToken) {
        String name = requireRealProfile(profileId);
        Long previousProfile = closePrevious(previousToken);
        IssuedSession issued = issue(profileId, name, SessionScope.FULL, SessionSource.FACE, durationMs());
        if (previousProfile != null && previousProfile != profileId) {
            events.publishEvent(new ProfileSignedOutEvent(previousProfile));
        }
        return issued;
    }

    /** Session d'un smartphone : duree fixe selon la portee, jamais prolongee, sans fermer la session du Pi. */
    @Transactional
    public IssuedSession openTransientSession(long profileId, SessionScope scope, String previousToken) {
        String name = requireRealProfile(profileId);
        closePrevious(previousToken);
        return issue(profileId, name, scope, SessionSource.HANDOFF, transientDurationSeconds(scope) * 1000L);
    }

    @Transactional(readOnly = true)
    public Optional<AuthenticatedSession> authenticate(String token) {
        return findValid(token)
                .map(s -> new AuthenticatedSession(s.getId(), s.getProfileId(), s.getExpiresAtMs(), s.getScope()));
    }

    /** Prolonge la session du profil courant (appele quand son visage est revu) ; sans effet sur un smartphone. */
    @Transactional
    public Optional<IssuedSession> heartbeat(String token) {
        return findValid(token).map(session -> {
            long expiresAt = session.getExpiresAtMs();
            if (session.getSource() == SessionSource.FACE) {
                expiresAt = clock.millis() + durationMs();
                sessions.updateExpiry(session.getId(), expiresAt);
            }
            String name = profiles.findNameById(session.getProfileId()).orElse("");
            return new IssuedSession(token, session.getProfileId(), name, expiresAt);
        });
    }

    @Transactional
    public void logout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        String id = hash(token);
        Optional<AppSession> existing = sessions.findById(id);
        sessions.deleteById(id);
        existing.ifPresent(session -> events.publishEvent(new ProfileSignedOutEvent(session.getProfileId())));
    }

    @Transactional
    public int purgeExpired() {
        return sessions.deleteExpired(clock.millis());
    }

    @Transactional
    public void invalidateAll() {
        sessions.deleteAllInBatch();
        events.publishEvent(new SessionsInvalidatedEvent());
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

    /** Duree fixe d'une session de smartphone, selon sa portee. */
    public long transientDurationSeconds(SessionScope scope) {
        var handoff = properties.handoff();
        int minutes = scope == SessionScope.WEIGHT ? handoff.weightSessionMinutes() : handoff.fullSessionMinutes();
        return minutes * 60L;
    }

    private Optional<AppSession> findValid(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        long now = clock.millis();
        return sessions.findById(hash(token)).filter(s -> s.getExpiresAtMs() > now);
    }

    private String requireRealProfile(long profileId) {
        String name = profiles.findNameById(profileId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Profil introuvable."));
        if (profiles.isGuest(profileId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Profil introuvable.");
        }
        return name;
    }

    /** Ferme la session precedente de l'appareil et renvoie son profil, ou null si elle n'existait plus. */
    private Long closePrevious(String previousToken) {
        if (previousToken == null || previousToken.isBlank()) {
            return null;
        }
        String id = hash(previousToken);
        Optional<AppSession> previous = sessions.findById(id);
        sessions.deleteById(id);
        return previous.map(AppSession::getProfileId).orElse(null);
    }

    private IssuedSession issue(long profileId, String name, SessionScope scope, SessionSource source, long durationMs) {
        String token = TokenSupport.newToken();
        long now = clock.millis();
        long expiresAt = now + durationMs;
        sessions.save(new AppSession(hash(token), profileId, now, expiresAt, scope, source));
        return new IssuedSession(token, profileId, name, expiresAt);
    }

    private long durationMs() {
        return durationSeconds() * 1000L;
    }

    static String hash(String token) {
        return TokenSupport.hash(token);
    }

    public record IssuedSession(String token, long profileId, String profileName, long expiresAtMs) {
    }
}
