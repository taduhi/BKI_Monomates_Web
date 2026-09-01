# MonoMates synthetic local dataset

These tab-separated files are loaded only when the Spring profile is `local`.
They are synthetic development data for frontend integration and proposal demonstrations. They are **not** evidence of real users, deployed bins, partner commitments, recycling performance, or sensor accuracy.

Files:
- `demo-users.tsv`: local login accounts and opening token adjustments.
- `demo-accepted-items.tsv`: accepted material policy. The current pilot accepts clear PET bottles only.
- `demo-bins.tsv`: illustrative Ho Chi Minh City bin locations, operational states, and simulated devices.
- `demo-vouchers.tsv`: synthetic reward catalogue.
- `demo-deposits.tsv`: relative-date deposit history used to populate activity and analytics screens.
- `demo-redemptions.tsv`: synthetic voucher spending history.

The initializer is idempotent: unique emails, bin codes, device codes, event IDs, and redemption codes prevent duplicates on normal restarts.
