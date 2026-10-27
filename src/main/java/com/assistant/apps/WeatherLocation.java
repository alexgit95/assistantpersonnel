package com.assistant.apps;

public record WeatherLocation(long id, String name, String region, String country,
                              double latitude, double longitude) {

    public String label() {
        if (region == null || region.isBlank()) {
            return name + ", " + country;
        }
        return name + ", " + region + ", " + country;
    }
}