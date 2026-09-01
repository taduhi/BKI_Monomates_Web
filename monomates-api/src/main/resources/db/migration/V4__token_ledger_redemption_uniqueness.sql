-- Mirrors V3's deposit-side invariant on the redemption side: a single
-- redemption can never be deducted from a user's balance twice, even if an
-- application-level bug ever tried to write a second REDEMPTION ledger row
-- for the same redemption. Deposit-linked rows (redemption_id IS NULL) are
-- unaffected by this index.
CREATE UNIQUE INDEX uq_ledger_redemption
ON token_ledger(redemption_id)
WHERE redemption_id IS NOT NULL;
