package com.gerai.analyticsservice.service;

import com.gerai.analyticsservice.dto.*;
import com.gerai.analyticsservice.repository.AttritionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service de calcul des prédictions d'attrition des employés actifs.
 *
 * Le score d'attrition est calculé sur 4 facteurs, chacun noté de 0 à 25 points :
 * <ul>
 *   <li>Facteur 1 — Ancienneté : les nouveaux employés (moins de 6 mois) obtiennent 25 pts</li>
 *   <li>Facteur 2 — Absences : jours de congé validés sur 12 mois (≥ 30 j → 25 pts)</li>
 *   <li>Facteur 3 — Taux de refus : proportion de demandes refusées (≥ 50 % → 25 pts)</li>
 *   <li>Facteur 4 — Engagement : aucun projet actif assigné → 25 pts</li>
 * </ul>
 *
 * Seuils de risque :
 * <ul>
 *   <li>score ≥ 70 → ÉLEVÉ</li>
 *   <li>score ≥ 40 → MOYEN</li>
 *   <li>score &lt; 40 → FAIBLE</li>
 * </ul>
 *
 * Les résultats sont mis en cache Caffeine ({@code @Cacheable}) pour éviter
 * des requêtes Oracle coûteuses à chaque appel. Le cache est invalidé via
 * {@link #refresh()} ou après modification des données par le Kafka consumer.
 *
 * {@code @Service} : bean Spring géré par le conteneur IoC.
 * {@code @RequiredArgsConstructor} : génère le constructeur avec injection de {@link AttritionRepository}.
 *
 * @since 1.0
 */
@Service
@RequiredArgsConstructor
public class AttritionService {

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final AttritionRepository repository;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Retourne la liste des prédictions d'attrition pour tous les employés actifs.
     * Le résultat est mis en cache sous la clé "attrition-predictions".
     *
     * @return la liste des {@link AttritionPredictionDTO} avec le score, le niveau et les facteurs
     */
    @Cacheable("attrition-predictions")
    public List<AttritionPredictionDTO> getPredictions() {
        return buildPredictions(repository.findAttritionRawData());
    }

    /**
     * Retourne le résumé agrégé des risques d'attrition (totaux par niveau, stats par département).
     * Le résultat est mis en cache sous la clé "attrition-summary".
     *
     * @return un {@link AttritionSummaryDTO} avec les indicateurs globaux et par département
     */
    @Cacheable("attrition-summary")
    public AttritionSummaryDTO getSummary() {
        List<AttritionPredictionDTO> predictions =
                buildPredictions(repository.findAttritionRawData());
        return buildSummary(predictions);
    }

    /**
     * Invalide les caches "attrition-predictions" et "attrition-summary".
     * Le prochain appel à {@link #getPredictions()} ou {@link #getSummary()}
     * recalculera les données depuis Oracle.
     */
    @CacheEvict(cacheNames = {"attrition-predictions", "attrition-summary"}, allEntries = true)
    public void refresh() {
        // evicts both caches; next call will recompute from DB
    }

    // ── Prediction list builder ───────────────────────────────────────────────

    /**
     * Transforme les lignes brutes Oracle en liste de prédictions d'attrition.
     *
     * @param rows les lignes brutes retournées par {@link com.gerai.analyticsservice.repository.AttritionRepository#findAttritionRawData()}
     * @return la liste des prédictions calculées pour chaque employé
     */
    private List<AttritionPredictionDTO> buildPredictions(List<Object[]> rows) {
        String now = LocalDate.now().atStartOfDay()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

        return rows.stream()
                .map(row -> scoreEmployee(row, now))
                .collect(Collectors.toList());
    }

    /**
     * Calcule le score d'attrition d'un employé à partir de ses données brutes Oracle.
     *
     * @param row       le tableau de colonnes Oracle pour un employé (voir {@link com.gerai.analyticsservice.repository.AttritionRepository})
     * @param timestamp l'horodatage du calcul au format "dd/MM/yyyy HH:mm"
     * @return le DTO de prédiction complet avec score, niveau, facteurs et recommandations
     */
    private AttritionPredictionDTO scoreEmployee(Object[] row, String timestamp) {
        long   employeId   = toLong(row[0]);
        String firstName   = str(row[1]);
        String lastName    = str(row[2]);
        String photoUrl    = str(row[3]);
        LocalDate hireDate = toLocalDate(row[4]);
        String departement = str(row[5]);
        String poste       = str(row[6]);
        int absenceDays    = toInt(row[7]);
        int totalDemandes  = toInt(row[8]);
        int refusees       = toInt(row[9]);
        int projetsActifs  = toInt(row[10]);

        // ── Four scored factors (each 0–25) ──────────────────────────────────
        RiskFactorDTO tenureFactor     = scoreTenure(hireDate);
        RiskFactorDTO absenceFactor    = scoreAbsence(absenceDays);
        RiskFactorDTO rejectionFactor  = scoreRejection(totalDemandes, refusees);
        RiskFactorDTO engagementFactor = scoreEngagement(projetsActifs);

        int score = tenureFactor.getImpact()
                  + absenceFactor.getImpact()
                  + rejectionFactor.getImpact()
                  + engagementFactor.getImpact();

        // Only include factors that actually contributed to the score
        List<RiskFactorDTO> facteurs = Arrays.asList(
                tenureFactor, absenceFactor, rejectionFactor, engagementFactor)
                .stream()
                .filter(f -> f.getImpact() > 0)
                .collect(Collectors.toList());

        return AttritionPredictionDTO.builder()
                .employeId(employeId)
                .nom(lastName)
                .prenom(firstName)
                .photo(photoUrl)
                .departement(departement)
                .poste(poste)
                .riskScore(score)
                .riskLevel(riskLevel(score))
                .facteurs(facteurs)
                .recommandations(buildRecommendations(
                        tenureFactor.getImpact(),
                        absenceFactor.getImpact(),
                        rejectionFactor.getImpact(),
                        engagementFactor.getImpact()))
                .lastUpdated(timestamp)
                .build();
    }

    // ── Summary builder ──────────────────────────────────────────────────────

    /**
     * Agrège la liste de prédictions en un résumé global et par département.
     *
     * @param predictions la liste des prédictions calculées par {@link #buildPredictions(List)}
     * @return un {@link AttritionSummaryDTO} avec les totaux par niveau et les stats par département
     */
    private AttritionSummaryDTO buildSummary(List<AttritionPredictionDTO> predictions) {
        if (predictions.isEmpty()) {
            return AttritionSummaryDTO.builder()
                    .totalEmployes(0).risqueEleve(0).risqueMoyen(0)
                    .risqueFaible(0).avgScore(0).deptStats(Collections.emptyList())
                    .build();
        }

        long eleve  = predictions.stream().filter(p -> "ELEVE".equals(p.getRiskLevel())).count();
        long moyen  = predictions.stream().filter(p -> "MOYEN".equals(p.getRiskLevel())).count();
        long faible = predictions.stream().filter(p -> "FAIBLE".equals(p.getRiskLevel())).count();
        double avg  = predictions.stream().mapToInt(AttritionPredictionDTO::getRiskScore).average().orElse(0);

        Map<String, List<AttritionPredictionDTO>> byDept = predictions.stream()
                .collect(Collectors.groupingBy(AttritionPredictionDTO::getDepartement));

        List<DeptAttritionStatDTO> deptStats = byDept.entrySet().stream()
                .map(e -> {
                    List<AttritionPredictionDTO> deptList = e.getValue();
                    double deptAvg = deptList.stream()
                            .mapToInt(AttritionPredictionDTO::getRiskScore).average().orElse(0);
                    long deptEleve = deptList.stream()
                            .filter(p -> "ELEVE".equals(p.getRiskLevel())).count();
                    return DeptAttritionStatDTO.builder()
                            .departement(e.getKey())
                            .avgScore(Math.round(deptAvg * 10.0) / 10.0)
                            .risqueEleve(deptEleve)
                            .total(deptList.size())
                            .build();
                })
                .sorted(Comparator.comparingDouble(DeptAttritionStatDTO::getAvgScore).reversed())
                .collect(Collectors.toList());

        return AttritionSummaryDTO.builder()
                .totalEmployes(predictions.size())
                .risqueEleve(eleve)
                .risqueMoyen(moyen)
                .risqueFaible(faible)
                .avgScore(Math.round(avg * 10.0) / 10.0)
                .deptStats(deptStats)
                .build();
    }

    // ── Scoring factors ──────────────────────────────────────────────────────

    /**
     * Factor 1 — Short tenure is the strongest early-attrition signal.
     * New employees who haven't yet built organisational ties leave more easily.
     */
    private RiskFactorDTO scoreTenure(LocalDate hireDate) {
        if (hireDate == null) {
            return RiskFactorDTO.builder()
                    .label("Ancienneté inconnue").impact(0)
                    .icon("ti ti-calendar-question").build();
        }
        long months = ChronoUnit.MONTHS.between(hireDate, LocalDate.now());
        int impact;
        String label;
        if (months < 6) {
            impact = 25; label = "Ancienneté < 6 mois";
        } else if (months < 12) {
            impact = 20; label = "Ancienneté < 1 an";
        } else if (months < 24) {
            impact = 12; label = "Ancienneté < 2 ans";
        } else if (months < 60) {
            impact = 5;  label = "Ancienneté < 5 ans";
        } else {
            impact = 0;  label = "Ancienneté ≥ 5 ans";
        }
        return RiskFactorDTO.builder()
                .label(label).impact(impact).icon("ti ti-clock").build();
    }

    /**
     * Factor 2 — High absence volume signals possible health issues or disengagement.
     * Measured on approved leave days in the last 12 months.
     */
    private RiskFactorDTO scoreAbsence(int absenceDays) {
        int impact;
        String label;
        if (absenceDays >= 30) {
            impact = 25; label = "Absences élevées (≥ 30 j/an)";
        } else if (absenceDays >= 20) {
            impact = 18; label = "Absences importantes (20-29 j/an)";
        } else if (absenceDays >= 10) {
            impact = 10; label = "Absences modérées (10-19 j/an)";
        } else if (absenceDays >= 5) {
            impact = 5;  label = "Absences légères (5-9 j/an)";
        } else {
            impact = 0;  label = "Taux d'absence normal";
        }
        return RiskFactorDTO.builder()
                .label(label).impact(impact).icon("ti ti-calendar-off").build();
    }

    /**
     * Factor 3 — A high rate of rejected requests signals friction with management,
     * which strongly correlates with voluntary departure.
     */
    private RiskFactorDTO scoreRejection(int total, int refused) {
        if (total == 0) {
            return RiskFactorDTO.builder()
                    .label("Aucune demande récente").impact(0)
                    .icon("ti ti-file-off").build();
        }
        double rate = (double) refused / total;
        int impact;
        String label;
        if (rate >= 0.5) {
            impact = 25; label = "Taux de refus très élevé (≥ 50 %)";
        } else if (rate >= 0.3) {
            impact = 15; label = "Taux de refus élevé (30-50 %)";
        } else if (rate >= 0.1) {
            impact = 8;  label = "Taux de refus modéré (10-30 %)";
        } else {
            impact = 0;  label = "Taux de refus faible";
        }
        return RiskFactorDTO.builder()
                .label(label).impact(impact).icon("ti ti-file-x").build();
    }

    /**
     * Factor 4 — Employees not assigned to any active project have fewer ties
     * and fewer growth opportunities, increasing attrition likelihood.
     */
    private RiskFactorDTO scoreEngagement(int activeProjects) {
        int impact;
        String label;
        if (activeProjects == 0) {
            impact = 25; label = "Aucun projet actif assigné";
        } else if (activeProjects == 1) {
            impact = 10; label = "Un seul projet actif";
        } else {
            impact = 0;  label = "Engagé sur plusieurs projets";
        }
        return RiskFactorDTO.builder()
                .label(label).impact(impact).icon("ti ti-briefcase").build();
    }

    // ── Risk level threshold ──────────────────────────────────────────────────

    /**
     * Détermine le niveau de risque d'attrition à partir du score calculé.
     *
     * @param score le score total d'attrition (somme des 4 facteurs, compris entre 0 et 100)
     * @return "ELEVE" si score ≥ 70, "MOYEN" si score ≥ 40, "FAIBLE" sinon
     */
    private String riskLevel(int score) {
        if (score >= 70) return "ELEVE";
        if (score >= 40) return "MOYEN";
        return "FAIBLE";
    }

    // ── Recommendations ───────────────────────────────────────────────────────

    /**
     * Génère une liste de recommandations RH personnalisées selon les facteurs contributeurs.
     * Au moins une recommandation est toujours retournée (message de suivi si profil stable).
     *
     * @param tenure     score du facteur ancienneté (0–25)
     * @param absence    score du facteur absences (0–25)
     * @param rejection  score du facteur taux de refus (0–25)
     * @param engagement score du facteur engagement projet (0–25)
     * @return la liste des recommandations RH à afficher dans l'interface Angular
     */
    private List<String> buildRecommendations(int tenure, int absence, int rejection, int engagement) {
        List<String> recs = new ArrayList<>();
        if (tenure >= 20)
            recs.add("Mettre en place un programme de mentorat et d'intégration renforcée");
        if (absence >= 18)
            recs.add("Analyser les causes des absences répétées avec le responsable RH");
        if (rejection >= 15)
            recs.add("Revoir le traitement des demandes RH avec le manager direct");
        if (engagement >= 10)
            recs.add("Affecter à de nouveaux projets stimulants pour renforcer l'engagement");
        if (recs.isEmpty())
            recs.add("Maintenir le suivi périodique — profil stable");
        return recs;
    }

    // ── Type conversion helpers ───────────────────────────────────────────────

    /**
     * Convertit un objet Oracle (BigDecimal, Long, Number) en {@code long}.
     *
     * @param o la valeur Oracle à convertir
     * @return la valeur {@code long} correspondante, ou 0 si la valeur est null ou non numérique
     */
    private long toLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Long l) return l;
        if (o instanceof BigDecimal bd) return bd.longValue();
        if (o instanceof Number n) return n.longValue();
        return 0L;
    }

    /**
     * Convertit un objet Oracle (BigDecimal, Number) en {@code int}.
     *
     * @param o la valeur Oracle à convertir
     * @return la valeur {@code int} correspondante, ou 0 si la valeur est null ou non numérique
     */
    private int toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof BigDecimal bd) return bd.intValue();
        if (o instanceof Number n) return n.intValue();
        return 0;
    }

    /**
     * Convertit un objet Oracle en {@link String}.
     *
     * @param o la valeur Oracle à convertir
     * @return la représentation textuelle de la valeur, ou {@code null} si la valeur est null
     */
    private String str(Object o) {
        return o == null ? null : o.toString();
    }

    /**
     * Convertit un objet Oracle (java.sql.Date, java.util.Date, LocalDate) en {@link LocalDate}.
     *
     * @param o la valeur Oracle à convertir
     * @return le {@link LocalDate} correspondant, ou {@code null} si la valeur est null ou non convertible
     */
    private LocalDate toLocalDate(Object o) {
        if (o == null) return null;
        if (o instanceof java.sql.Date d) return d.toLocalDate();
        if (o instanceof java.util.Date d)
            return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        if (o instanceof LocalDate ld) return ld;
        return null;
    }
}
