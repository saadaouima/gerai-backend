package com.gerai_backend.gerai.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Service d'envoi d'emails transactionnels pour le microservice {@code employe-service}.
 * Actuellement utilisé pour envoyer le mot de passe temporaire lors de la création d'un compte.
 *
 * <p>@Service : enregistré comme bean Spring et injecté dans {@link EmployeeService}.</p>
 * <p>Les erreurs d'envoi sont journalisées mais ne font pas échouer la requête principale —
 * l'employé est déjà créé en base et dans Keycloak.</p>
 *
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    /**
     * Envoie un email de bienvenue contenant le mot de passe temporaire
     * à l'adresse email professionnelle du nouvel employé.
     *
     * @param toEmail           l'adresse email du destinataire (employé)
     * @param firstName         le prénom de l'employé (utilisé dans le corps de l'email)
     * @param username          le login Keycloak de l'employé (format {@code prenom.nom})
     * @param temporaryPassword le mot de passe temporaire à communiquer (en clair, jamais stocké)
     */
    public void sendTemporaryPassword(String toEmail,
                                      String firstName,
                                      String username,
                                      String temporaryPassword) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject("Welcome — Your Account Has Been Created");
            message.setText(buildEmailBody(firstName, username, temporaryPassword));

            mailSender.send(message);
            log.info("Temporary password email sent to: {}", toEmail);

        } catch (Exception ex) {
            // Log but don't fail the whole request — employee is already created
            log.error("Failed to send email to {}: {}", toEmail, ex.getMessage());
        }
    }

    /**
     * Construit le corps de l'email de bienvenue avec les informations de connexion.
     *
     * @param firstName         le prénom de l'employé
     * @param username          le login Keycloak de l'employé
     * @param temporaryPassword le mot de passe temporaire en clair
     * @return le corps de l'email au format texte brut
     */
    private String buildEmailBody(String firstName,
                                  String username,
                                  String temporaryPassword) {
        return """
                Dear %s,

                Your HR system account has been created.

                Username          : %s
                Temporary Password: %s

                Please log in and change your username and password immediately.
                Your temporary password will expire after first login.

                Regards,
                HR Department
                """.formatted(firstName, username, temporaryPassword);
    }
}