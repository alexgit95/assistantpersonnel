package com.assistant.apps;

import org.springframework.stereotype.Component;

@Component
public class WeightAssistantApp implements AssistantApp {

    @Override
    public String key() {
        return "weight";
    }

    @Override
    public String title() {
        return "Suivi du poids";
    }

    @Override
    public String description() {
        return "Saisissez et consultez votre historique de poids.";
    }

    @Override
    public String fragmentTemplate() {
        return "fragments/widgets/weight :: content";
    }

    @Override
    public String widgetModule() {
        return "/js/widgets/weight.js";
    }

    @Override
    public boolean defaultEnabled() {
        return true;
    }

    @Override
    public int defaultPosition() {
        return 0;
    }
}