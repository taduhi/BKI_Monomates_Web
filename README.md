# MonoMates — Plastique Recycling Reward Platform

MonoMates is a smart-bin recycling reward platform. A user finds a nearby bin, scans its QR
code to start a deposit session, inserts an accepted item, and earns **Plastique Tokens** once
the backend confirms a valid deposit. Tokens are redeemable for demo vouchers.

This repository holds **Prototype v0**: the full web stack (frontend + backend + database) for
the software loop — authentication, bins, QR/deposit sessions, reward ledger, activity,
vouchers, and admin management. Real hardware (IR sensor, ESP32-CAM, camera classification,
load cell) and the map view are **out of scope for this repository** and are integrated by a
separate team/module later; the backend's deposit-event flow is designed so that integration
does not require changing this web stack's contracts (see `project_docs/BKI_Monomates_web.md`).

## Repository layout

```text
.
├── monomates-api/        Spring Boot 4 + PostgreSQL backend (REST API)
├── monomates-frontend/   Vite multi-page vanilla JS/HTML/CSS frontend
├── project_docs/         Product/technical spec (BKI_Monomates_web.md)
└── structure.md          Full generated directory tree of this repository
```

See `structure.md` for the complete file tree, and each module's own `README.md` for
module-specific detail (`monomates-api/README.md`, `monomates-frontend/README.md`).

## Core principle

A QR scan alone never earns a token. The backend only awards tokens after a session-linked
deposit event is confirmed:

- QR scan only → **0 PT**
- Deposit event with no active session → **0 PT**
- Valid deposit → **+1 PT** (once per user/bin/day)
- Valid deposit **and** accepted clear PET bottle → **+2 PT** total

## Tech stack

| Layer | Stack |
|---|---|
| Frontend | Vite 8, vanilla JavaScript (ES modules), HTML, CSS — no framework |
| Backend | Java 21, Spring Boot 4, Spring Security (JWT via HttpOnly cookie + CSRF), Spring Data JPA |
| Database | PostgreSQL 17, schema managed by Flyway |
| Auth | JWT in an HttpOnly cookie, double-submit CSRF token, BCrypt password hashing |
| Dev data | Editable TSV-based synthetic dataset, seeded only under the `local` Spring profile |

The original product brief proposed React/Node/Express/MongoDB (see
`project_docs/BKI_Monomates_web.md` §42); this implementation intentionally keeps the
Vite/vanilla-JS + Spring Boot/PostgreSQL stack that was already in place, since it satisfies
every functional requirement without a costly framework rewrite. This was a deliberate,
user-confirmed decision — not an oversight.

## Quick start (local development)

You need **Node.js 18+** and **Docker Desktop**. If you'd rather run the backend directly
instead of through Docker (faster rebuild loop while editing backend code), you also need
**Java 21** and **Maven** — that's option B below.

1. **Start the backend**

   **Option A — fully containerized, no Java/Maven required (recommended if you're only
   running the app, not editing backend code):**

   ```bash
   cd monomates-api
   docker compose up -d --build
   ```

   This starts both PostgreSQL and the API in Docker. First run takes a minute to build the
   image. Verify: `http://localhost:8081/api/v1/status` and `http://localhost:8081/actuator/health`.

   **Option B — Postgres in Docker, API run directly (faster edit/rebuild loop):**

   ```bash
   cd monomates-api
   docker compose up -d postgres
   cp .env.example .env   # defaults work as-is for local Docker Postgres
   mvn spring-boot:run
   ```

   Verify: `http://localhost:8080/api/v1/status` and `http://localhost:8080/actuator/health`.

   Either way, demo accounts (local profile only) are ready to use:
   `user@monomates.local` / `User123!`, `admin@monomates.local` / `Admin123!`.
   Full details in `monomates-api/README.md`.

2. **Start the frontend**

   ```bash
   cd monomates-frontend
   npm install
   cp .env.example .env   # set VITE_API_BASE_URL to match how the backend is running
   npm run dev
   ```

   Open the printed local URL (typically `http://localhost:5173`).

   - Backend started with Option A (`docker compose`) → `VITE_API_BASE_URL=http://localhost:8081/api/v1`
   - Backend started with Option B (`mvn spring-boot:run`) → `VITE_API_BASE_URL=http://localhost:8080/api/v1`

3. **Run the whole flow without real hardware**

   Log in, open a bin, scan its QR (via the bin detail page), then use
   `POST /api/v1/testing/simulate-deposit` (documented in `monomates-api/README.md`) to simulate
   a deposit outcome and watch the reward, balance, and activity update live.

## Running tests

Backend integration tests boot a full Spring context against a **real, isolated** PostgreSQL
database — never the dev database above, and never a remote/shared database. See
"Running backend tests" in `monomates-api/README.md` for the one-time setup and the command to
run the suite.

```bash
cd monomates-frontend && npm run build   # frontend production build
cd monomates-api && mvn test             # backend suite (point DATABASE_URL at an isolated DB first)
```

## Security notes

- Never commit any `.env` file. Both `.gitignore` files in this repo (root and per-module)
  already exclude `.env*` except `.env.example`.
- Rotate any credential that was ever pasted into a chat/AI session before treating it as
  private again, even if it was never committed to a file.
- Production deployment requires `SPRING_PROFILES_ACTIVE=prod`, a strong random `JWT_SECRET`,
  and `COOKIE_SECURE=true` — the backend refuses to start under the `prod` profile otherwise.
  See "Production" in `monomates-api/README.md`.

## Project documentation

`project_docs/BKI_Monomates_web.md` is the original product/technical brief (v0 through v2) —
read it for the full reward-logic rules, database design, and the hardware event API/contract
(`POST /api/iot/deposit-events`, device auth, event payload shape) that a future
hardware-integration effort should target.

Internal development-process notes (multi-agent coordination guide, dated worklogs, planning
and audit notes) exist on the original development machine but are intentionally not published
here — they document how this prototype was built session-by-session and add no value to
someone running or continuing the project.
