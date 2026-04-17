# 📊 GerAI Analytics Service

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://www.oracle.com/java/)
[![JasperReports](https://img.shields.io/badge/Reporting-JasperReports%206.20-blue)](https://community.jaspersoft.com/)

## 📝 Présentation
Le **Analytics Service** est le moteur d'intelligence décisionnelle de la plateforme GerAI. Il centralise, agrège et transforme les données RH en indicateurs de performance (KPIs) et en documents officiels.

### Points clés :
* **Moteur JasperReports** : Génération de PDF et Excel professionnels.
* **Sécurité Multicouche** : Filtrage automatique des données selon le rôle (Admin vs Chef de département).
* **Performance** : Mise en cache des statistiques via **Caffeine Cache** et synchronisation temps réel via **Kafka**.

---

## 🛠 Stack Technique
* **Runtime**: Java 21, Spring Boot 3.x
* **Reporting**: JasperReports 6.20.x
* **Database**: Oracle DB (JPA/Hibernate)
* **Messaging**: Apache Kafka (Consommation des events de demandes)
* **Sécurité**: Keycloak (OAuth2 / JWT)
* **Cache**: Caffeine Cache

---

## 🚀 Installation et Démarrage rapide

### Prérequis
Assurez-vous que les services suivants sont opérationnels :
* **Oracle DB** (Port `1521`)
* **Keycloak** (Port `8080`)
* **Kafka/Zookeeper** (Port `9092`)

### Lancer le service
```bash
git clone [https://github.com/votre-org/analytics-service.git](https://github.com/votre-org/analytics-service.git)
cd analytics-service
mvn clean install
mvn spring-boot:run 
````

# 🧪 Guide de Test & Endpoints
1️⃣ Authentification (Keycloak)
Avant de tester les APIs, récupérez un token valide :

# --- Pour un profil RH/ADMIN ---
TOKEN=$(curl -s -X POST "$KEYCLOAK_ISSUER_URI/protocol/openid-connect/token" \
-d "grant_type=password" \
-d "client_id=gerai-backend" \
-d "username=$TEST_ADMIN_EMAIL" \
-d "password=$TEST_ADMIN_PASSWORD" | jq -r '.access_token')

# --- Pour un profil CHEF (Filtrage par département) ---
TOKEN_CHEF=$(curl -s -X POST "$KEYCLOAK_ISSUER_URI/protocol/openid-connect/token" \
-d "grant_type=password" \
-d "client_id=gerai-backend" \
-d "username=$TEST_CHEF_EMAIL" \
-d "password=$TEST_CHEF_PASSWORD" | jq -r '.access_token')

2️⃣ API Analytics (`/api/analytics`)

| Méthode | Endpoint            | Rôle   | Description                                                   |
|---------|---------------------|--------|---------------------------------------------------------------|
| GET     | /dashboard          | RH/ADMIN | KPIs globaux (Taux absentéisme, demandes...)                  |
| GET     | /conges             | RH/CHEF | Statistiques détaillées sur les congés                        |
| GET     | /formations         | RH/CHEF | Budget et suivi des formations                                |
| GET     | /par-mois           | RH/CHEF | Data pour graphiques (Mois/Type)                              |
| GET     | /employe/{id}       | RH/CHEF | Historique complet d'un collaborateur                         |
| GET     | /par-departement    | RH/ADMIN | Comparatif de performance entre services                      |
| GET     | /top-absences       | RH/ADMIN | Top 5 des employés les plus absents                           |
| GET     | /report/conges      | RH/CHEF | Export JSON brut pour debug/PowerBI                           |

### Exemple d'appel
```bash
curl -X GET http://localhost:8085/api/analytics/dashboard \
  -H "Authorization: Bearer $TOKEN" | jq .
````

# 3️⃣ API Reporting (`/api/reports`)

Ajoutez `?deptId=X` si vous êtes **RH** (optionnel) ou si vous êtes **Chef** (automatique via JWT).

---

## 📊 Endpoints

| Méthode | Endpoint             | Format | Description                                   |
|---------|----------------------|--------|-----------------------------------------------|
| GET     | /conges/pdf          | PDF    | Liste des congés avec zébrure et totaux       |
| GET     | /conges/excel        | XLSX   | Export Excel des congés                       |
| GET     | /formations/pdf      | PDF    | Rapport annuel des formations                 |
| GET     | /dashboard/pdf       | PDF    | Synthèse du dashboard en format PDF           |
| GET     | /employe/{id}/pdf    | PDF    | Fiche individuelle (CV interne / Historique)  |

---

## 📡 Intégration Kafka

Le service écoute le **topic `notification-events`**.  
À chaque validation de demande, les statistiques sont mises à jour en temps réel.

### 🔄 Simulation d’une validation de congé
```json
{
  "destinataireId": "1",
  "typeDemande": "CONGE",
  "statut": "VALIDE_RH",
  "nbJours": 5,
  "sourceService": "DEMANDES-SERVICE"
}
````

# 📁 Architecture des dossiers
analytics-service/
├── src/main/java/com/gerai/analytics/
│   ├── controller/      # API Rest (Analytics & Reports)
│   ├── service/         # Moteur Jasper & Calculs stats
│   ├── repository/      # Requêtes Oracle complexes
│   ├── config/          # Sécurité JWT & Kafka
│   └── event/           # Consumer Kafka
└── src/main/resources/
    ├── reports/         # Templates Jasper (.jrxml)
    └── application.yml  # Config DB & Cache

## ⚠️ Remarques Importantes

- [!IMPORTANT] **Mapping Jasper** :  
  Les fichiers `.jrxml` dépendent strictement des noms de champs fournis par le service.  
  Toute modification dans le `StatsService` (méthode `rowsToMaps`) doit être répercutée dans le template Jasper, sinon valeurs nulles.

- [!TIP] **Cache** :  
  Le cache est automatiquement invalidé lors de la réception d’un event Kafka ou toutes les **60 minutes**.

---

© 2026 **Synapse Platform** - Microservice Analytics

