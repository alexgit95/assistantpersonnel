package com.assistant.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "profile_app",
        uniqueConstraints = @UniqueConstraint(name = "uk_profile_app_key", columnNames = {"profile_id", "app_key"}),
        indexes = @Index(name = "idx_profile_app_order", columnList = "profile_id, enabled, position"))
public class ProfileApp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @Column(name = "app_key", nullable = false, length = 64)
    private String appKey;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "config_json", nullable = false, columnDefinition = "TEXT")
    private String configJson;

    protected ProfileApp() {
    }

    public ProfileApp(Profile profile, String appKey, int position, boolean enabled, String configJson) {
        this.profile = profile;
        this.appKey = appKey;
        this.position = position;
        this.enabled = enabled;
        this.configJson = configJson;
    }

    public Long getId() {
        return id;
    }

    public Profile getProfile() {
        return profile;
    }

    public String getAppKey() {
        return appKey;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
    }
}
