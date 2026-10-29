package com.assistant.news;

import com.assistant.apps.ProfileAppService;
import com.assistant.common.ApiException;
import com.assistant.integrations.ExternalApiModeService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class NewsService {

    static final String APP_KEY = "news";
    static final String CONFIG_KEY = "categoryIds";
    static final int MAX_SELECTION = 50;

    private final NewsCategoryService categories;
    private final NewsFeedService feeds;
    private final ProfileAppService profileApps;
    private final ObjectMapper objectMapper;
    private final ExternalApiModeService mode;

    public NewsService(NewsCategoryService categories, NewsFeedService feeds, ProfileAppService profileApps,
                       ObjectMapper objectMapper, ExternalApiModeService mode) {
        this.categories = categories;
        this.feeds = feeds;
        this.profileApps = profileApps;
        this.objectMapper = objectMapper;
        this.mode = mode;
    }

    public List<CategoryInfo> listCategories() {
        return categories.listAll().stream().map(c -> new CategoryInfo(c.getId(), c.getName())).toList();
    }

    public List<Long> selection(long profileId) {
        ProfileAppService.AppPreference preference = profileApps.listForProfile(profileId).stream()
                .filter(app -> APP_KEY.equals(app.key())).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Application Actualités introuvable."));
        return readIds(preference.configJson());
    }

    public List<Long> saveSelection(long profileId, List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.size() > MAX_SELECTION) {
            throw invalid("Sélection de catégories invalide.");
        }
        Set<Long> known = categories.listAll().stream().map(NewsCategory::getId).collect(Collectors.toSet());
        Set<Long> unique = new LinkedHashSet<>();
        for (Long id : categoryIds) {
            if (id == null || !known.contains(id)) {
                throw invalid("Une catégorie sélectionnée est inconnue.");
            }
            unique.add(id);
        }
        ProfileAppService.AppPreference preference = profileApps.listForProfile(profileId).stream()
                .filter(app -> APP_KEY.equals(app.key())).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Application Actualités introuvable."));
        try {
            ObjectNode config = objectMapper.createObjectNode();
            if (objectMapper.readTree(preference.configJson()) instanceof ObjectNode object) {
                config = object.deepCopy();
            }
            config.set(CONFIG_KEY, objectMapper.valueToTree(List.copyOf(unique)));
            profileApps.updateConfiguration(profileId, APP_KEY, objectMapper.writeValueAsString(config));
        } catch (JacksonException exception) {
            throw invalid("La configuration du widget Actualités est invalide.");
        }
        return List.copyOf(unique);
    }

    public Overview overview(long profileId) {
        ProfileAppService.EnabledApp app = profileApps.requireEnabled(profileId, APP_KEY);
        Set<Long> selected = new LinkedHashSet<>(readIds(app.configJson()));
        List<CategoryContent> contents = new ArrayList<>();
        for (NewsCategory category : categories.listAll()) {
            if (selected.contains(category.getId())) {
                NewsFeedService.FeedResult result = feeds.load(category);
                contents.add(new CategoryContent(category.getId(), category.getName(), result.entries(),
                        result.notice()));
            }
        }
        return new Overview(mode.isTestMode(), contents);
    }

    private List<Long> readIds(String configJson) {
        List<Long> ids = new ArrayList<>();
        try {
            JsonNode node = objectMapper.readTree(configJson).path(CONFIG_KEY);
            if (node.isArray()) {
                for (JsonNode item : node) {
                    if (item.isIntegralNumber() && !ids.contains(item.longValue())) {
                        ids.add(item.longValue());
                    }
                }
            }
        } catch (JacksonException exception) {
            return List.of();
        }
        return List.copyOf(ids);
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    public record CategoryInfo(long id, String name) {
    }

    public record CategoryContent(long id, String name, List<NewsEntry> entries, String notice) {
    }

    public record Overview(boolean testMode, List<CategoryContent> categories) {
    }
}
