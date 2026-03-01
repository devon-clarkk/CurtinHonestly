package com.curtinhonestly.backend.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ProfanityFilterService {

    private Set<String> badWords = new HashSet<>();

    @PostConstruct
    public void init() throws IOException {
        try {
            // Using the requested optimization pattern
            byte[] bytes = getClass().getResourceAsStream("/profanity-list.csv").readAllBytes();
            String csv = new String(bytes, StandardCharsets.UTF_8);
            
            // Store in lowercase for case-insensitive matching
            badWords = Arrays.stream(csv.split(","))
                    .map(word -> word.trim().toLowerCase())
                    .filter(word -> !word.isEmpty())
                    .collect(Collectors.toSet());
            
            log.info("Loaded {} unique profanity words into HashSet.", badWords.size());
        } catch (Exception e) {
            log.error("Failed to load profanity list: {}", e.getMessage());
            badWords = new HashSet<>(Arrays.asList("fuck", "shit", "asshole", "bitch", "cunt", "dick", "piss", "bastard"));
        }
    }

    /**
     * Checks if the text contains any blacklisted words using O(1) HashSet lookup.
     * Splits text by non-word characters to handle punctuation and spaces.
     */
    public boolean containsProfanity(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        // Split by anything that isn't a letter or number (handles spaces, dots, commas, etc)
        String[] words = text.toLowerCase().split("\\W+");
        
        for (String word : words) {
            if (badWords.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
