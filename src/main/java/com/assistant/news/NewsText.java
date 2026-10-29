package com.assistant.news;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conversion de contenu de flux (souvent HTML) en texte brut, sans jamais interpreter de balise. */
final class NewsText {

    private static final Pattern BLOCKS = Pattern.compile("<(script|style)\\b.*?</\\1\\s*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern BREAKS = Pattern.compile("<br\\s*/?>|</(p|li|div|h[1-6])\\s*>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TAGS = Pattern.compile("<[^>]*>");
    private static final Pattern ENTITY = Pattern.compile("&(#x[0-9a-fA-F]{1,6}|#[0-9]{1,7}|[a-zA-Z]{2,8});");
    private static final Pattern INLINE_SPACES = Pattern.compile("[\\t\\f\\x0B\\u00A0 ]+");
    private static final Pattern BLANK_LINES = Pattern.compile("\\s*\\n\\s*");

    private NewsText() {
    }

    static String plain(String markup, int maxLength) {
        if (markup == null || markup.isBlank()) {
            return "";
        }
        String text = BLOCKS.matcher(markup).replaceAll(" ");
        text = BREAKS.matcher(text).replaceAll("\n");
        text = TAGS.matcher(text).replaceAll("");
        text = decodeEntities(text);
        text = INLINE_SPACES.matcher(text).replaceAll(" ");
        text = BLANK_LINES.matcher(text).replaceAll("\n").strip();
        if (text.length() > maxLength) {
            text = text.substring(0, maxLength).stripTrailing() + "…";
        }
        return text;
    }

    private static String decodeEntities(String text) {
        Matcher matcher = ENTITY.matcher(text);
        StringBuilder result = new StringBuilder(text.length());
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(decode(matcher.group(1), matcher.group())));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String decode(String name, String original) {
        if (name.startsWith("#")) {
            try {
                int codePoint = name.startsWith("#x") || name.startsWith("#X")
                        ? Integer.parseInt(name.substring(2), 16) : Integer.parseInt(name.substring(1));
                return Character.isValidCodePoint(codePoint) ? new String(Character.toChars(codePoint)) : original;
            } catch (NumberFormatException exception) {
                return original;
            }
        }
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "amp" -> "&";
            case "lt" -> "<";
            case "gt" -> ">";
            case "quot" -> "\"";
            case "apos" -> "'";
            case "nbsp" -> " ";
            case "hellip" -> "…";
            case "rsquo", "lsquo" -> "'";
            case "ldquo", "rdquo" -> "\"";
            case "laquo" -> "«";
            case "raquo" -> "»";
            case "ndash" -> "–";
            case "mdash" -> "—";
            default -> original;
        };
    }
}
