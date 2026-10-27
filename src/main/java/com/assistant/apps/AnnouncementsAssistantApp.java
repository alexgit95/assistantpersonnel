package com.assistant.apps;

import org.springframework.stereotype.Component;

@Component
public class AnnouncementsAssistantApp implements AssistantApp {

    @Override
    public String key() {
        return "announcements";
    }

    @Override
    public String title() {
        return "Annonces";
    }

    @Override
    public String description() {
        return "Annonces communes à tous, avec compte à rebours.";
    }

    @Override
    public String fragmentTemplate() {
        return "fragments/widgets/announcements :: content";
    }

    @Override
    public String widgetModule() {
        return "/js/widgets/announcements.js";
    }
}
