package com.assistant.apps;

/** Contrat d'un widget decouvert comme bean Spring, sans dependance au profil courant. */
public interface AssistantApp {

    String key();

    String title();

    String description();

    /** Nom Thymeleaf de type "fragments/widgets/weather :: content". */
    String fragmentTemplate();

    default String widgetModule() {
        return null;
    }

    /** Configuration JSON objet par defaut pour un profil qui active l'application. */
    default String defaultConfigJson() {
        return "{}";
    }

    default boolean defaultEnabled() {
        return false;
    }

    default int defaultPosition() {
        return ProfileAppService.UNASSIGNED_SLOT;
    }
}
