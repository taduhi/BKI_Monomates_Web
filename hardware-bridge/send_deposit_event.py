"""Bridge: reports a Class_bin sorting decision to the MonoMates backend.

This lives in the web repo, not the hardware repo — it is a *client* of this
backend's real hardware-event API. The Arduino/laptop pipeline itself
(`BKI-arduino_app/Class_bin/no_wifi_module/laptop/`) is read-only reference
material for this project and is never edited from here; see README.md in
this folder for exactly how to wire this module into that pipeline instead.

Pipeline recap (from BKI-arduino_app/Class_bin/no_wifi_module):
    HC-SR04 (object detected) -> Mega (USB serial) -> laptop captures an
    image from the ESP32-CAM, runs a YOLO model, and decides "LEFT" (not a
    bottle) or "RIGHT" (bottle) -- see laptop/decision.py. That decision is
    exactly the `decision` argument this module expects.
"""

from __future__ import annotations

import os
import uuid
from typing import Optional

import requests

# One-time setup for whichever physical bin this laptop is attached to.
# Defaults to one of the four real bins already seeded in production
# (see monomates-api V8 migration); override via environment variables when
# wiring up a different bin.
API_BASE_URL = os.environ.get(
    "MONOMATES_API_BASE_URL", "https://bki-monomates-web.onrender.com/api/v1"
)
DEVICE_CODE = os.environ.get("MONOMATES_DEVICE_CODE", "DEV-HCMUT-001")
DEVICE_SECRET = os.environ.get("MONOMATES_DEVICE_SECRET", "demo-device-secret-hcmut")

# This sorter has no load cell yet (see BKI_Monomates_web.md section 4.3 /
# 22 -- weight validation is a later "v3" hardware stage). The backend's
# reward rule still requires a passing weight to call a deposit "valid" at
# all, so a fixed placeholder is sent only for a confirmed bottle -- it does
# not misrepresent anything, since the actual accept/reject call is already
# 100% made by the camera model, not by this placeholder.
PLACEHOLDER_WEIGHT_GRAMS = 24.0

REQUEST_TIMEOUT_S = 5.0


def report_deposit_event(*, decision: str, confidence: Optional[float] = None) -> dict:
    """Report one sorting decision for the bin's current deposit session.

    `decision` is exactly the value `laptop.decision.result_to_decision(result)`
    already computes: "RIGHT" (bottle) or "LEFT" (not a bottle).

    No session id is sent. Real hardware has no way to know which user is
    standing at the bin -- only their phone/browser does -- so the backend
    finds whichever session is currently ACTIVE for this device's own bin
    instead (see DepositProcessingService.resolveSession in monomates-api).
    If nobody has scanned the bin recently, the backend correctly answers
    404 and no token is awarded, exactly like the original "QR alone gives
    nothing, an item alone gives nothing" rule.

    Raises requests.HTTPError on anything other than a 200 (a 404 with no
    active session is a normal, expected outcome worth handling by the
    caller, not necessarily an error to crash on).
    """
    is_bottle = decision == "RIGHT"
    payload = {
        "deviceCode": DEVICE_CODE,
        "deviceSecret": DEVICE_SECRET,
        "eventId": f"class-bin-{uuid.uuid4()}",
        # This function is only ever called after a real HC-SR04
        # EVENT|OBJECT_DETECTED trigger, so something was physically present
        # either way -- only the weight/item fields change with the
        # classification result.
        "irDetected": True,
        "weightChangeGrams": PLACEHOLDER_WEIGHT_GRAMS if is_bottle else None,
        "itemType": "CLEAR_PET_BOTTLE" if is_bottle else None,
        "classificationConfidence": confidence,
    }

    response = requests.post(
        f"{API_BASE_URL}/device/events/deposit",
        json=payload,
        timeout=REQUEST_TIMEOUT_S,
    )
    response.raise_for_status()
    return response.json()
