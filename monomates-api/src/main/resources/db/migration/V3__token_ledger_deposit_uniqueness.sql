-- Defense-in-depth invariant: a single deposit can never be awarded the same
-- ledger transaction type twice (e.g. two DEPOSIT_BASE rows for one deposit),
-- even if application-level idempotency checks were ever bypassed by a bug
-- or a future code path. Redemption-linked rows (deposit_id IS NULL) are
-- unaffected by this index.
CREATE UNIQUE INDEX uq_ledger_deposit_type
ON token_ledger(deposit_id, transaction_type)
WHERE deposit_id IS NOT NULL;
