# GerAI Backend — Plateforme RH Intégrée

Bienvenue dans le projet **GerAI Backend**.  
Cette architecture microservices gère les **ressources humaines**, la **communication en temps réel** et les **analyses prédictives**.

---

## 🏗️ Architecture du Projet

- `init-db/` : Infrastructure **Oracle 23c** et migrations **Flyway**
- `employe-service/` : Gestion des employés (**Port 8081**)
- `analytics-service/` : Calculs statistiques (**Port 8087**)
- `chat-service/` : Messagerie temps réel **WebSockets** (**Port 8086**)
- `demandes-service/` : Gestion des flux métiers (**Port 8085**)
- `notification-service/` : Envoi de mails et notifications (**Port 8084**)
- `projets-service/` : Gestion des projets (**Port 8087**)
- `taches-service/` : Gestion des tâches (**Port 8088**)
- `docker-compose.yml` : Services d'infrastructure (**Kafka, MailHog**)

---

## 🚀 Démarrage Rapide

### 1. Lancer l'infrastructure (Docker)
À la racine du projet, lancez les services de support :

```bash
docker-compose up -d
````
Ceci démarre **Kafka** (Messaging) et **MailHog** (Emails de test).

---

## 2. Initialiser la Base de Données
Allez dans le dossier `init-db` et suivez le README spécifique :

```bash
cd init-db
docker-compose up -d
````
## 3. Lancer les Microservices
Chaque service doit être lancé dans son propre terminal (ou via IntelliJ) en suivant cet ordre recommandé :

1. `notification-service`
2. `demandes-service`
3. `chat-service`
4. `analytics-service`

---

## 📧 Outils de développement
- **Interface Kafka** : Le broker est disponible sur `localhost:9092`
- **Console MailHog** : Visualisez les emails envoyés par le système sur [http://localhost:8025](http://localhost:8025)
- **Keycloak** : Assurez-vous que votre instance Keycloak tourne sur le **port 8080**

---

## 🔐 Sécurité & Variables d'environnement
Chaque module possède un fichier `.env.example`.  
Copiez-le en `.env` dans chaque dossier avant de démarrer les services.

---

© 2026 GerAI Team