package com.assistant.apps;

import org.springframework.stereotype.Component;

@Component
public class WeatherAssistantApp implements AssistantApp {

    @Override
    public String key() {
        return "weather";
    }

    @Override
    public String title() {
        return "Météo";
    }

    @Override
    public String description() {
        return "Météo actuelle et prévisions pour une ville choisie.";
    }

    @Override
    public String fragmentTemplate() {
        return "fragments/widgets/weather :: content";
    }

    @Override
    public String widgetModule() {
        return "/js/widgets/weather.js";
    }
}