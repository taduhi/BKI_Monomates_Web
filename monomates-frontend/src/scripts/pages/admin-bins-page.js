import { requireAdmin } from "../guards/admin-guard.js";
import {
  deleteAdminBin,
  getAdminBins,
  getAdminDevices,
  saveAdminBin,
  updateAdminBin,
  updateAdminDeviceConnection
} from "../api/admin-api.js";
import { ApiError } from "../api/api-client.js";
import { escapeHtml } from "../utils/dom.js";
import { setLoading } from "../components/loading.js";
import { formatDateTime } from "../utils/date.js";

await requireAdmin();
addBinButton.disabled = false;

const ACCEPTED_ITEM_CODES = ["CLEAR_PET_BOTTLE"];

const STATUS_META = {
  ACTIVE: { label: "Available", chipClass: "active" },
  FULL: { label: "Full", chipClass: "error" },
  MAINTENANCE: { label: "Maintenance", chipClass: "pending" },
  OFFLINE: { label: "Offline", chipClass: "inactive" }
};

let editingBin = null;
let allBins = [];
let devicesByBinId = new Map();
let configuringDevice = null;
let selectedConnectionMode = "AUTO";
let swapDirections = false;
let deviceRefreshPending = false;

function statusMeta(status) {
  return STATUS_META[status] ?? STATUS_META.OFFLINE;
}

function hideFormError() {
  binFormError.classList.add("hidden");
  binFormError.textContent = "";
}

function showFormError(message) {
  binFormError.textContent = message;
  binFormError.classList.remove("hidden");
}

function openCreateModal() {
  editingBin = null;
  binModalTitle.textContent = "Add bin";
  binForm.reset();
  binPublicCode.disabled = false;
  binCapacity.value = "0";
  hideFormError();
  openModal("bm");
}

function openEditModal(bin) {
  editingBin = bin;
  binModalTitle.textContent = "Edit bin";
  binName.value = bin.name;
  binPublicCode.value = bin.publicCode;
  binPublicCode.disabled = false;
  binStatus.value = bin.status;
  binCapacity.value = String(bin.capacityPercent);
  binLocationName.value = bin.location?.name ?? "";
  binAddress.value = bin.location?.address ?? "";
  binLatitude.value = bin.location?.latitude ?? "";
  binLongitude.value = bin.location?.longitude ?? "";
  hideFormError();
  openModal("bm");
}
window.editBin = openEditModal;

function selectConnectionMode(mode) {
  selectedConnectionMode = mode;
  connectionModeButtons.querySelectorAll("button[data-mode]").forEach((button) => {
    const selected = button.dataset.mode === mode;
    button.classList.toggle("selected", selected);
    button.setAttribute("aria-pressed", String(selected));
  });
}

connectionModeButtons.addEventListener("click", (event) => {
  const button = event.target.closest("button[data-mode]");
  if (button) selectConnectionMode(button.dataset.mode);
});

function selectDirectionRouting(swapped) {
  swapDirections = swapped;
  directionRoutingButtons.querySelectorAll("button[data-swapped]").forEach((button) => {
    const selected = (button.dataset.swapped === "true") === swapped;
    button.classList.toggle("selected", selected);
    button.setAttribute("aria-pressed", String(selected));
  });
}

directionRoutingButtons.addEventListener("click", (event) => {
  const button = event.target.closest("button[data-swapped]");
  if (button) selectDirectionRouting(button.dataset.swapped === "true");
});

function openConnectionModal(bin) {
  const device = devicesByBinId.get(bin.id);
  if (!device) {
    window.toast?.("No device is configured for this bin.", "error");
    return;
  }
  configuringDevice = device;
  connectionBinName.textContent = `${bin.name} · ${bin.publicCode}`;
  connectionDeviceCode.textContent = device.deviceCode;
  connectionPort.value = String(device.bridgePort ?? 8000);
  acceptedDirection.value = device.acceptedDirection ?? "RIGHT";
  selectDirectionRouting(Boolean(device.swapDirections));
  selectConnectionMode(device.connectionMode ?? "AUTO");
  connectionFormError.classList.add("hidden");
  connectionFormError.textContent = "";
  openModal("connectionModal");
}

