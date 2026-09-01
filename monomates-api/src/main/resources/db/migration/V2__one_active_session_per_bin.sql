-- Normalize legacy rows before enforcing the invariant. Expired rows may
-- still be marked ACTIVE because expiry is finalized lazily by the API.
UPDATE deposit_sessions
SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP
WHERE status = 'ACTIVE' AND expires_at <= CURRENT_TIMESTAMP;

-- V1 did not prevent two users from opening the same bin concurrently.
-- Keep the newest live session and cancel any older duplicates so this
-- migration remains deployable on an existing prototype database.
WITH ranked_active AS (
  SELECT
    id,
    ROW_NUMBER() OVER (
      PARTITION BY bin_id
      ORDER BY started_at DESC, id DESC
    ) AS position
  FROM deposit_sessions
  WHERE status = 'ACTIVE'
)
UPDATE deposit_sessions AS sessions
SET status = 'CANCELLED', updated_at = CURRENT_TIMESTAMP
FROM ranked_active
WHERE sessions.id = ranked_active.id AND ranked_active.position > 1;

CREATE UNIQUE INDEX uq_one_active_session_per_bin
ON deposit_sessions(bin_id)
WHERE status = 'ACTIVE';
