# 💬 Chat Service (GerAI)

Le **Chat Service** est le moteur de communication en temps réel de l'écosystème **GerAI**.  
Il permet aux employés d'échanger des **messages textuels**, des **fichiers** et des **images**, tout en gérant des **conversations privées** et des **groupes de discussion**.

---

## 🚀 Installation et Démarrage rapide

### 1. Configurer les secrets
Avant de lancer le service, créez votre fichier de configuration locale :

```bash
cp .env.example .env
````
# 🚀 Démarrage du Chat Service (GerAI)

## 1. Configuration des secrets

Éditez le fichier `.env` pour y renseigner vos accès **Oracle**, **Keycloak** et le chemin de stockage des fichiers.

---

## 2. Lancer le service

```bash
mvn clean install
mvn spring-boot:run
````
# 🧪 Guide de Test & Authentification

Récupérez un token valide en utilisant les variables de votre fichier `.env` :

```bash
# Chargez vos variables locales
source .env 

TOKEN=$(curl -s -X POST "$KEYCLOAK_ISSUER_URI/protocol/openid-connect/token" \
-d "grant_type=password" -d "client_id=gerai-backend" \
-d "username=$TEST_ADMIN_EMAIL" -d "password=$TEST_ADMIN_PASSWORD" | jq -r '.access_token')
````
# 🛠 Architecture Technique

- **Backend** : Spring Boot 3 & Spring Security
- **Base de données** : Oracle Database (Persistance des messages et conversations)
- **Sécurité** : Keycloak (JWT) avec extraction du claim `employee_id`
- **Protocole** : STOMP sur WebSockets

---

# 📌 API REST Endpoints

## 🗨️ Conversations

| Méthode | Endpoint                        | Description |
|---------|---------------------------------|-------------|
| GET     | /api/chat/conversations         | Liste toutes les conversations de l'utilisateur. |
| POST    | /api/chat/conversations         | Crée/Récupère une conversation directe avec un autre employé. |
| POST    | /api/chat/conversations/groupe  | Crée un nouveau groupe de discussion. |

## ✉️ Messages & Médias

| Méthode | Endpoint                                      | Description |
|---------|-----------------------------------------------|-------------|
| GET     | /api/chat/conversations/{id}/messages         | Historique des messages (déclenche aussi le "lu"). |
| POST    | /api/chat/conversations/{id}/messages         | Envoi d'un message (Fallback REST). |
| POST    | /api/chat/conversations/{id}/read             | Marque manuellement une conversation comme lue. |
| POST    | /api/chat/conversations/{id}/upload           | Upload et envoi d'un fichier/image dans le chat. |

## 👥 Utilisateurs

| Méthode | Endpoint             | Description |
|---------|----------------------|-------------|
| GET     | /api/chat/users      | Liste tous les utilisateurs disponibles via Keycloak Admin. |

---

# 🔌 WebSockets (STOMP)

Le service utilise des destinations spécifiques pour le temps réel.

### Destinations d'envoi (Client → Serveur)
- `/app/chat.envoyer` : Envoyer un message en temps réel.
- `/app/chat.typing` : Signaler que l'utilisateur est en train d'écrire.

### Destinations d'écoute (Serveur → Client)
- `/topic/conversation/{conversationId}` : Recevoir les nouveaux messages de la conversation.
- `/user/queue/typing` : Recevoir les indicateurs de saisie des autres.

---

# 🔐 Stratégie d'Identification (Employee ID)

Le service utilise les **IDs Oracle (Long)** au lieu des UUIDs Keycloak pour garantir l'intégrité des données métiers.

### Extraction de l'ID
Le `ChatController` suit une logique robuste :
1. **Priorité JWT** : Recherche du claim `employee_id` dans le token.
2. **Fallback Keycloak** : Si le claim est absent, le service interroge l'API Keycloak Admin via l'email pour retrouver l'ID Oracle stocké dans les attributs utilisateur.

### Sécurité WebSocket
- L'accès est sécurisé par le `WebSocketAuthChannelInterceptor`.
- Seuls les utilisateurs avec un token valide et un `employee_id` reconnu peuvent établir une connexion STOMP.

---

# 📁 Format des Payloads

### Envoi de message (JSON)

```json
{
  "conversationId": 102,
  "content": "Bonjour l'équipe !",
  "type": "TEXTE",
  "replyToId": null
}
````
## 📁 Indicateur de saisie (JSON)

```json
{
  "conversationId": 102,
  "destinataireEmployeeId": 5,
  "isTyping": true
}
````
## ⚙️ Configuration (Variables d'environnement)

Le service est configuré via les variables du fichier `.env`.  
Voici les paramètres clés utilisés dans le `application.properties` :

- `SERVER_PORT` : Port d'écoute du service (défaut: 8086)
- `DB_URL` / `DB_USER` / `DB_PASSWORD` : Connexion à la base Oracle
- `KEYCLOAK_ISSUER_URI` : URL de validation des tokens
- `FILE_UPLOAD_DIR` : Répertoire local de stockage des médias

⚠️ **Note aux développeurs** :  
Lors de l'ajout de nouveaux participants à un groupe, assurez-vous que leurs **IDs existent bien dans la table EMPLOYEES** de la base Oracle afin d'éviter les erreurs de contrainte d'intégrité.

---

© 2026 GerAI Backend - Chat Service
