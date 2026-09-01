# MonoMates API endpoints

Base URL: `/api/v1`

Every unsafe method (POST/PATCH/PUT/DELETE) except `/device/**` requires a CSRF token: fetch `GET /auth/csrf` (it returns `{ "token": "..." }` and sets the `XSRF-TOKEN` cookie), then send that token back as the `X-XSRF-TOKEN` header on the mutating request, alongside the auth cookie.

## Public

| Method | Path | Purpose |
|---|---|---|
| GET | `/status` | API status |
| GET | `/auth/csrf` | Issue a CSRF token (also sets the `XSRF-TOKEN` cookie) |
| POST | `/auth/register` | Register and set auth cookie |
| POST | `/auth/login` | Login and set auth cookie |
| POST | `/auth/logout` | Clear auth cookie |
| GET | `/bins` | List bins |
| GET | `/bins/{publicCode}` | Bin details |
| GET | `/vouchers` | Available vouchers |
| POST | `/device/events/deposit` | Hardware deposit event |
| POST | `/device/heartbeat` | Device heartbeat |

## Authenticated user

| Method | Path | Purpose |
|---|---|---|
| GET | `/auth/me` | Current user |
| GET/PATCH | `/users/me` | Profile |
| POST | `/bins/{publicCode}/sessions` | Start 60-second session |
| GET | `/sessions/{sessionId}` | Session status |
| POST | `/sessions/{sessionId}/cancel` | Cancel session |
| GET | `/users/me/deposits` | Deposit history |
| GET | `/users/me/token-ledger` | Token history |
| GET | `/users/me/token-balance` | Token balance |
| GET | `/users/me/redemptions` | Issued voucher codes and redemption history |
| POST | `/vouchers/{voucherId}/redeem` | Redeem voucher |
| POST | `/testing/simulate-deposit` | Local demo simulation |
| GET | `/testing/dataset-summary` | Synthetic local dataset counts |

## ADMIN

| Method | Path | Purpose |
|---|---|---|
| GET/POST | `/admin/bins` | List/create bins |
| PATCH | `/admin/bins/{id}` | Update a bin |
| GET/POST | `/admin/vouchers` | List/create vouchers |
| PATCH | `/admin/vouchers/{id}` | Update a voucher |
| GET | `/admin/transactions` | Ledger report |
| GET | `/admin/analytics/summary` | Pilot summary |

Voucher create/update payloads support validity dates, image URL, terms,
redemption display text, redemption instructions, and a per-user redemption
limit. Successful redemptions return a unique demo code rather than a shared
code.

Web responses include `X-RateLimit-Limit`; rejected requests return HTTP 429
and `Retry-After`. `/device/**` is intentionally outside this web rate limiter.
