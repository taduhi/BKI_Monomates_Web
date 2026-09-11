import QRCode from "qrcode";
import { getBin } from "../api/bins-api.js";
import { ApiError } from "../api/api-client.js";
import { escapeHtml, queryParam } from "../utils/dom.js";
import { renderTokenBalancePill } from "../components/token-balance.js";
import { formatDateTime } from "../utils/date.js";

renderTokenBalancePill();

const STATUS_META = {
  ACTIVE: { label: "Available", chipClass: "active", barClass: "ok" },
  FULL: { label: "Full", chipClass: "error", barClass: "bad" },
  MAINTENANCE: { label: "Maintenance", chipClass: "pending", barClass: "warn" },
  OFFLINE: { label: "Offline", chipClass: "inactive", barClass: "bad" }
};

function statusMeta(status) {
  return STATUS_META[status] ?? STATUS_META.OFFLINE;
}

function showError(message) {
  detailHero.classList.add("hidden");
  detailErrorMessage.textContent = message;
  detailError.classList.remove("hidden");
}

function renderAcceptedItems(items) {
  if (!items || items.length === 0) {
    acceptedItemsCard.innerHTML = `
      <div class="card" style="box-shadow:none;background:#f8fafc;margin-top:14px">
        <p class="small muted">No accepted items are configured for this bin yet.</p>
      </div>
    `;
    return;
  }
  acceptedItemsCard.innerHTML = items
    .map(
      (item) => `
        <div class="card green" style="box-shadow:none;margin-top:14px">
          <span class="badge green">Eligible for a reward</span>
          <h3 class="ctitle" style="margin-top:10px">${escapeHtml(item.name)}</h3>
          <p class="small muted">${escapeHtml(item.description ?? "")} Earn +${item.bonusTokens} PT when this item is accepted.</p>
        </div>
      `
    )
    .join("");
}

function renderBin(bin) {
  const meta = statusMeta(bin.status);
  binCode.textContent = bin.publicCode;
  binName.textContent = bin.name;
  binStatusChip.className = `chip ${meta.chipClass}`;
  binStatusLabel.textContent = meta.label;
  binAddress.textContent = bin.location?.address ?? "Location unavailable";
  binUpdatedAt.textContent = bin.updatedAt
    ? `Last updated ${formatDateTime(bin.updatedAt)}`
    : "Update time unavailable";
  binCapacityText.textContent = `${bin.capacityPercent}%`;
  binCapacityBar.className = `bar ${meta.barClass}`;
  binCapacityBar.style.width = `${bin.capacityPercent}%`;
  const rawLatitude = bin.location?.latitude;
  const rawLongitude = bin.location?.longitude;
  const latitude = Number(rawLatitude);
  const longitude = Number(rawLongitude);
  const hasCoordinates = rawLatitude != null && rawLongitude != null &&
    Number.isFinite(latitude) && Number.isFinite(longitude);
  if (hasCoordinates) {
    directionsLink.href = `https://www.google.com/maps/dir/?api=1&destination=${latitude},${longitude}`;
    directionsLink.classList.remove("disabled");
    directionsLink.removeAttribute("aria-disabled");
  } else {
    directionsLink.removeAttribute("href");
    directionsLink.classList.add("disabled");
    directionsLink.setAttribute("aria-disabled", "true");
  }
  const savedLocation = readSavedLocation();
  binDistance.textContent = savedLocation && hasCoordinates
    ? `${formatDistance(distanceMeters(savedLocation.latitude, savedLocation.longitude, latitude, longitude))} away`
    : "Use your location on the bin list to see distance";
  if (bin.status === "ACTIVE") {
    scanQrLink.href = `../deposit/session.html?code=${encodeURIComponent(bin.publicCode)}`;
    scanQrLink.classList.remove("disabled");
    scanQrLink.removeAttribute("aria-disabled");
  } else {
    scanQrLink.removeAttribute("href");
    scanQrLink.classList.add("disabled");
    scanQrLink.setAttribute("aria-disabled", "true");
    scanQrLink.title = `Deposits are unavailable while this bin is ${meta.label.toLowerCase()}.`;
  }
  renderAcceptedItems(bin.acceptedItems);
  renderBinQr(bin);
}

// Render the canonical MonoMates URL for this bin. Both a phone's native QR
// reader and the in-app scanner can read it; the in-app scanner also supports
// a plain public code and the legacy `qrCodeId` query parameter.
function renderBinQr(bin) {
  if (bin.status !== "ACTIVE") {
    qrCard.classList.add("hidden");
    return;
  }
  const url = `${window.location.origin}/pages/deposit/session.html?code=${encodeURIComponent(bin.publicCode)}`;
  QRCode.toCanvas(binQrCanvas, url, { width: 180, margin: 1 }, (error) => {
    if (error) {
      console.error("Could not render bin QR code", error);
      qrCard.classList.add("hidden");
      return;
    }
    qrCard.classList.remove("hidden");
  });
}

function readSavedLocation() {
  try {
    const value = JSON.parse(sessionStorage.getItem("monomates:lastLocation"));
    return Number.isFinite(value?.latitude) && Number.isFinite(value?.longitude)
      ? value
      : null;
  } catch {
    return null;
  }
}

function distanceMeters(lat1, lon1, lat2, lon2) {
  const radians = (degrees) => (degrees * Math.PI) / 180;
  const deltaLat = radians(lat2 - lat1);
  const deltaLon = radians(lon2 - lon1);
  const a = Math.sin(deltaLat / 2) ** 2 +
    Math.cos(radians(lat1)) * Math.cos(radians(lat2)) *
    Math.sin(deltaLon / 2) ** 2;
  return 6_371_000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function formatDistance(meters) {
  return meters < 1_000
    ? `${Math.round(meters)} m`
    : `${(meters / 1_000).toFixed(1)} km`;
}

async function loadBin() {
  const publicCode = queryParam("code");
  if (!publicCode) {
    showError("No bin was specified. Go back and choose a bin from the list.");
    return;
  }
  try {
    const bin = await getBin(publicCode);
    renderBin(bin);
  } catch (error) {
    const message =
      error instanceof ApiError
        ? error.message
        : "Could not load this bin. Please try again.";
    showError(message);
  }
}

loadBin();
