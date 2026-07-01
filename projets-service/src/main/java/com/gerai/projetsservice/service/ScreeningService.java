package com.gerai.projetsservice.service;

import com.gerai.projetsservice.model.KillerQuestion;
import com.gerai.projetsservice.model.ScreeningQuestion;
import com.gerai.projetsservice.repository.KillerQuestionRepository;
import com.gerai.projetsservice.repository.ScreeningQuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Service de gestion du screening (présélection) des candidats.
 * <p>
 * Fournit deux fonctionnalités principales pour le portail de candidature public :
 * <ol>
 *   <li>Validation des killer questions — questions éliminatoires dont une mauvaise
 *       réponse bloque l'accès au formulaire de candidature.</li>
 *   <li>Calcul du score de screening pondéré (0–100) basé sur les réponses du candidat
 *       aux questions de présélection ({@link ScreeningQuestion}).</li>
 * </ol>
 * </p>
 * <p>
 * {@code @Service} : composant Spring géré par le conteneur IoC.<br>
 * {@code @Slf4j} : journalisation SLF4J via Lombok.<br>
 * {@code @RequiredArgsConstructor} : injection des repositories par constructeur.
 * </p>
 *
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScreeningService {

    /** Repository JPA pour les killer questions (questions éliminatoires). */
    private final KillerQuestionRepository killerRepo;
    /** Repository JPA pour les questions de screening pondérées. */
    private final ScreeningQuestionRepository screeningRepo;

    /**
     * Validates killer question answers.
     * @return null if all passed, or the text of the first failing question.
     */
    public String validateKillers(Long jobId, Map<Long, String> answers) {
        List<KillerQuestion> questions = killerRepo.findByJobIdOrderByDisplayOrderAsc(jobId);
        for (KillerQuestion kq : questions) {
            String given = answers == null ? null : answers.get(kq.getId());
            if (!kq.getExpectedAnswer().equalsIgnoreCase(given != null ? given.trim() : "")) {
                return kq.getQuestion();
            }
        }
        return null; // all passed
    }

    /**
     * Computes a 0–100 screening score from candidate answers.
     * Each question contributes (matchScore × weight) to the total.
     * For NUMBER_SCALE: partial credit = min(given / expected, 1.0).
     */
    public int computeScore(Long jobId, Map<Long, String> answers) {
        List<ScreeningQuestion> questions = screeningRepo.findByJobIdOrderByDisplayOrderAsc(jobId);
        if (questions.isEmpty()) return 0;

        int totalWeight = questions.stream().mapToInt(ScreeningQuestion::getWeight).sum();
        if (totalWeight == 0) return 0;

        double earned = 0;
        for (ScreeningQuestion sq : questions) {
            String given = answers == null ? null : answers.getOrDefault(sq.getId(), "");
            earned += matchScore(sq, given) * sq.getWeight();
        }
        return (int) Math.round(earned / totalWeight * 100);
    }

    private double matchScore(ScreeningQuestion sq, String given) {
        if (given == null || given.isBlank() || sq.getCorrectAnswer() == null) return 0;
        return switch (sq.getType()) {
            case "YES_NO", "SINGLE_CHOICE" ->
                sq.getCorrectAnswer().equalsIgnoreCase(given.trim()) ? 1.0 : 0.0;
            case "NUMBER_SCALE" -> {
                try {
                    double val      = Double.parseDouble(given.trim());
                    double expected = Double.parseDouble(sq.getCorrectAnswer().trim());
                    yield expected <= 0 ? 0 : Math.min(1.0, val / expected);
                } catch (NumberFormatException e) { yield 0; }
            }
            default -> 0;
        };
    }
}
