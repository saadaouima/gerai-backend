package com.gerai.projetsservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Configuration de l'expéditeur SMTP pour l'envoi d'emails (candidats, entretiens, offres).
 * <p>
 * Charge les identifiants SMTP depuis les variables d'environnement système ou depuis
 * un fichier {@code .env} local recherché dans plusieurs emplacements. Le serveur SMTP
 * configuré est Gmail avec STARTTLS sur le port 587.
 * </p>
 * <p>
 * {@code @Configuration} : fournit les beans Spring de messagerie.
 * </p>
 *
 * @since 1.0
 */
@Configuration
public class MailConfig {

    /**
     * Crée et configure le bean {@link JavaMailSender} pour Gmail SMTP.
     * <p>
     * Recherche les identifiants dans cet ordre :
     * <ol>
     *   <li>Variables d'environnement système ({@code SMTP_USER} / {@code SMTP_PASS}).</li>
     *   <li>Fichier {@code .env} local ({@code MAIL_USERNAME} / {@code MAIL_PASSWORD}).</li>
     * </ol>
     * </p>
     *
     * @return le sender SMTP configuré pour Gmail
     */
    @Bean
    public JavaMailSender javaMailSender() {
        Map<String, String> env = loadDotEnv();

        String username = resolveAny(env, "SMTP_USER", "MAIL_USERNAME");
        String password = resolveAny(env, "SMTP_PASS", "MAIL_PASSWORD");

        System.out.println("[MailConfig] user=" + username + " pass=" + (password.isEmpty() ? "EMPTY" : "OK"));

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("smtp.gmail.com");
        sender.setPort(587);
        sender.setUsername(username);
        sender.setPassword(password);
        sender.setDefaultEncoding("UTF-8");

        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");

        return sender;
    }

    /**
     * Charge les variables depuis un fichier {@code .env} en cherchant dans plusieurs chemins.
     *
     * @return map des paires clé/valeur lues depuis le fichier .env, vide si aucun fichier trouvé
     */
    private Map<String, String> loadDotEnv() {
        Map<String, String> map = new HashMap<>();
        File[] candidates = {
            new File("projets-service/.env"),
            new File("C:/Users/marie/Documents/GRH_PFE/gerai-backend/gerai/projets-service/.env"),
            new File(".env")
        };
        for (File f : candidates) {
            if (!f.exists()) continue;
            try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int eq = line.indexOf('=');
                    if (eq < 1) continue;
                    String key = line.substring(0, eq).trim();
                    String val = line.substring(eq + 1).trim();
                    if ((val.startsWith("\"") && val.endsWith("\"")) ||
                        (val.startsWith("'")  && val.endsWith("'"))) {
                        val = val.substring(1, val.length() - 1);
                    }
                    map.put(key, val);
                }
            } catch (Exception e) {
                System.out.println("[MailConfig] read error: " + e.getMessage());
            }
            if (map.containsKey("SMTP_USER") || map.containsKey("MAIL_USERNAME")) break;
        }
        return map;
    }

    /**
     * Résout la première valeur non vide parmi plusieurs clés en consultant d'abord
     * les variables système, puis la map .env fournie.
     *
     * @param env  map chargée depuis le fichier .env
     * @param keys liste de clés à tester dans l'ordre
     * @return la première valeur trouvée, ou chaîne vide si aucune clé n'est résolue
     */
    private String resolveAny(Map<String, String> env, String... keys) {
        for (String key : keys) {
            String val = System.getenv(key);
            if (val != null && !val.isBlank()) return val;
            val = env.get(key);
            if (val != null && !val.isBlank()) return val;
        }
        return "";
    }
}
