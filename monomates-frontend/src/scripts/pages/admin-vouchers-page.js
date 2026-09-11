import { requireAdmin } from "../guards/admin-guard.js";
import { getAdminVouchers, saveAdminVoucher, updateAdminVoucher } from "../api/admin-api.js";
import { ApiError } from "../api/api-client.js";
import { escapeHtml } from "../utils/dom.js";
import { setLoading } from "../components/loading.js";
import { formatDateTime } from "../utils/date.js";

await requireAdmin();
addVoucherButton.disabled = false;

const STATUS_META = {
  ACTIVE: { label: "Active", chipClass: "active" },
  INACTIVE: { label: "Inactive", chipClass: "inactive" },
  EXPIRED: { label: "Expired", chipClass: "error" }
};

let editingVoucher = null;
let allVouchers = [];

function statusMeta(status) {
  return STATUS_META[status] ?? STATUS_META.INACTIVE;
}

function hideFormError() {
  voucherFormError.classList.add("hidden");
  voucherFormError.textContent = "";
}

function showFormError(message) {
  voucherFormError.textContent = message;
  voucherFormError.classList.remove("hidden");
}

function openCreateModal() {
  editingVoucher = null;
  voucherModalTitle.textContent = "Add voucher";
  voucherForm.reset();
  voucherStatus.value = "ACTIVE";
  voucherMaxPerUser.value = "1";
  hideFormError();
  openModal("vm");
}

function openEditModal(voucher) {
  editingVoucher = voucher;
  voucherModalTitle.textContent = "Edit voucher";
  voucherTitle.value = voucher.title;
  voucherPartnerName.value = voucher.partnerName;
  voucherDescription.value = voucher.description ?? "";
  voucherTokenCost.value = String(voucher.tokenCost);
  voucherInventory.value = String(voucher.inventory);
  voucherStatus.value = voucher.status;
  voucherValidFrom.value = toLocalDateTime(voucher.validFrom);
  voucherValidUntil.value = toLocalDateTime(voucher.validUntil);
  voucherMaxPerUser.value = String(voucher.maxRedemptionsPerUser ?? 1);
  voucherDisplayText.value = voucher.redemptionDisplayText ?? "";
  voucherInstructions.value = voucher.redemptionInstructions ?? "";
  voucherTerms.value = voucher.termsAndConditions ?? "";
  voucherImageUrl.value = voucher.imageUrl ?? "";
  hideFormError();
  openModal("vm");
}

function toLocalDateTime(value) {
  if (!value) return "";
  const date = new Date(value);
  const offset = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}

function toIsoDateTime(value) {
  return value ? new Date(value).toISOString() : null;
}

addVoucherButton.addEventListener("click", openCreateModal);

function validateVoucherDates() {
  const invalid = voucherValidFrom.value && voucherValidUntil.value &&
    new Date(voucherValidUntil.value) <= new Date(voucherValidFrom.value);
  voucherValidUntil.setCustomValidity(invalid ? "Expiry date must be after the valid-from date." : "");
  return !invalid;
}

voucherValidFrom.addEventListener("change", validateVoucherDates);
voucherValidUntil.addEventListener("change", validateVoucherDates);

voucherSaveButton.addEventListener("click", async () => {
  hideFormError();
  validateVoucherDates();
  if (!voucherForm.reportValidity()) return;
  const payload = {
    partnerName: voucherPartnerName.value.trim(),
    title: voucherTitle.value.trim(),
    description: voucherDescription.value.trim() || null,
    tokenCost: Number(voucherTokenCost.value),
    inventory: Number(voucherInventory.value),
    validFrom: toIsoDateTime(voucherValidFrom.value),
    validUntil: toIsoDateTime(voucherValidUntil.value),
    status: voucherStatus.value,
    imageUrl: voucherImageUrl.value.trim() || null,
    termsAndConditions: voucherTerms.value.trim() || null,
    redemptionInstructions: voucherInstructions.value.trim() || null,
    redemptionDisplayText: voucherDisplayText.value.trim() || null,
    maxRedemptionsPerUser: Number(voucherMaxPerUser.value)
  };
  setLoading(voucherSaveButton, true, "Saving…");
  try {
    if (editingVoucher) {
      await updateAdminVoucher(editingVoucher.id, payload);
    } else {
      await saveAdminVoucher(payload);
    }
    closeModal("vm");
    window.toast?.("Voucher saved.", "success");
    await loadVouchers();
  } catch (error) {
    showFormError(error instanceof ApiError ? error.message : "Could not save this voucher.");
  } finally {
    setLoading(voucherSaveButton, false);
  }
});

function renderRow(voucher) {
  const meta = statusMeta(voucher.status);
  const tr = document.createElement("tr");
  tr.innerHTML = `
    <td>
      <div class="tt">${escapeHtml(voucher.title)}</div>
      <div class="tm">${escapeHtml(voucher.partnerName)}</div>
    </td>
    <td><span class="chip ${meta.chipClass}">${meta.label}</span></td>
    <td>${voucher.tokenCost} PT</td>
    <td>${voucher.inventory}</td>
    <td>${voucher.validUntil ? formatDateTime(voucher.validUntil) : "No expiry"}</td>
    <td>
      <button aria-label="Edit voucher" class="iconbtn" type="button" title="Edit voucher">
        <svg aria-hidden="true" class="ico sm" viewBox="0 0 24 24"><path d="M12 20h9M16.5 3.5a2.1 2.1 0 0 1 3 3L8 18l-4 1 1-4Z"></path></svg>
      </button>
    </td>
  `;
  tr.querySelector(".iconbtn").addEventListener("click", () => openEditModal(voucher));
  return tr;
}

function renderVouchers() {
  const query = adminVoucherSearch.value.trim().toLowerCase();
  const status = adminVoucherStatusFilter.value;
  const filtered = allVouchers.filter((voucher) => {
    const haystack = `${voucher.title} ${voucher.partnerName} ${voucher.description ?? ""}`.toLowerCase();
    return haystack.includes(query) && (status === "all" || voucher.status === status);
  });
  vouchersTableBody.innerHTML = "";
  filtered.forEach((voucher) => vouchersTableBody.appendChild(renderRow(voucher)));
  if (filtered.length === 0) {
    vouchersTableBody.innerHTML = '<tr><td colspan="6"><p class="muted">No vouchers match these filters.</p></td></tr>';
  }
  vouchersCountLabel.textContent = `${filtered.length} of ${allVouchers.length} voucher types`;
}

adminVoucherSearch.addEventListener("input", renderVouchers);
adminVoucherStatusFilter.addEventListener("change", renderVouchers);

async function loadVouchers() {
  vouchersCountLabel.textContent = "Loading…";
  try {
    allVouchers = await getAdminVouchers();
    vouchersTotalValue.textContent = String(allVouchers.length);
    vouchersActiveValue.textContent = String(allVouchers.filter((v) => v.status === "ACTIVE").length);
    vouchersLowStockValue.textContent = String(
      allVouchers.filter((v) => v.status === "ACTIVE" && v.inventory > 0 && v.inventory <= 10).length
    );
    renderVouchers();
  } catch (error) {
    const message = error instanceof ApiError ? error.message : "Could not load vouchers.";
    vouchersTableBody.innerHTML = `<tr><td colspan="6"><p class="muted">${escapeHtml(message)}</p></td></tr>`;
    vouchersCountLabel.textContent = "Could not load vouchers";
  }
}

loadVouchers();
