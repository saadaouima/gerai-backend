package com.gerai.demandesservice.service;

import com.gerai.demandesservice.dto.DemandeRequest;
import com.gerai.demandesservice.model.StatutDemande;
import com.gerai.demandesservice.model.TypeDemande;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DemandeServiceIT {

    @Autowired
    private DemandeService demandeService;

    @Autowired
    private DemandeRepository demandeRepository;

    @MockitoBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void testWorkflowCompletConge() {

        // 1️⃣ Création
        DemandeRequest request = DemandeRequest.builder()
                .employeNom("Nour")
                .type(TypeDemande.CONGE)
                .chefId("CHEF_ALICE")
                .adminRhId("RH_BOB")
                .description("Congé annuel")
                .dateDebut(LocalDate.now())
                .dateFin(LocalDate.now().plusDays(5))
                .build();

        Demande creee = demandeService.creerDemande(
                request,
                "EMP_NOUR"   // 🔐 vient normalement du JWT
        );

        assertNotNull(creee.getId());
        assertEquals(StatutDemande.EN_ATTENTE, creee.getStatut());

        // 2️⃣ Le chef voit la demande
        List<Demande> aValiderChef =
                demandeService.getDemandesAValiderParChef("CHEF_ALICE");

        assertTrue(
                aValiderChef.stream()
                        .anyMatch(d -> d.getId().equals(creee.getId()))
        );

        // 3️⃣ Le chef valide
        demandeService.traiterActionChef(
                creee.getId(),
                "CHEF_ALICE",
                true,
                "Profite bien !"
        );

        // 4️⃣ Le RH voit la demande
        List<Demande> aValiderRh =
                demandeService.getDemandesAValiderParRh("RH_BOB");

        assertTrue(
                aValiderRh.stream()
                        .anyMatch(d -> d.getId().equals(creee.getId()))
        );

        // 5️⃣ RH valide
        demandeService.traiterActionRh(
                creee.getId(),
                "RH_BOB",
                true,
                "Validé côté RH"
        );

        // 6️⃣ Vérification finale
        Demande finale =
                demandeRepository.findById(creee.getId()).orElseThrow();

        assertEquals(StatutDemande.VALIDEE_RH, finale.getStatut());
    }
}