package com.assistant.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;

/** Session faciale glissante ; l'identifiant stocke est le hash SHA-256 du jeton du cookie. */
@Entity
@Table(name = "app_session", indexes = {
        @Index(name = "idx_app_session_expires", columnList = "expires_at_ms"),
        @Index(name = "idx_app_session_profile", columnList = "profile_id")})
public class AppSession {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "profile_id", nullable = false)
    private long profileId;

    @Column(name = "created_at_ms", nullable = false)
    private long createdAtMs;

    @Column(name = "expires_at_ms", nullable = false)
    private long expiresAtMs;

    /** Valeur par defaut en base : les sessions deja presentes lors de l'ajout de la colonne restent completes. */
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'FULL'")
    @Column(name = "scope", length = 16, nullable = false)
    private SessionScope scope = SessionScope.FULL;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'FACE'")
    @Column(name = "source", length = 16, nullable = false)
    private SessionSource source = SessionSource.FACE;

    protected AppSession() {
    }

    public AppSession(String id, long profileId, long createdAtMs, long expiresAtMs) {
        this(id, profileId, createdAtMs, expiresAtMs, SessionScope.FULL, SessionSource.FACE);
    }

    public AppSession(String id, long profileId, long createdAtMs, long expiresAtMs,
                      SessionScope scope, SessionSource source) {
        this.id = id;
        this.profileId = profileId;
        this.createdAtMs = createdAtMs;
        this.expiresAtMs = expiresAtMs;
        this.scope = scope;
        this.source = source;
    }

    public String getId() {
        return id;
    }

    public long getProfileId() {
        return profileId;
    }

    public long getCreatedAtMs() {
        return createdAtMs;
    }

    public long getExpiresAtMs() {
        return expiresAtMs;
    }

    public SessionScope getScope() {
        return scope == null ? SessionScope.FULL : scope;
    }

    public SessionSource getSource() {
        return source == null ? SessionSource.FACE : source;
    }
}
