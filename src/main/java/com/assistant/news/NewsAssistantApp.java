package com.assistant.news;

import com.assistant.apps.AssistantApp;
import org.springframework.stereotype.Component;

@Component
public class NewsAssistantApp implements AssistantApp {

    @Override
    public String key() {
        return "news";
    }

    @Override
    public String title() {
        return "Actualités";
    }

    @Override
    public String description() {
        return "Actualités des catégories de flux RSS de votre choix.";
    }

    @Override
    public String fragmentTemplate() {
        return "fragments/widgets/news :: content";
    }

    @Override
    public String widgetModule() {
        return "/js/widgets/news.js";
    }

    @Override
    public boolean guestAllowed() {
        return true;
    }
}
