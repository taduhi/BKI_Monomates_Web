# MonoMates API

Spring Boot + PostgreSQL backend for the MonoMates smart recycling prototype.

## Included

- HttpOnly-cookie JWT registration, login, logout, and role authorization
- Double-submit CSRF protection (`GET /auth/csrf` + `X-XSRF-TOKEN` header) on every unsafe request except `/device/**`
- User profile APIs
- Recycling-bin and accepted-item APIs
- 60-second QR deposit sessions with one session per user/bin/day
- Device authentication, heartbeat, deposit events, and duplicate-event prevention
- Reward ledger: valid deposit `+1 PT`; accepted clear PET bottle `+1 PT` bonus
- Deposit history, token balance, voucher catalogue, and transactional redemption
- Persistent redemption history with unique per-redemption demo codes
- Voucher instructions, validity windows, inventory, and per-user redemption limits
- ADMIN bin, voucher, transaction, and analytics APIs
- Per-IP web API rate limiting (device routes remain independent)
- Flyway database schema
- Editable TSV-based synthetic local dataset
- Simulated deposit endpoint for development before hardware is available

## Requirements

- Java 21
- Maven
- Docker Desktop or PostgreSQL

## Start locally

```bash
docker compose up -d postgres
mvn spring-boot:run
```

Check:

```text
http://localhost:8080/api/v1/status
http://localhost:8080/actuator/health
```

Frontend environment:

```env
VITE_API_BASE_URL=http://localhost:8080/api/v1
```

The frontend API client must use `credentials: "include"`.

When the API is started through this repository's `compose.yaml`, it is exposed
on port `8081`, so use `VITE_API_BASE_URL=http://localhost:8081/api/v1`.

## Synthetic local dataset

When the `local` profile starts, `DemoDataInitializer` reads:

```text
src/main/resources/dataset/
├── demo-users.tsv
├── demo-accepted-items.tsv
├── demo-bins.tsv
├── demo-vouchers.tsv
├── demo-deposits.tsv
└── demo-redemptions.tsv
```

It supplies:

- 6 synthetic accounts;
- 4 illustrative smart bins in Ho Chi Minh City;
- 4 simulated devices;
- clear PET as the only accepted pilot item;
- 3 synthetic voucher types;
- 26 historical deposit outcomes;
- 4 voucher redemptions and matching token-ledger records.

The records are synthetic and must not be presented as real deployment results.

Disable local seeding with:

```text
DEMO_DATA_ENABLED=false
```

## Demo accounts (`local` profile only)

```text
User:     user@monomates.local / User123!
Admin:    admin@monomates.local / Admin123!
Operator: operator@monomates.local / Operator123!
```

The three hidden sort controls use this separate account:

```text
Secret controls: demo@monomates.app / monomates1
```

Override its email with `DEMO_SECRET_ACCOUNT_EMAIL` when needed.

Simulated devices:

```text
DEV-HCMUT-001  / demo-device-secret-hcmut
DEV-YOUTH-001  / demo-device-secret-youth
DEV-D10-001    / demo-device-secret-d10
DEV-LIBRARY-001 / demo-device-secret-library
```

## Test a new deposit without hardware

1. Login as the demo user.
2. Start a session at a bin the user has not used today:

```http
POST /api/v1/bins/BIN-HCMUT-001/sessions
```

3. Use the returned `sessionId`:

```http
POST /api/v1/testing/simulate-deposit
Content-Type: application/json

{
  "sessionId": "<session-id>",
  "outcome": "ACCEPTED_PET"
}
```

Outcomes:

```text
ACCEPTED_PET    +2 PT
VALID_UNCERTAIN +1 PT
REJECTED         0 PT
```

## Reset the local database

```bash
docker compose down -v
docker compose up -d postgres
mvn spring-boot:run
```

## Running backend tests

Integration tests boot the full Spring context against a real PostgreSQL database and must not run against the dev database used above (they register throwaway users, bins, and vouchers). Create a separate database once:

```bash
docker exec <postgres-container> psql -U monomates -d postgres -c "CREATE DATABASE monomates_test OWNER monomates;"
```

Then point `DATABASE_URL` at it for test runs:

```bash
DATABASE_URL=jdbc:postgresql://localhost:5432/monomates_test mvn test
```

Never point automated tests at a Neon (or other shared/remote) database — apply Flyway migrations and smoke-test Neon manually only after the full local/isolated suite passes.

## Production

Set `SPRING_PROFILES_ACTIVE=prod`, a strong `JWT_SECRET` (at least 32 bytes), database credentials, the production `FRONTEND_ORIGIN`, and `COOKIE_SECURE=true`. The app refuses to start under the `prod` profile when the JWT secret is still the published development default or secure cookies are disabled. The local dataset initializer and testing controller are disabled outside the local profile. If the frontend and backend are not deployed on the same site, review whether `SameSite=Lax` cookies still reach the API from cross-site requests before launch.

Web rate limits default to 30 login/register attempts and 300 other API requests
per remote address per minute. Override them with
`AUTH_RATE_LIMIT_PER_MINUTE` and `API_RATE_LIMIT_PER_MINUTE`. Device routes are
excluded because the hardware integration owns its own authentication and
traffic policy.

See:

- `docs/API.md` for endpoints;
- `docs/DATA_STRATEGY.md` for dataset behavior and pilot-data planning;
- `dataset/templates/` for future real-pilot CSV schemas;
- `requests/monomates-api.http` for ready-to-run requests.
