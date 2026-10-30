package com.assistant.apps.transit.passages;

import com.assistant.apps.AssistantApp;
import org.springframework.stereotype.Component;

@Component
public class TransitPassagesAssistantApp implements AssistantApp {

    @Override
    public String key() {
        return "transit-passages";
    }

    @Override
    public String title() {
        return "Prochains passages";
    }

    @Override
    public String description() {
        return "Prochains passages en minutes à vos arrêts favoris d'Île-de-France.";
    }

    @Override
    public String fragmentTemplate() {
        return "fragments/widgets/transit-passages :: content";
    }

    @Override
    public String widgetModule() {
        return "/js/widgets/transit-passages.js";
    }
}
