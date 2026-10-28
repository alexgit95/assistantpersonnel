package com.assistant.quota;

import com.assistant.config.AssistantProperties;
import java.time.Clock;
import java.time.LocalTime;
import org.springframework.stereotype.Component;

/** Fenetre sans appel externe (heure de Paris), eventuellement a cheval sur minuit ; vide = jamais en pause. */
@Component
public class QuietHours {

    private final LocalTime from;
    private final LocalTime to;
    private final Clock clock;

    public QuietHours(AssistantProperties properties, Clock clock) {
        this.from = parse(properties.prim().pauseFrom());
        this.to = parse(properties.prim().pauseTo());
        this.clock = clock;
    }

    public boolean paused() {
        if (from == null || to == null || from.equals(to)) {
            return false;
        }
        LocalTime now = LocalTime.now(clock.withZone(ApiQuotaService.PARIS));
        return from.isBefore(to)
                ? !now.isBefore(from) && now.isBefore(to)
                : !now.isBefore(from) || now.isBefore(to);
    }

    /** Texte court de reprise, par exemple « 5 h » ou « 5 h 30 ». */
    public String resumeLabel() {
        if (to == null) {
            return "";
        }
        return to.getMinute() == 0 ? to.getHour() + " h" : to.getHour() + " h " + String.format("%02d", to.getMinute());
    }

    private static LocalTime parse(String value) {
        return value == null || value.isBlank() ? null : LocalTime.parse(value.strip());
    }
}
