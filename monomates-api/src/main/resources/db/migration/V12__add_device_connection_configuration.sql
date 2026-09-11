ALTER TABLE devices
  ADD COLUMN connection_mode VARCHAR(20) NOT NULL DEFAULT 'AUTO',
  ADD COLUMN bridge_port INTEGER NOT NULL DEFAULT 8000,
  ADD COLUMN accepted_direction VARCHAR(10) NOT NULL DEFAULT 'RIGHT',
  ADD COLUMN swap_directions BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN active_transport VARCHAR(20);

ALTER TABLE devices
  ADD CONSTRAINT ck_devices_connection_mode
    CHECK (connection_mode IN ('AUTO', 'USB', 'WIFI')),
  ADD CONSTRAINT ck_devices_bridge_port
    CHECK (bridge_port BETWEEN 1 AND 65535),
  ADD CONSTRAINT ck_devices_accepted_direction
    CHECK (accepted_direction IN ('LEFT', 'RIGHT')),
  ADD CONSTRAINT ck_devices_active_transport
    CHECK (active_transport IS NULL OR active_transport IN ('USB', 'WIFI'));
