package com.assistant.apps.transit.passages;

public class PassageUnavailableException extends RuntimeException {

    private final boolean unknownStop;

    public PassageUnavailableException(boolean unknownStop, Throwable cause) {
        super(unknownStop ? "Arrêt inconnu de PRIM." : "Le service des prochains passages est indisponible.", cause);
        this.unknownStop = unknownStop;
    }

    /** Vrai quand PRIM rejette l'identifiant d'arrêt (HTTP 400). */
    public boolean unknownStop() {
        return unknownStop;
    }
}
