package com.gerai.projetsservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Filtre Servlet de limitation du débit (rate limiting) à fenêtre glissante
 * pour l'endpoint public de candidature spontanée.
 * <p>
 * Limite : {@code MAX_REQUESTS} requêtes par {@code WINDOW_SECONDS} secondes
 * par adresse IP, sans dépendance externe (utilise {@link ConcurrentHashMap}
 * et horodatages Unix).
 * En cas de dépassement, répond avec HTTP 429 et l'en-tête {@code Retry-After}.
 * </p>
 * <p>
 * {@code @Component} : enregistre ce filtre automatiquement dans la chaîne Servlet.<br>
 * {@code @Slf4j} : journalisation SLF4J via Lombok.
 * </p>
 *
 * @since 1.0
 */
@Slf4j
@Component
public class PublicRateLimitFilter extends OncePerRequestFilter {

    /** Nombre maximal de candidatures autorisées par IP dans la fenêtre temporelle. */
    private static final int  MAX_REQUESTS    = 10;

    /** Durée de la fenêtre glissante en secondes (1 heure). */
    private static final long WINDOW_SECONDS  = 3600; // 1 hour

    /** Chemin URI ciblé par ce filtre. */
    private static final String TARGET_PATH   = "/api/public/apply";

    /** Méthode HTTP ciblée par ce filtre. */
    private static final String TARGET_METHOD = "POST";

    /** Journal des horodatages de requête indexé par adresse IP. */
    private final ConcurrentHashMap<String, Deque<Long>> requestLog = new ConcurrentHashMap<>();

    /**
     * Détermine si ce filtre doit être ignoré pour la requête courante.
     * Le filtre n'est actif que pour {@code POST /api/public/apply}.
     *
     * @param request la requête HTTP entrante
     * @return {@code true} si la requête ne correspond pas à la cible, {@code false} sinon
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !(TARGET_METHOD.equalsIgnoreCase(request.getMethod())
                 && request.getRequestURI().endsWith(TARGET_PATH));
    }

    /**
     * Applique la logique de limitation du débit pour les requêtes ciblées.
     * <p>
     * Compte les requêtes de l'IP dans la fenêtre glissante et renvoie
     * HTTP 429 si le quota est dépassé.
     * </p>
     *
     * @param request  la requête HTTP entrante
     * @param response la réponse HTTP sortante
     * @param chain    la chaîne de filtres suivante
     * @throws ServletException en cas d'erreur Servlet
     * @throws IOException      en cas d'erreur d'entrée/sortie
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String ip = resolveClientIp(request);
        long   now = Instant.now().getEpochSecond();
        long   windowStart = now - WINDOW_SECONDS;

        Deque<Long> timestamps = requestLog.computeIfAbsent(ip, k -> new ArrayDeque<>());

        synchronized (timestamps) {
            // Remove timestamps older than the sliding window
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }

            if (timestamps.size() >= MAX_REQUESTS) {
                long retryAfter = WINDOW_SECONDS - (now - timestamps.peekFirst());
                log.warn("[RateLimit] IP {} blocked on POST /api/public/apply ({}/{} in {}s window)",
                         ip, timestamps.size(), MAX_REQUESTS, WINDOW_SECONDS);
                response.setStatus(429);
                response.setHeader("Retry-After", String.valueOf(retryAfter));
                response.setContentType("application/json");
                response.getWriter().write(
                    "{\"error\":\"Trop de candidatures depuis cette adresse IP. "
                    + "Veuillez réessayer dans " + (retryAfter / 60) + " minute(s).\"}");
                return;
            }

            timestamps.addLast(now);
        }

        chain.doFilter(request, response);
    }

    /**
     * Résout l'adresse IP réelle du client en tenant compte du proxy inverse.
     * <p>
     * Consulte l'en-tête {@code X-Forwarded-For} en priorité, puis
     * utilise {@link HttpServletRequest#getRemoteAddr()} en dernier recours.
     * </p>
     *
     * @param request la requête HTTP entrante
     * @return l'adresse IP du client
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
