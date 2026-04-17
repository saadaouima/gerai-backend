package com.gerai.demandesservice.service;

import com.gerai.demandesservice.dto.NotificationMessage;
import com.gerai.demandesservice.model.StatutDemande;
import com.gerai.demandesservice.model.TypeDemande;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DemandeServiceTest {

    @Mock
    private DemandeRepository demandeRepository;

    @Mock
    private KafkaTemplate<String, NotificationMessage> kafkaTemplate;

    @InjectMocks
    private DemandeService demandeService;

    @Test
    void testValidationChefApprouvee() {

        Demande d = Demande.builder()
                .id(1L)
                .employeId("EMP01")
                .employeNom("Nour Bousaidi")
                .type(TypeDemande.CONGE)
                .chefId("CHEF01")
                .adminRhId("RH01")
                .statut(StatutDemande.EN_ATTENTE)
                .build();

        when(demandeRepository.findById(1L)).thenReturn(Optional.of(d));
        when(demandeRepository.save(any())).thenReturn(d);

        Demande resultat = demandeService.traiterActionChef(
                1L,
                "CHEF01",
                true,
                "Bien reçu"
        );

        assertEquals(StatutDemande.VALIDEE_CHEF, resultat.getStatut());

        verify(kafkaTemplate, times(1))
                .send(eq("notifications-demandes"), any());
    }

    @Test
    void testValidationChefRefusee() {

        Demande d = Demande.builder()
                .id(2L)
                .employeId("EMP02")
                .employeNom("Ahmed Ben Ali")
                .type(TypeDemande.FORMATION)
                .chefId("CHEF01")
                .statut(StatutDemande.EN_ATTENTE)
                .build();

        when(demandeRepository.findById(2L)).thenReturn(Optional.of(d));
        when(demandeRepository.save(any())).thenReturn(d);

        Demande resultat = demandeService.traiterActionChef(
                2L,
                "CHEF01",
                false,
                "Non justifié"
        );

        assertEquals(StatutDemande.REJETEE, resultat.getStatut());

        verify(kafkaTemplate, times(1))
                .send(eq("notifications-demandes"), any());
    }

    @Test
    void testValidationRhApprouvee() {

        Demande d = Demande.builder()
                .id(3L)
                .employeId("EMP03")
                .employeNom("Sana Trabelsi")
                .type(TypeDemande.PRET)
                .adminRhId("RH01")
                .statut(StatutDemande.EN_ATTENTE) // ✔ pour PRET, validation directe RH
                .build();

        when(demandeRepository.findById(3L)).thenReturn(Optional.of(d));
        when(demandeRepository.save(any())).thenReturn(d);

        Demande resultat = demandeService.traiterActionRh(
                3L,
                "RH01",      // ✅ paramètre manquant ajouté
                true,
                "Accordé"
        );

        assertEquals(StatutDemande.VALIDEE_RH, resultat.getStatut());

        verify(kafkaTemplate, times(1))
                .send(eq("notifications-demandes"), any());
    }
}