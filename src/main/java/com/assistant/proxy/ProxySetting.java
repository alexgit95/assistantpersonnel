package com.assistant.proxy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Reglage unique (id = 1) du proxy sortant ; le mot de passe est stocke en clair (usage LAN). */
@Entity
@Table(name = "proxy_setting")
public class ProxySetting {

    @Id
    private Long id;

    @Column(nullable = false)
    private boolean enabled;

    @Column(length = 300)
    private String url;

    @Column(length = 200)
    private String username;

    @Column(length = 200)
    private String password;

    @Column(name = "no_proxy", length = 500)
    private String noProxy;

    protected ProxySetting() {
    }

    public ProxySetting(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNoProxy() {
        return noProxy;
    }

    public void setNoProxy(String noProxy) {
        this.noProxy = noProxy;
    }
}
