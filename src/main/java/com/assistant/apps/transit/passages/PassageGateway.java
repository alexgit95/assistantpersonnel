package com.assistant.apps.transit.passages;

public interface PassageGateway {

    /** Passages prévus à l'arrêt {@code arrid} ; lève {@link PassageUnavailableException} en cas d'échec. */
    PassageFeed fetch(String arrid);
}
