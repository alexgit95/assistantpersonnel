package com.assistant.apps.transit;

public record TransitLine(String id, String name, String shortName, String transportMode,
                          String networkName, String lineColor, String textColor) {

    public String label() {
        if (networkName != null && !networkName.isBlank()) {
            return networkName + " " + shortName;
        }
        return name;
    }

    public String getLabel() {
        return label();
    }
}
