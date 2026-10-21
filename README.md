# Il Mio Garage - Backend REST API

Backend REST production-grade per **Il Mio Garage**, applicazione mobile React Native / Expo per la gestione di account, veicoli e storico interventi di manutenzione svolti.

---

## 1. Stack Tecnologico

- **Java 21** (JDK LTS)
- **Spring Boot 3.3.5**
  - **Spring Web** (MVC REST JSON UTF-8)
  - **Spring Security** (Autenticazione stateless con JWT, BCrypt, RBAC, Rate Limiting)
  - **Spring Data JPA** & **Hibernate 6** (Dialetto PostgreSQL, `ddl-auto=validate`)
  - **Bean Validation** (Jakarta Validation per DTO e vincoli di business)
- **PostgreSQL & Neon** (Pienamente compatibile con Neon serverless, SSL require, connection pool HikariCP)
- **Flyway** (Migrazioni database versionate)
- **JJWT 0.12.6** (Firma e validazione crittografica HMAC-SHA256, token rotation e blacklist)
- **springdoc-openapi 2.6.0** (OpenAPI 3.0 / Swagger UI)
- **JUnit 5, Mockito & AssertJ** (Test unitari e di integrazione end-to-end con MockMvc)
- **H2 in PostgreSQL mode** (Test integrati a zero dipendenze esterne con schema Flyway)

---

## 2. Struttura del Progetto

Il codice è organizzato per moduli/feature con netta separazione delle responsabilità:

```text
com.ilmiogarage
├── config/                  # Configurazioni Spring (Security, CORS, OpenAPI, Jackson)
├── controller/              # Controller REST (Auth, User, Vehicle, Maintenance, Dashboard, Cost)
├── dto/                     # Data Transfer Objects immutabili (Records)
│   ├── auth/                # RegisterRequest, LoginRequest, AuthResponse, etc.
│   ├── common/              # PagedResponse, ApiErrorResponse, FieldErrorDto
│   ├── cost/                # CostSummaryResponse, CostByTypeResponse
│   ├── dashboard/           # DashboardResponse
│   ├── maintenance/         # CreateMaintenanceRequest, MaintenanceResponse, etc.
│   ├── user/                # UserResponse, UpdateUserRequest
│   └── vehicle/             # CreateVehicleRequest, VehicleResponse, etc.
├── exception/               # Eccezioni applicative e GlobalExceptionHandler (RFC 7807)
├── mapper/                  # Mapper tra Entity e DTO (User, Vehicle, Maintenance)
├── model/
│   ├── entity/              # Entità JPA (User, RefreshToken, Vehicle, MaintenanceRecord)
│   └── enums/               # Enums di dominio (VehicleIcon, MaintenanceType)
├── repository/              # Spring Data JPA Repositories con query ottimizzate e indici
├── security/                # Filtri JWT, Rate Limiting, UserPrincipal, SecurityUtils
└── service/                 # Logica di business transazionale (Auth, User, Vehicle, Maintenance, Cost, Dashboard)
```

---

## 3. Prerequisiti

- **JDK 21** o superiore installato (`java -version`).
- **Maven 3.9+** oppure lo script wrapper incluso `mvnw.cmd` / `./mvnw`.
- **PostgreSQL 14+** (locale o istanza cloud **Neon**).

---

## 4. Variabili d'Ambiente e Configurazione Neon

Tutte le credenziali e i parametri di runtime sono gestiti tramite variabili d'ambiente. È disponibile il template [`.env.example`](file:///.env.example).

### Tabella Variabili d'Ambiente

| Variabile | Descrizione | Default / Esempio |
|---|---|---|
| `PORT` | Porta HTTP del server | `8080` |
| `DB_URL` | URL JDBC PostgreSQL | `jdbc:postgresql://localhost:5432/mygarage` |
| `DB_USERNAME` | Utente PostgreSQL | `postgres` |
| `DB_PASSWORD` | Password PostgreSQL | `postgres` |
| `DB_POOL_MAX` | Connessioni massime pool HikariCP | `10` |
| `DB_POOL_MIN` | Connessioni minime idle pool HikariCP | `2` |
| `JWT_SECRET` | Chiave segreta HMAC SHA-256 (min 256 bit / 32 caratteri o Base64) | *stringa random sicura* |
| `JWT_EXPIRATION_SECONDS` | Durata access token JWT in secondi | `900` (15 minuti) |
| `JWT_REFRESH_EXPIRATION_SECONDS` | Durata refresh token in secondi | `2592000` (30 giorni) |
| `CORS_ALLOWED_ORIGINS` | Origini permesse CORS separate da virgola | `http://localhost:8081,http://localhost:19006` |

