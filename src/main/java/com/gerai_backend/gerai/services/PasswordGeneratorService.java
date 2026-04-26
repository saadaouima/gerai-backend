package com.gerai_backend.gerai.services;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
public class PasswordGeneratorService {

    private static final String UPPERCASE  = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWERCASE  = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS     = "0123456789";
    private static final String SPECIAL    = "!@#$%^&*";

    private static final String ALL_CHARS  = UPPERCASE + LOWERCASE + DIGITS + SPECIAL;

    private static final int PASSWORD_LENGTH = 12;

    // SecureRandom is cryptographically strong — never use Random for passwords
    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        StringBuilder password = new StringBuilder(PASSWORD_LENGTH);

        // Guarantee at least 1 character from each required category
        // This ensures the password always passes Keycloak's policy
        password.append(randomChar(UPPERCASE));
        password.append(randomChar(LOWERCASE));
        password.append(randomChar(DIGITS));
        password.append(randomChar(SPECIAL));

        // Fill the remaining characters randomly from all categories
        for (int i = 4; i < PASSWORD_LENGTH; i++) {
            password.append(randomChar(ALL_CHARS));
        }

        // Shuffle to avoid always having uppercase first
        return shuffle(password.toString());
    }

    private char randomChar(String source) {
        return source.charAt(secureRandom.nextInt(source.length()));
    }

    private String shuffle(String input) {
        char[] chars = input.toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = secureRandom.nextInt(i + 1);
            char temp = chars[i];
            chars[i] = chars[j];
            chars[j] = temp;
        }
        return new String(chars);
    }
}