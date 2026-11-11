package com.woodlogix.desktop.api.client;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

final class ApiUri {
    private ApiUri() {
    }

    static String path(String template, String... segments) {
        Objects.requireNonNull(template, "template must not be null");
        Objects.requireNonNull(segments, "segments must not be null");
        String result = template;
        for (String segment : segments) {
            result = result.replaceFirst("\\{segment\\}", encode(segment));
        }
        return result;
    }

    static String query(String path, String name, String value) {
        Objects.requireNonNull(path, "path must not be null");
        return path + "?" + encode(name) + "=" + encode(value);
    }

    static String encode(String value) {
        Objects.requireNonNull(value, "value must not be null");
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder encoded = new StringBuilder(bytes.length);
        for (byte byteValue : bytes) {
            int unsigned = byteValue & 0xff;
            if ((unsigned >= 'a' && unsigned <= 'z')
                    || (unsigned >= 'A' && unsigned <= 'Z')
                    || (unsigned >= '0' && unsigned <= '9')
                    || unsigned == '-' || unsigned == '.' || unsigned == '_'
                    || unsigned == '~') {
                encoded.append((char) unsigned);
            } else {
                encoded.append('%');
                encoded.append(Character.toUpperCase(Character.forDigit(unsigned >>> 4, 16)));
                encoded.append(Character.toUpperCase(Character.forDigit(unsigned & 0x0f, 16)));
            }
        }
        return encoded.toString();
    }
}
