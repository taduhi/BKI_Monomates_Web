"""Local bridge between the unmodified MonoMates hardware and web API.

AUTO mode prefers a connected Mega/CH340 serial device. When none is
available it exposes the existing ESP8266-compatible HTTP endpoint on the
configured LAN port (8000 by default). Connection settings and pending scan
commands are read from the backend heartbeat response.
"""

from __future__ import annotations

import argparse
import json
import os
import signal
import sys
import threading
import time
import uuid
from dataclasses import dataclass
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Callable

import requests


API_BASE_URL = os.environ.get(
    "MONOMATES_API_BASE_URL", "http://localhost:8080/api/v1"
).rstrip("/")
DEVICE_CODE = os.environ.get("MONOMATES_DEVICE_CODE", "DEV-HCMUT-001")
DEVICE_SECRET = os.environ.get("MONOMATES_DEVICE_SECRET", "")
FIRMWARE_VERSION = "monomates-bridge-1.0"
HEARTBEAT_SECONDS = float(os.environ.get("MONOMATES_HEARTBEAT_SECONDS", "1.0"))
REQUEST_TIMEOUT_SECONDS = 8.0
PLACEHOLDER_WEIGHT_GRAMS = 24.0
MINIMUM_CLASSIFICATION_CONFIDENCE = float(
    os.environ.get("MONOMATES_MINIMUM_CLASSIFICATION_CONFIDENCE", "0.70")
)

DEFAULT_HARDWARE_ROOT = (
    Path(__file__).resolve().parents[1]
    / "BKI-arduino_app"
    / "Class_bin"
    / "no_wifi_module"
)
HARDWARE_ROOT = Path(
    os.environ.get("MONOMATES_HARDWARE_ROOT", str(DEFAULT_HARDWARE_ROOT))
).resolve()
CAMERA_URL = os.environ.get("MONOMATES_CAMERA_URL", "").strip()
USB_PORT = os.environ.get("MONOMATES_USB_PORT", "").strip()


class BackendClient:
    def __init__(self, base_url: str, device_code: str, device_secret: str):
        self.base_url = base_url
        self.device_code = device_code
        self.device_secret = device_secret
        self.http = requests.Session()

    def heartbeat(self, transport: str | None) -> dict:
        response = self.http.post(
            f"{self.base_url}/device/heartbeat",
            json={
                "deviceCode": self.device_code,
                "deviceSecret": self.device_secret,
                "firmwareVersion": FIRMWARE_VERSION,
                "transport": transport,
            },
            timeout=REQUEST_TIMEOUT_SECONDS,
        )
        response.raise_for_status()
        return response.json()

    def report(self, payload: dict) -> dict:
        response = self.http.post(
            f"{self.base_url}/device/events/deposit",
            json={
                "deviceCode": self.device_code,
                "deviceSecret": self.device_secret,
                **payload,
            },
            timeout=REQUEST_TIMEOUT_SECONDS,
        )
        response.raise_for_status()
        return response.json()


@dataclass(frozen=True)
class ScanCommand:
    session_id: str
    requested_at: str


class BridgeState:
    def __init__(self):
        self._lock = threading.Lock()
        self.mode = "AUTO"
        self.port = 8000
        self.accepted_direction = "RIGHT"
        self.swap_directions = False
        self.command: ScanCommand | None = None
        self.pending_report: dict | None = None

    def update(self, heartbeat: dict) -> None:
        with self._lock:
            self.mode = heartbeat.get("connectionMode") or "AUTO"
            self.port = int(heartbeat.get("bridgePort") or 8000)
            self.accepted_direction = heartbeat.get("acceptedDirection") or "RIGHT"
            self.swap_directions = bool(heartbeat.get("swapDirections"))
            if heartbeat.get("command") == "SCAN_ITEM" and heartbeat.get("sessionId"):
                self.command = ScanCommand(
                    str(heartbeat["sessionId"]),
                    str(heartbeat.get("scanRequestedAt") or ""),
                )
            elif self.pending_report is None:
                self.command = None

    def snapshot(self) -> tuple[str, int, str, bool, ScanCommand | None]:
        with self._lock:
            return (
                self.mode,
                self.port,
                self.accepted_direction,
                self.swap_directions,
                self.command,
            )

    def queue_report(self, report: dict) -> None:
        with self._lock:
            self.pending_report = report

    def report_snapshot(self) -> dict | None:
        with self._lock:
            return dict(self.pending_report) if self.pending_report else None

    def report_succeeded(self, event_id: str) -> None:
        with self._lock:
            if self.pending_report and self.pending_report["eventId"] == event_id:
                self.pending_report = None
                self.command = None


def configure_hardware_imports() -> Callable[[str], dict]:
    if not HARDWARE_ROOT.is_dir():
        raise RuntimeError(f"Hardware pipeline was not found: {HARDWARE_ROOT}")
    sys.path.insert(0, str(HARDWARE_ROOT))
    if CAMERA_URL:
        from laptop import config as hardware_config

        hardware_config.CAMERA_CAPTURE_URL = CAMERA_URL
    from laptop.app import process_event

    return process_event


