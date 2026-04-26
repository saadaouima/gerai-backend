# 🗄️ GerAI — Module Database

Ce module gère l'infrastructure de la base de données **Oracle 23c Free** et l'évolution du schéma via **Flyway**.  
Grâce à l'automatisation intégrée, chaque développeur dispose d'un environnement identique en quelques secondes.

---

## 📋 Prérequis

- **Docker Desktop** (installé et démarré).
- **Make** (utilitaire pour exécuter les commandes raccourcies).
- Un **compte Oracle** (optionnel, pour accéder au container registry si nécessaire).

---

## 🚀 Démarrage rapide

### 1. Configuration des secrets (Obligatoire)

Le projet utilise des variables d'environnement pour ne jamais stocker de mots de passe en clair :

```bash
cd gerai/database
cp .env.example .env
````
# 🗄️ GerAI — Module Database

Ce module gère l'infrastructure de la base de données **Oracle 23c Free** et l'évolution du schéma via **Flyway**.  
Grâce à l'automatisation intégrée, chaque développeur dispose d'un environnement identique en quelques secondes.

---

## 🚀 Démarrage rapide

### 1. Configuration des secrets (Obligatoire)

Ouvrez le fichier `.env` et définissez vos propres mots de passe (**Oracle Root, DB User, etc.**).

---

### 2. Initialisation complète (Méthode recommandée)

Si vous avez **Make**, une seule commande prépare l'intégralité de la base (Démarrage + Création utilisateur + Migrations + Seeds) :

```bash
make db-init
````
> **Note** : Cette commande inclut une pause de 90 secondes pour laisser le temps à l'instance Oracle de s'initialiser totalement avant de lancer Flyway.

---

## 3. Méthode manuelle (Sans Make)

```bash
# A. Lancer Oracle
docker-compose up -d oracle

# B. Surveiller les logs jusqu'au message "DATABASE IS READY TO USE!"
docker-compose logs -f oracle

# C. Lancer les migrations
docker-compose up flyway
````
## 📂 Structure du module
database/
├── docker-compose.yml    ← Définition des services Oracle & Flyway
├── Makefile              ← Automatisation des tâches courantes
├── .env.example          ← Modèle à copier vers .env
│
├── migrations/           ← Évolution du schéma (Flyway)
│   ├── V1__core_hr.sql   ← Tables de base (EMPLOYEES, CONTRACTS...)
│   ├── V2...             ← Projets, Demandes, Communication, etc.
│   └── V6__reporting...  ← Audit et Logs
│
└── seeds/                ← Données de test (insérées après migrations)
├── S1__ref_data.sql  ← Référentiels techniques
├── S2__demo_emps.sql ← /!\ Modifier les UUIDs Keycloak ici
└── S3__demo_reqs.sql ← Exemples de demandes pour le workflow

---

## 🛠️ Commandes utiles (Makefile)

| Commande         | Description |
|------------------|-------------|
| `make db-init`   | Installation de A à Z (Start, Setup User, Migrate). |
| `make db-start`  | Démarre uniquement le conteneur Oracle. |
| `make db-migrate`| Relance Flyway pour appliquer les nouveaux fichiers SQL. |
| `make db-reset`  | ⚠️ Purge totale : supprime les données et recrée tout. |
| `make db-connect`| Ouvre une console **sqlplus** interactive dans le conteneur. |
| `make db-status` | Affiche l'état de santé du conteneur Oracle. |

---

## 🔌 Connexion à la base

### Via un client SQL (DBeaver, SQL Developer, etc.)

| Champ           | Valeur |
|-----------------|--------|
| **Host**        | localhost |
| **Port**        | 1521 |
| **Service Name**| FREEPDB1 (ou la valeur de `DB_NAME` dans `.env`) |
| **Utilisateur** | gerai_user |
| **Mot de passe**| Celui défini dans votre `.env` local |

---

### Via les microservices Spring Boot

Le fichier `application.properties` de chaque service doit pointer vers les variables d'environnement :

```properties
spring.datasource.url=jdbc:oracle:thin:@localhost:1521/${DB_NAME}
spring.datasource.username=${DB_USER}
spring.datasource.password=${DB_PASSWORD}
````
## ⚠️ Règles de contribution

- **Immutabilité** : Ne modifiez jamais un fichier `V__` déjà commité. Flyway bloque la migration si le Checksum change. Créez toujours un nouveau fichier (ex: `V7__ajout_colonne.sql`).

- **UUIDs Keycloak** : Avant de lancer les seeds, assurez-vous de récupérer les vrais UUIDs de vos utilisateurs dans **Keycloak Admin Console** pour les reporter dans `S2__demo_employees.sql`.

- **Sécurité** : Le fichier `.env` est ignoré par Git. Ne forcez jamais son ajout au dépôt.

---

© 2026 GerAI Backend - Database Management Module