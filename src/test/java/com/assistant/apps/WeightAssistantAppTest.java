package com.assistant.apps;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WeightAssistantAppTest {

    private final WeightAssistantApp app = new WeightAssistantApp();

    @Test
    void theWidgetStaysReservedToConnectedProfilesAndEnabledByDefault() {
        assertThat(app.key()).isEqualTo("weight");
        assertThat(app.guestAllowed()).isFalse();
        assertThat(app.defaultEnabled()).isTrue();
        assertThat(app.defaultPosition()).isZero();
    }

    @Test
    void theWidgetUsesTheChartAndQrFragmentAndItsModule() {
        assertThat(app.fragmentTemplate()).isEqualTo("fragments/widgets/weight :: content");
        assertThat(app.widgetModule()).isEqualTo("/js/widgets/weight.js");
        assertThat(app.description()).contains("téléphone");
    }
}
