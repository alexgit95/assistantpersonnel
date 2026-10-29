package com.assistant.news;

import com.assistant.integrations.ExternalApiModeService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** Cache memoire par categorie : un seul appel sortant par categorie et par periode, quel que soit le nombre de profils. */
@Service
public class NewsFeedService {

    private static final Logger LOG = LoggerFactory.getLogger(NewsFeedService.class);
    static final Duration TTL = Duration.ofMinutes(10);
    static final Duration RETRY_DELAY = Duration.ofMinutes(1);
    static final String STALE_NOTICE = "Actualisation indisponible; affichage des dernières données reçues.";
    static final String UNAVAILABLE_NOTICE = "Ce flux d'actualités est momentanément indisponible.";

    private final NewsGateway liveGateway;
    private final NewsGateway testGateway;
    private final ExternalApiModeService mode;
    private final Clock clock;
    private final Map<Long, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Map<Long, Object> locks = new ConcurrentHashMap<>();

    public NewsFeedService(@Qualifier("rssClient") NewsGateway liveGateway,
                           @Qualifier("mockNewsGateway") NewsGateway testGateway,
                           ExternalApiModeService mode, Clock clock) {
        this.liveGateway = liveGateway;
        this.testGateway = testGateway;
        this.mode = mode;
        this.clock = clock;
    }

    public FeedResult load(NewsCategory category) {
        if (mode.isTestMode()) {
            return new FeedResult(testGateway.fetch(category), null);
        }
        long id = category.getId();
        Instant now = clock.instant();
        CacheEntry current = cache.get(id);
        if (isFresh(current, category, now)) {
            return current.result();
        }
        synchronized (locks.computeIfAbsent(id, key -> new Object())) {
            now = clock.instant();
            current = cache.get(id);
            if (isFresh(current, category, now)) {
                return current.result();
            }
            CacheEntry next;
            try {
                next = new CacheEntry(category.getFeedUrl(), now, new FeedResult(liveGateway.fetch(category), null));
            } catch (NewsUnavailableException exception) {
                LOG.warn("Flux d'actualités indisponible (categorie {}): {}", id, exception.getMessage());
                next = failed(current, category, now);
            }
            cache.put(id, next);
            return next.result();
        }
    }

    public void invalidate(long categoryId) {
        cache.remove(categoryId);
        locks.remove(categoryId);
    }

    private boolean isFresh(CacheEntry entry, NewsCategory category, Instant now) {
        return entry != null && entry.feedUrl().equals(category.getFeedUrl())
                && Duration.between(entry.fetchedAt(), now).compareTo(TTL) < 0;
    }

    private CacheEntry failed(CacheEntry previous, NewsCategory category, Instant now) {
        boolean hasStale = previous != null && previous.feedUrl().equals(category.getFeedUrl())
                && !previous.result().entries().isEmpty();
        List<NewsEntry> stale = hasStale ? previous.result().entries() : List.of();
        // Date antidatee pour ne retenter qu'apres RETRY_DELAY au lieu d'un cycle complet.
        Instant retryAt = now.minus(TTL).plus(RETRY_DELAY);
        return new CacheEntry(category.getFeedUrl(), retryAt,
                new FeedResult(stale, hasStale ? STALE_NOTICE : UNAVAILABLE_NOTICE));
    }

    public record FeedResult(List<NewsEntry> entries, String notice) {
    }

    private record CacheEntry(String feedUrl, Instant fetchedAt, FeedResult result) {
    }
}