connectionSaveButton.addEventListener("click", async () => {
  if (!configuringDevice || !connectionForm.reportValidity()) return;
  connectionFormError.classList.add("hidden");
  setLoading(connectionSaveButton, true, "Saving…");
  try {
    const saved = await updateAdminDeviceConnection(configuringDevice.binId, {
      connectionMode: selectedConnectionMode,
      bridgePort: Number(connectionPort.value),
      acceptedDirection: acceptedDirection.value,
      swapDirections
    });
    devicesByBinId.set(saved.binId, saved);
    closeModal("connectionModal");
    window.toast?.("Connection settings saved.", "success");
    renderBins();
  } catch (error) {
    connectionFormError.textContent = error instanceof ApiError ? error.message : "Could not save connection settings.";
    connectionFormError.classList.remove("hidden");
  } finally {
    setLoading(connectionSaveButton, false);
  }
});

addBinButton.addEventListener("click", openCreateModal);

binSaveButton.addEventListener("click", async () => {
  hideFormError();
  if (!binForm.reportValidity()) return;
  const payload = {
    publicCode: binPublicCode.value.trim(),
    name: binName.value.trim(),
    status: binStatus.value,
    capacityPercent: Number(binCapacity.value),
    locationName: binLocationName.value.trim(),
    address: binAddress.value.trim(),
    latitude: binLatitude.value === "" ? null : Number(binLatitude.value),
    longitude: binLongitude.value === "" ? null : Number(binLongitude.value),
    acceptedItemCodes: ACCEPTED_ITEM_CODES
  };
  setLoading(binSaveButton, true, "Saving…");
  try {
    if (editingBin) {
      await updateAdminBin(editingBin.id, payload);
    } else {
      await saveAdminBin(payload);
    }
    closeModal("bm");
    window.toast?.("Bin saved.", "success");
    await loadBins();
  } catch (error) {
    const message = error instanceof ApiError ? error.message : "Could not save this bin.";
    showFormError(message);
  } finally {
    setLoading(binSaveButton, false);
  }
});

async function handleDeleteBin(bin) {
  if (!window.confirm(`Delete "${bin.name}" (${bin.publicCode}) permanently? This cannot be undone.`)) return;
  try {
    await deleteAdminBin(bin.id);
    window.toast?.("Bin deleted.", "success");
    await loadBins();
  } catch (error) {
    const message = error instanceof ApiError ? error.message : "Could not delete this bin.";
    window.toast?.(message, "error");
  }
}

