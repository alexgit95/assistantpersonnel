package com.assistant.apps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assistant.common.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AssistantAppRegistryTest {

    private record StubApp(String key, String title, String description, String fragmentTemplate)
            implements AssistantApp {
    }

    @Test
    void discoversApplicationsAndReturnsStableKeyOrder() {
        var registry = new AssistantAppRegistry(List.of(
                new StubApp("weather", "Météo", "Conditions et prévisions", "widgets/weather :: content"),
                new StubApp("calendar", "Agenda", "Événements", "widgets/calendar :: content")));

        assertThat(registry.all()).extracting(AssistantApp::key).containsExactly("calendar", "weather");
        assertThat(registry.require("weather").title()).isEqualTo("Météo");
        assertThat(registry.contains("calendar")).isTrue();
    }

    @Test
    void emptyRegistryIsValidBeforeAnyWidgetIsAdded() {
        assertThat(new AssistantAppRegistry(List.of()).all()).isEmpty();
    }

    @Test
    void rejectsUnknownApplicationLookup() {
        var registry = new AssistantAppRegistry(List.of());

        assertThatThrownBy(() -> registry.require("missing"))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void rejectsInvalidKeysAndFragmentsAtStartup() {
        assertThatThrownBy(() -> new AssistantAppRegistry(List.of(
                new StubApp("Bad Key", "Widget", "Description", "widgets/sample :: content"))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AssistantAppRegistry(List.of(
                new StubApp("sample", "Widget", "Description", null))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsDuplicateKeysAtStartup() {
        assertThatThrownBy(() -> new AssistantAppRegistry(List.of(
                new StubApp("sample", "First", "Description", "widgets/one :: content"),
                new StubApp("sample", "Second", "Description", "widgets/two :: content"))))
                .isInstanceOf(IllegalStateException.class);
    }

        @Test
        void rejectsWidgetModulesOutsideTheStaticWidgetDirectory() {
                AssistantApp invalidModule = new AssistantApp() {
                        @Override
                        public String key() {
                                return "sample";
                        }

                        @Override
                        public String title() {
                                return "Widget";
                        }

                        @Override
                        public String description() {
                                return "Description";
                        }

                        @Override
                        public String fragmentTemplate() {
                                return "widgets/sample :: content";
                        }

                        @Override
                        public String widgetModule() {
                                return "https://invalid.example/widget.js";
                        }
                };

                assertThatThrownBy(() -> new AssistantAppRegistry(List.of(invalidModule)))
                                .isInstanceOf(IllegalStateException.class);
        }
}
