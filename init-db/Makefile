# ═══════════════════════════════════════════════════════════
#  GerAI — database/Makefile (Version Dynamique)
# ═══════════════════════════════════════════════════════════

# Charger les variables du fichier .env
ifneq (,$(wildcard ./.env))
    include .env
    export
endif

.PHONY: db-start db-stop db-migrate db-reset db-logs db-connect db-status db-setup-user

# Commande SQLPlus utilisant les variables du .env
SQLPLUS_SYS = docker exec -i gerai-oracle sqlplus sys/$(ORACLE_ROOT_PASSWORD)@localhost:1521/$(DB_NAME) as sysdba

db-start:
	@echo "🚀 Démarrage d'Oracle Free 23ai..."
	docker-compose up -d oracle
	@echo "⏳ Attente du démarrage complet (check docker ps)..."

db-setup-user:
	@echo "👤 Configuration de l'utilisateur $(DB_USER)..."
	@echo "CREATE USER $(DB_USER) IDENTIFIED BY $(DB_PASSWORD); \
	 GRANT CONNECT, RESOURCE, DBA TO $(DB_USER); \
	 GRANT UNLIMITED TABLESPACE TO $(DB_USER); \
	 EXIT;" | $(SQLPLUS_SYS)
	@echo "✅ Utilisateur $(DB_USER) prêt."

db-migrate:
	@echo "🏗️ Lancement des migrations Flyway..."
	docker-compose up flyway
	@echo "✅ Migrations terminées."

db-init:
	@$(MAKE) db-start
	@echo "💤 Sommeil de 90s pour l'initialisation système..."
	sleep 90
	@$(MAKE) db-setup-user
	@$(MAKE) db-migrate
	@echo "✨ Base GerAI initialisée avec succès."

db-stop:
	@echo "🛑 Arrêt des services..."
	docker-compose down

db-reset:
	@echo "⚠️ Suppression totale des données et des volumes !"
	docker-compose down -v
	@$(MAKE) db-init

db-logs:
	docker-compose logs -f oracle

db-connect:
	@echo "🔌 Connexion à la base $(DB_NAME) via SQL*Plus..."
	docker exec -it gerai-oracle sqlplus $(DB_USER)/$(DB_PASSWORD)@localhost:1521/$(DB_NAME)

db-status:
	docker-compose ps