### Connessione a PostgreSQL su Neon

Neon richiede connessioni crittografate SSL. Per collegarsi a un database Neon:
1. Crea un database su [Neon.tech](https://neon.tech).
2. Copia la Connection String JDBC e assicurati che contenga il parametro `sslmode=require`.
3. Esempio di configurazione:
   ```bash
   DB_URL=jdbc:postgresql://ep-sweet-mountain-123456-pooler.eu-central-1.aws.neon.tech/neondb?sslmode=require
   DB_USERNAME=neondb_owner
   DB_PASSWORD=npg_xxxxxxxxxxxx
   ```
4. All'avvio dell'applicazione, **Flyway** applicherà automaticamente le migrazioni creando le tabelle e gli indici ottimizzati. In produzione viene usato `spring.jpa.hibernate.ddl-auto=validate` per prevenire alterazioni non controllate dello schema.

---

## 5. Compilazione, Test e Avvio Locale

### Esecuzione Test

Per eseguire l'intera suite di test unitari e di integrazione:

```bash
# Windows
.\mvnw.cmd test

# Linux / macOS
./mvnw test
```

### Avvio in Sviluppo con File .env

Il file [`pom.xml`](file:///pom.xml) è configurato con `properties-maven-plugin` e `spring-boot-maven-plugin` per leggere automaticamente le variabili dal file `.env` (se presente nella root) e iniettarle come proprietà di sistema durante il comando `spring-boot:run`. Inoltre, [`MyGarageApplication.java`](file:///src/main/java/com/ilmiogarage/MyGarageApplication.java) supporta il caricamento trasparente di `.env` anche in caso di avvio diretto da IDE o da file JAR.

1. Crea o copia il file `.env` a partire da `.env.example`:
   ```bash
   cp .env.example .env
   ```
2. Modifica i parametri desiderati in `.env` (es. `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, ecc.).
3. Avvia l'applicazione con Maven:
   ```bash
   # Windows
   .\mvnw.cmd spring-boot:run

   # Linux / macOS
   ./mvnw spring-boot:run
   ```
   *Maven caricherà automaticamente le proprietà definite in `.env` senza necessità di impostarle manualmente nella sessione del terminale.*

### Packaging ed Esecuzione JAR

```bash
# Compilazione ed impacchettamento del fat JAR eseguibile
.\mvnw.cmd package -DskipTests

# Avvio del JAR
java -jar target/my-garage-backend-1.0.0.jar
```

---

## 6. Documentazione OpenAPI / Swagger UI

Quando il server è in esecuzione, la documentazione interattiva è accessibile a:

- **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI 3.0 JSON**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

Per testare gli endpoint protetti su Swagger UI:
1. Esegui la registrazione (`/api/v1/auth/register`) o il login (`/api/v1/auth/login`).
2. Copia l'`accessToken`.
3. Clicca sul pulsante **Authorize** in alto a destra e inserisci il token.

---

## 7. Esempi cURL Completi

Tutti i comandi seguenti possono essere eseguiti direttamente da terminale o PowerShell.

### 7.1 Registrazione nuovo utente

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Marco Rossi",
    "email": "marco.rossi@example.com",
    "password": "PasswordSicura123"
  }'
```

*Risposta `201 Created`:*
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "wOqH8F0_f9f0zJ-...",
  "tokenType": "Bearer",
  "expiresInSeconds": 900,
  "user": {
    "id": "c1389ea5-fba5-4201-9fbd-52467d0d0c3e",
    "name": "Marco Rossi",
    "email": "marco.rossi@example.com",
    "createdAt": "2026-10-20T17:30:00Z"
  }
}
```

### 7.2 Login utente

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "marco.rossi@example.com",
    "password": "PasswordSicura123"
  }'
```

### 7.3 Lettura profilo utente autenticato

```bash
curl -X GET http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer <ACCESS_TOKEN>"
```

### 7.4 Creazione nuovo veicolo

```bash
curl -X POST http://localhost:8080/api/v1/vehicles \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "icon": "carFront",
    "brand": "BMW",
    "model": "Serie 1",
    "year": 2021,
    "plate": "AB123CD",
    "mileage": 68420
  }'
```

*Risposta `201 Created`:*
```json
{
  "id": "e2da19f3-d0ea-44a6-bfe0-cf52e85ab821",
  "icon": "carFront",
  "brand": "BMW",
  "model": "Serie 1",
  "year": 2021,
  "plate": "AB123CD",
  "mileage": 68420,
  "createdAt": "2026-10-20T17:31:00Z",
  "updatedAt": "2026-10-20T17:31:00Z",
  "lastMaintenance": null
}
```

### 7.5 Lista veicoli con ultima manutenzione

```bash
curl -X GET http://localhost:8080/api/v1/vehicles \
  -H "Authorization: Bearer <ACCESS_TOKEN>"
```

### 7.6 Dettaglio singolo veicolo

```bash
curl -X GET http://localhost:8080/api/v1/vehicles/e2da19f3-d0ea-44a6-bfe0-cf52e85ab821 \
  -H "Authorization: Bearer <ACCESS_TOKEN>"
```

### 7.7 Registrazione manutenzione svolta (con sync km automatico)

```bash
curl -X POST http://localhost:8080/api/v1/vehicles/e2da19f3-d0ea-44a6-bfe0-cf52e85ab821/maintenances \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "type": "OIL_CHANGE",
    "date": "2026-10-15",
    "mileage": 70000,
    "cost": 115.50,
    "notes": "Olio sintetico 5W-30 e filtro",
    "customDescription": null
  }'
```

*Nota: poiché 70.000 km > 68.420 km, il veicolo viene aggiornato atomicamente a 70.000 km.*

### 7.8 Registrazione manutenzione con tipo `OTHER`

```bash
curl -X POST http://localhost:8080/api/v1/vehicles/e2da19f3-d0ea-44a6-bfe0-cf52e85ab821/maintenances \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "type": "OTHER",
    "customDescription": "Sostituzione specchietto laterale",
    "date": "2026-10-18",
    "mileage": 70200,
    "cost": 85.00,
    "notes": null
  }'
```

### 7.9 Modifica manutenzione esistente

```bash
curl -X PATCH http://localhost:8080/api/v1/maintenances/<MAINTENANCE_ID> \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "cost": 90.00,
    "notes": "Note aggiornate dopo la fattura"
  }'
```

### 7.10 Riepilogo Dashboard Home

```bash
curl -X GET http://localhost:8080/api/v1/dashboard \
  -H "Authorization: Bearer <ACCESS_TOKEN>"
```

### 7.11 Riepilogo Costi complessivi e per anno

```bash
curl -X GET "http://localhost:8080/api/v1/costs/summary?year=2026" \
  -H "Authorization: Bearer <ACCESS_TOKEN>"
```

*Risposta `200 OK`:*
```json
{
  "currency": "EUR",
  "totalSpent": 200.50,
  "year": 2026,
  "yearSpent": 200.50,
  "byType": [
    {
      "type": "OIL_CHANGE",
      "typeLabel": "Cambio olio",
      "count": 1,
      "total": 115.50
    },
    {
      "type": "OTHER",
      "typeLabel": "Sostituzione specchietto laterale",
      "count": 1,
      "total": 85.00
    }
  ],
  "recentMaintenances": [...]
}
```

### 7.12 Refresh token (con token rotation)

```bash
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "<REFRESH_TOKEN_ATTUALE>"
  }'
```

### 7.13 Logout (revoca sessione e token)

```bash
curl -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "<REFRESH_TOKEN_ATTUALE>"
  }'
```

---

## 8. Regole di Business e Sicurezza Implementate

1. **Chilometraggio Veicolo**:
   - Quando una manutenzione viene creata con `mileage` maggiore del chilometraggio attuale del veicolo, il veicolo viene aggiornato automaticamente al nuovo chilometraggio all'interno della medesima transazione.
   - Manutenzioni storiche (con km inferiore) non riducono il chilometraggio del veicolo.
   - La modifica o l'eliminazione di un intervento di manutenzione non decrementa mai il chilometraggio del veicolo (il chilometraggio registrato rappresenta l'ultimo valore noto).
   - L'aggiornamento esplicito del chilometraggio tramite `PATCH /api/v1/vehicles/{id}` valida strettamente `mileage >= 0`.

2. **Tipologie di Manutenzione e Descrizioni**:
   - Supportate: `OIL_CHANGE`, `SERVICE`, `BRAKES`, `TIRE_CHANGE`, `WIPER_CHANGE`, `INSPECTION`, `BATTERY`, `TIMING_BELT`, `OTHER`.
   - Se `type == OTHER`: `customDescription` è obbligatoria (1-100 caratteri). Nelle risposte `typeLabel` restituisce la descrizione personalizzata.
   - Se `type != OTHER`: `customDescription` deve essere `null` o assente; `typeLabel` restituisce l'etichetta italiana corrispondente.

3. **Targa Veicolo**:
   - Univoca **per proprietario**, non a livello globale (due utenti diversi possono inserire la stessa targa).
   - Normalizzata automaticamente rimuovendo spazi esterni e convertendo in maiuscolo.

4. **Errori RFC 7807 (ProblemDetail esteso)**:
   - Tutte le risposte di errore restituiscono lo schema stabile con campi `type`, `title`, `status`, `detail`, `code`, `instance`, `fieldErrors` e `traceId`.
   - Codici applicativi: `VALIDATION_ERROR`, `UNAUTHORIZED`, `EMAIL_ALREADY_EXISTS`, `RESOURCE_NOT_FOUND`, `PLATE_ALREADY_EXISTS`, `INVALID_MAINTENANCE_TYPE`, `INVALID_DATE`, `INTERNAL_ERROR`.
   - Non vengono mai esposte stack trace o dettagli interni al client.

5. **Protezione Dati & IDOR**:
   - `ownerId` ricavato sempre ed esclusivamente dal token JWT validato.
   - Risorse appartenenti ad altri utenti o inesistenti restituiscono sempre `404 Not Found` (senza rivelare l'esistenza della risorsa a terzi).

6. **Cancellazione a Cascata Transazionale**:
   - L'eliminazione di un veicolo cancella in modo transazionale tutti i suoi interventi di manutenzione associati, prevenendo record orfani.

---

## 9. Risultati dei Test

Tutti i **36 test** (unitari e di integrazione) sono stati eseguiti e superati con successo:

```text
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.ilmiogarage.integration.AuthIntegrationTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.ilmiogarage.integration.DashboardAndCostIntegrationTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.ilmiogarage.integration.MaintenanceIntegrationTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.ilmiogarage.integration.OwnershipAndSecurityIntegrationTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.ilmiogarage.integration.UserIntegrationTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.ilmiogarage.integration.VehicleAndCascadeIntegrationTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.ilmiogarage.integration.VehicleIntegrationTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.ilmiogarage.service.CostServiceTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.ilmiogarage.service.MaintenanceServiceTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.ilmiogarage.service.VehicleServiceTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.ilmiogarage.validation.ValidationAndEnumsTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] Results:
[INFO] Tests run: 36, Failures: 0, Errors: 0, Skipped: 0
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

### Mappatura Casi di Test Obbligatori

| # | Requisito Obbligatorio | Classe e Metodo di Test | Esito |
|---|---|---|:---:|
| 1 | Un utente non può leggere o modificare veicoli/manutenzioni di un altro | `OwnershipAndSecurityIntegrationTest.userCannotAccessOtherUserVehicle`, `userCannotAccessOtherUserMaintenance` | **SUPERATO** |
| 2 | Registrazione con email duplicata fallisce con 409 `EMAIL_ALREADY_EXISTS` | `AuthIntegrationTest.register_duplicateEmail_failsWith409` | **SUPERATO** |
| 3 | Nuova manutenzione con km superiore aggiorna il veicolo | `MaintenanceIntegrationTest.newMaintenance_higherKm_updatesVehicleKm`, `MaintenanceServiceTest.createMaintenance_higherMileage_updatesVehicle` | **SUPERATO** |
| 4 | Manutenzione storica con km inferiore non riduce il km corrente | `MaintenanceIntegrationTest.historicalMaintenance_lowerKm_doesNotReduceVehicleKm`, `MaintenanceServiceTest.createMaintenance_lowerMileage_doesNotDecreaseVehicle` | **SUPERATO** |
| 5 | Modifica o cancellazione manutenzione non riduce il km corrente | `MaintenanceIntegrationTest.updateOrDeleteMaintenance_doesNotReduceCurrentMileage`, `MaintenanceServiceTest.deleteMaintenance_doesNotDecreaseVehicleMileage` | **SUPERATO** |
| 6 | Costo totale e aggregazioni per anno/tipo sono calcolati dai record | `DashboardAndCostIntegrationTest.dashboardAndCostSummary_calculatedFromRecords`, `CostServiceTest.getCostSummary_aggregatesAccurately` | **SUPERATO** |
| 7 | `OTHER` senza descrizione non è accettato (400 `VALIDATION_ERROR`) | `MaintenanceIntegrationTest.otherWithoutDescription_rejected`, `MaintenanceServiceTest.createMaintenance_otherWithoutDescription_throwsValidationException` | **SUPERATO** |
| 8 | Date impossibili (future o < 1900) e valori negativi non accettati | `MaintenanceIntegrationTest.invalidDateAndNegativeValues_rejected`, `ValidationAndEnumsTest.*` | **SUPERATO** |
| 9 | Cancellazione veicolo elimina i suoi interventi in modo transazionale | `VehicleAndCascadeIntegrationTest.deleteVehicle_cascadesMaintenances` | **SUPERATO** |
| 10 | Sort, filtri e paginazione non consentono accesso cross-user | `OwnershipAndSecurityIntegrationTest.paginationAndFilters_doNotLeakCrossUserData` | **SUPERATO** |
