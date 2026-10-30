package com.assistant.guest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Reglage unique (id = 1) : identifie le profil invite global ; sans cle etrangere, comme app_session. */
@Entity
@Table(name = "guest_setting")
public class GuestSetting {

    @Id
    private Long id;

    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    protected GuestSetting() {
    }

    public GuestSetting(Long id, Long profileId) {
        this.id = id;
        this.profileId = profileId;
    }

    public Long getId() {
        return id;
    }

    public Long getProfileId() {
        return profileId;
    }

    public void setProfileId(Long profileId) {
        this.profileId = profileId;
    }
}
