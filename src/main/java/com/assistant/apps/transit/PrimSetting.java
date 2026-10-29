package com.assistant.apps.transit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Reglage unique (id = 1) : la cle PRIM est stockee en clair (usage LAN). */
@Entity
@Table(name = "prim_setting")
public class PrimSetting {

    @Id
    private Long id;

    @Column(name = "api_key", length = 200)
    private String apiKey;

    protected PrimSetting() {
    }

    public PrimSetting(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
