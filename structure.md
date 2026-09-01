# Repository Structure

Full directory tree of what this repository publishes on GitHub (build artifacts,
dependency folders, local secrets, and internal AI-agent development-process docs
are excluded — see `.gitignore`). Enough is here to clone, run locally, and continue
development or hardware integration.

Excluded from this listing: `node_modules/`, `target/`, `dist/`, `.git/`, `.env` files,
and `project_docs/` process docs (worklogs, coordination notes, planning/audit notes —
kept only on the original development machine).

See `README.md` for what each top-level folder is for.

```text
.
├── monomates-api/
│   ├── dataset/
│   │   └── templates/
│   │       ├── README.md
│   │       ├── pilot-bin-inventory.csv
│   │       ├── pilot-data-dictionary.csv
│   │       ├── pilot-sensor-events.csv
│   │       └── pilot-vouchers.csv
│   ├── docs/
│   │   ├── API.md
│   │   └── DATA_STRATEGY.md
│   ├── requests/
│   │   └── monomates-api.http
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── com/
│   │   │   │       └── monomates/
│   │   │   │           └── api/
│   │   │   │               ├── admin/
│   │   │   │               │   ├── AdminAnalyticsController.java
│   │   │   │               │   ├── AdminBinController.java
│   │   │   │               │   ├── AdminTransactionController.java
│   │   │   │               │   └── AdminVoucherController.java
│   │   │   │               ├── auth/
│   │   │   │               │   ├── dto/
│   │   │   │               │   │   ├── AuthResponse.java
│   │   │   │               │   │   ├── LoginRequest.java
│   │   │   │               │   │   └── RegisterRequest.java
│   │   │   │               │   ├── AuthController.java
│   │   │   │               │   └── AuthService.java
│   │   │   │               ├── bin/
│   │   │   │               │   ├── dto/
│   │   │   │               │   │   ├── BinResponse.java
│   │   │   │               │   │   └── SaveBinRequest.java
│   │   │   │               │   ├── AcceptedItemType.java
│   │   │   │               │   ├── AcceptedItemTypeRepository.java
│   │   │   │               │   ├── BinController.java
│   │   │   │               │   ├── BinService.java
│   │   │   │               │   ├── BinStatus.java
│   │   │   │               │   ├── Location.java
│   │   │   │               │   ├── LocationRepository.java
│   │   │   │               │   ├── RecyclingBin.java
│   │   │   │               │   └── RecyclingBinRepository.java
│   │   │   │               ├── common/
│   │   │   │               │   ├── exception/
│   │   │   │               │   │   ├── ApiErrorResponse.java
│   │   │   │               │   │   ├── BusinessRuleException.java
│   │   │   │               │   │   ├── ConflictException.java
│   │   │   │               │   │   ├── GlobalExceptionHandler.java
│   │   │   │               │   │   ├── NotFoundException.java
│   │   │   │               │   │   └── UnauthorizedException.java
│   │   │   │               │   ├── model/
│   │   │   │               │   │   └── BaseEntity.java
│   │   │   │               │   └── web/
│   │   │   │               │       └── HealthController.java
│   │   │   │               ├── config/
│   │   │   │               │   └── DemoDataInitializer.java
│   │   │   │               ├── deposit/
│   │   │   │               │   ├── dto/
│   │   │   │               │   │   ├── DepositResponse.java
│   │   │   │               │   │   └── SessionResponse.java
│   │   │   │               │   ├── Deposit.java
│   │   │   │               │   ├── DepositProcessingService.java
│   │   │   │               │   ├── DepositProperties.java
│   │   │   │               │   ├── DepositRepository.java
│   │   │   │               │   ├── DepositSession.java
│   │   │   │               │   ├── DepositSessionController.java
│   │   │   │               │   ├── DepositSessionRepository.java
│   │   │   │               │   ├── DepositSessionService.java
│   │   │   │               │   ├── DepositStatus.java
│   │   │   │               │   ├── SessionStatus.java
│   │   │   │               │   └── UserDepositController.java
│   │   │   │               ├── device/
│   │   │   │               │   ├── dto/
│   │   │   │               │   │   ├── DeviceDepositEventRequest.java
│   │   │   │               │   │   └── HeartbeatRequest.java
│   │   │   │               │   ├── Device.java
│   │   │   │               │   ├── DeviceController.java
│   │   │   │               │   ├── DeviceEvent.java
│   │   │   │               │   ├── DeviceEventRepository.java
│   │   │   │               │   ├── DeviceRepository.java
│   │   │   │               │   ├── DeviceService.java
│   │   │   │               │   ├── DeviceStatus.java
│   │   │   │               │   └── EventProcessingStatus.java
│   │   │   │               ├── reward/
│   │   │   │               │   ├── dto/
│   │   │   │               │   │   ├── TokenBalanceResponse.java
│   │   │   │               │   │   └── TokenLedgerResponse.java
│   │   │   │               │   ├── RewardController.java
│   │   │   │               │   ├── RewardService.java
│   │   │   │               │   ├── TokenLedgerEntry.java
│   │   │   │               │   ├── TokenLedgerRepository.java
│   │   │   │               │   └── TokenTransactionType.java
│   │   │   │               ├── security/
│   │   │   │               │   ├── ApiRateLimitFilter.java
│   │   │   │               │   ├── AuthProperties.java
│   │   │   │               │   ├── CookieBearerTokenResolver.java
│   │   │   │               │   ├── CurrentUserService.java
│   │   │   │               │   ├── JwtConfiguration.java
│   │   │   │               │   ├── JwtService.java
│   │   │   │               │   ├── RateLimitProperties.java
│   │   │   │               │   └── SecurityConfiguration.java
│   │   │   │               ├── testing/
│   │   │   │               │   ├── DatasetSummaryController.java
│   │   │   │               │   └── LocalDemoController.java
│   │   │   │               ├── user/
│   │   │   │               │   ├── dto/
│   │   │   │               │   │   ├── UpdateProfileRequest.java
│   │   │   │               │   │   └── UserResponse.java
│   │   │   │               │   ├── UserAccount.java
│   │   │   │               │   ├── UserController.java
│   │   │   │               │   ├── UserRepository.java
│   │   │   │               │   ├── UserRole.java
│   │   │   │               │   └── UserStatus.java
│   │   │   │               ├── voucher/
│   │   │   │               │   ├── dto/
│   │   │   │               │   │   ├── RedemptionResponse.java
│   │   │   │               │   │   ├── SaveVoucherRequest.java
│   │   │   │               │   │   └── VoucherResponse.java
│   │   │   │               │   ├── Redemption.java
│   │   │   │               │   ├── RedemptionController.java
│   │   │   │               │   ├── RedemptionRepository.java
│   │   │   │               │   ├── RedemptionStatus.java
│   │   │   │               │   ├── Voucher.java
│   │   │   │               │   ├── VoucherController.java
│   │   │   │               │   ├── VoucherRepository.java
│   │   │   │               │   ├── VoucherService.java
│   │   │   │               │   └── VoucherStatus.java
│   │   │   │               └── MonoMatesApiApplication.java
│   │   │   └── resources/
│   │   │       ├── dataset/
│   │   │       │   ├── README.md
│   │   │       │   ├── demo-accepted-items.tsv
│   │   │       │   ├── demo-bins.tsv
│   │   │       │   ├── demo-deposits.tsv
│   │   │       │   ├── demo-redemptions.tsv
│   │   │       │   ├── demo-users.tsv
│   │   │       │   └── demo-vouchers.tsv
│   │   │       ├── db/
│   │   │       │   └── migration/
│   │   │       │       ├── V1__initial_schema.sql
│   │   │       │       ├── V2__one_active_session_per_bin.sql
│   │   │       │       ├── V3__token_ledger_deposit_uniqueness.sql
│   │   │       │       ├── V4__token_ledger_redemption_uniqueness.sql
│   │   │       │       └── V5__voucher_redemption_details.sql
│   │   │       ├── application-local.yml
│   │   │       ├── application-prod.yml
│   │   │       └── application.yml
│   │   └── test/
│   │       └── java/
│   │           └── com/
│   │               └── monomates/
│   │                   └── api/
│   │                       ├── ApiRateLimitFilterTest.java
│   │                       ├── ArchitectureSmokeTest.java
│   │                       ├── AuthFlowTest.java
│   │                       ├── DemoDatasetResourceTest.java
│   │                       ├── DepositSessionExpiryTest.java
│   │                       ├── DepositSessionFlowTest.java
│   │                       ├── DeviceDepositEventExpiryTest.java
│   │                       ├── DeviceDepositEventFlowTest.java
│   │                       ├── EndToEndFlowTest.java
│   │                       └── VoucherRedemptionFlowTest.java
│   ├── .env.example
│   ├── .gitignore
│   ├── Dockerfile
│   ├── README.md
│   ├── compose.neon.yaml
│   ├── compose.yaml
│   └── pom.xml
├── monomates-frontend/
│   ├── pages/
│   │   ├── activity/
│   │   │   └── index.html
│   │   ├── admin/
│   │   │   ├── bins.html
│   │   │   ├── transactions.html
│   │   │   └── vouchers.html
│   │   ├── auth/
│   │   │   └── login.html
│   │   ├── bins/
│   │   │   ├── detail.html
│   │   │   └── index.html
│   │   ├── deposit/
│   │   │   └── session.html
│   │   ├── profile/
│   │   │   └── index.html
│   │   └── rewards/
│   │       └── index.html
│   ├── public/
│   │   ├── manifest.webmanifest
│   │   └── robots.txt
│   ├── src/
│   │   ├── assets/
│   │   │   ├── fonts/
│   │   │   │   └── .gitkeep
│   │   │   ├── icons/
│   │   │   │   └── .gitkeep
│   │   │   └── images/
│   │   │       ├── bins/
│   │   │       │   └── .gitkeep
│   │   │       ├── illustrations/
│   │   │       │   └── .gitkeep
│   │   │       ├── logo/
│   │   │       │   └── monomates-logo.png
│   │   │       └── rewards/
│   │   │           └── .gitkeep
│   │   ├── scripts/
│   │   │   ├── api/
│   │   │   │   ├── admin-api.js
│   │   │   │   ├── api-client.js
│   │   │   │   ├── auth-api.js
│   │   │   │   ├── bins-api.js
│   │   │   │   ├── deposit-api.js
│   │   │   │   ├── profile-api.js
│   │   │   │   └── rewards-api.js
│   │   │   ├── components/
│   │   │   │   ├── loading.js
│   │   │   │   ├── modal.js
│   │   │   │   ├── toast.js
│   │   │   │   └── token-balance.js
│   │   │   ├── guards/
│   │   │   │   ├── admin-guard.js
│   │   │   │   └── auth-guard.js
│   │   │   ├── pages/
│   │   │   │   ├── activity-page.js
│   │   │   │   ├── admin-bins-page.js
│   │   │   │   ├── admin-transactions-page.js
│   │   │   │   ├── admin-vouchers-page.js
│   │   │   │   ├── bin-detail-page.js
│   │   │   │   ├── bins-page.js
│   │   │   │   ├── deposit-session-page.js
│   │   │   │   ├── login-page.js
│   │   │   │   ├── profile-page.js
│   │   │   │   └── rewards-page.js
│   │   │   ├── utils/
│   │   │   │   ├── auth-intent.js
│   │   │   │   ├── date.js
│   │   │   │   ├── dom.js
│   │   │   │   ├── format.js
│   │   │   │   └── storage.js
│   │   │   ├── app.js
│   │   │   └── config.js
│   │   └── styles/
│   │       ├── base/
│   │       │   ├── reset.css
│   │       │   ├── typography.css
│   │       │   └── variables.css
│   │       ├── components/
│   │       │   ├── buttons.css
│   │       │   ├── cards.css
│   │       │   ├── forms.css
│   │       │   ├── loading.css
│   │       │   ├── modal.css
│   │       │   ├── navigation.css
│   │       │   ├── table.css
│   │       │   └── toast.css
│   │       ├── layouts/
│   │       │   ├── admin-layout.css
│   │       │   ├── app-layout.css
│   │       │   └── auth-layout.css
│   │       ├── pages/
│   │       │   ├── activity.css
│   │       │   ├── admin.css
│   │       │   ├── bin-detail.css
│   │       │   ├── bins.css
│   │       │   ├── deposit.css
│   │       │   ├── login.css
│   │       │   ├── profile.css
│   │       │   └── rewards.css
│   │       └── main.css
│   ├── .env.example
│   ├── .gitignore
│   ├── README.md
│   ├── index.html
│   ├── package-lock.json
│   ├── package.json
│   └── vite.config.js
├── project_docs/
│   └── BKI_Monomates_web.md
├── .gitignore
├── README.md
└── structure.md
```
