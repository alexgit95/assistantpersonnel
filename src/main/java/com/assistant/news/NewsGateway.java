package com.assistant.news;

import java.util.List;

public interface NewsGateway {

    /** Retourne au plus {@link RssClient#MAX_ENTRIES} entrees ou leve {@link NewsUnavailableException}. */
    List<NewsEntry> fetch(NewsCategory category);
}
