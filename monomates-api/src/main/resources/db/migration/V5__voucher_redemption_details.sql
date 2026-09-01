ALTER TABLE vouchers
  ADD COLUMN terms_and_conditions VARCHAR(2000),
  ADD COLUMN redemption_instructions VARCHAR(1000),
  ADD COLUMN redemption_display_text VARCHAR(300),
  ADD COLUMN max_redemptions_per_user INTEGER NOT NULL DEFAULT 1
    CHECK (max_redemptions_per_user > 0);

UPDATE vouchers
SET
  redemption_instructions = 'Show the issued code at the partner counter.',
  redemption_display_text = title
WHERE redemption_instructions IS NULL OR redemption_display_text IS NULL;

CREATE INDEX idx_redemptions_user_voucher
ON redemptions(user_id, voucher_id);
