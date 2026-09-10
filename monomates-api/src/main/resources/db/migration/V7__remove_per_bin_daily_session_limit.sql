-- Replaces the old "one session per user/bin/day" invariant with an
-- application-level daily total per user (see app.deposit.daily-scan-limit),
-- enforced in DepositSessionService instead of the database. The demo
-- account is exempt from that application-level limit entirely.
ALTER TABLE deposit_sessions DROP CONSTRAINT uq_session_user_bin_day;
