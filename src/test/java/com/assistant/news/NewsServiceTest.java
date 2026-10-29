package com.assistant.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;

import com.assistant.apps.ProfileAppService;
import com.assistant.common.ApiException;
import com.assistant.integrations.ExternalApiModeService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class NewsServiceTest {

    @Mock
    NewsCategoryService categories;
    @Mock
    NewsFeedService feeds;
    @Mock
    ProfileAppService profileApps;
    @Mock
    ExternalApiModeService mode;

    final ObjectMapper objectMapper = new ObjectMapper();
    NewsService service;
    NewsCategory tech;
    NewsCategory sport;
    NewsCategory culture;

    @BeforeEach
    void setUp() {
        service = new NewsService(categories, feeds, profileApps, objectMapper, mode);
        tech = category(1L, "Tech", 0);
        sport = category(2L, "Sport", 1);
        culture = category(3L, "Culture", 2);
    }

    private static NewsCategory category(long id, String name, int position) {
        NewsCategory category = new NewsCategory(name, "https://example.org/" + id, position);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    private static ProfileAppService.AppPreference preference(String configJson) {
        return new ProfileAppService.AppPreference("news", "Actualités", "d", "f", "/js/widgets/news.js", true, 0,
                configJson);
    }

    @Test
    void listsCategoriesInDisplayOrder() {
        when(categories.listAll()).thenReturn(List.of(tech, sport));

        assertThat(service.listCategories()).containsExactly(
                new NewsService.CategoryInfo(1L, "Tech"), new NewsService.CategoryInfo(2L, "Sport"));
    }

    @Test
    void readsTheSelectionFromTheProfileConfiguration() {
        when(profileApps.listForProfile(4L)).thenReturn(List.of(preference("{\"categoryIds\":[3,1,3,\"x\"]}")));

        assertThat(service.selection(4L)).containsExactly(3L, 1L);
    }

    @Test
    void anInvalidConfigurationMeansNoSelection() {
        when(profileApps.listForProfile(4L)).thenReturn(List.of(preference("pas du json")));

        assertThat(service.selection(4L)).isEmpty();
    }

    @Test
    void savesTheSelectionKeepingOtherConfigurationKeys() {
        when(categories.listAll()).thenReturn(List.of(tech, sport, culture));
        when(profileApps.listForProfile(4L)).thenReturn(List.of(preference("{\"other\":true}")));

        List<Long> saved = service.saveSelection(4L, List.of(2L, 1L, 2L));

        assertThat(saved).containsExactly(2L, 1L);
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(profileApps).updateConfiguration(eq(4L), eq("news"), json.capture());
        assertThat(objectMapper.readTree(json.getValue()).path("categoryIds").toString()).isEqualTo("[2,1]");
        assertThat(objectMapper.readTree(json.getValue()).path("other").asBoolean()).isTrue();
    }

    @Test
    void refusesAnUnknownCategoryOrAnOversizedSelection() {
        when(categories.listAll()).thenReturn(List.of(tech));

        assertThatThrownBy(() -> service.saveSelection(4L, List.of(1L, 99L))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.saveSelection(4L, null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.saveSelection(4L,
                java.util.Collections.nCopies(NewsService.MAX_SELECTION + 1, 1L))).isInstanceOf(ApiException.class);
        verify(profileApps, never()).updateConfiguration(anyLong(), anyString(), anyString());
    }

    @Test
    void overviewReturnsOnlySelectedCategoriesInDisplayOrderAndIgnoresDeletedOnes() {
        when(profileApps.requireEnabled(4L, "news")).thenReturn(new ProfileAppService.EnabledApp(
                new NewsAssistantApp(), 0, "{\"categoryIds\":[3,1,42]}"));
        when(categories.listAll()).thenReturn(List.of(tech, sport, culture));
        when(feeds.load(tech)).thenReturn(new NewsFeedService.FeedResult(List.of(new NewsEntry("T", "D")), null));
        when(feeds.load(culture)).thenReturn(new NewsFeedService.FeedResult(List.of(), "notice"));
        when(mode.isTestMode()).thenReturn(true);

        NewsService.Overview overview = service.overview(4L);

        assertThat(overview.testMode()).isTrue();
        assertThat(overview.categories()).extracting(NewsService.CategoryContent::name)
                .containsExactly("Tech", "Culture");
        assertThat(overview.categories().get(1).notice()).isEqualTo("notice");
        verify(feeds, never()).load(sport);
    }

    @Test
    void overviewWithoutSelectionIsEmpty() {
        when(profileApps.requireEnabled(4L, "news")).thenReturn(new ProfileAppService.EnabledApp(
                new NewsAssistantApp(), 0, "{}"));
        when(categories.listAll()).thenReturn(List.of(tech));

        assertThat(service.overview(4L).categories()).isEmpty();
    }
}
