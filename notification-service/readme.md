# 🔔 Notification Service (Synapse)

Ce microservice gère l'envoi, la consultation et le suivi des notifications au sein de l'écosystème **Synapse**.  
Il combine une **API REST** pour la gestion manuelle et un **consommateur Kafka** pour réagir automatiquement aux événements métiers (validations de congés, nouveaux messages, etc.).

---

## 🚀 Installation et Démarrage rapide

### 1. Configurer les secrets

Avant de lancer le service, créez votre fichier de configuration locale à partir du modèle :

```bash
cp .env.example .env
````
## ⚠️ Note
Pour l'envoi d'emails via Gmail, vous devez générer un **Mot de passe d'application** dans votre compte Google.

---

## 🚀 Lancer le service
```bash
mvn clean install
mvn spring-boot:run
````
# 📥 Intégration Kafka (Automatique)

Le service écoute en continu le topic **notification-events**.  
Lorsqu'un autre microservice de l'écosystème Synapse (ex: `demandes-service`) publie un événement, ce service :
- Persiste la notification dans la base **Oracle**.
- Déclenche l'envoi d'un email asynchrone à l'employé concerné via le serveur SMTP configuré.

---

# 🚀 Endpoints de l'API REST

Tous les endpoints sont préfixés par `/api/notifications`.

| Méthode | Endpoint        | Accès          | Description |
|---------|-----------------|----------------|-------------|
| POST    | /               | RH, ADMIN      | Création manuelle d'une notification |
| GET     | /               | Tout utilisateur | Liste toutes les notifications de l'utilisateur |
| GET     | /unread         | Tout utilisateur | Liste uniquement les notifications non lues |
| GET     | /unread/count   | Tout utilisateur | Nombre de notifications non lues |
| PUT     | /{id}/read      | Propriétaire   | Marque une notification spécifique comme lue |

---

# 🆔 Gestion de l'attribut `employee_id`

- Synapse utilise l'**ID technique Oracle (type Long)** pour lier les notifications aux employés, garantissant une cohérence parfaite avec les tables métiers.
- **Configuration Keycloak** :
    - **Attribut** : Chaque utilisateur Keycloak doit posséder l'attribut `employee_id`.
    - **Claim** : Un Protocol Mapper est configuré pour injecter cet attribut dans le **Token JWT**.
    - **Extraction** : Le `NotificationController` extrait cet ID directement du JWT.
    - Si le claim est absent → **401 Unauthorized**.

---

# 🛠 Exemple de Payload (POST)

```json
{
  "employeeId": 3,
  "type": "INFO",
  "title": "Validation de Congé",
  "content": "Votre demande pour la période de Mai a été acceptée.",
  "triggeredBy": 1,
  "referenceType": "LEAVE_REQUEST",
  "referenceId": 105
}
````
# ⚙️ Configuration (Variables d'environnement)

Le service s'appuie sur le fichier `.env` pour injecter les valeurs dans `application.properties` :

| Variable     | Description |
|--------------|-------------|
| SERVER_PORT  | Port d'écoute (défaut: 8084) |
| DB_URL       | URL JDBC Oracle (ex: `jdbc:oracle:thin:@localhost:1521/FREEPDB1`) |
| KAFKA_SERVERS| Broker Kafka (défaut: `localhost:9092`) |
| SMTP_USER    | Email utilisé pour l'envoi (ex: `votre_nom@gmail.com`) |
| SMTP_PASS    | Mot de passe d'application Google (16 caractères) |

---

# 🗄️ Structure de la Base de Données

Le service s'appuie sur la table **NOTIFICATIONS** :

- **Clé Primaire** : `NOTIFICATION_ID` (générée par séquence Oracle).
- **Clés Étrangères** : `EMPLOYEE_ID` et `TRIGGERED_BY` pointant vers la table **EMPLOYEES**.

---

© 2026 Synapse Platform - Notification Service

