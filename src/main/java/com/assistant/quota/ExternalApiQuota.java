package com.assistant.quota;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Compteur d'appels journalier a une API externe ; la cle naturelle "api:jour" garantit l'unicite. */
@Entity
@Table(name = "external_api_quota")
public class ExternalApiQuota {

    @Id
    @Column(length = 48)
    private String id;

    @Column(name = "api_name", nullable = false, length = 32)
    private String apiName;

    @Column(name = "quota_day", nullable = false, length = 10)
    private String quotaDay;

    @Column(nullable = false)
    private int calls;

    protected ExternalApiQuota() {
    }

    public ExternalApiQuota(String apiName, String quotaDay, int calls) {
        this.id = apiName + ":" + quotaDay;
        this.apiName = apiName;
        this.quotaDay = quotaDay;
        this.calls = calls;
    }

    public String getId() {
        return id;
    }

    public String getApiName() {
        return apiName;
    }

    public String getQuotaDay() {
        return quotaDay;
    }

    public int getCalls() {
        return calls;
    }
}
