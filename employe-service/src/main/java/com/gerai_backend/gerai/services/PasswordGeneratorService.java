package com.gerai_backend.gerai.services;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;

/**
 * Service de génération de mots de passe temporaires cryptographiquement sûrs,
 * respectant la politique de complexité Keycloak (majuscules, minuscules, chiffres, caractères spéciaux).
 *
 * <p>@Service : enregistré comme bean Spring et injecté dans {@link EmployeeService}.</p>
 * <p>Utilise {@link SecureRandom} (générateur cryptographiquement fort)
 * pour garantir l'imprévisibilité des mots de passe générés.</p>
 *
 * @since 1.0
 */
@Service
public class PasswordGeneratorService {

    /** Alphabet des majuscules utilisables dans les mots de passe. */
    private static final String UPPERCASE  = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    /** Alphabet des minuscules utilisables dans les mots de passe. */
    private static final String LOWERCASE  = "abcdefghijklmnopqrstuvwxyz";

    /** Chiffres utilisables dans les mots de passe. */
    private static final String DIGITS     = "0123456789";

    /** Caractères spéciaux utilisables dans les mots de passe. */
    private static final String SPECIAL    = "!@#$%^&*";

    /** Alphabet complet combinant toutes les catégories de caractères. */
    private static final String ALL_CHARS  = UPPERCASE + LOWERCASE + DIGITS + SPECIAL;

    /** Longueur fixe des mots de passe générés. */
    private static final int PASSWORD_LENGTH = 12;

    /** Générateur de nombres aléatoires cryptographiquement sûr. */
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Génère un mot de passe aléatoire de {@value PASSWORD_LENGTH} caractères
     * garantissant au moins un caractère de chaque catégorie (majuscule, minuscule,
     * chiffre, caractère spécial), puis mélange le résultat pour éviter les patterns prévisibles.
     *
     * @return un mot de passe temporaire cryptographiquement sûr
     */
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

    /**
     * Sélectionne un caractère aléatoire depuis un alphabet source donné.
     *
     * @param source l'alphabet source (chaîne de caractères autorisés)
     * @return un caractère choisi aléatoirement dans la source
     */
    private char randomChar(String source) {
        return source.charAt(secureRandom.nextInt(source.length()));
    }

    /**
     * Mélange aléatoirement les caractères d'une chaîne (algorithme de Fisher-Yates).
     * Utilisé pour éviter que les catégories obligatoires soient toujours dans le même ordre.
     *
     * @param input la chaîne de caractères à mélanger
     * @return la même chaîne avec les caractères dans un ordre aléatoire
     */
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