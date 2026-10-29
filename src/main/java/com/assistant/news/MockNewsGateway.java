package com.assistant.news;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Donnees factices du mode test : aucun appel reseau. */
@Component
public class MockNewsGateway implements NewsGateway {

    @Override
    public List<NewsEntry> fetch(NewsCategory category) {
        List<NewsEntry> entries = new ArrayList<>();
        for (int index = 1; index <= 4; index++) {
            entries.add(new NewsEntry(category.getName() + " : actualité de test " + index,
                    "Description simulée de l'actualité " + index + " de la catégorie " + category.getName()
                            + ". Aucun flux réel n'est interrogé en mode test."));
        }
        return List.copyOf(entries);
    }
}