def result_payload(result: dict, command: ScanCommand, accepted_direction: str) -> dict:
    decision = str(result.get("decision") or "").upper()
    confidence = result.get("confidence")
    common = {
        "eventId": f"bridge-{uuid.uuid4()}",
        "sessionId": command.session_id,
        "irDetected": True,
        "classificationConfidence": confidence,
        "rawPayload": json.dumps(
            {
                "source": "monomates-bridge",
                "decision": decision or "INVALID",
                "label": result.get("label"),
                "hardwareError": result.get("error"),
            },
            ensure_ascii=False,
        ),
    }
    confidence_is_valid = confidence is None or float(confidence) >= MINIMUM_CLASSIFICATION_CONFIDENCE
    if result.get("ok") and confidence_is_valid and decision == accepted_direction:
        return {
            **common,
            "weightChangeGrams": PLACEHOLDER_WEIGHT_GRAMS,
            "itemType": "CLEAR_PET_BOTTLE",
        }
    if result.get("ok") and confidence_is_valid and decision in {"LEFT", "RIGHT"}:
        return {**common, "weightChangeGrams": 0.0, "itemType": None}
    return {
        **common,
        "weightChangeGrams": PLACEHOLDER_WEIGHT_GRAMS,
        "itemType": None,
    }


def physical_reply(reply: str, swap_directions: bool) -> str:
    normalized = str(reply or "DECISION|NONE").strip().upper()
    if not swap_directions:
        return normalized
    if normalized == "DECISION|LEFT":
        return "DECISION|RIGHT"
    if normalized == "DECISION|RIGHT":
        return "DECISION|LEFT"
    return normalized


class EventProcessor:
    def __init__(self, state: BridgeState, client: BackendClient):
        self.state = state
        self.client = client
        self._pipeline: Callable[[str], dict] | None = None
        self._lock = threading.Lock()

    def _process_hardware(self, message: str) -> dict:
        if self._pipeline is None:
            self._pipeline = configure_hardware_imports()
        return self._pipeline(message)

    def handle(self, message: str) -> str:
        with self._lock:
            _, _, accepted_direction, swap_directions, command = self.state.snapshot()
            if command is None:
                print("Hardware event ignored: press Scan item on the web first.")
                return "DECISION|NONE"
            if self.state.report_snapshot() is not None:
                print("Hardware event ignored: the previous result is still uploading.")
                return "DECISION|NONE"
            try:
                result = self._process_hardware(message)
            except Exception as exc:
                print("Hardware pipeline error:", exc)
                result = {
                    "ok": False,
                    "reply": "ERROR|MODEL",
                    "decision": "ERROR",
                    "error": str(exc),
                }
            payload = result_payload(result, command, accepted_direction)
            self.state.queue_report(payload)
            self.flush_pending()
            return physical_reply(
                str(result.get("reply") or "DECISION|NONE"),
                swap_directions,
            )

    def flush_pending(self) -> bool:
        payload = self.state.report_snapshot()
        if payload is None:
            return True
        try:
            response = self.client.report(payload)
            print(
                "Web result:",
                response.get("status"),
                f"({response.get('tokensAwarded', 0)} PT)",
            )
            self.state.report_succeeded(payload["eventId"])
            return True
        except requests.RequestException as exc:
            status = exc.response.status_code if exc.response is not None else None
            if status in {404, 409, 422}:
                print("Result discarded because the scan session is no longer available:", exc)
                self.state.report_succeeded(payload["eventId"])
                return False
            print("Result upload will retry:", exc)
            return False


def find_usb_port() -> str | None:
    if USB_PORT:
        return USB_PORT
    try:
        from serial.tools import list_ports
    except ImportError:
        return None
    hints = ("ch340", "arduino", "mega", "usb-serial", "wch")
    ports = list(list_ports.comports())
    for port in ports:
        description = f"{port.device} {port.description} {port.hwid}".lower()
        if "bluetooth" not in description and any(hint in description for hint in hints):
            return port.device
    return None


class Worker:
    transport: str

    def __init__(self):
        self.stop_event = threading.Event()
        self.thread: threading.Thread | None = None

    def start(self) -> None:
        self.thread = threading.Thread(target=self.run, daemon=True)
        self.thread.start()

    def stop(self) -> None:
        self.stop_event.set()
        if self.thread:
            self.thread.join(timeout=3)

    def alive(self) -> bool:
        return bool(self.thread and self.thread.is_alive())

    def run(self) -> None:
        raise NotImplementedError


