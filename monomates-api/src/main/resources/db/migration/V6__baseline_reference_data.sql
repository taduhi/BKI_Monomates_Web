-- Baseline reference data that must exist regardless of Spring profile.
-- Unlike the synthetic demo dataset (fake users/deposits/ledger history,
-- seeded only under the `local` profile by DemoDataInitializer), the rows
-- below are real catalog/infrastructure configuration: without at least one
-- bin, one accepted item type and one device, the app has nothing to scan
-- and DepositProcessingService.processDemo() (used by both the local
-- simulate-deposit endpoint and the account-gated secret-sort endpoint)
-- cannot find a device to attach an event to. There is currently no admin
-- API to create accepted item types or devices, so a `prod` deployment with
-- zero rows here would be non-functional out of the box.
--
-- accepted_item_types.code is upserted (ON CONFLICT ... DO UPDATE ...
-- RETURNING) because on a database that already ran the local seeder before
-- this migration was introduced, CLEAR_PET_BOTTLE already exists; the local
-- seeder itself then reconciles this row to its own TSV values on every
-- startup, so this is purely to obtain the row's id either way, not to fix
-- its final field values.
WITH loc1 AS (
  INSERT INTO locations (id, name, address, latitude, longitude, created_at, updated_at)
  VALUES (gen_random_uuid(), 'MonoMates Demo Location A', '123 Demo Street, Ho Chi Minh City', 10.762622, 106.660172, now(), now())
  RETURNING id
), loc2 AS (
  INSERT INTO locations (id, name, address, latitude, longitude, created_at, updated_at)
  VALUES (gen_random_uuid(), 'MonoMates Demo Location B', '456 Demo Avenue, Ho Chi Minh City', 10.772622, 106.680172, now(), now())
  RETURNING id
), item AS (
  INSERT INTO accepted_item_types (id, code, name, description, base_tokens, bonus_tokens, active, created_at, updated_at)
  VALUES (gen_random_uuid(), 'CLEAR_PET_BOTTLE', 'Clear PET bottle', 'Empty, clear PET bottle accepted by the MonoMates pilot.', 1, 1, true, now(), now())
  ON CONFLICT (code) DO UPDATE SET code = EXCLUDED.code
  RETURNING id
), bin1 AS (
  INSERT INTO bins (id, public_code, name, location_id, status, capacity_percent, last_seen_at, created_at, updated_at)
  SELECT gen_random_uuid(), 'BIN-DEMO-001', 'MonoMates Demo Bin 1', loc1.id, 'ACTIVE', 10, now(), now(), now()
  FROM loc1
  RETURNING id
), bin2 AS (
  INSERT INTO bins (id, public_code, name, location_id, status, capacity_percent, last_seen_at, created_at, updated_at)
  SELECT gen_random_uuid(), 'BIN-DEMO-002', 'MonoMates Demo Bin 2', loc2.id, 'ACTIVE', 15, now(), now(), now()
  FROM loc2
  RETURNING id
), link1 AS (
  INSERT INTO bin_accepted_items (bin_id, item_type_id)
  SELECT bin1.id, item.id FROM bin1, item
), link2 AS (
  INSERT INTO bin_accepted_items (bin_id, item_type_id)
  SELECT bin2.id, item.id FROM bin2, item
), dev1 AS (
  INSERT INTO devices (id, bin_id, device_code, secret_hash, status, firmware_version, last_heartbeat_at, created_at, updated_at)
  SELECT gen_random_uuid(), bin1.id, 'DEV-DEMO-001', '$2a$12$NHKj77LnfjyzhEpH6pSNqeR0H1Ohx4Ra50tOvowWIyBEFI6TKzzKK', 'ACTIVE', '1.0.0', now(), now(), now()
  FROM bin1
), dev2 AS (
  INSERT INTO devices (id, bin_id, device_code, secret_hash, status, firmware_version, last_heartbeat_at, created_at, updated_at)
  SELECT gen_random_uuid(), bin2.id, 'DEV-DEMO-002', '$2a$12$mP3tZVK4yOko4oRcX7AfL.ACviozC2yhZm6C5pBsnIfTt4yyY4lRa', 'ACTIVE', '1.0.0', now(), now(), now()
  FROM bin2
)
INSERT INTO vouchers (
  id, partner_name, title, description, token_cost, inventory,
  valid_from, valid_until, status, image_url,
  terms_and_conditions, redemption_instructions, redemption_display_text,
  max_redemptions_per_user, created_at, updated_at
)
VALUES
  (gen_random_uuid(), 'MonoMates', 'Free Coffee Voucher', 'Redeem for one free coffee at a participating campus cafe.', 5, 50,
   now(), now() + interval '1 year', 'ACTIVE', NULL,
   NULL, 'Show the issued code at the partner counter.', 'Free Coffee Voucher',
   1, now(), now()),
  (gen_random_uuid(), 'MonoMates', 'Reusable Tote Bag', 'Redeem for one MonoMates reusable tote bag.', 10, 30,
   now(), now() + interval '1 year', 'ACTIVE', NULL,
   NULL, 'Show the issued code at the partner counter.', 'Reusable Tote Bag',
   1, now(), now());
