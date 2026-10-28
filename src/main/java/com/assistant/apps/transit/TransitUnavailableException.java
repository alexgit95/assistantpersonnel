package com.assistant.apps.transit;

public class TransitUnavailableException extends RuntimeException {

    public TransitUnavailableException(Throwable cause) {
        super("Le service d'information trafic est indisponible.", cause);
    }
}
