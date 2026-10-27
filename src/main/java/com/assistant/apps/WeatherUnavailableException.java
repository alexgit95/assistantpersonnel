package com.assistant.apps;

public class WeatherUnavailableException extends RuntimeException {

    public WeatherUnavailableException(Throwable cause) {
        super(cause);
    }
}