package com.assistant.kiosk;

import com.assistant.config.AssistantProperties;
import com.assistant.common.TokenSupport;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/** Verifie la cle de kiosque d'une requete ; cle configuree vide = controle desactive (profil local). */
@Component
public class KioskAccess {

    public static final String HEADER = "X-Kiosk-Key";

    private final String key;

    public KioskAccess(AssistantProperties properties) {
        this.key = properties.kiosk().key();
    }

    public boolean isAuthorized(HttpServletRequest request) {
        return key.isBlank() || TokenSupport.matches(request.getHeader(HEADER), key);
    }
}
