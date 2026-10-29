package com.assistant.news;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "news_category")
public class NewsCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = NewsCategoryService.MAX_NAME_LENGTH)
    private String name;

    @Column(name = "feed_url", nullable = false, length = NewsCategoryService.MAX_URL_LENGTH)
    private String feedUrl;

    @Column(nullable = false)
    private int position;

    protected NewsCategory() {
    }

    public NewsCategory(String name, String feedUrl, int position) {
        update(name, feedUrl, position);
    }

    public void update(String name, String feedUrl, int position) {
        this.name = name;
        this.feedUrl = feedUrl;
        this.position = position;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getFeedUrl() {
        return feedUrl;
    }

    public int getPosition() {
        return position;
    }
}
