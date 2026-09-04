package com.curtinhonestly.backend.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates and normalises user-supplied links before they are stored and
 * rendered as hrefs on public pages.
 *
 * Rules: whitespace and control characters are stripped; only http and https
 * are allowed (javascript:, data:, file: and friends are rejected); a missing
 * scheme becomes https://; the scheme and host are lower-cased; path, query
 * string and fragment are kept as typed; user-info (user@host) is rejected
 * because it is only ever used to disguise the real destination.
 *
 * Pure and dependency-free so it can be unit tested without Spring.
 */
public final class SafeUrl {

    public static final int MAX_LENGTH = 500;

    private static final Pattern SCHEME = Pattern.compile("^([a-zA-Z][a-zA-Z0-9+.-]*):");
    private static final Pattern STRIP = Pattern.compile("[\\s\\p{Cntrl}]+");
    private static final Pattern HOST = Pattern.compile(
            "^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$");

    private SafeUrl() {
    }

    /**
     * Returns the normalised URL, or throws {@link IllegalArgumentException}
     * with a message suitable for showing to the person who typed it.
     */
    public static String normalise(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("A link is required.");
        }
        String cleaned = STRIP.matcher(raw).replaceAll("");
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("A link is required.");
        }

        Matcher schemeMatch = SCHEME.matcher(cleaned);
        if (schemeMatch.find()) {
            String scheme = schemeMatch.group(1).toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                throw new IllegalArgumentException("Only http and https links are allowed.");
            }
            // Repair "https:example.com" and "https:/example.com" to the canonical form.
            String rest = cleaned.substring(schemeMatch.end()).replaceFirst("^/+", "");
            cleaned = scheme + "://" + rest;
        } else if (cleaned.startsWith("//")) {
            cleaned = "https:" + cleaned;
        } else {
            cleaned = "https://" + cleaned;
        }

        URI uri;
        try {
            uri = new URI(cleaned);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("That does not look like a valid web address.");
        }

        if (uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException("Links with a username before the host are not allowed.");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("That does not look like a valid web address.");
        }
        host = host.toLowerCase(Locale.ROOT);
        if (!HOST.matcher(host).matches()) {
            throw new IllegalArgumentException("Enter a full web address, such as https://example.com/page.");
        }

        StringBuilder out = new StringBuilder(uri.getScheme()).append("://").append(host);
        if (uri.getPort() != -1) {
            out.append(':').append(uri.getPort());
        }
        String path = uri.getRawPath();
        if (path != null && !path.isEmpty()) {
            out.append(path);
        }
        if (uri.getRawQuery() != null) {
            out.append('?').append(uri.getRawQuery());
        }
        if (uri.getRawFragment() != null) {
            out.append('#').append(uri.getRawFragment());
        }

        String result = out.toString();
        if (result.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Links must be " + MAX_LENGTH + " characters or fewer.");
        }
        return result;
    }

    /** Same as {@link #normalise} but returns empty instead of throwing. */
    public static Optional<String> tryNormalise(String raw) {
        try {
            return Optional.of(normalise(raw));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
