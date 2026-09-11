# Hardware bridge

Connects the physical Class_bin sorter (in the separate, read-only
`BKI-arduino_app/` folder) to this backend's real hardware-event API,
`POST /api/v1/device/events/deposit`.

**This repo never edits `BKI-arduino_app/`.** That folder is someone else's
git history, kept locally for reference only (see `.gitignore`). All new
code lives here instead; wiring it into their pipeline is a small,
reversible edit you make yourself in their copy.

## Before you start: confirm the backend has this fix live

Render (the backend host) can take a while to redeploy after a push —
sometimes minutes, occasionally much longer. Check first with:

```bash
curl -X POST https://bki-monomates-web.onrender.com/api/v1/device/events/deposit \
  -H "Content-Type: application/json" \
  -d "{\"deviceCode\":\"DEV-HCMUT-001\",\"deviceSecret\":\"demo-device-secret-hcmut\",\"eventId\":\"probe-1\",\"irDetected\":true,\"weightChangeGrams\":24.0,\"itemType\":\"CLEAR_PET_BOTTLE\",\"classificationConfidence\":0.95}"
```

- `"message":"No active deposit session for this bin."` → **fix is live**, proceed.
- `"fieldErrors":{"sessionId":"must not be null"}` → **not deployed yet**, wait
  and retry later; wiring up real hardware against the old backend will just
  fail with this same error on every event.

## What this does

`send_deposit_event.py` takes the sorting decision the hardware pipeline
already computes (`"RIGHT"` = bottle, `"LEFT"` = not a bottle — see
`Class_bin/no_wifi_module/laptop/decision.py`) and reports it to the live
backend, exactly like a session-linked deposit made through the web app.
No session id is needed: the backend automatically finds whichever session
is currently active for that bin (see the "Backend design note" below).

## How to wire it in

In `BKI-arduino_app/Class_bin/no_wifi_module/laptop/app.py`, `reader_loop()`
currently does:

```python
if kind == "event":
    print("Pipeline: capture → predict → decision")
    link.send_line(decide_from_event(payload))
    print("Chờ ultrasound tiếp theo…")
```

Change it to call `process_event` directly (it already returns everything
needed — `decide_from_event` just throws that away) and report the result:

```python
if kind == "event":
    print("Pipeline: capture → predict → decision")
    result = process_event(payload)
    link.send_line(result["reply"])
    if result["ok"]:
        from send_deposit_event import report_deposit_event
        try:
            report_deposit_event(
                decision=result["decision"],
                confidence=result["confidence"],
            )
        except Exception as exc:
            print("MonoMates report failed (non-fatal):", exc)
    print("Chờ ultrasound tiếp theo…")
```

Then copy (or symlink) `send_deposit_event.py` into
`BKI-arduino_app/Class_bin/no_wifi_module/laptop/` so the `import` above
resolves — or add this folder to `PYTHONPATH` instead if you'd rather not
duplicate the file.

`process_event` and `result_to_decision` are already imported at the top of
`app.py`, so no new imports are needed there beyond the one shown above.

## Configuration

Defaults to bin `BIN-HCMUT-001` (device `DEV-HCMUT-001`). Override for a
different physical bin with environment variables before running
`python run.py`:

```bash
set MONOMATES_DEVICE_CODE=DEV-YOUTH-001
set MONOMATES_DEVICE_SECRET=demo-device-secret-youth
```

All four seeded devices/secrets (from `monomates-api` migration
`V8__replace_placeholder_bins_with_real_locations.sql`):

| Bin | Device code | Device secret |
|---|---|---|
| BIN-HCMUT-001 | DEV-HCMUT-001 | demo-device-secret-hcmut |
| BIN-YOUTH-001 | DEV-YOUTH-001 | demo-device-secret-youth |
| BIN-D10-001 | DEV-D10-001 | demo-device-secret-d10 |
| BIN-LIBRARY-001 | DEV-LIBRARY-001 | demo-device-secret-library |

`MONOMATES_API_BASE_URL` defaults to the live production backend
(`https://bki-monomates-web.onrender.com/api/v1`); override it to point at
a local `docker compose` backend instead while testing.

## End-to-end test

1. On a phone/browser, log in, open the bin matching the device you
   configured above, and scan it (starts an ACTIVE session).
2. Trigger the HC-SR04 (put an object within ~20 cm) so the pipeline
   produces one `RIGHT`/`LEFT` decision.
3. Watch the balance on the phone update within ~1.5s (the app polls the
   session every 1.5 seconds) — a real end-to-end deposit, no simulate
   button involved.
4. If the bin has no active session (nobody scanned it recently), the
   backend answers 404 and prints an error here — expected, not a bug.

## Backend design note (why no session id is needed)

`BKI_Monomates_web.md` section 9 specifies that hardware should identify
itself by bin only; the backend finds the bin's active session. The
endpoint originally required a session id (fine for the browser-driven demo
buttons, which already know their own session), which made it unusable by
real hardware that has no way to learn one. `DeviceDepositEventRequest.sessionId`
is now optional — see `DepositProcessingService.resolveSession` in
`monomates-api` — restoring the bin-level lookup the spec always intended.

## Known simplification: no weight sensor yet

This sorter has an ultrasonic presence sensor and a camera classifier, but
no load cell. The backend's reward rule still gates a "valid" deposit on a
minimum weight, so `send_deposit_event.py` sends a fixed passing placeholder
only when the camera confirms a bottle — the accept/reject decision itself
is still made entirely by the camera model, never by this placeholder. Real
weight validation is a later hardware stage (`BKI_Monomates_web.md`
section 4.3).
