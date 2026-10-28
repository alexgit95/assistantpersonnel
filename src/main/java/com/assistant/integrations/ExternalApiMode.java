package com.assistant.integrations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "external_api_mode")
public class ExternalApiMode {

    @Id
    private Long id;

    @Column(name = "test_mode", nullable = false)
    private boolean testMode;

    protected ExternalApiMode() {
    }

    public ExternalApiMode(Long id, boolean testMode) {
        this.id = id;
        this.testMode = testMode;
    }

    public Long getId() {
        return id;
    }

    public boolean isTestMode() {
        return testMode;
    }

    public void setTestMode(boolean testMode) {
        this.testMode = testMode;
    }
}
