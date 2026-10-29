package com.assistant.apps;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WeatherAdviceTest {

    private static final int CLEAR = 0;
    private static final int RAIN = 61;
    private static final int SNOW = 73;
    private static final int STORM = 95;

    @Test
    void neutralMessageWhenNothingParticular() {
        assertThat(WeatherAdvice.advise(14, 20, 10, 20, CLEAR)).isEqualTo("Rien de particulier, profite de ta journée.");
    }

    @Test
    void umbrellaFromFiftyPercentRain() {
        assertThat(WeatherAdvice.advise(14, 20, 49, 20, CLEAR)).doesNotContain("parapluie");
        assertThat(WeatherAdvice.advise(14, 20, 50, 20, RAIN)).isEqualTo("Prends un parapluie.");
    }

    @Test
    void umbrellaAndJacketAreCombinedInOneSentence() {
        assertThat(WeatherAdvice.advise(9, 14, 70, 20, RAIN)).isEqualTo("Prends un parapluie et une veste.");
    }

    @Test
    void jacketBelowTwelveDegrees() {
        assertThat(WeatherAdvice.advise(11.9, 20, 0, 20, CLEAR)).isEqualTo("Prends une veste.");
        assertThat(WeatherAdvice.advise(12, 20, 0, 20, CLEAR)).doesNotContain("veste");
    }

    @Test
    void veryColdBelowFiveDegrees() {
        assertThat(WeatherAdvice.advise(3, 8, 0, 20, CLEAR)).isEqualTo("Il fait très froid, couvre-toi bien.");
        assertThat(WeatherAdvice.advise(5, 8, 0, 20, CLEAR)).isEqualTo("Prends une veste.");
    }

    @Test
    void gustsAboveFiftyAreMentioned() {
        assertThat(WeatherAdvice.advise(14, 20, 0, 50, CLEAR)).doesNotContain("Rafales");
        assertThat(WeatherAdvice.advise(14, 20, 0, 62, CLEAR)).isEqualTo("Rafales jusqu'à 62 km/h, attention au vent.");
    }

    @Test
    void heatFromTwentyEightDegrees() {
        assertThat(WeatherAdvice.advise(18, 28, 0, 20, CLEAR)).isEqualTo("Il fera chaud, pense à t'hydrater.");
        assertThat(WeatherAdvice.advise(18, 27.9, 0, 20, CLEAR)).doesNotContain("chaud");
    }

    @Test
    void stormAndSnowHaveTheirOwnMessageAndSkipTheUmbrella() {
        assertThat(WeatherAdvice.advise(14, 20, 90, 20, STORM)).isEqualTo("Orage prévu, reste à l'abri.");
        assertThat(WeatherAdvice.advise(2, 4, 90, 20, SNOW))
                .startsWith("De la neige est attendue").doesNotContain("parapluie");
    }

    @Test
    void sentencesFollowThePriorityOrder() {
        assertThat(WeatherAdvice.advise(9, 14, 70, 62, RAIN))
                .isEqualTo("Prends un parapluie et une veste. Rafales jusqu'à 62 km/h, attention au vent.");
    }
}
