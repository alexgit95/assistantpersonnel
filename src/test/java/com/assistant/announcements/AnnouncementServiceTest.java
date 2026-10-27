package com.assistant.announcements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assistant.common.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class AnnouncementServiceTest {

    static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");

    @Mock
    AnnouncementRepository repository;

    AnnouncementService service;

    @BeforeEach
    void setUp() {
        service = new AnnouncementService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void listActiveUsesServerClockAndReturnsItInTheResponse() {
        long now = NOW.toEpochMilli();
        when(repository.findByStartsAtMsLessThanEqualAndEndsAtMsGreaterThanOrderByEndsAtMsAscIdAsc(now, now))
                .thenReturn(List.of(new Announcement("Bientôt", now - 1, now + 5000, true)));

        var result = service.listActive();

        assertThat(result.serverNow()).isEqualTo(now);
        assertThat(result.announcements()).extracting(AnnouncementService.ActiveView::title)
                .containsExactly("Bientôt");
        assertThat(result.announcements().get(0).startsAt()).isEqualTo(now - 1);
        assertThat(result.announcements().get(0).endsAt()).isEqualTo(now + 5000);
        assertThat(result.announcements().get(0).countdown()).isTrue();
    }

    @Test
    void statusIsUpcomingActiveOrExpiredAndEndIsExclusive() {
        long now = NOW.toEpochMilli();

        assertThat(AnnouncementService.statusAt(new Announcement("a", now + 1, now + 9, false), now))
                .isEqualTo(AnnouncementService.Status.UPCOMING);
        assertThat(AnnouncementService.statusAt(new Announcement("a", now, now + 9, false), now))
                .isEqualTo(AnnouncementService.Status.ACTIVE);
        assertThat(AnnouncementService.statusAt(new Announcement("a", now - 9, now, false), now))
                .isEqualTo(AnnouncementService.Status.EXPIRED);
    }

    @Test
    void datesAreInterpretedInParisTimeSummerAndWinter() {
        assertThat(AnnouncementService.toEpochMs(LocalDateTime.of(2026, 7, 1, 18, 0)))
                .isEqualTo(Instant.parse("2026-07-01T16:00:00Z").toEpochMilli());
        assertThat(AnnouncementService.toEpochMs(LocalDateTime.of(2026, 1, 1, 18, 0)))
                .isEqualTo(Instant.parse("2026-01-01T17:00:00Z").toEpochMilli());
        assertThat(AnnouncementService.toParisTime(Instant.parse("2026-07-01T16:00:00Z").toEpochMilli()))
                .isEqualTo(LocalDateTime.of(2026, 7, 1, 18, 0));
    }

    @Test
    void nonExistentSpringForwardHourIsResolvedByShiftingForward() {
        // 2026-03-29 02:30 n'existe pas a Paris : decale a 03:30 (UTC+2).
        assertThat(AnnouncementService.toEpochMs(LocalDateTime.of(2026, 3, 29, 2, 30)))
                .isEqualTo(Instant.parse("2026-03-29T01:30:00Z").toEpochMilli());
    }

    @Test
    void createTrimsTitleAndStoresUtcMilliseconds() {
        when(repository.save(any(Announcement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var view = service.create("  Réunion  ", LocalDateTime.of(2026, 11, 1, 9, 0),
                LocalDateTime.of(2026, 11, 2, 9, 0), true);

        assertThat(view.title()).isEqualTo("Réunion");
        assertThat(view.startsAt()).isEqualTo(LocalDateTime.of(2026, 11, 1, 9, 0));
        assertThat(view.countdown()).isTrue();
    }

    @Test
    void rejectsBlankTitleTooLongTitleMissingDatesAndInvertedRange() {
        var start = LocalDateTime.of(2026, 11, 1, 9, 0);
        var end = LocalDateTime.of(2026, 11, 2, 9, 0);

        assertBadRequest(() -> service.create("   ", start, end, false));
        assertBadRequest(() -> service.create("x".repeat(201), start, end, false));
        assertBadRequest(() -> service.create("Titre", null, end, false));
        assertBadRequest(() -> service.create("Titre", start, start, false));
        assertBadRequest(() -> service.create("Titre", end, start, false));
        verify(repository, never()).save(any());
    }

    @Test
    void updateAndDeleteReportUnknownAnnouncementsAsNotFound() {
        when(repository.findById(9L)).thenReturn(Optional.empty());
        when(repository.existsById(9L)).thenReturn(false);

        assertThatThrownBy(() -> service.update(9L, "T", LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 1, 2, 0, 0), false))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.delete(9L))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updateModifiesTheExistingAnnouncement() {
        var existing = new Announcement("Ancien", 1L, 2L, false);
        when(repository.findById(3L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        var view = service.update(3L, "Nouveau", LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 1, 2, 0, 0), true);

        assertThat(view.title()).isEqualTo("Nouveau");
        assertThat(existing.isCountdown()).isTrue();
    }

    private static void assertBadRequest(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
