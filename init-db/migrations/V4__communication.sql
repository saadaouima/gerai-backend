-- ═══════════════════════════════════════════════════════════
--  GerAI — V4__communication.sql
--  Domaine : Communication (Chat + Notifications)
--  Tables  : CONVERSATIONS, CONVERSATION_PARTICIPANTS,
--             MESSAGES, MESSAGE_READS,
--             NOTIFICATIONS, EMAIL_LOGS
-- ═══════════════════════════════════════════════════════════

-- ─── CONVERSATIONS ──────────────────────────────────────────
CREATE TABLE CONVERSATIONS (
                               conversation_id NUMBER         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                               type            VARCHAR2(20)   DEFAULT 'DIRECT' NOT NULL
                                   CONSTRAINT ck_conv_type
                                   CHECK (type IN ('DIRECT','GROUPE','ANNONCE')),
                               name            VARCHAR2(200),
                               description     VARCHAR2(500),
                               created_by      NUMBER         NOT NULL,
                               last_message_at TIMESTAMP,
                               is_active       NUMBER(1)      DEFAULT 1 NOT NULL
                                   CONSTRAINT ck_conv_active CHECK (is_active IN (0,1)),
                               created_at      TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
                               CONSTRAINT fk_conv_creator FOREIGN KEY (created_by) REFERENCES EMPLOYEES(employee_id)
);

-- ─── CONVERSATION_PARTICIPANTS ───────────────────────────────
CREATE TABLE CONVERSATION_PARTICIPANTS (
                                           participant_id  NUMBER         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                           conversation_id NUMBER         NOT NULL,
                                           employee_id     NUMBER         NOT NULL,
                                           role            VARCHAR2(20)   DEFAULT 'MEMBRE'
                                   CONSTRAINT ck_cp_role
                                   CHECK (role IN ('ADMIN','MEMBRE')),
                                           joined_at       TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
                                           left_at         TIMESTAMP,
                                           is_muted        NUMBER(1)      DEFAULT 0
                                   CONSTRAINT ck_cp_muted CHECK (is_muted IN (0,1)),
                                           last_read_at    TIMESTAMP,
                                           CONSTRAINT fk_cp_conv FOREIGN KEY (conversation_id) REFERENCES CONVERSATIONS(conversation_id),
                                           CONSTRAINT fk_cp_emp  FOREIGN KEY (employee_id)     REFERENCES EMPLOYEES(employee_id),
                                           CONSTRAINT uk_cp_conv_emp UNIQUE (conversation_id, employee_id)
);

-- ─── MESSAGES ───────────────────────────────────────────────
CREATE TABLE MESSAGES (
                          message_id      NUMBER         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                          conversation_id NUMBER         NOT NULL,
                          sender_id       NUMBER         NOT NULL,
                          content         CLOB           NOT NULL,
                          type            VARCHAR2(30)   DEFAULT 'TEXTE'
                                   CONSTRAINT ck_msg_type
                                   CHECK (type IN ('TEXTE','IMAGE','FICHIER','SYSTEME')),
                          attachment_url  VARCHAR2(500),
                          reply_to_id     NUMBER,                        -- auto-référence (réponse à un message)
                          is_deleted      NUMBER(1)      DEFAULT 0
                                   CONSTRAINT ck_msg_deleted CHECK (is_deleted IN (0,1)),
                          sent_at         TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
                          edited_at       TIMESTAMP,
                          CONSTRAINT fk_msg_conv     FOREIGN KEY (conversation_id) REFERENCES CONVERSATIONS(conversation_id),
                          CONSTRAINT fk_msg_sender   FOREIGN KEY (sender_id)       REFERENCES EMPLOYEES(employee_id),
                          CONSTRAINT fk_msg_reply_to FOREIGN KEY (reply_to_id)     REFERENCES MESSAGES(message_id)
);

