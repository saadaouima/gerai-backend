package com.gerai.analyticsservice.service;

import com.gerai.analyticsservice.dto.AttritionPredictionDTO;
import com.gerai.analyticsservice.repository.AttritionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttritionServiceTest {

    @Mock
    private AttritionRepository repository;

    @InjectMocks
    private AttritionService service;

    /**
     * Builds one Oracle raw row matching the 11 columns of findAttritionRawData():
     * [0] EMPLOYEE_ID  [1] FIRST_NAME  [2] LAST_NAME  [3] PHOTO_URL
     * [4] HIRE_DATE    [5] DEPARTEMENT [6] POSTE
     * [7] ABSENCE_DAYS [8] TOTAL_DEMANDES [9] DEMANDES_REFUSEES [10] PROJETS_ACTIFS
     */
    private Object[] row(long id, String firstName, String lastName,
                         LocalDate hireDate, int absenceDays,
                         int totalDemandes, int refused, int projetsActifs) {
        return new Object[]{
            BigDecimal.valueOf(id),
            firstName,
            lastName,
            null,
            hireDate != null ? Date.valueOf(hireDate) : null,
            "Département Test",
            "Poste Test",
            BigDecimal.valueOf(absenceDays),
            BigDecimal.valueOf(totalDemandes),
            BigDecimal.valueOf(refused),
            BigDecimal.valueOf(projetsActifs)
        };
    }

    /** Wraps one or more rows in a properly-typed List<Object[]>. */
    private List<Object[]> rows(Object[]... arrays) {
        List<Object[]> list = new ArrayList<>();
        for (Object[] a : arrays) list.add(a);
        return list;
    }

    // ─── Test 1 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("1 — Ancienneté < 6 mois → tenure score 25, riskLevel FAIBLE si autres facteurs nuls")
    void whenTenureLessThan6Months_thenTenureScore25() {
        LocalDate hireDate = LocalDate.now().minusMonths(3); // months < 6 → impact 25
        when(repository.findAttritionRawData()).thenReturn(rows(
            row(1L, "Nour", "Ben Ali", hireDate,
                0,      // absence < 5 → 0
                0, 0,   // no requests → 0
                2)      // 2 projects → 0
        ));

        List<AttritionPredictionDTO> result = service.getPredictions();

        assertThat(result).hasSize(1);
        AttritionPredictionDTO pred = result.get(0);
        assertThat(pred.getRiskScore()).isEqualTo(25);
        assertThat(pred.getRiskLevel()).isEqualTo("FAIBLE"); // 25 < 40
        assertThat(pred.getFacteurs())
            .hasSize(1)
            .extracting("label")
            .containsExactly("Ancienneté < 6 mois");
    }

    // ─── Test 2 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("2 — Absences >= 30 jours/an → absence score 25")
    void whenAbsenceDaysGe30_thenAbsenceScore25() {
        LocalDate hireDate = LocalDate.now().minusYears(6); // ancienneté ≥ 5 ans → 0
        when(repository.findAttritionRawData()).thenReturn(rows(
            row(2L, "Ahmed", "Triki", hireDate,
                30,    // absence ≥ 30 → 25
                0, 0,
                2)
        ));

        List<AttritionPredictionDTO> result = service.getPredictions();

        assertThat(result).hasSize(1);
        AttritionPredictionDTO pred = result.get(0);
        assertThat(pred.getRiskScore()).isEqualTo(25);
        assertThat(pred.getFacteurs())
            .hasSize(1)
            .extracting("label")
            .containsExactly("Absences élevées (≥ 30 j/an)");
    }

    // ─── Test 3 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("3 — Taux de refus >= 50% (2/4) → rejection score 25")
    void whenRejectionRateGe50Percent_thenRejectionScore25() {
        LocalDate hireDate = LocalDate.now().minusYears(6);
        when(repository.findAttritionRawData()).thenReturn(rows(
            row(3L, "Sana", "Mejri", hireDate,
                0,
                4, 2,  // 2/4 = 50 % → impact 25
                2)
        ));

        List<AttritionPredictionDTO> result = service.getPredictions();

        assertThat(result).hasSize(1);
        AttritionPredictionDTO pred = result.get(0);
        assertThat(pred.getRiskScore()).isEqualTo(25);
        assertThat(pred.getFacteurs())
            .hasSize(1)
            .extracting("label")
            .containsExactly("Taux de refus très élevé (≥ 50 %)");
    }

    // ─── Test 4 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("4 — Aucun projet actif → engagement score 25")
    void whenNoActiveProject_thenEngagementScore25() {
        LocalDate hireDate = LocalDate.now().minusYears(6);
        when(repository.findAttritionRawData()).thenReturn(rows(
            row(4L, "Hedi", "Mansour", hireDate,
                0, 0, 0,
                0) // 0 projets → impact 25
        ));

        List<AttritionPredictionDTO> result = service.getPredictions();

        assertThat(result).hasSize(1);
        AttritionPredictionDTO pred = result.get(0);
        assertThat(pred.getRiskScore()).isEqualTo(25);
        assertThat(pred.getFacteurs())
            .hasSize(1)
            .extracting("label")
            .containsExactly("Aucun projet actif assigné");
    }

    // ─── Test 5 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("5 — Cumul des 4 facteurs maximaux → score 100, niveau ELEVE, 4 facteurs retournés")
    void whenAllRiskFactorsMaximal_thenScore100AndLevelEleve() {
        // tenure < 6 mois (25) + absence ≥ 30 (25) + refus ≥ 50% (25) + 0 projets (25) = 100
        LocalDate hireDate = LocalDate.now().minusMonths(2);
        when(repository.findAttritionRawData()).thenReturn(rows(
            row(5L, "Test", "Max", hireDate,
                30,   // absence ≥ 30 → 25
                4, 2, // 2/4 = 50 % → 25
                0)    // 0 projets → 25
        ));

        List<AttritionPredictionDTO> result = service.getPredictions();

        assertThat(result).hasSize(1);
        AttritionPredictionDTO pred = result.get(0);
        assertThat(pred.getRiskScore()).isEqualTo(100);
        assertThat(pred.getRiskLevel()).isEqualTo("ELEVE");
        assertThat(pred.getFacteurs()).hasSize(4);
        assertThat(pred.getFacteurs())
            .extracting("impact")
            .containsExactlyInAnyOrder(25, 25, 25, 25);
    }

    // ─── Test 6 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("6 — Profil stable → score 0, niveau FAIBLE, aucun facteur, recommandation générique")
    void whenStableProfile_thenScore0AndNoFactors() {
        LocalDate hireDate = LocalDate.now().minusYears(6); // ancienneté ≥ 5 ans → 0
        when(repository.findAttritionRawData()).thenReturn(rows(
            row(6L, "Ali", "Stable", hireDate,
                2,    // < 5 jours → 0
                10, 0, // 0 % refus → 0
                2)    // 2 projets → 0
        ));

        List<AttritionPredictionDTO> result = service.getPredictions();

        assertThat(result).hasSize(1);
        AttritionPredictionDTO pred = result.get(0);
        assertThat(pred.getRiskScore()).isEqualTo(0);
        assertThat(pred.getRiskLevel()).isEqualTo("FAIBLE");
        assertThat(pred.getFacteurs()).isEmpty();
        assertThat(pred.getRecommandations())
            .containsExactly("Maintenir le suivi périodique — profil stable");
    }

    // ─── Test 7 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("7 — Score >= 70 → niveau ELEVE (seuil exact : 85 points)")
    void whenScoreGe70_thenRiskLevelEleve() {
        // tenure < 6 mois (25) + absence ≥ 30 (25) + refus ≥ 50% (25) + 1 projet (10) = 85
        LocalDate hireDate = LocalDate.now().minusMonths(3);
        when(repository.findAttritionRawData()).thenReturn(rows(
            row(7L, "Test", "Eleve", hireDate,
                30,   // absence ≥ 30 → 25
                2, 1, // 1/2 = 50 % → 25
                1)    // 1 projet → 10
        ));

        List<AttritionPredictionDTO> result = service.getPredictions();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRiskScore()).isEqualTo(85);
        assertThat(result.get(0).getRiskLevel()).isEqualTo("ELEVE");
    }

    // ─── Test 8 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("8 — Plusieurs employés : scores calculés indépendamment et dans l'ordre du repository")
    void whenMultipleEmployees_thenScoredIndependently() {
        LocalDate longAgo = LocalDate.now().minusYears(6); // stable
        LocalDate recent  = LocalDate.now().minusMonths(2); // risqué
        when(repository.findAttritionRawData()).thenReturn(rows(
            row(1L, "Stable", "User", longAgo, 2,  10, 0, 2), // score = 0
            row(2L, "Risky",  "User", recent,  30, 4,  2, 0)  // score = 100
        ));

        List<AttritionPredictionDTO> result = service.getPredictions();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getEmployeId()).isEqualTo(1L);
        assertThat(result.get(0).getRiskScore()).isEqualTo(0);
        assertThat(result.get(1).getEmployeId()).isEqualTo(2L);
        assertThat(result.get(1).getRiskScore()).isEqualTo(100);
    }
}
