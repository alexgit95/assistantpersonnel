package com.assistant.apps.transit;

import com.assistant.apps.AssistantApp;
import org.springframework.stereotype.Component;

@Component
public class TransitAssistantApp implements AssistantApp {

    @Override
    public String key() {
        return "transit";
    }

    @Override
    public String title() {
        return "Transports";
    }

    @Override
    public String description() {
        return "Alertes trafic sur vos lignes d'Île-de-France favorites.";
    }

    @Override
    public String fragmentTemplate() {
        return "fragments/widgets/transit :: content";
    }

    @Override
    public String widgetModule() {
        return "/js/widgets/transit.js";
    }
}
