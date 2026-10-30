package com.assistant.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Jetons aleatoires de 256 bits, condensat SHA-256 et comparaison en temps constant. */
public final class TokenSupport {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenSupport() {
    }

    public static String newToken() {
        return randomUrlSafe(32);
    }

    /** Identifiant opaque court (non secret) pour referencer un jeton sans le reveler. */
    public static String newId() {
        return randomUrlSafe(12);
    }

    public static String hash(String token) {
        return HexFormat.of().formatHex(digest(token));
    }

    /** Comparaison en temps constant ; faux si l'une des valeurs est absente. */
    public static boolean matches(String given, String expected) {
        if (given == null || expected == null) {
            return false;
        }
        return MessageDigest.isEqual(digest(given), digest(expected));
    }

    private static String randomUrlSafe(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
