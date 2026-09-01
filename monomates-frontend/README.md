# MonoMates Frontend

Vite multi-page frontend connected to the MonoMates Spring Boot API.

## Run locally

1. Install dependencies with `npm install`.
2. Copy `.env.example` to `.env` and set `VITE_API_BASE_URL`.
3. Run `npm run dev`.

Use `http://localhost:8081/api/v1` when the backend runs through its Docker
Compose file, or port `8080` when Spring Boot runs directly.

## Implemented web scope

- Public static bin list and bin detail, browser geolocation distance, and directions.
- Login/signup with resumable QR/deposit intent.
- Server-authoritative deposit-session polling.
- Real token balance, grouped activity, vouchers, redemption history, and profile data.
- Admin bin, voucher, and transaction management backed by live APIs.
- Role-aware navigation: admin links are removed for guests and normal users.
- Mobile-first responsive layouts and explicit loading/empty/error states.

The root URL redirects to the bin finder, which is the Prototype v0 home
screen. Real maps, sensors, cameras, and load-cell integration are outside this
web repository's current scope.

## Commands

```bash
npm run dev
npm run build
npm audit
```

`dist/`, `node_modules/`, and all `.env` files are ignored by Git.
