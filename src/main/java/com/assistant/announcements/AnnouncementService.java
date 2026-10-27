package com.assistant.announcements;

import com.assistant.common.ApiException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnnouncementService {

    public static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    public static final int MAX_TITLE_LENGTH = 200;

    private final AnnouncementRepository announcements;
    private final Clock clock;

    public AnnouncementService(AnnouncementRepository announcements, Clock clock) {
        this.announcements = announcements;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ActiveAnnouncements listActive() {
        long now = clock.millis();
        List<ActiveView> views = announcements
                .findByStartsAtMsLessThanEqualAndEndsAtMsGreaterThanOrderByEndsAtMsAscIdAsc(now, now).stream()
                .map(a -> new ActiveView(a.getId(), a.getTitle(), a.getStartsAtMs(), a.getEndsAtMs(), a.isCountdown()))
                .toList();
        return new ActiveAnnouncements(now, views);
    }

    @Transactional(readOnly = true)
    public List<AdminView> listAll() {
        long now = clock.millis();
        return announcements.findAllByOrderByEndsAtMsAscIdAsc().stream().map(a -> toAdminView(a, now)).toList();
    }

    @Transactional
    public AdminView create(String title, LocalDateTime startsAt, LocalDateTime endsAt, boolean countdown) {
        Announcement announcement = validated(null, title, startsAt, endsAt, countdown);
        return toAdminView(announcements.save(announcement), clock.millis());
    }

    @Transactional
    public AdminView update(long id, String title, LocalDateTime startsAt, LocalDateTime endsAt, boolean countdown) {
        Announcement existing = announcements.findById(id).orElseThrow(AnnouncementService::notFound);
        validated(existing, title, startsAt, endsAt, countdown);
        return toAdminView(announcements.save(existing), clock.millis());
    }

    @Transactional
    public void delete(long id) {
        if (!announcements.existsById(id)) {
            throw notFound();
        }
        announcements.deleteById(id);
    }

    /** Interprete une saisie en heure de Paris ; Java decale les heures inexistantes du passage a l'heure d'ete. */
    public static long toEpochMs(LocalDateTime parisTime) {
        return parisTime.atZone(PARIS).toInstant().toEpochMilli();
    }

    public static LocalDateTime toParisTime(long epochMs) {
        return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMs), PARIS);
    }

    static Status statusAt(Announcement announcement, long now) {
        if (now < announcement.getStartsAtMs()) {
            return Status.UPCOMING;
        }
        return now < announcement.getEndsAtMs() ? Status.ACTIVE : Status.EXPIRED;
    }

    private static Announcement validated(Announcement target, String title, LocalDateTime startsAt,
                                          LocalDateTime endsAt, boolean countdown) {
        String cleanTitle = title == null ? "" : title.strip();
        if (cleanTitle.isEmpty() || cleanTitle.length() > MAX_TITLE_LENGTH) {
            throw invalid("Le titre est obligatoire (" + MAX_TITLE_LENGTH + " caractères maximum).");
        }
        if (startsAt == null || endsAt == null) {
            throw invalid("Les dates de début et de fin sont obligatoires.");
        }
        long start = toEpochMs(startsAt);
        long end = toEpochMs(endsAt);
        if (end <= start) {
            throw invalid("La date de fin doit être postérieure à la date de début.");
        }
        if (target == null) {
            return new Announcement(cleanTitle, start, end, countdown);
        }
        target.update(cleanTitle, start, end, countdown);
        return target;
    }

    private AdminView toAdminView(Announcement a, long now) {
        return new AdminView(a.getId(), a.getTitle(), toParisTime(a.getStartsAtMs()), toParisTime(a.getEndsAtMs()),
                a.isCountdown(), statusAt(a, now));
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Annonce introuvable.");
    }

    public enum Status {
        UPCOMING("À venir"), ACTIVE("En cours"), EXPIRED("Expirée");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public record ActiveView(Long id, String title, long startsAt, long endsAt, boolean countdown) {
    }

    public record ActiveAnnouncements(long serverNow, List<ActiveView> announcements) {
    }

    public record AdminView(Long id, String title, LocalDateTime startsAt, LocalDateTime endsAt, boolean countdown,
                            Status status) {
    }
}
