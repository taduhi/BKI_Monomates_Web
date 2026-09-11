import { getBins } from "../api/bins-api.js";
import { getTokenBalance } from "../api/rewards-api.js";
import { ApiError } from "../api/api-client.js";
import { escapeHtml } from "../utils/dom.js";
import { renderTokenBalancePill } from "../components/token-balance.js";

renderTokenBalancePill();

const STATUS_META = {
  ACTIVE: { filterValue: "available", label: "Available", chipClass: "active", barClass: "ok" },
  FULL: { filterValue: "full", label: "Full", chipClass: "error", barClass: "bad" },
  MAINTENANCE: { filterValue: "maintenance", label: "Maintenance", chipClass: "pending", barClass: "warn" },
  OFFLINE: { filterValue: "offline", label: "Offline", chipClass: "inactive", barClass: "bad" }
};

let allBins = [];
let filterEmptyElement = null;
let currentPosition = null;

function statusMeta(status) {
  return STATUS_META[status] ?? STATUS_META.OFFLINE;
}

function renderBinCard(bin) {
  const meta = statusMeta(bin.status);
  const acceptedNames = bin.acceptedItems.map((item) => item.name).join(", ") || "No accepted items configured";
  const searchKey = `${bin.name} ${bin.location?.address ?? ""}`.toLowerCase();
  const rawLatitude = bin.location?.latitude;
  const rawLongitude = bin.location?.longitude;
  const latitude = Number(rawLatitude);
  const longitude = Number(rawLongitude);
  const hasCoordinates = rawLatitude != null && rawLongitude != null &&
    Number.isFinite(latitude) && Number.isFinite(longitude);
  const distance = currentPosition && hasCoordinates
    ? distanceMeters(currentPosition.latitude, currentPosition.longitude, latitude, longitude)
    : null;
  const directionsUrl = hasCoordinates
    ? `https://www.google.com/maps/dir/?api=1&destination=${latitude},${longitude}`
    : null;

  const article = document.createElement("article");
  article.className = "card hover bincard";
  article.dataset.name = searchKey;
  article.dataset.status = meta.filterValue;
  article.innerHTML = `
    <div style="display:flex;justify-content:space-between;gap:12px">
      <div>
        <h3 class="ctitle">${escapeHtml(bin.name)}</h3>
        <div class="csub">${escapeHtml(bin.location?.address ?? "Location unavailable")}</div>
      </div>
      <span class="chip ${meta.chipClass}"><span class="dot"></span>${meta.label}</span>
    </div>
    <div class="small" style="display:flex;justify-content:space-between;margin-top:14px">
      <span class="muted">Capacity</span>
      <b>${bin.capacityPercent}%</b>
    </div>
    <div class="progress" style="margin-top:7px">
      <div class="bar ${meta.barClass}" style="width:${bin.capacityPercent}%"></div>
    </div>
    <div style="display:flex;justify-content:space-between;align-items:center;margin-top:14px;gap:12px">
      <span class="badge blue">${escapeHtml(acceptedNames)}</span>
      <span class="small muted">${distance == null ? "Enable location for distance" : formatDistance(distance)}</span>
    </div>
    <div class="rowbtn" style="margin-top:14px">
      <a class="ghost" href="detail.html?code=${encodeURIComponent(bin.publicCode)}">View details</a>
      ${directionsUrl
        ? `<a class="ghost" href="${directionsUrl}" target="_blank" rel="noopener">Directions</a>`
        : '<span class="ghost disabled" aria-disabled="true">Directions unavailable</span>'}
    </div>
  `;
  return article;
}

