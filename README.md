# Internal Wallet Service

A high-traffic-ready wallet service for application-specific credits (e.g. Gold Coins, reward points). Tracks balances with a **double-entry ledger**, **idempotent** operations, and **deadlock-safe** locking.

---

## Quick Start

### Option 1: Docker (recommended)

```bash
docker-compose up -d
```

- **PostgreSQL**: `localhost:5432` (user: `wallet`, password: `wallet123`, DB: `walletdb`)
- **App**: http://localhost:8080  
- Migrations and seed data run automatically on startup (Flyway).

### Option 2: Local database + Spring Boot

1. Start PostgreSQL and create a database (e.g. `walletdb`).
2. Set connection in `src/main/resources/application.properties` (or env):
   - `spring.datasource.url`, `username`, `password`
3. Run the app:
   ```bash
   ./mvnw spring-boot:run
   ```
4. Flyway will create schema and apply seed data.

### Seed data (what you get)

- **Asset types**: Gold Coins, Diamonds, Loyalty Points  
- **System wallets**: Treasury (id=1), Bonus Pool (id=2), Revenue (id=3) for Gold Coins  
- **Users**: `userId=1` and `userId=2` with **Gold Coins** wallets and **initial balances** (500 and 300 respectively)

To re-seed only data on an existing DB (schema already applied), you can run `seed.sql` manually against the DB.

---

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/wallet/topup` | User purchases credits (Treasury → user). |
| `POST` | `/wallet/bonus` | System grants free credits (Bonus Pool → user). |
| `POST` | `/wallet/spend` | User spends credits (user → Revenue). |
| `GET`  | `/wallet/{userId}/balance` | Get user balance (sum of ledger entries). |

### Request body (topup / bonus / spend)

```json
{
  "userId": 1,
  "amount": 100,
  "idempotencyKey": "unique-key-per-operation"
}
```

- **idempotencyKey**: Required. Reusing the same key returns the same transaction id and does not apply the operation again.

### Examples

```bash
# Balance (user 1 has 500 after seed)
curl http://localhost:8080/wallet/1/balance

# Top-up 100 (assume payment already processed)
curl -X POST http://localhost:8080/wallet/topup -H "Content-Type: application/json" \
  -d '{"userId":1,"amount":100,"idempotencyKey":"pay-001"}'

# Spend 30
curl -X POST http://localhost:8080/wallet/spend -H "Content-Type: application/json" \
  -d '{"userId":1,"amount":30,"idempotencyKey":"spend-001"}'

# Balance again (500 + 100 - 30 = 570)
curl http://localhost:8080/wallet/1/balance
```

---

## Technology Choices

- **Java 21 + Spring Boot 3** – Mature ecosystem, strong transaction and concurrency support.  
- **PostgreSQL** – ACID, robust locking, and serializable options.  
- **JPA / Spring Data JPA** – For clear entity mapping and `PESSIMISTIC_WRITE` locking.  
- **Flyway** – Versioned schema and seed data; reproducible setup.  
- **Docker + docker-compose** – One command to run DB and app with migrations.

---

## Concurrency and Data Integrity

### 1. Double-entry ledger

- We do **not** only update a balance column. Every movement is stored as **ledger entries** (debit/credit) linked to a **transaction**.
- Each operation creates one transaction and two ledger rows (source debit, destination credit). Balances are derived as `SUM(amount)` per wallet, so the history is auditable and consistent.

### 2. Idempotency

- Every mutation request requires an **idempotency key**.  
- Before creating a new transaction we look up by this key. If a transaction already exists, we return its id and **do not** create new ledger entries.  
- Duplicate requests (e.g. retries) therefore do not double-credit or double-debit.

### 3. Race conditions and locking

- Wallets involved in a transfer are locked with **pessimistic write locks** (`SELECT ... FOR UPDATE`) so two concurrent operations on the same wallet are serialized.
- **Deadlock avoidance**: we always lock the two wallets in **ascending wallet id order** (e.g. lock smaller id first, then larger). This global ordering prevents circular wait and thus deadlocks between two-wallet operations.

### 4. Transaction boundary

- Each operation runs inside a single **database transaction** (`@Transactional`). Either all ledger entries and the transaction record are committed, or none are, so we never leave partial or inconsistent state.

---

## Project layout

- `src/main/resources/db/migration/` – Flyway: `V1__init.sql` (schema), `V2__seed_data.sql` (asset types + initial balances).
- `seed.sql` – Optional standalone seed script; Flyway already applies equivalent data.
- `Dockerfile` + `docker-compose.yml` – Run app and PostgreSQL with migrations on startup.

---

## Deliverables checklist

- **Source code** – This repo.  
- **seed.sql / setup** – `seed.sql` plus Flyway migrations for DB init and seed.  
- **README** – Setup, tech choices, and concurrency strategy (above).  
- **Double-entry ledger** – Implemented.  
- **Idempotency** – Via `idempotency_key` on transactions.  
- **Deadlock avoidance** – Lock ordering by wallet id.  
- **Containerization** – Dockerfile and docker-compose included.
