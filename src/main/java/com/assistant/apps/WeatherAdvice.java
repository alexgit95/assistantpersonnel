package com.assistant.apps;

import java.util.ArrayList;
import java.util.List;

/** Phrase de conseil du jour (tutoiement) deduite des temperatures, de la pluie, des rafales et du code WMO. */
final class WeatherAdvice {

    static final int RAIN_CHANCE_PERCENT = 50;
    static final int GUST_ALERT_KMH = 50;
    static final double VERY_COLD_C = 5;
    static final double COOL_C = 12;
    static final double HOT_C = 28;

    private WeatherAdvice() {
    }

    static String advise(double minC, double maxC, int rainChancePercent, int maxGustKmh, int worstCode) {
        List<String> sentences = new ArrayList<>();
        boolean storm = WeatherCode.isStorm(worstCode);
        boolean snow = WeatherCode.isSnow(worstCode);
        boolean rain = rainChancePercent >= RAIN_CHANCE_PERCENT && !storm && !snow;
        if (storm) {
            sentences.add("Orage prévu, reste à l'abri.");
        } else if (snow) {
            sentences.add("De la neige est attendue, couvre-toi et fais attention en route.");
        }
        List<String> items = new ArrayList<>();
        if (rain) {
            items.add("un parapluie");
        }
        if (minC >= VERY_COLD_C && minC < COOL_C) {
            items.add("une veste");
        }
        if (!items.isEmpty()) {
            sentences.add("Prends " + String.join(" et ", items) + ".");
        }
        if (minC < VERY_COLD_C) {
            sentences.add("Il fait très froid, couvre-toi bien.");
        }
        if (maxGustKmh > GUST_ALERT_KMH) {
            sentences.add("Rafales jusqu'à " + maxGustKmh + " km/h, attention au vent.");
        }
        if (maxC >= HOT_C) {
            sentences.add("Il fera chaud, pense à t'hydrater.");
        }
        return sentences.isEmpty() ? "Rien de particulier, profite de ta journée." : String.join(" ", sentences);
    }
}
