package packettracer.exapp.lib.introspect;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * JSON escaping and small string helpers for introspection payloads.
 */
public final class IntrospectJson {
    private IntrospectJson() {
    }

    public static String jsonQuoted(String rawValue) {
        String value = rawValue == null ? "" : rawValue;
        StringBuilder escaped = new StringBuilder();
        escaped.append('"');

        for (int index = 0; index < value.length(); index++) {
            char currentChar = value.charAt(index);

            switch (currentChar) {
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    if (currentChar < 0x20) {
                        String hex = Integer.toHexString(currentChar);
                        escaped.append("\\u");

                        for (int padding = hex.length(); padding < 4; padding++) {
                            escaped.append('0');
                        }

                        escaped.append(hex);
                    } else {
                        escaped.append(currentChar);
                    }
                    break;
            }
        }

        escaped.append('"');
        return escaped.toString();
    }

    public static void appendSourceJson(StringBuilder json, String fieldName, boolean observed, String reason) {
        json.append('"').append(fieldName).append("\":{");
        json.append("\"observed\":").append(observed ? "true" : "false");

        if (!reason.isEmpty()) {
            json.append(",\"reason\":").append(jsonQuoted(reason));
        }

        json.append('}');
    }

    public static void appendJsonArray(StringBuilder json, String fieldName, List<?> values) {
        json.append(",\"").append(fieldName).append("\":[");

        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                json.append(',');
            }

            Object value = values.get(index);
            json.append(value == null ? "null" : String.valueOf(value));
        }

        json.append(']');
    }

    public static String toJsonStringArray(List<String> values) {
        StringBuilder json = new StringBuilder();
        json.append('[');

        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                json.append(',');
            }

            json.append(jsonQuoted(values.get(index)));
        }

        json.append(']');
        return json.toString();
    }

    public static String preferDisplayName(String preferred, String fallback) {
        if (preferred != null && !preferred.isEmpty()) {
            return preferred;
        }

        return humanizeEnumName(fallback);
    }

    public static String humanizeEnumName(String rawValue) {
        if (rawValue == null || rawValue.isEmpty()) {
            return "";
        }

        String lower = rawValue.toLowerCase(Locale.US).replace('_', ' ');
        StringBuilder display = new StringBuilder();
        boolean capitalizeNext = true;

        for (int index = 0; index < lower.length(); index++) {
            char currentChar = lower.charAt(index);

            if (currentChar == ' ') {
                capitalizeNext = true;
                display.append(currentChar);
                continue;
            }

            if (capitalizeNext) {
                display.append(Character.toUpperCase(currentChar));
                capitalizeNext = false;
            } else {
                display.append(currentChar);
            }
        }

        return display.toString();
    }

    public static String decodeCliCommand(String rawCommand) {
        if (rawCommand == null) {
            return "";
        }

        if (!rawCommand.startsWith("b64:")) {
            return rawCommand;
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(rawCommand.substring(4));
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (Throwable throwable) {
            return rawCommand;
        }
    }
}
