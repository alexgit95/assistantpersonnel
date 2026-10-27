package com.assistant.announcements;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "announcement", indexes = @Index(name = "idx_announcement_ends", columnList = "ends_at_ms"))
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = AnnouncementService.MAX_TITLE_LENGTH)
    private String title;

    @Column(name = "starts_at_ms", nullable = false)
    private long startsAtMs;

    @Column(name = "ends_at_ms", nullable = false)
    private long endsAtMs;

    @Column(nullable = false)
    private boolean countdown;

    protected Announcement() {
    }

    public Announcement(String title, long startsAtMs, long endsAtMs, boolean countdown) {
        update(title, startsAtMs, endsAtMs, countdown);
    }

    public void update(String title, long startsAtMs, long endsAtMs, boolean countdown) {
        this.title = title;
        this.startsAtMs = startsAtMs;
        this.endsAtMs = endsAtMs;
        this.countdown = countdown;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public long getStartsAtMs() {
        return startsAtMs;
    }

    public long getEndsAtMs() {
        return endsAtMs;
    }

    public boolean isCountdown() {
        return countdown;
    }
}
