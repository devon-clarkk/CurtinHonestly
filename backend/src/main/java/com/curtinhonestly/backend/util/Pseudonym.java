package com.curtinhonestly.backend.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

/**
 * Stable, anonymous display names for board authors.
 *
 * Boards are anonymous like reviews, but a conversation needs to show that two
 * replies came from the same person. The name is derived from a keyed hash of
 * the user id: HMAC-SHA256(secret, userId) picks an adjective, an Australian
 * animal and a two-digit suffix ("Quiet Quokka 42"). The same user always gets
 * the same name for a given secret, and nothing about the name can be reversed
 * into the id without the secret. Rotating the secret renames everyone.
 *
 * Pure: no Spring, no I/O, unit-tested directly.
 */
public final class Pseudonym {

    /** Shown when the author's account has been deleted (author is null). */
    public static final String FORMER_STUDENT = "Former student";

    static final String[] ADJECTIVES = {
            "Quiet", "Brisk", "Clever", "Sunny", "Gentle", "Curious", "Patient", "Bold",
            "Calm", "Witty", "Keen", "Nimble", "Steady", "Cheerful", "Sharp", "Mellow",
            "Swift", "Tidy", "Brave", "Humble", "Lively", "Rusty", "Dusty", "Salty",
            "Sleepy", "Hasty", "Jolly", "Lucky", "Mighty", "Plucky", "Snappy", "Spry",
            "Wise", "Zesty", "Breezy", "Chirpy", "Dapper", "Eager", "Frosty", "Grumpy",
            "Handy", "Jaunty", "Loyal", "Merry", "Peppy", "Quirky", "Rowdy", "Sturdy"
    };

    static final String[] ANIMALS = {
            "Quokka", "Wombat", "Numbat", "Bilby", "Dingo", "Echidna", "Platypus", "Koala",
            "Kangaroo", "Wallaby", "Possum", "Bandicoot", "Cassowary", "Emu", "Kookaburra", "Lorikeet",
            "Galah", "Cockatoo", "Magpie", "Lyrebird", "Brolga", "Rosella", "Currawong", "Pardalote",
            "Goanna", "Skink", "Gecko", "Bluetongue", "Frilly", "Dugong", "Cuttlefish", "Seadragon",
            "Dunnart", "Potoroo", "Bettong", "Quoll", "Pademelon", "Tawny", "Boobook", "Wobbegong"
    };

    private static final String ALGORITHM = "HmacSHA256";

    private Pseudonym() {
    }

    /**
     * The display name for a user id under the given secret, or
     * {@link #FORMER_STUDENT} when the id is null (anonymised content).
     */
    public static String forUser(String userId, String secret) {
        if (userId == null || userId.isBlank()) {
            return FORMER_STUDENT;
        }
        if (secret == null || secret.isEmpty()) {
            throw new IllegalArgumentException("A pseudonym secret is required.");
        }
        byte[] digest = hmac(userId, secret);
        ByteBuffer buffer = ByteBuffer.wrap(digest);
        int adjective = Math.floorMod(buffer.getInt(), ADJECTIVES.length);
        int animal = Math.floorMod(buffer.getInt(), ANIMALS.length);
        int suffix = Math.floorMod(buffer.getInt(), 100);
        return ADJECTIVES[adjective] + " " + ANIMALS[animal] + " " + String.format("%02d", suffix);
    }

    private static byte[] hmac(String message, String secret) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is unavailable on this JVM.", e);
        }
    }
}
