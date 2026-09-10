-- V6 seeded two clearly-labelled placeholder bins ("MonoMates Demo Bin 1/2"
-- at "123/456 Demo Street") purely so a fresh prod deploy had something to
-- scan. Replace them with the same real-looking Ho Chi Minh City locations
-- already used throughout local development (dataset/demo-bins.tsv), so a
-- first-time visitor sees genuine-looking bins instead of an obvious
-- "demo" watermark.
--
-- Every insert below is upserted by its natural key (public_code /
-- device_code), matching how DemoDataInitializer already reconciles this
-- exact same data under the local profile — safe to run on a database where
-- these bins already exist (local/test) as well as one where they don't
-- (a fresh prod database).
DELETE FROM token_ledger
WHERE deposit_id IN (
  SELECT d.id FROM deposits d
  JOIN deposit_sessions s ON d.session_id = s.id
  JOIN bins b ON s.bin_id = b.id
  WHERE b.public_code IN ('BIN-DEMO-001', 'BIN-DEMO-002')
);

DELETE FROM deposits
WHERE session_id IN (
  SELECT s.id FROM deposit_sessions s
  JOIN bins b ON s.bin_id = b.id
  WHERE b.public_code IN ('BIN-DEMO-001', 'BIN-DEMO-002')
);

DELETE FROM bin_accepted_items
WHERE bin_id IN (SELECT id FROM bins WHERE public_code IN ('BIN-DEMO-001', 'BIN-DEMO-002'));

DELETE FROM deposit_sessions
WHERE bin_id IN (SELECT id FROM bins WHERE public_code IN ('BIN-DEMO-001', 'BIN-DEMO-002'));

DELETE FROM device_events
WHERE device_id IN (SELECT id FROM devices WHERE device_code IN ('DEV-DEMO-001', 'DEV-DEMO-002'));

DELETE FROM devices
WHERE device_code IN ('DEV-DEMO-001', 'DEV-DEMO-002');

DELETE FROM bins
WHERE public_code IN ('BIN-DEMO-001', 'BIN-DEMO-002');

DELETE FROM locations
WHERE name IN ('MonoMates Demo Location A', 'MonoMates Demo Location B');

