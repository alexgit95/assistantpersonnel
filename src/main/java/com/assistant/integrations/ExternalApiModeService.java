package com.assistant.integrations;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

@Service
public class ExternalApiModeService {

    static final long SETTING_ID = 1L;

    private final ExternalApiModeRepository repository;
    private volatile boolean testMode;

    public ExternalApiModeService(ExternalApiModeRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    void load() {
        testMode = repository.findById(SETTING_ID).map(ExternalApiMode::isTestMode).orElse(false);
    }

    public boolean isTestMode() {
        return testMode;
    }

    public void setTestMode(boolean enabled) {
        ExternalApiMode settings = repository.findById(SETTING_ID)
                .orElseGet(() -> new ExternalApiMode(SETTING_ID, false));
        settings.setTestMode(enabled);
        repository.saveAndFlush(settings);
        testMode = enabled;
    }
}
