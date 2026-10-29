package com.assistant.apps;

final class WeatherCode {

    private WeatherCode() {
    }

    static String description(int code) {
        return switch (code) {
            case 0 -> "Ciel dégagé";
            case 1 -> "Principalement dégagé";
            case 2 -> "Partiellement nuageux";
            case 3 -> "Couvert";
            case 45, 48 -> "Brouillard";
            case 51, 53, 55 -> "Bruine";
            case 56, 57 -> "Bruine verglaçante";
            case 61, 63, 65 -> "Pluie";
            case 66, 67 -> "Pluie verglaçante";
            case 71, 73, 75, 77 -> "Neige";
            case 80, 81, 82 -> "Averses";
            case 85, 86 -> "Averses de neige";
            case 95, 96, 99 -> "Orage";
            default -> "Conditions inconnues";
        };
    }

    /** Rang de severite : orage > neige > pluie > bruine > brouillard > couvert > peu nuageux > degage. */
    static int severity(int code) {
        return switch (code) {
            case 95, 96, 99 -> 8;
            case 71, 73, 75, 77, 85, 86 -> 7;
            case 61, 63, 65, 66, 67, 80, 81, 82 -> 6;
            case 51, 53, 55, 56, 57 -> 5;
            case 45, 48 -> 4;
            case 3 -> 3;
            case 2 -> 2;
            case 0, 1 -> 1;
            default -> 0;
        };
    }

    static boolean isStorm(int code) {
        return code == 95 || code == 96 || code == 99;
    }

    static boolean isSnow(int code) {
        return code == 71 || code == 73 || code == 75 || code == 77 || code == 85 || code == 86;
    }
}