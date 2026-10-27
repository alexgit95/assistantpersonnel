package com.assistant.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SessionPurgeTask {

    private static final Logger LOG = LoggerFactory.getLogger(SessionPurgeTask.class);

    private final SessionService sessionService;

    public SessionPurgeTask(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    void purge() {
        int removed = sessionService.purgeExpired();
        if (removed > 0) {
            LOG.debug("{} session(s) expiree(s) supprimee(s)", removed);
        }
    }
}
