# GerAI Backend — Plateforme RH Intégrée

Backend app for HR management system built with Spring Boot microservices.

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21+ |
| Framework | Spring Boot 3.x |
| Database | Oracle Free 23c |
| ORM | Hibernate / Spring Data JPA |
| Identity | Keycloak 26.5.5 |
| Security | Spring Security + OAuth2 / JWT |
| Messaging | Apache Kafka |
| Containerization | Docker + Docker Compose |

---

## 🏗️ Architecture du Projet

- `employe-service/` : Gestion des employés (**Port 8081**)
- `demandes-service/` : Gestion des flux métiers (**Port 8085**)
- `chat-service/` : Messagerie temps réel WebSockets (**Port 8086**)
- `analytics-service/` : Calculs statistiques et rapports (**Port 8087**)
- `notification-service/` : Envoi de mails et notifications (**Port 8084**)
- `projets-service/` : Gestion des projets (**Port 8087**)
- `taches-service/` : Gestion des tâches (**Port 8088**)
- `init-db/` : Infrastructure Oracle 23c et migrations Flyway
- `docker-compose.yml` : Services d'infrastructure (Oracle, Keycloak, Kafka, MailHog)

---

## 🚀 Démarrage Rapide

### 1. Créer le fichier `.env`

```bash
cp .env.example .env
```

Remplissez les valeurs dans `.env`. Voir la section **Gmail App Password Setup** pour `MAIL_PASSWORD`.

---

### 2. Lancer l'infrastructure (Docker)

À la racine du projet :

```bash
docker-compose up -d
```

Ceci démarre **Oracle**, **Keycloak**, **Kafka** et **MailHog**.

---

### 3. Configurer Keycloak

Ouvrez la console d'administration : `http://localhost:8080/admin`

#### Créer le Realm

```
Left dropdown → Create realm
Name: gerai-realm
Enabled: ON
```

#### Créer le Client

```
gerai-realm → Clients → Create client
Client ID             : gerai-backend
Client authentication : ON
Direct Access Grants  : ON
```

Copier le **Client Secret** :
```
gerai-backend → Credentials tab → Client Secret → coller dans .env KC_CLIENT_SECRET
```

#### Créer les rôles Client

```
gerai-backend → Roles → Create role (répéter pour chacun) :
  ✅ admin
  ✅ employees:read
  ✅ employees:write
  ✅ employees:update
  ✅ employees:delete
```

---

### 4. Initialiser la Base de Données

```bash
cd init-db
docker-compose up -d
```

Ou connectez-vous via SQL Developer et exécutez le script DDL depuis `init-db/`.

---

### 5. Lancer les Microservices

Chaque service se lance dans son propre terminal (ou via IntelliJ) dans cet ordre :

1. `employe-service`
2. `notification-service`
3. `demandes-service`
4. `chat-service`
5. `analytics-service`
6. `projets-service`
7. `taches-service`

```bash
cd <service-name>
mvn spring-boot:run
```

---

## 📧 Outils de développement

- **Oracle** : `localhost:1521` / EM Express : `http://localhost:5500`
- **Keycloak** : `http://localhost:8080`
- **Kafka** : `localhost:9092`
- **MailHog** : `http://localhost:8025`

---

## 🔐 Sécurité & Variables d'environnement

Chaque module possède un fichier `.env.example`.  
Copiez-le en `.env` dans chaque dossier avant de démarrer les services.

---

## Gmail App Password Setup

Required for sending temporary passwords via email:

```
1. Go to   → https://myaccount.google.com/security
2. Enable  → 2-Step Verification
3. Go to   → https://myaccount.google.com/apppasswords
4. Name    → gerai-backend
5. Click   → Create
6. Copy    → the 16-character password
7. Paste   → into .env MAIL_PASSWORD
```

---

© 2026 GerAI Team
