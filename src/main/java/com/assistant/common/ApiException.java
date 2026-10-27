package com.assistant.common;

import org.springframework.http.HttpStatus;

/** Erreur metier convertie en reponse HTTP (message destine a l'utilisateur, en francais). */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
