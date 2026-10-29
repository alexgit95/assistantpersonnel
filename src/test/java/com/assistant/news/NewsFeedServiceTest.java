package com.assistant.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assistant.integrations.ExternalApiModeService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NewsFeedServiceTest {

    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-29T10:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Mock
    NewsGateway live;
    @Mock
    NewsGateway test;
    @Mock
    ExternalApiModeService mode;

    final MutableClock clock = new MutableClock();
    NewsFeedService service;
    NewsCategory category;
    final List<NewsEntry> entries = List.of(new NewsEntry("Titre", "Desc"));

    @BeforeEach
    void setUp() {
        service = new NewsFeedService(live, test, mode, clock);
        category = new NewsCategory("Tech", "https://example.org/feed", 0);
        ReflectionTestUtils.setField(category, "id", 7L);
    }

    @Test
    void cachesAFeedForTenMinutesAcrossCallers() {
        when(live.fetch(category)).thenReturn(entries);

        assertThat(service.load(category).entries()).isEqualTo(entries);
        clock.now = clock.now.plus(Duration.ofMinutes(9));
        assertThat(service.load(category).entries()).isEqualTo(entries);

        verify(live, times(1)).fetch(category);
    }

    @Test
    void refetchesOnceTheTtlHasElapsed() {
        when(live.fetch(category)).thenReturn(entries);
        service.load(category);
        clock.now = clock.now.plus(Duration.ofMinutes(10));

        service.load(category);

        verify(live, times(2)).fetch(category);
    }

    @Test
    void servesTheLastKnownEntriesWithANoticeWhenRefreshFails() {
        when(live.fetch(category)).thenReturn(entries).thenThrow(new NewsUnavailableException("boom"));
        service.load(category);
        clock.now = clock.now.plus(Duration.ofMinutes(10));

        NewsFeedService.FeedResult result = service.load(category);

        assertThat(result.entries()).isEqualTo(entries);
        assertThat(result.notice()).isEqualTo(NewsFeedService.STALE_NOTICE);
    }

    @Test
    void returnsOnlyANoticeWhenThereIsNothingCached() {
        when(live.fetch(category)).thenThrow(new NewsUnavailableException("boom"));

        NewsFeedService.FeedResult result = service.load(category);

        assertThat(result.entries()).isEmpty();
        assertThat(result.notice()).isEqualTo(NewsFeedService.UNAVAILABLE_NOTICE);
    }

    @Test
    void retriesAFailedFeedAfterOneMinuteOnly() {
        when(live.fetch(category)).thenThrow(new NewsUnavailableException("boom")).thenReturn(entries);
        service.load(category);
        clock.now = clock.now.plus(Duration.ofSeconds(30));
        service.load(category);
        verify(live, times(1)).fetch(category);

        clock.now = clock.now.plus(NewsFeedService.RETRY_DELAY);
        NewsFeedService.FeedResult result = service.load(category);

        assertThat(result.entries()).isEqualTo(entries);
        assertThat(result.notice()).isNull();
    }

    @Test
    void usesFakeDataWithoutAnyOutboundCallInTestMode() {
        when(mode.isTestMode()).thenReturn(true);
        when(test.fetch(category)).thenReturn(entries);

        assertThat(service.load(category).entries()).isEqualTo(entries);

        verify(live, never()).fetch(any());
    }

    @Test
    void invalidationAndUrlChangesForceANewFetch() {
        when(live.fetch(any())).thenReturn(entries);
        service.load(category);

        service.invalidate(7L);
        service.load(category);
        verify(live, times(2)).fetch(category);

        category.update("Tech", "https://example.org/other", 0);
        service.load(category);
        verify(live, times(3)).fetch(category);
    }
}