class UsbWorker(Worker):
    transport = "USB"

    def __init__(self, port: str, processor: EventProcessor):
        super().__init__()
        self.port = port
        self.processor = processor

    def run(self) -> None:
        try:
            import serial

            with serial.Serial(self.port, 115200, timeout=0.25) as link:
                print(f"USB connected: {self.port}")
                time.sleep(2.5)
                link.reset_input_buffer()
                while not self.stop_event.is_set():
                    raw = link.readline()
                    if not raw:
                        continue
                    message = raw.decode(errors="ignore").strip()
                    if not message.startswith("EVENT|"):
                        continue
                    reply = self.processor.handle(message)
                    link.write((reply.strip() + "\n").encode("utf-8"))
                    link.flush()
        except Exception as exc:
            if not self.stop_event.is_set():
                print("USB disconnected:", exc)


class WifiWorker(Worker):
    transport = "WIFI"

    def __init__(self, port: int, processor: EventProcessor):
        super().__init__()
        self.port = port
        self.processor = processor
        self.server: ThreadingHTTPServer | None = None

    def stop(self) -> None:
        self.stop_event.set()
        if self.server:
            self.server.shutdown()
        super().stop()

    def run(self) -> None:
        processor = self.processor

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                if self.path != "/health":
                    self.send_error(404)
                    return
                body = b'{"ok":true,"transport":"WIFI"}'
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def do_POST(self):
                if self.path != "/api/v1/event":
                    self.send_error(404)
                    return
                length = int(self.headers.get("Content-Length", "0"))
                message = self.rfile.read(length).decode("utf-8", errors="replace").strip()
                reply = processor.handle(message).encode("utf-8")
                self.send_response(200)
                self.send_header("Content-Type", "text/plain; charset=utf-8")
                self.send_header("Content-Length", str(len(reply)))
                self.end_headers()
                self.wfile.write(reply)

            def log_message(self, format_string, *args):
                print("Wi-Fi:", format_string % args)

        try:
            self.server = ThreadingHTTPServer(("0.0.0.0", self.port), Handler)
            self.server.timeout = 0.5
            print(f"Wi-Fi listener: http://0.0.0.0:{self.port}/api/v1/event")
            self.server.serve_forever(poll_interval=0.25)
        except Exception as exc:
            if not self.stop_event.is_set():
                print("Wi-Fi listener stopped:", exc)
        finally:
            if self.server:
                self.server.server_close()


class Supervisor:
    def __init__(self, client: BackendClient):
        self.client = client
        self.state = BridgeState()
        self.processor = EventProcessor(self.state, client)
        self.worker: Worker | None = None
        self.worker_key: tuple[str, str | int] | None = None
        self.stop_event = threading.Event()

    def stop(self, *_args) -> None:
        self.stop_event.set()

    def _target(self) -> tuple[str, str | int] | None:
        mode, port, _, _, _ = self.state.snapshot()
        usb = find_usb_port()
        if mode == "USB":
            return ("USB", usb) if usb else None
        if mode == "WIFI":
            return "WIFI", port
        return ("USB", usb) if usb else ("WIFI", port)

    def _switch_worker(self, target: tuple[str, str | int] | None) -> None:
        if target == self.worker_key and self.worker and self.worker.alive():
            return
        if self.worker:
            self.worker.stop()
        self.worker = None
        self.worker_key = None
        if target is None:
            print("USB mode selected, but no compatible USB serial device was found.")
            return
        transport, value = target
        self.worker = (
            UsbWorker(str(value), self.processor)
            if transport == "USB"
            else WifiWorker(int(value), self.processor)
        )
        self.worker_key = target
        self.worker.start()

    def run(self) -> None:
        if not DEVICE_SECRET:
            raise SystemExit("MONOMATES_DEVICE_SECRET is required.")
        print("MonoMates hardware bridge")
        print("Device:", DEVICE_CODE)
        print("API:", API_BASE_URL)
        last_error = None
        while not self.stop_event.is_set():
            transport = self.worker.transport if self.worker and self.worker.alive() else None
            try:
                heartbeat = self.client.heartbeat(transport)
                self.state.update(heartbeat)
                self._switch_worker(self._target())
                self.processor.flush_pending()
                if last_error:
                    print("Backend connection restored.")
                last_error = None
            except requests.RequestException as exc:
                message = str(exc)
                if message != last_error:
                    print("Backend unavailable:", message)
                last_error = message
            self.stop_event.wait(HEARTBEAT_SECONDS)
        if self.worker:
            self.worker.stop()


def main() -> None:
    parser = argparse.ArgumentParser(description="MonoMates USB/Wi-Fi hardware bridge")
    parser.add_argument("--check", action="store_true", help="authenticate once and print backend settings")
    args = parser.parse_args()
    client = BackendClient(API_BASE_URL, DEVICE_CODE, DEVICE_SECRET)
    if args.check:
        if not DEVICE_SECRET:
            raise SystemExit("MONOMATES_DEVICE_SECRET is required.")
        print(json.dumps(client.heartbeat(None), indent=2))
        return
    supervisor = Supervisor(client)
    signal.signal(signal.SIGINT, supervisor.stop)
    signal.signal(signal.SIGTERM, supervisor.stop)
    supervisor.run()


if __name__ == "__main__":
    main()
