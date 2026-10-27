package com.assistant.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

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

    protected AppSession() {
    }

    public AppSession(String id, long profileId, long createdAtMs, long expiresAtMs) {
        this.id = id;
        this.profileId = profileId;
        this.createdAtMs = createdAtMs;
        this.expiresAtMs = expiresAtMs;
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
}
