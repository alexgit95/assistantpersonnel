package com.assistant.kiosk;

import com.assistant.config.AssistantProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** Refuse de demarrer en onprem ou remote sans cle de kiosque ; en dev, une cle vide desactive le controle. */
@Component
public class KioskStartupCheck {

    private static final Logger LOG = LoggerFactory.getLogger(KioskStartupCheck.class);

    public KioskStartupCheck(AssistantProperties properties, Environment environment) {
        if (!properties.kiosk().key().isBlank()) {
            return;
        }
        if (environment.acceptsProfiles(Profiles.of("onprem", "remote", "prod"))) {
            throw new IllegalStateException("ASSISTANT_KIOSK_KEY est obligatoire en mode onprem ou remote.");
        }
        LOG.warn("ASSISTANT_KIOSK_KEY est vide : les appels reserves au Pi ne sont pas controles (mode dev uniquement).");
    }
}
