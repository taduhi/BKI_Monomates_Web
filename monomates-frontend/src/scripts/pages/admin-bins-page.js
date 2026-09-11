import { requireAdmin } from "../guards/admin-guard.js";
import { getAdminBins, saveAdminBin, updateAdminBin } from "../api/admin-api.js";
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
  binPublicCode.disabled = true;
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

function renderRow(bin) {
  const meta = statusMeta(bin.status);
  const tr = document.createElement("tr");
  tr.innerHTML = `
    <td>
      <div class="tt">${escapeHtml(bin.name)}</div>
      <div class="tm">${escapeHtml(bin.location?.address ?? "")}</div>
    </td>
    <td><span class="chip ${meta.chipClass}"><span class="dot"></span>${meta.label}</span></td>
    <td>${bin.capacityPercent}%</td>
    <td>${escapeHtml(bin.publicCode)}</td>
    <td>${bin.updatedAt ? formatDateTime(bin.updatedAt) : "—"}</td>
    <td>
      <div class="actions">
        <button aria-label="Edit bin" class="iconbtn" type="button" title="Edit bin">
          <svg aria-hidden="true" class="ico sm" viewBox="0 0 24 24"><path d="M12 20h9M16.5 3.5a2.1 2.1 0 0 1 3 3L8 18l-4 1 1-4Z"></path></svg>
        </button>
      </div>
    </td>
  `;
  tr.querySelector(".iconbtn").addEventListener("click", () => openEditModal(bin));
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
    binsTableBody.innerHTML = '<tr><td colspan="6"><p class="muted">No bins match these filters.</p></td></tr>';
  }
  binsCountLabel.textContent = `${filtered.length} of ${allBins.length} bins`;
}

adminBinSearch.addEventListener("input", renderBins);
adminBinStatusFilter.addEventListener("change", renderBins);

async function loadBins() {
  binsCountLabel.textContent = "Loading…";
  try {
    allBins = await getAdminBins();
    binsTotalValue.textContent = String(allBins.length);
    binsAvailableValue.textContent = String(allBins.filter((b) => b.status === "ACTIVE").length);
    binsAttentionValue.textContent = String(allBins.filter((b) => b.status === "FULL" || b.status === "MAINTENANCE" || b.status === "OFFLINE").length);
    renderBins();
  } catch (error) {
    const message = error instanceof ApiError ? error.message : "Could not load bins.";
    binsTableBody.innerHTML = `<tr><td colspan="6"><p class="muted">${escapeHtml(message)}</p></td></tr>`;
    binsCountLabel.textContent = "Could not load bins";
  }
}

loadBins();