function distanceMeters(lat1, lon1, lat2, lon2) {
  const radians = (degrees) => (degrees * Math.PI) / 180;
  const earthRadius = 6_371_000;
  const deltaLat = radians(lat2 - lat1);
  const deltaLon = radians(lon2 - lon1);
  const a = Math.sin(deltaLat / 2) ** 2 +
    Math.cos(radians(lat1)) * Math.cos(radians(lat2)) *
    Math.sin(deltaLon / 2) ** 2;
  return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function formatDistance(meters) {
  return meters < 1_000
    ? `${Math.round(meters)} m away`
    : `${(meters / 1_000).toFixed(1)} km away`;
}

function renderMessage(message) {
  bl.innerHTML = `
    <div class="card empty">
      <div class="emptyicon">
        <svg aria-hidden="true" class="ico lg" viewBox="0 0 24 24"><path d="M12 9v4M12 17h.01M10.3 3.86l-8.18 14.18A2 2 0 0 0 3.94 21h16.12a2 2 0 0 0 1.82-2.96L13.7 3.86a2 2 0 0 0-3.4 0Z"></path></svg>
      </div>
      <p class="muted">${escapeHtml(message)}</p>
    </div>
  `;
}

function applyFilters() {
  const query = bq.value.trim().toLowerCase();
  const status = bs.value;
  let matchCount = 0;
  document.querySelectorAll(".bincard").forEach((card) => {
    const matches = card.dataset.name.includes(query) && (status === "all" || card.dataset.status === status);
    card.classList.toggle("hidden", !matches);
    if (matches) matchCount += 1;
  });
  filterEmptyElement?.classList.toggle("hidden", matchCount !== 0);
}
bq.addEventListener("input", applyFilters);
bs.addEventListener("change", applyFilters);

function nearbyBinsPreferenceEnabled() {
  // Mirrors the same-keyed toggle on the profile page (default on, matching
  // its "aria-pressed" default there) so "Nearby bin suggestions" actually
  // controls this sort instead of being a no-op switch.
  return localStorage.getItem("monomates:preference:nearbyBins") !== "false";
}

function renderBins() {
  const bins = [...allBins].sort((left, right) => {
    if (!currentPosition || !nearbyBinsPreferenceEnabled()) return left.name.localeCompare(right.name);
    return distanceForBin(left) - distanceForBin(right);
  });
  bl.innerHTML = "";
  bins.forEach((bin) => bl.appendChild(renderBinCard(bin)));
  filterEmptyElement = document.createElement("div");
  filterEmptyElement.className = "card empty hidden";
  filterEmptyElement.innerHTML = '<p class="muted">No bins match these filters.</p>';
  bl.appendChild(filterEmptyElement);
  applyFilters();
}

function distanceForBin(bin) {
  const latitude = Number(bin.location?.latitude);
  const longitude = Number(bin.location?.longitude);
  if (
    bin.location?.latitude == null ||
    bin.location?.longitude == null ||
    !Number.isFinite(latitude) ||
    !Number.isFinite(longitude)
  ) return Number.POSITIVE_INFINITY;
  return distanceMeters(
    currentPosition.latitude,
    currentPosition.longitude,
    latitude,
    longitude
  );
}

useLocationButton.addEventListener("click", () => {
  if (!navigator.geolocation) {
    window.toast?.("Location is not supported by this browser.", "error");
    return;
  }
  useLocationButton.disabled = true;
  navigator.geolocation.getCurrentPosition(
    (position) => {
      currentPosition = {
        latitude: position.coords.latitude,
        longitude: position.coords.longitude
      };
      sessionStorage.setItem("monomates:lastLocation", JSON.stringify(currentPosition));
      useLocationButton.textContent = "Using your location";
      renderBins();
    },
    () => {
      useLocationButton.disabled = false;
      window.toast?.("Location permission was denied. The full bin list is still available.", "error");
    },
    { enableHighAccuracy: false, timeout: 10_000, maximumAge: 300_000 }
  );
});

async function loadBalance() {
  try {
    const balance = await getTokenBalance();
    binsBalanceValue.textContent = `${balance.balance} PT`;
    binsBalanceCard.classList.remove("hidden");
  } catch (error) {
    // A 401 means the session ended and the auth guard is already
    // redirecting to login — hiding the card avoids a flash of a stale
    // value. Any other failure is unrelated to login state, so still show
    // the card with a placeholder instead of making the balance vanish.
    if (error instanceof ApiError && error.status === 401) {
      binsBalanceCard.classList.add("hidden");
    } else {
      binsBalanceValue.textContent = "— PT";
      binsBalanceCard.classList.remove("hidden");
    }
  }
}

async function loadBins() {
  renderMessage("Loading nearby bins…");
  try {
    allBins = await getBins();
    binCount.textContent = `${allBins.length} ${allBins.length === 1 ? "location" : "locations"}`;
    if (allBins.length === 0) {
      renderMessage("No smart bins are available yet.");
      return;
    }
    renderBins();
  } catch (error) {
    binCount.textContent = "Locations unavailable";
    const message = error instanceof ApiError ? error.message : "Could not load bins. Please try again.";
    renderMessage(message);
  }
}

loadBins();
loadBalance();