-- ─── MESSAGE_READS ───────────────────────────────────────────
CREATE TABLE MESSAGE_READS (
                               read_id     NUMBER         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                               message_id  NUMBER         NOT NULL,
                               employee_id NUMBER         NOT NULL,
                               read_at     TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
                               CONSTRAINT fk_mr_message FOREIGN KEY (message_id)  REFERENCES MESSAGES(message_id),
                               CONSTRAINT fk_mr_emp     FOREIGN KEY (employee_id) REFERENCES EMPLOYEES(employee_id),
                               CONSTRAINT uk_mr_msg_emp UNIQUE (message_id, employee_id)
);

-- ─── NOTIFICATIONS ───────────────────────────────────────────
CREATE TABLE NOTIFICATIONS (
                               notification_id NUMBER         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                               employee_id     NUMBER,                        -- null = broadcast par rôle
                               type            VARCHAR2(50)   NOT NULL
                                   CONSTRAINT ck_notif_type
                                   CHECK (type IN ('NOUVELLE_DEMANDE','DEMANDE_APPROUVEE',
                                                   'DEMANDE_REJETEE','NOUVEAU_MESSAGE',
                                                   'RAPPEL','INFO','SYSTEME')),
                               title           VARCHAR2(200)  NOT NULL,
                               content         CLOB,
                               reference_type  VARCHAR2(50),                  -- DEMANDE, MESSAGE, PROJET...
                               reference_id    NUMBER,
                               action_url      VARCHAR2(500),
                               is_read         NUMBER(1)      DEFAULT 0 NOT NULL
                                   CONSTRAINT ck_notif_read CHECK (is_read IN (0,1)),
                               read_at         TIMESTAMP,
                               triggered_by    NUMBER,
                               created_at      TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
                               CONSTRAINT fk_notif_emp     FOREIGN KEY (employee_id)  REFERENCES EMPLOYEES(employee_id),
                               CONSTRAINT fk_notif_trigger FOREIGN KEY (triggered_by) REFERENCES EMPLOYEES(employee_id)
);

-- ─── EMAIL_LOGS ──────────────────────────────────────────────
CREATE TABLE EMAIL_LOGS (
                            log_id         NUMBER         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                            employee_id    NUMBER,
                            email_type     VARCHAR2(60)   NOT NULL,
                            recipient      VARCHAR2(255)  NOT NULL,
                            subject        VARCHAR2(300)  NOT NULL,
                            body_preview   VARCHAR2(500),
                            template_key   VARCHAR2(60),
                            reference_type VARCHAR2(50),
                            reference_id   NUMBER,
                            status         VARCHAR2(20)   DEFAULT 'ENVOYE' NOT NULL
                                  CONSTRAINT ck_email_status
                                  CHECK (status IN ('ENVOYE','ECHOUE','EN_ATTENTE')),
                            error_message  VARCHAR2(500),
                            sent_at        TIMESTAMP,
                            created_at     TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
                            CONSTRAINT fk_email_emp FOREIGN KEY (employee_id) REFERENCES EMPLOYEES(employee_id)
);

-- ─── INDEX ──────────────────────────────────────────────────
CREATE INDEX idx_msg_conv       ON MESSAGES(conversation_id, sent_at DESC);
CREATE INDEX idx_msg_sender     ON MESSAGES(sender_id);
CREATE INDEX idx_notif_emp      ON NOTIFICATIONS(employee_id, is_read, created_at DESC);
CREATE INDEX idx_notif_type     ON NOTIFICATIONS(type);
CREATE INDEX idx_cp_conv        ON CONVERSATION_PARTICIPANTS(conversation_id);
CREATE INDEX idx_cp_emp         ON CONVERSATION_PARTICIPANTS(employee_id);
CREATE INDEX idx_email_emp      ON EMAIL_LOGS(employee_id, created_at DESC);
CREATE INDEX idx_email_status   ON EMAIL_LOGS(status);