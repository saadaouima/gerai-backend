# 📝 GerAI - Demandes Service

Ce microservice gère le cycle de vie complexe des demandes (**congés, formations, prêts, etc.**) au sein de l'écosystème **GerAI**.  
Il assure la transition entre un modèle métier unifié et une persistance multi-tables sous **Oracle DB**.

---

## 🚀 Installation et Démarrage rapide

### 1. Configurer les secrets
Avant de lancer le service, créez votre fichier de configuration locale à partir du modèle :

```bash
cp .env.example .env
````
## 🚀 Installation et Démarrage rapide

### 1. Configurer les secrets
Éditez le fichier `.env` pour y renseigner vos accès **Oracle**, **Kafka** et **Keycloak**.

### 2. Lancer le service

```bash
mvn clean install
mvn spring-boot:run
````
## 🚀 Fonctionnalités Clés

- **Workflow Multi-niveaux** : Validation hiérarchique (Chef) puis administrative (RH).
- **Dispatching Intelligent** : Routage automatique vers 5 tables Oracle spécifiques (`LEAVE`, `TRAINING`, `LOAN`, `DOCUMENT`, `AUTHORIZATION`).
- **Sécurité Contextuelle** : Protection des endpoints par rôles (Keycloak) avec vérification de l'appartenance à l'équipe.
- **Notifications & Analytics** : Publication d'événements Kafka pour les notifications temps réel et le calcul des compteurs d'absences.

---

## 🛠️ Stack Technique

- **Java 21 / Spring Boot 3.x**
- **Spring Security & OAuth2 (Keycloak JWT)**
- **Spring Data JPA (Oracle Database 21c)**
- **Apache Kafka** (Messaging asynchrone pour notifications)

---

## 🧪 Guide de Test & Authentification

Avant d'utiliser Postman ou CURL, chargez vos variables locales et récupérez un token valide :

```bash
source .env

# Récupération du token pour un profil RH/ADMIN
TOKEN=$(curl -s -X POST "$KEYCLOAK_ISSUER_URI/protocol/openid-connect/token" \
  -d "grant_type=password" -d "client_id=gerai-backend" \
  -d "username=$TEST_ADMIN_EMAIL" -d "password=$TEST_ADMIN_PASSWORD" | jq -r '.access_token')
```
# 🛣️ API Reference

## 👤 Espace Employé

| Méthode | Endpoint                   | Description |
|---------|----------------------------|-------------|
| POST    | /api/demandes              | Soumettre une nouvelle demande (JSON dynamique). |
| GET     | /api/demandes/mes-demandes | Consulter mon historique personnel. |

### Exemple de Body (POST)

```json
{
  "type": "CONGE",
  "leaveTypeId": 1,
  "startDate": "2026-05-01",
  "endDate": "2026-05-05",
  "daysCount": 5,
  "reason": "Vacances annuelles"
}
````
## 👔 Espace Chef (Management)

| Méthode | Endpoint                          | Description |
|---------|-----------------------------------|-------------|
| GET     | /api/demandes/equipe              | Toutes les demandes de mes collaborateurs. |
| GET     | /api/demandes/equipe/en-attente   | Demandes nécessitant ma validation (Niveau 1). |
| PUT     | /api/demandes/{type}/{id}/valider | Valider/Rejeter une demande. |

---

## 🏢 Espace RH / Admin

| Méthode | Endpoint                          | Description |
|---------|-----------------------------------|-------------|
| GET     | /api/demandes/toutes              | Vue globale sur toutes les demandes. |
| GET     | /api/demandes/en-attente-rh       | Demandes pré-validées par les chefs (Niveau 2). |
| PUT     | /api/demandes/{type}/{id}/valider | Validation finale. |

---

## ⚙️ Configuration (Variables d'environnement)

Le service s'appuie sur le fichier `.env` pour injecter les propriétés dans `application.properties` :

| Variable             | Description |
|----------------------|-------------|
| `SERVER_PORT`        | Port d'écoute (défaut: 8085). |
| `DB_URL`             | JDBC Oracle (ex: `jdbc:oracle:thin:@localhost:1521/FREEPDB1`). |
| `DB_USER` / `DB_PASSWORD` | Identifiants de la base de données. |
| `KAFKA_SERVERS`      | Adresse du broker Kafka (défaut: `localhost:9092`). |
| `KEYCLOAK_ISSUER_URI`| URL du Realm pour la vérification JWT. |

---

## 🔔 Notifications WebSocket

Les notifications générées par ce service sont envoyées via **Kafka** (`topic notification-events`), puis routées par le service dédié vers les clients :

- **Topic Utilisateur** : `/user/queue/notifications`

⚠️ **Note** : Assurez-vous que le broker Kafka est actif pour garantir la livraison des messages.

---

© 2026 GerAI Backend - Demandes Service
