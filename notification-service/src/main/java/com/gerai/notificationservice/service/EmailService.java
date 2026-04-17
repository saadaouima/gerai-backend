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

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromAddress;

    @Value("${app.mail.enabled:true}")
    private boolean emailEnabled;

    private static final Map<TypeNotification, String> TYPE_SUBJECTS = Map.of(
            TypeNotification.NOUVELLE_DEMANDE,  "Nouvelle demande reçue — GerAI",
            TypeNotification.DEMANDE_APPROUVEE, "Demande approuvée — GerAI",
            TypeNotification.DEMANDE_REJETEE,   "Demande refusée — GerAI",
            TypeNotification.NOUVEAU_MESSAGE,   "Nouveau message — GerAI",
            TypeNotification.RAPPEL,            "Rappel — GerAI",
            TypeNotification.INFO,              "Notification — GerAI"
    );

    /**
     * Envoie un email à partir d'un NotificationEvent Kafka.
     *
     * CORRECTION : utilise event.getTitle() + event.getContent()
     * (ancienne version utilisait event.getTitre() et event.getMessage()
     * qui n'existent plus dans le nouveau NotificationEvent).
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