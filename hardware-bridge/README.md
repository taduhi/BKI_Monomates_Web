# MonoMates hardware bridge

This local bridge connects the existing, unmodified `BKI-arduino_app`
hardware to the MonoMates web API. It supports both USB and shared-Wi-Fi
operation and reads the selected mode from Admin > Bins.

No firmware or file under `BKI-arduino_app` needs to be edited.

## End-to-end flow

1. The user scans a bin QR code and presses **Scan item**.
2. The backend attaches a `SCAN_ITEM` command to that bin's authenticated
   heartbeat response.
3. The bridge receives the command. In `AUTO` mode it uses a compatible USB
   serial device when present, otherwise it listens for the ESP8266 over the
   shared Wi-Fi network.
4. The existing hardware pipeline detects an item, captures an ESP32-CAM
   image, classifies it, and returns `LEFT`, `RIGHT`, or an error.
5. The bridge maps the configured accepted direction to `Accepted`, the
   opposite direction to `Not accepted`, and a camera/model/unknown result to
   `Invalid`. It posts one idempotent event for the exact pending session.
6. The browser's existing session poll displays the same result.

Events received before **Scan item** are ignored. Network failures are retried
with the same event ID, so a retry cannot grant a reward twice.

## Install

From `hardware-bridge`:

```powershell
python -m pip install -r requirements.txt
```

The camera/model dependencies remain owned by the existing hardware project.
Install its requirements if they are not already present:

```powershell
python -m pip install -r ..\BKI-arduino_app\Class_bin\no_wifi_module\requirements.txt
```

## Configure

Set the device identity on the laptop. Do not commit the real secret.

```powershell
$env:MONOMATES_API_BASE_URL='http://localhost:8080/api/v1'
$env:MONOMATES_DEVICE_CODE='DEV-HCMUT-001'
$env:MONOMATES_DEVICE_SECRET='your-device-secret'
$env:MONOMATES_CAMERA_URL='http://192.168.1.49/capture'
```

Use `https://bki-monomates-web.onrender.com/api/v1` as the API URL only after
the matching backend version has been deployed.

Optional variables:

- `MONOMATES_USB_PORT=COM3` pins a specific USB port; otherwise Mega/CH340 is
  detected automatically.
- `MONOMATES_HARDWARE_ROOT` points to the existing
  `Class_bin/no_wifi_module` directory when it is not next to this repository.
- `MONOMATES_HEARTBEAT_SECONDS` defaults to `1.0`.
- `MONOMATES_MINIMUM_CLASSIFICATION_CONFIDENCE` defaults to `0.70`; a lower
  confidence is reported as `Invalid`, never as an accepted/rejected guess.

Check authentication and the Admin-selected settings:

```powershell
python monomates_bridge.py --check
```

Start the bridge:

```powershell
python monomates_bridge.py
```

## Connection modes

Configure each bin in **Admin > Bins > Configure connection**:

- **Auto**: prefer USB; when no compatible COM device exists, listen on the
  configured Wi-Fi port.
- **USB**: require the Mega/CH340 serial connection.
- **Wi-Fi**: expose `POST /api/v1/event` on all laptop network interfaces.

`Accepted item direction` controls how the classifier's current LEFT/RIGHT
output maps to the web result. `Physical output: Swap left/right` independently
reverses the command returned to the servo, so reversed compartments can be
corrected from Admin without changing or reflashing hardware code.

The default local port is `8000`. In Wi-Fi mode the existing ESP8266 firmware
must already target this laptop's current LAN IP and the same port. Because the
firmware target is hardcoded, changing the port in Admin cannot rewrite the
board; keep `8000` unless the already-flashed target uses another port. A DHCP
reservation/static laptop IP is recommended.

The bridge reuses the existing read-only camera/model pipeline from
`BKI-arduino_app`. It does not copy or alter that code.

## Current seeded device identities

For local development, the existing Flyway seed provides:

| Bin | Device code |
|---|---|
| `BIN-HCMUT-001` | `DEV-HCMUT-001` |
| `BIN-YOUTH-001` | `DEV-YOUTH-001` |
| `BIN-D10-001` | `DEV-D10-001` |
| `BIN-LIBRARY-001` | `DEV-LIBRARY-001` |

Production secrets should be supplied privately through environment variables
and rotated separately; they are never displayed in the customer UI.
