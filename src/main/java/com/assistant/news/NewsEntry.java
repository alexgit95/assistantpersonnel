package com.assistant.news;

/** Entree d'un flux : titre et description deja convertis en texte brut. */
public record NewsEntry(String title, String description) {
}
