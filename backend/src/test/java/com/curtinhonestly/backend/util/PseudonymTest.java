package com.curtinhonestly.backend.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PseudonymTest {

    private static final String SECRET = "unit-test-secret";
    private static final Pattern SHAPE = Pattern.compile("^[A-Z][a-z]+ [A-Z][a-z]+ \\d{2}$");

    @Test
    void sameUserAndSecretAlwaysGiveTheSameName() {
        String first = Pseudonym.forUser("user-1", SECRET);
        String second = Pseudonym.forUser("user-1", SECRET);
        assertThat(first).isEqualTo(second);
    }

    @Test
    void differentUsersGetDifferentNamesInPractice() {
        Set<String> names = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            names.add(Pseudonym.forUser(UUID.nameUUIDFromBytes(("u" + i).getBytes()).toString(), SECRET));
        }
        // 48 * 40 * 100 = 192,000 combinations; 200 draws should be almost all distinct.
        assertThat(names.size()).isGreaterThan(190);
    }

    @Test
    void changingTheSecretChangesTheName() {
        assertThat(Pseudonym.forUser("user-1", SECRET))
                .isNotEqualTo(Pseudonym.forUser("user-1", SECRET + "-rotated"));
    }

    @Test
    void nameIsAdjectiveAnimalAndTwoDigitSuffix() {
        for (int i = 0; i < 500; i++) {
            String name = Pseudonym.forUser("id-" + i, SECRET);
            assertThat(name).matches(SHAPE);
            String[] parts = name.split(" ");
            assertThat(Pseudonym.ADJECTIVES).contains(parts[0]);
            assertThat(Pseudonym.ANIMALS).contains(parts[1]);
        }
    }

    @Test
    void wordListsAreCleanAndDistinct() {
        for (String word : Pseudonym.ADJECTIVES) {
            assertThat(word).matches("^[A-Z][a-z]+$");
        }
        for (String word : Pseudonym.ANIMALS) {
            assertThat(word).matches("^[A-Z][a-z]+$");
        }
        assertThat(Set.of(Pseudonym.ADJECTIVES)).hasSize(Pseudonym.ADJECTIVES.length);
        assertThat(Set.of(Pseudonym.ANIMALS)).hasSize(Pseudonym.ANIMALS.length);
    }

    @Test
    void nullUserIdReadsAsFormerStudent() {
        assertThat(Pseudonym.forUser(null, SECRET)).isEqualTo(Pseudonym.FORMER_STUDENT);
        assertThat(Pseudonym.forUser("  ", SECRET)).isEqualTo(Pseudonym.FORMER_STUDENT);
    }

    @Test
    void missingSecretIsRejected() {
        assertThatThrownBy(() -> Pseudonym.forUser("user-1", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nameNeverContainsTheUserId() {
        String id = "3f9a1c2e-7b4d-4e6f-9a8b-1c2d3e4f5a6b";
        assertThat(Pseudonym.forUser(id, SECRET)).doesNotContain(id);
    }
}
