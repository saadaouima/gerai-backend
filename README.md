# GerAI Backend

Backend app for HR management system built with Spring Boot.

## Tech Stack

| Layer | Technology                     |
|---|--------------------------------|
| Language | Java 25                        |
| Framework | Spring Boot 3.x                |
| Database | Oracle Free 23c                |
| ORM | Hibernate / Spring Data JPA    |
| Identity | Keycloak 26.5.5                |
| Security | Spring Security + OAuth2 / JWT |
| Containerization | Docker + Docker Compose        |

## Architecture

```
Postman / Frontend
        │
        ▼
Spring Boot (port 8081)
        │
        ├──► Oracle DB   (port 1521)  — stores HR data
        └──► Keycloak    (port 8080)  — stores identity & roles
```

---

## Prerequisites

Make sure you have the following installed:

- [Docker Desktop](https://www.docker.com/products/docker-desktop)
- [Java 21+](https://adoptium.net/)
- [Maven 3.9+](https://maven.apache.org/)
- [IntelliJ IDEA](https://www.jetbrains.com/idea/) (recommended)
- [Postman](https://www.postman.com/) (for testing)

---

### Getting Started

1. Clone the Repository

    **git clone https://github.com/saadaouima/gerai-backend.git**

    **cd gerai-backend**

### 2. Create `.env` File

```bash
cp .env.example .env
```

Open `.env` and fill in your values:
> See **Gmail App Password Setup** section below for `MAIL_PASSWORD`.

---

### 3. Start Oracle and Keycloak with Docker

```bash
docker compose up -d
```


### 4. Setup Keycloak

Open Keycloak Admin Console:
```
http://localhost:8080/admin
Login: admin / your_keycloak_admin_password
```

#### Create Realm

```
Left dropdown → Create realm
Name: grh-realm
Enabled: ON
Click Create
```

#### Create Client

```
grh-realm → Clients → Create client
Client ID         : grh-backend
Client Protocol   : openid-connect
Click Next

Client authentication : ON
Authorization         : OFF
Direct Access Grants  : ON  ← important for Postman testing
Click Save
```

Copy the **Client Secret**:
```
grh-backend → Credentials tab → Client Secret → copy and paste into .env KC_CLIENT_SECRET
```

#### Create Client Roles

```
grh-backend → Roles tab → Create role (repeat for each):
  ✅ admin
  ✅ employees:read
  ✅ employees:write
  ✅ employees:update
  ✅ employees:delete
```

   Create Client:

        grh-realm → Clients → Create client
        Client ID         : grh-backend
        Client Protocol   : openid-connect
        Click Next
        
        Client authentication : ON
        Authorization         : OFF
        Direct Access Grants  : ON  ← important for Postman testing
        Click Save
   
   Copy the Client Secret:
   
         grh-backend → Credentials tab → Client Secret → copy and paste into .env KC_CLIENT_SECRET

   Create Client Roles:

   grh-backend → Roles tab → Create role (repeat for each):

           ✅ employees:read
           ✅ employees:write
           ✅ employees:update
           ✅ employees:delete

#### Create HR Manager User

```
grh-realm → Users → Create user
Username      : hr.manager
Email         : hr@company.com
First name    : HR
Last name     : Manager
Email verified: ON
Click Create

→ Credentials tab → Set password
Password  : YOUR_PASSWORD
Temporary : OFF

→ Role mappings tab
→ Client roles → grh-backend
→ Assign "admin" role
```

---

### 5. Setup Oracle Database

Connect to Oracle using SQL Developer or any Oracle client:

```
Host     : localhost
Port     : 1521
Service  : FREEPDB1
Username : your_db_username
Password : your_db_password
```

Run the migration script:

```sql
-- Create employees table
CREATE TABLE employees (
    id               VARCHAR2(36)   PRIMARY KEY,
    first_name       VARCHAR2(50)   NOT NULL,
    last_name        VARCHAR2(50)   NOT NULL,
    email            VARCHAR2(100)  NOT NULL UNIQUE,
    hire_date        DATE,
    job_title        VARCHAR2(100),
    salary           NUMBER(10, 2),
    keycloak_user_id VARCHAR2(36)   UNIQUE
);
```

---

### 6. Run the Spring Boot App

In IntelliJ:
```
Run → Run 'GeraiApplication'
```

Or via terminal:
```bash
mvn spring-boot:run
```

App starts at:
```
http://localhost:8081/api
```

---

## API Endpoints

Base URL: `http://localhost:8081/api`

All endpoints require:
```
Authorization: Bearer {access_token}
```

| Method | Endpoint | Role Required | Description |
|---|---|---|---|
| `POST` | `/employees` | `admin` or `employees:write` | Create employee + Keycloak user |
| `GET` | `/employees` | `admin` or `employees:read` | Get all employees |
| `GET` | `/employees/{id}` | `admin` or `employees:read` | Get employee by ID |
| `GET` | `/employees/search?email=` | `admin` or `employees:read` | Search by email |
| `PUT` | `/employees/{id}` | `admin` or `employees:update` | Update employee |
| `DELETE` | `/employees/{id}` | `admin` or `employees:delete` | Delete employee + Keycloak user |
| `PUT` | `/employees/{id}/change-password` | `admin` | Change employee password |

---

## Testing with Postman

### Step 1 — Get Access Token

```
Method  : POST
URL     : http://localhost:8080/realms/grh-realm/protocol/openid-connect/token
Headers : Content-Type: application/x-www-form-urlencoded

Body (x-www-form-urlencoded):
  client_id     : grh-backend
  client_secret : YOUR_CLIENT_SECRET
  username      : hr.manager
  password      : YOUR_PASSWORD
  grant_type    : password
```

Copy the `access_token` from the response.

---

### Step 2 — Create Employee

```
Method  : POST
URL     : http://localhost:8081/api/employees
Headers :
  Content-Type  : application/json
  Authorization : Bearer {access_token}

Body:
{
    "username"  : "john.doe",
    "firstName" : "John",
    "lastName"  : "Doe",
    "email"     : "john.doe@company.com",
    "hireDate"  : "2024-03-12",
    "jobTitle"  : "Software Engineer",
    "salary"    : 7500
}
```

Expected response — **201 Created**:
```json
{
    "id"                : "a3f1c2d4-...",
    "firstName"         : "John",
    "lastName"          : "Doe",
    "email"             : "john.doe@company.com",
    "hireDate"          : "2024-03-12",
    "jobTitle"          : "Software Engineer",
    "salary"            : 75000.00,
    "keycloakUserId"    : "b7e2d1a3-...",
    "temporaryPassword" : "EXAMPLE_PASSWORD"
}
```

> The `temporaryPassword` is shown **once only** — send it to the employee via email.

---

### Step 3 — Get All Employees

```
Method  : GET
URL     : http://localhost:8081/api/employees
Headers : Authorization: Bearer {access_token}
```

---

### Step 4 — Get Employee By ID

```
Method  : GET
URL     : http://localhost:8081/api/employees/{id}
Headers : Authorization: Bearer {access_token}
```

---

### Step 5 — Search By Email

```
Method  : GET
URL     : http://localhost:8081/api/employees/search?email=john.doe@company.com
Headers : Authorization: Bearer {access_token}
```

---

### Step 6 — Update Employee

```
Method  : PUT
URL     : http://localhost:8081/api/employees/{id}
Headers :
  Content-Type  : application/json
  Authorization : Bearer {access_token}

Body:
{
    "username"  : "john.doe",
    "firstName" : "John",
    "lastName"  : "Doe",
    "email"     : "john.doe@company.com",
    "hireDate"  : "2024-03-12",
    "jobTitle"  : "Senior Software Engineer",
    "salary"    : 95000.00
}
```

---

### Step 7 — Delete Employee

```
Method  : DELETE
URL     : http://localhost:8081/api/employees/{id}
Headers : Authorization: Bearer {access_token}
```

Expected → **204 No Content**

Verify in Keycloak:
```
http://localhost:8080/admin → grh-realm → Users
→ Employee should no longer exist ✅
```

---

---

## Employee First Login Flow

```
Employee receives email with temporary password
        │
        ▼
Opens browser → http://localhost:8080/realms/grh-realm/account
        │
        ▼
Enters username + temporary password
        │
        ▼
Keycloak forces password change
        │
        ▼
Employee sets new permanent password
        │
        ▼
Login complete ✅
```

---

## Gmail App Password Setup

Required for sending temporary passwords via email:

```
1. Go to   → https://myaccount.google.com/security
2. Enable  → 2-Step Verification
3. Go to   → https://myaccount.google.com/apppasswords
4. Name    → grh-backend
5. Click   → Create
6. Copy    → the 16-character password
7. Paste   → into .env MAIL_PASSWORD