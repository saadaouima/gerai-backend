package com.gerai.notificationservice.service;

import com.gerai.notificationservice.enums.TypeNotification;
import com.gerai.notificationservice.event.NotificationEvent;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Service d'envoi d'emails HTML via SMTP Gmail pour les événements de notification critiques.
 * <p>
 * {@code @Service} : enregistre ce bean dans le contexte Spring comme composant de la couche service.<br>
 * {@code @RequiredArgsConstructor} : injecte {@link JavaMailSender} par constructeur Lombok.<br>
 * {@code @Slf4j} : fournit un logger Lombok pour tracer les envois et les erreurs SMTP.<br>
 * {@code @Async} (sur les méthodes d'envoi) : exécute les envois dans un thread séparé pour
 * ne pas bloquer le thread Kafka consommateur.
 * </p>
 * <p>
 * L'envoi email est conditionné par la propriété {@code app.mail.enabled} (défaut : {@code true}).
 * Un email n'est envoyé que si l'événement contient une adresse email valide.
 * </p>
 *
 * @since 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    /** Bean JavaMailSender configuré par {@link com.gerai.notificationservice.config.MailConfig}. */
    private final JavaMailSender mailSender;

    /** Adresse email expéditrice (ex. : {@code synapse-noreply@gmail.com}). */
    @Value("${spring.mail.username}")
    private String fromAddress;

    /** Interrupteur global d'envoi d'emails, désactivable via {@code app.mail.enabled=false}. */
    @Value("${app.mail.enabled:true}")
    private boolean emailEnabled;

    /** Table de correspondance entre le type de notification et l'objet de l'email. */
    private static final Map<TypeNotification, String> TYPE_SUBJECTS = Map.of(
            TypeNotification.NOUVELLE_DEMANDE,  "Nouvelle demande reçue — GerAI",
            TypeNotification.DEMANDE_APPROUVEE, "Demande approuvée — GerAI",
            TypeNotification.DEMANDE_REJETEE,   "Demande refusée — GerAI",
            TypeNotification.NOUVEAU_MESSAGE,   "Nouveau message — GerAI",
            TypeNotification.RAPPEL,            "Rappel — GerAI",
            TypeNotification.INFO,              "Notification — GerAI"
    );

    /**
     * Compose et envoie un email HTML à partir d'un événement de notification Kafka.
     * <p>
     * L'envoi est ignoré si {@code emailEnabled} est {@code false} ou si l'événement
     * ne contient pas d'adresse email valide. L'exécution est asynchrone pour ne pas
     * bloquer le traitement du message Kafka.
     * </p>
     *
     * @param event l'événement de notification Kafka contenant l'adresse email destinataire,
     *              le titre, le contenu et le type sémantique
     */
    @Async
    public void envoyerDepuisEvent(NotificationEvent event) {
        if (!emailEnabled
                || event.getEmail() == null
                || event.getEmail().isBlank()) {
            return;
        }

        TypeNotification typeEnum = TypeNotification.fromStatut(event.getType());

        String sujet   = TYPE_SUBJECTS.getOrDefault(typeEnum, "Notification GerAI");
        String titre   = event.getTitle()   != null ? event.getTitle()   : sujet;
        String contenu = event.getContent() != null
                ? event.getContent().replace("\n", "<br/>")
                : "";

        String corps = buildHtmlBody(titre, contenu, typeEnum);
        envoyerHtml(event.getEmail(), sujet, corps);
    }

    /**
     * Construit et envoie un email HTML brut de manière asynchrone via SMTP Gmail.
     * <p>
     * Utilise {@link jakarta.mail.internet.MimeMessage} avec encodage UTF-8 et
     * corps au format HTML. Les erreurs SMTP sont capturées et journalisées sans
     * propager d'exception pour ne pas impacter le flux Kafka.
     * </p>
     *
     * @param to        l'adresse email du destinataire
     * @param sujet     l'objet de l'email
     * @param corpsHtml le corps HTML complet de l'email
     */
    @Async
    public void envoyerHtml(String to, String sujet, String corpsHtml) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom("GerAI <" + fromAddress + ">");
            helper.setTo(to);
            helper.setSubject(sujet);
            helper.setText(corpsHtml, true);
            mailSender.send(message);
            log.info("[EmailService] Email envoyé avec succès à : {}", to);
        } catch (Exception e) {
            log.error("[EmailService] Échec envoi email à {} : {}", to, e.getMessage());
        }
    }

    /**
     * Génère le corps HTML complet de l'email à partir du titre, du contenu et du type
     * de notification (utilisé pour la couleur de la bannière et la bordure latérale).
     *
     * @param titre   le titre affiché dans la bannière colorée de l'email
     * @param contenu le corps du message, avec les retours à la ligne convertis en {@code <br/>}
     * @param type    le type de notification déterminant la couleur hexadécimale du template
     * @return le code HTML complet de l'email prêt à être envoyé
     */
    private String buildHtmlBody(String titre,
                                 String contenu,
                                 TypeNotification type) {
        String couleur = type.getColor();
        String heure   = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm"));

        return """
                <!DOCTYPE html>
                <html>
                <body style="margin:0;padding:0;font-family:'Segoe UI',Tahoma,Geneva,Verdana,sans-serif;">
                  <div style="max-width:600px;margin:20px auto;border:1px solid #ddd;border-radius:8px;overflow:hidden;">
                    <div style="background-color:%s;padding:20px;text-align:center;color:white;">
                      <h2 style="margin:0;">%s</h2>
                    </div>
                    <div style="padding:25px;line-height:1.6;color:#333;">
                      <p>Bonjour,</p>
                      <div style="border-left:4px solid %s;background:#f4f4f4;padding:15px;margin:20px 0;">
                        %s
                      </div>
                      <p style="font-size:13px;color:#666;">Date : %s</p>
                    </div>
                    <div style="background:#f9f9f9;padding:15px;text-align:center;font-size:11px;color:#888;border-top:1px solid #eee;">
                      GerAI — Votre assistant de gestion intelligent
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(couleur, titre, couleur, contenu, heure);
    }
}