function renderRow(bin) {
  const meta = statusMeta(bin.status);
  const device = devicesByBinId.get(bin.id);
  const requestedMode = device?.connectionMode ?? "AUTO";
  const activeTransport = device?.activeTransport;
  const isConnected = Boolean(device?.bridgeOnline);
  const connectionText = isConnected
    ? activeTransport === "USB"
      ? "USB connected"
      : activeTransport === "WIFI"
        ? "Wi-Fi listener online"
        : "Bridge online · Detecting"
    : "Bridge offline";
  const modeLabel = requestedMode === "WIFI"
    ? "Wi-Fi"
    : requestedMode[0] + requestedMode.slice(1).toLowerCase();
  const tr = document.createElement("tr");
  tr.innerHTML = `
    <td>
      <div class="tt">${escapeHtml(bin.name)}</div>
      <div class="tm">${escapeHtml(bin.location?.address ?? "")}</div>
    </td>
    <td><span class="chip ${meta.chipClass}"><span class="dot"></span>${meta.label}</span></td>
    <td>${bin.capacityPercent}%</td>
    <td>${escapeHtml(bin.publicCode)}</td>
    <td>
      <span class="chip ${isConnected ? "active" : "inactive"}"><span class="dot"></span>${escapeHtml(connectionText)}</span>
      <div class="tm">Mode ${escapeHtml(modeLabel)} · Port ${device?.bridgePort ?? 8000}</div>
      <div class="tm">${device?.lastHeartbeatAt ? `Bridge seen ${escapeHtml(formatDateTime(device.lastHeartbeatAt))}` : "Bridge has not connected yet"}</div>
      <div class="tm">${device?.lastHardwareEventAt ? `Last hardware result ${escapeHtml(formatDateTime(device.lastHardwareEventAt))}` : "No hardware result received yet"}</div>
      ${device?.swapDirections ? '<div class="tm">Left/right swapped</div>' : ''}
    </td>
    <td>${bin.updatedAt ? formatDateTime(bin.updatedAt) : "—"}</td>
    <td>
      <div class="actions">
        <button aria-label="Configure connection" class="iconbtn" type="button" title="Configure connection">
          <svg aria-hidden="true" class="ico sm" viewBox="0 0 24 24"><path d="M5 12h14M12 5v14"></path><circle cx="12" cy="12" r="9"></circle></svg>
        </button>
        <button aria-label="Edit bin" class="iconbtn" type="button" title="Edit bin">
          <svg aria-hidden="true" class="ico sm" viewBox="0 0 24 24"><path d="M12 20h9M16.5 3.5a2.1 2.1 0 0 1 3 3L8 18l-4 1 1-4Z"></path></svg>
        </button>
        <button aria-label="Delete bin" class="iconbtn" type="button" title="Delete bin">
          <svg aria-hidden="true" class="ico sm" viewBox="0 0 24 24"><path d="M3 6h18M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2m3 0-1 14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2L4 6M10 11v6M14 11v6"></path></svg>
        </button>
      </div>
    </td>
  `;
  const [connectionButton, editButton, deleteButton] = tr.querySelectorAll(".iconbtn");
  connectionButton.addEventListener("click", () => openConnectionModal(bin));
  editButton.addEventListener("click", () => openEditModal(bin));
  deleteButton.addEventListener("click", () => handleDeleteBin(bin));
  return tr;
}

function renderBins() {
  const query = adminBinSearch.value.trim().toLowerCase();
  const status = adminBinStatusFilter.value;
  const filtered = allBins.filter((bin) => {
    const haystack = `${bin.name} ${bin.publicCode} ${bin.location?.address ?? ""}`.toLowerCase();
    return haystack.includes(query) && (status === "all" || bin.status === status);
  });
  binsTableBody.innerHTML = "";
  filtered.forEach((bin) => binsTableBody.appendChild(renderRow(bin)));
  if (filtered.length === 0) {
    binsTableBody.innerHTML = '<tr><td colspan="7"><p class="muted">No bins match these filters.</p></td></tr>';
  }
  binsCountLabel.textContent = `${filtered.length} of ${allBins.length} bins`;
}

adminBinSearch.addEventListener("input", renderBins);
adminBinStatusFilter.addEventListener("change", renderBins);

async function loadBins() {
  binsCountLabel.textContent = "Loading…";
  try {
    const [bins, devices] = await Promise.all([getAdminBins(), getAdminDevices()]);
    allBins = bins;
    devicesByBinId = new Map(devices.map((device) => [device.binId, device]));
    binsTotalValue.textContent = String(allBins.length);
    binsAvailableValue.textContent = String(allBins.filter((b) => b.status === "ACTIVE").length);
    binsAttentionValue.textContent = String(allBins.filter((b) => b.status === "FULL" || b.status === "MAINTENANCE" || b.status === "OFFLINE").length);
    renderBins();
  } catch (error) {
    const message = error instanceof ApiError ? error.message : "Could not load bins.";
    binsTableBody.innerHTML = `<tr><td colspan="7"><p class="muted">${escapeHtml(message)}</p></td></tr>`;
    binsCountLabel.textContent = "Could not load bins";
  }
}

async function refreshDeviceStatuses() {
  if (deviceRefreshPending || document.hidden) return;
  deviceRefreshPending = true;
  try {
    const devices = await getAdminDevices();
    devicesByBinId = new Map(devices.map((device) => [device.binId, device]));
    renderBins();
  } catch {
    // Preserve the last known state; the next refresh retries automatically.
  } finally {
    deviceRefreshPending = false;
  }
}

await loadBins();
setInterval(refreshDeviceStatuses, 5_000);