WITH item AS (
  SELECT id FROM accepted_item_types WHERE code = 'CLEAR_PET_BOTTLE'
),
loc_hcmut AS (
  INSERT INTO locations (id, name, address, latitude, longitude, created_at, updated_at)
  VALUES (gen_random_uuid(), 'HCMUT Campus', '268 Ly Thuong Kiet, District 10, Ho Chi Minh City', 10.772100, 106.657900, now(), now())
  RETURNING id
),
loc_youth AS (
  INSERT INTO locations (id, name, address, latitude, longitude, created_at, updated_at)
  VALUES (gen_random_uuid(), 'Youth Cultural House', '4 Pham Ngoc Thach, District 1, Ho Chi Minh City', 10.783600, 106.695300, now(), now())
  RETURNING id
),
loc_d10 AS (
  INSERT INTO locations (id, name, address, latitude, longitude, created_at, updated_at)
  VALUES (gen_random_uuid(), 'District 10 Community Pilot Site', 'District 10, Ho Chi Minh City', 10.774200, 106.667700, now(), now())
  RETURNING id
),
loc_library AS (
  INSERT INTO locations (id, name, address, latitude, longitude, created_at, updated_at)
  VALUES (gen_random_uuid(), 'Ho Chi Minh City General Sciences Library', '69 Ly Tu Trong, District 1, Ho Chi Minh City', 10.775600, 106.699600, now(), now())
  RETURNING id
),
bin_hcmut AS (
  INSERT INTO bins (id, public_code, name, location_id, status, capacity_percent, last_seen_at, created_at, updated_at)
  SELECT gen_random_uuid(), 'BIN-HCMUT-001', 'HCMUT Smart Bin A', loc_hcmut.id, 'ACTIVE', 24, now(), now(), now()
  FROM loc_hcmut
  ON CONFLICT (public_code) DO NOTHING
  RETURNING id
),
bin_youth AS (
  INSERT INTO bins (id, public_code, name, location_id, status, capacity_percent, last_seen_at, created_at, updated_at)
  SELECT gen_random_uuid(), 'BIN-YOUTH-001', 'Youth Cultural House Bin', loc_youth.id, 'ACTIVE', 58, now(), now(), now()
  FROM loc_youth
  ON CONFLICT (public_code) DO NOTHING
  RETURNING id
),
bin_d10 AS (
  INSERT INTO bins (id, public_code, name, location_id, status, capacity_percent, last_seen_at, created_at, updated_at)
  SELECT gen_random_uuid(), 'BIN-D10-001', 'District 10 Community Bin', loc_d10.id, 'MAINTENANCE', 42, now(), now(), now()
  FROM loc_d10
  ON CONFLICT (public_code) DO NOTHING
  RETURNING id
),
bin_library AS (
  INSERT INTO bins (id, public_code, name, location_id, status, capacity_percent, last_seen_at, created_at, updated_at)
  SELECT gen_random_uuid(), 'BIN-LIBRARY-001', 'City Library Smart Bin', loc_library.id, 'FULL', 96, now(), now(), now()
  FROM loc_library
  ON CONFLICT (public_code) DO NOTHING
  RETURNING id
),
link_hcmut AS (
  INSERT INTO bin_accepted_items (bin_id, item_type_id)
  SELECT bin_hcmut.id, item.id FROM bin_hcmut, item
),
link_youth AS (
  INSERT INTO bin_accepted_items (bin_id, item_type_id)
  SELECT bin_youth.id, item.id FROM bin_youth, item
),
link_d10 AS (
  INSERT INTO bin_accepted_items (bin_id, item_type_id)
  SELECT bin_d10.id, item.id FROM bin_d10, item
),
link_library AS (
  INSERT INTO bin_accepted_items (bin_id, item_type_id)
  SELECT bin_library.id, item.id FROM bin_library, item
),
dev_hcmut AS (
  INSERT INTO devices (id, bin_id, device_code, secret_hash, status, firmware_version, last_heartbeat_at, created_at, updated_at)
  SELECT gen_random_uuid(), bin_hcmut.id, 'DEV-HCMUT-001', '$2a$12$Vv.YjM9AsqoOGw710CZ/D.ZMrm1.xxCJAGY/w99uHupmYh7160qrS', 'ACTIVE', 'demo-1.1', now(), now(), now()
  FROM bin_hcmut
  ON CONFLICT (device_code) DO NOTHING
),
dev_youth AS (
  INSERT INTO devices (id, bin_id, device_code, secret_hash, status, firmware_version, last_heartbeat_at, created_at, updated_at)
  SELECT gen_random_uuid(), bin_youth.id, 'DEV-YOUTH-001', '$2a$12$uOrjiumAXgbsh1/wE39apOFNhkdRH7cFuc/JLfBXOjVK.e0ue9NHC', 'ACTIVE', 'demo-1.1', now(), now(), now()
  FROM bin_youth
  ON CONFLICT (device_code) DO NOTHING
),
dev_d10 AS (
  INSERT INTO devices (id, bin_id, device_code, secret_hash, status, firmware_version, last_heartbeat_at, created_at, updated_at)
  SELECT gen_random_uuid(), bin_d10.id, 'DEV-D10-001', '$2a$12$9P.Hi5AIa73tnpfQifNS2uWgogEiOsp/XbB9ohtQJw4hcI7zCvY1K', 'MAINTENANCE', 'demo-1.0', now(), now(), now()
  FROM bin_d10
  ON CONFLICT (device_code) DO NOTHING
)
INSERT INTO devices (id, bin_id, device_code, secret_hash, status, firmware_version, last_heartbeat_at, created_at, updated_at)
SELECT gen_random_uuid(), bin_library.id, 'DEV-LIBRARY-001', '$2a$12$F/Cmvl/bsbJjstqkXee7jOvoLC8hM4HrVyYanpvbdPxpRTMIOzTZq', 'ACTIVE', 'demo-1.1', now(), now(), now()
FROM bin_library
ON CONFLICT (device_code) DO NOTHING;
