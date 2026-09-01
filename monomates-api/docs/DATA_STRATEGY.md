# MonoMates data strategy

## 1. What is implemented now

The backend contains a **synthetic local dataset** so development can continue before a real pilot dataset exists. It is loaded only when the active Spring profile is `local`.

The dataset provides:

- 6 synthetic accounts: users, administrator, and bin operator;
- 1 accepted item policy: `CLEAR_PET_BOTTLE`;
- 4 illustrative Ho Chi Minh City smart-bin records;
- 4 simulated hardware devices;
- 3 synthetic voucher types;
- 26 historical deposit outcomes;
- 4 historical voucher redemptions;
- token-ledger entries that exactly follow the MonoMates reward rules.

The synthetic deposit outcomes are:

- `ACCEPTED_PET`: valid physical event, clear PET accepted, **+2 PT total**;
- `VALID_UNCERTAIN`: valid physical event but uncertain classification, **+1 PT**;
- `REJECTED`: invalid physical event, **0 PT**.

These records are for UI integration, API testing, analytics demonstrations, and the Dow proposal. They are **not real pilot results** and must not be presented as measured user behavior, deployed-bin performance, partner commitments, or classifier accuracy.

## 2. Dataset files

The editable source files are located at:

```text
src/main/resources/dataset/
├── README.md
├── demo-users.tsv
├── demo-accepted-items.tsv
├── demo-bins.tsv
├── demo-vouchers.tsv
├── demo-deposits.tsv
└── demo-redemptions.tsv
```

They use tab-separated values so addresses and descriptions may contain commas without complicated escaping.

`DemoDataInitializer` reads these resources and inserts only missing records. Duplicate prevention uses the same identifiers as the application:

- user email;
- accepted-item code;
- bin public code;
- device code;
- device event ID;
- one user/bin/local-date session;
- voucher title;
- redemption code.

Historical dates are relative to the current date and interpreted in `Asia/Ho_Chi_Minh`.

After changing an existing user password, voucher inventory, or historical row in the TSV files, reset the local database volume so the dataset is rebuilt from a clean schema. Normal application restarts do not duplicate the synthetic events.

## 3. Enable or disable the dataset

The local profile enables the dataset by default:

```yaml
app:
  demo-data:
    enabled: ${DEMO_DATA_ENABLED:true}
```

Disable it without changing code:

```text
DEMO_DATA_ENABLED=false
```

The initializer is annotated with `@Profile("local")`, so it does not run under the `prod` profile.

## 4. Reset the local dataset

Flyway creates the schema. The initializer then loads the local data.

For a completely clean Docker database:

```bash
docker compose down -v
docker compose up -d postgres
mvn spring-boot:run
```

On Windows, use your installed Maven command or the Maven wrapper from your Spring Initializr project.

## 5. Demo accounts

```text
User
user@monomates.local
User123!

Administrator
admin@monomates.local
Admin123!

Operator
operator@monomates.local
Operator123!
```

Additional synthetic users use `Demo123!`.

## 6. Real pilot data later

The root folder `dataset/templates/` contains CSV templates for future collection:

```text
dataset/templates/
├── pilot-sensor-events.csv
├── pilot-bin-inventory.csv
├── pilot-vouchers.csv
└── pilot-data-dictionary.csv
```

When hardware is available, the operational path remains:

```text
ESP32/device
    -> POST /api/v1/device/events/deposit
    -> device_events
    -> deposit_sessions
    -> deposits
    -> token_ledger
```

Recommended real-pilot fields include:

- unique device event ID;
- device and bin code;
- device timestamp;
- IR detection result;
- weight change;
- classifier label and confidence;
- model version;
- optional image object-storage key;
- later human-review label.

Do not store image bytes directly in PostgreSQL. Store images in object storage and keep only the object key or URL in the database.

## 7. Data-governance boundary

Before collecting real pilot data, define:

- user consent and privacy notice;
- retention period;
- who can view raw sensor or image evidence;
- device-clock synchronization;
- data-quality checks;
- rules for correcting false rewards;
- separation between synthetic, test, and real pilot records.
