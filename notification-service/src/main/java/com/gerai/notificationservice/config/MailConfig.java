package com.gerai.notificationservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/**
 * Configuration du client SMTP pour l'envoi d'emails via Gmail.
 * <p>
 * {@code @Configuration} : déclare cette classe comme source de beans Spring ;
 * les propriétés sont lues depuis {@code application.properties} (préfixe
 * {@code spring.mail.*}).
 * </p>
 * <p>
 * La connexion SMTP utilise le protocole STARTTLS sur le port 587 (Gmail).
 * Le mode debug SMTP est activé pour faciliter le diagnostic des erreurs d'envoi.
 * </p>
 *
 * @since 1.0
 */
@Configuration
public class MailConfig {

    /** Hôte du serveur SMTP (ex. : {@code smtp.gmail.com}). */
    @Value("${spring.mail.host}")
    private String host;

    /** Port SMTP (587 par défaut pour STARTTLS). */
    @Value("${spring.mail.port:587}")
    private int port;

    /** Adresse email expéditrice utilisée pour l'authentification SMTP. */
    @Value("${spring.mail.username}")
    private String username;

    /** Mot de passe d'application Gmail pour l'authentification SMTP. */
    @Value("${spring.mail.password}")
    private String password;

    /** Indique si l'authentification SMTP est requise ({@code true} par défaut). */
    @Value("${spring.mail.properties.mail.smtp.auth:true}")
    private boolean smtpAuth;

    /** Indique si STARTTLS doit être activé pour chiffrer la connexion ({@code true} par défaut). */
    @Value("${spring.mail.properties.mail.smtp.starttls.enable:true}")
    private boolean starttls;

    /**
     * Crée et configure le bean {@link JavaMailSender} utilisé par {@code EmailService}
     * pour construire et envoyer les messages MIME HTML.
     *
     * @return une instance {@link JavaMailSenderImpl} configurée pour Gmail via STARTTLS
     */
    @Bean
    public JavaMailSender javaMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();

        mailSender.setHost(host);
        mailSender.setPort(port);
        mailSender.setUsername(username);
        mailSender.setPassword(password);
        mailSender.setDefaultEncoding("UTF-8");

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", String.valueOf(smtpAuth));
        props.put("mail.smtp.starttls.enable", String.valueOf(starttls));
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.ssl.trust", host);

        // Activé pour voir les logs SMTP détaillés si l'envoi échoue
        props.put("mail.debug", "true");

        return mailSender;
    }
}