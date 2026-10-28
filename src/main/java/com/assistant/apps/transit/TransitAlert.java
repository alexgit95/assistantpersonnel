package com.assistant.apps.transit;

public record TransitAlert(String id, String title, String shortMessage, String message,
						   String severity, String cause) {
}
