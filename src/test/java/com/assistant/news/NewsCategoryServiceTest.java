package com.assistant.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assistant.common.ApiException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class NewsCategoryServiceTest {

    @Mock
    NewsCategoryRepository repository;
    @Mock
    NewsFeedService feeds;

    NewsCategoryService service;

    @BeforeEach
    void setUp() {
        service = new NewsCategoryService(repository, new FeedUrlPolicy(), feeds);
    }

    @Test
    void createsACategoryAtTheNextPositionWithTrimmedValues() {
        when(repository.count()).thenReturn(2L);
        when(repository.findAllByOrderByPositionAscIdAsc()).thenReturn(List.of(
                new NewsCategory("A", "https://a.org/f", 0), new NewsCategory("B", "https://b.org/f", 4)));
        when(repository.save(any(NewsCategory.class))).thenAnswer(call -> call.getArgument(0));

        NewsCategory saved = service.create("  Tech  ", " https://example.org/rss ", null);

        assertThat(saved.getName()).isEqualTo("Tech");
        assertThat(saved.getFeedUrl()).isEqualTo("https://example.org/rss");
        assertThat(saved.getPosition()).isEqualTo(5);
    }

    @Test
    void usesTheRequestedPosition() {
        when(repository.count()).thenReturn(0L);
        when(repository.save(any(NewsCategory.class))).thenAnswer(call -> call.getArgument(0));

        assertThat(service.create("Tech", "https://example.org/rss", 3).getPosition()).isEqualTo(3);
    }

    @Test
    void rejectsInvalidInputWithoutSaving() {
        assertThatThrownBy(() -> service.create(" ", "https://example.org/rss", null))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.create("x".repeat(101), "https://example.org/rss", null))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.create("Tech", "http://example.org/rss", null))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.create("Tech", "https://127.0.0.1/rss", null))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.create("Tech", "https://example.org/rss", -1))
                .isInstanceOf(ApiException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void refusesMoreThanFiftyCategories() {
        when(repository.count()).thenReturn(50L);

        assertThatThrownBy(() -> service.create("Tech", "https://example.org/rss", null))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void updateChangesTheCategoryAndInvalidatesItsCache() {
        NewsCategory existing = new NewsCategory("Old", "https://old.org/f", 1);
        when(repository.findById(5L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        service.update(5L, "New", "https://new.org/f", null);

        assertThat(existing.getName()).isEqualTo("New");
        assertThat(existing.getFeedUrl()).isEqualTo("https://new.org/f");
        assertThat(existing.getPosition()).isEqualTo(1);
        verify(feeds).invalidate(5L);
    }

    @Test
    void updateAndDeleteReportAMissingCategoryAsNotFound() {
        when(repository.findById(9L)).thenReturn(Optional.empty());
        when(repository.existsById(9L)).thenReturn(false);

        assertThatThrownBy(() -> service.update(9L, "N", "https://n.org/f", 1))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.delete(9L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void deleteRemovesTheCategoryAndItsCache() {
        when(repository.existsById(5L)).thenReturn(true);

        service.delete(5L);

        verify(repository).deleteById(5L);
        verify(feeds).invalidate(5L);
    }
}
