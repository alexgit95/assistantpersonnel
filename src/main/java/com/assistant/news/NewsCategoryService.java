package com.assistant.news;

import com.assistant.common.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NewsCategoryService {

    public static final int MAX_NAME_LENGTH = 100;
    public static final int MAX_URL_LENGTH = 500;
    static final int MAX_CATEGORIES = 50;
    static final int MAX_POSITION = 10_000;

    private final NewsCategoryRepository categories;
    private final FeedUrlPolicy urlPolicy;
    private final NewsFeedService feeds;

    public NewsCategoryService(NewsCategoryRepository categories, FeedUrlPolicy urlPolicy, NewsFeedService feeds) {
        this.categories = categories;
        this.urlPolicy = urlPolicy;
        this.feeds = feeds;
    }

    @Transactional(readOnly = true)
    public List<NewsCategory> listAll() {
        return categories.findAllByOrderByPositionAscIdAsc();
    }

    @Transactional
    public NewsCategory create(String name, String feedUrl, Integer position) {
        String cleanName = cleanName(name);
        String cleanUrl = cleanUrl(feedUrl);
        if (categories.count() >= MAX_CATEGORIES) {
            throw invalid("Au maximum " + MAX_CATEGORIES + " catégories peuvent être créées.");
        }
        int cleanPosition = position == null ? nextPosition() : cleanPosition(position);
        return categories.save(new NewsCategory(cleanName, cleanUrl, cleanPosition));
    }

    @Transactional
    public NewsCategory update(long id, String name, String feedUrl, Integer position) {
        NewsCategory existing = categories.findById(id).orElseThrow(NewsCategoryService::notFound);
        String cleanName = cleanName(name);
        String cleanUrl = cleanUrl(feedUrl);
        int cleanPosition = position == null ? existing.getPosition() : cleanPosition(position);
        existing.update(cleanName, cleanUrl, cleanPosition);
        NewsCategory saved = categories.save(existing);
        feeds.invalidate(id);
        return saved;
    }

    @Transactional
    public void delete(long id) {
        if (!categories.existsById(id)) {
            throw notFound();
        }
        categories.deleteById(id);
        feeds.invalidate(id);
    }

    private int nextPosition() {
        return categories.findAllByOrderByPositionAscIdAsc().stream()
                .mapToInt(NewsCategory::getPosition).max().orElse(-1) + 1;
    }

    private String cleanName(String name) {
        String value = name == null ? "" : name.strip();
        if (value.isEmpty() || value.length() > MAX_NAME_LENGTH) {
            throw invalid("Le nom de la catégorie est obligatoire (" + MAX_NAME_LENGTH + " caractères maximum).");
        }
        return value;
    }

    private String cleanUrl(String feedUrl) {
        return urlPolicy.validateForSave(feedUrl).toString();
    }

    private static int cleanPosition(int position) {
        if (position < 0 || position > MAX_POSITION) {
            throw invalid("La position doit être comprise entre 0 et " + MAX_POSITION + ".");
        }
        return position;
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Catégorie introuvable.");
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
