import { requireAdmin } from "../guards/admin-guard.js";
import { getAdminTransactions } from "../api/admin-api.js";
import { ApiError } from "../api/api-client.js";
import { escapeHtml } from "../utils/dom.js";
import { formatDateTime } from "../utils/date.js";

await requireAdmin();

const PAGE_SIZE = 10;

const TYPE_META = {
  DEPOSIT_BASE: { label: "Deposit", badgeClass: "green" },
  PET_BONUS: { label: "Bonus", badgeClass: "green" },
  REDEMPTION: { label: "Redemption", badgeClass: "blue" },
  ADJUSTMENT: { label: "Adjustment", badgeClass: "gray" },
  REVERSAL: { label: "Reversal", badgeClass: "gray" }
};

let allEntries = [];
let filteredEntries = [];
let currentPage = 1;
const exportTransactionsButton = document.getElementById("exportTransactionsButton");

function typeMeta(type) {
  return TYPE_META[type] ?? { label: type, badgeClass: "gray" };
}

function readableValue(value) {
  if (!value) return "—";
  return String(value)
    .toLowerCase()
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

function applyFilters() {
  const query = txSearch.value.trim().toLowerCase();
  const type = txType.value;
  filteredEntries = allEntries.filter((entry) => {
    const matchesType = type === "all" || entry.type === type;
    const haystack = `${entry.userEmail ?? ""} ${entry.userFullName ?? ""} ${entry.binCode ?? ""} ${entry.voucherTitle ?? ""} ${entry.description ?? ""}`.toLowerCase();
    return matchesType && haystack.includes(query);
  });
  currentPage = 1;
  renderPage();
}
txSearch.addEventListener("input", applyFilters);
txType.addEventListener("change", applyFilters);

function renderRow(entry) {
  const meta = typeMeta(entry.type);
  const source = entry.binCode ?? entry.voucherTitle ?? "—";
  const tr = document.createElement("tr");
  tr.innerHTML = `
    <td>
      <div class="tt">${escapeHtml(entry.description)}</div>
      <div class="tm">${escapeHtml(entry.userFullName ?? entry.userEmail ?? "")}</div>
    </td>
    <td>${escapeHtml(entry.userEmail ?? "—")}</td>
    <td>${escapeHtml(source)}</td>
    <td><span class="badge ${meta.badgeClass}">${meta.label}</span></td>
    <td style="${entry.amount > 0 ? "color:#15803d" : ""}"><b>${entry.amount > 0 ? "+" : ""}${entry.amount} PT</b></td>
    <td>${formatDateTime(entry.createdAt)}</td>
    <td>
      <button aria-label="View transaction detail" class="iconbtn" type="button" title="View detail">
        <svg aria-hidden="true" class="ico sm" viewBox="0 0 24 24"><path d="M2.1 12a10.5 10.5 0 0 1 19.8 0 10.5 10.5 0 0 1-19.8 0"></path><circle cx="12" cy="12" r="3"></circle></svg>
      </button>
    </td>
  `;
  tr.querySelector(".iconbtn").addEventListener("click", () => showDetail(entry));
  return tr;
}

function showDetail(entry) {
  txDetailTitle.textContent = entry.description;
  const rows = [
    ["Amount", `${entry.amount > 0 ? "+" : ""}${entry.amount} PT`],
    ["Type", typeMeta(entry.type).label],
    ["User", entry.userFullName ? `${entry.userFullName} (${entry.userEmail})` : entry.userEmail ?? "—"],
    ["Bin", entry.binCode ?? "—"],
    ["Deposit session", entry.depositSessionId ?? "—"],
    ["Verification method", readableValue(entry.verificationMethod)],
    ["Verification status", readableValue(entry.verificationStatus)],
    ["Voucher", entry.voucherTitle ?? "—"],
    ["Date", formatDateTime(entry.createdAt)],
    ["Entry ID", entry.id]
  ];
  txDetailBody.innerHTML = rows
    .map(([label, value]) => `<div class="drow"><span>${escapeHtml(label)}</span><strong>${escapeHtml(String(value))}</strong></div>`)
    .join("");
  openModal("tm");
}

function renderPage() {
  const totalPages = Math.max(1, Math.ceil(filteredEntries.length / PAGE_SIZE));
  currentPage = Math.min(currentPage, totalPages);
  const start = (currentPage - 1) * PAGE_SIZE;
  const pageEntries = filteredEntries.slice(start, start + PAGE_SIZE);

  txTableBody.innerHTML = "";
  if (pageEntries.length === 0) {
    txTableBody.innerHTML = '<tr><td colspan="7"><p class="muted">No transactions match these filters.</p></td></tr>';
  } else {
    pageEntries.forEach((entry) => txTableBody.appendChild(renderRow(entry)));
  }

  txCountLabel.textContent = `Showing ${pageEntries.length} of ${filteredEntries.length} records`;
  txPageLabel.textContent = `Page ${currentPage} of ${totalPages}`;
  txPrevPage.disabled = currentPage <= 1;
  txNextPage.disabled = currentPage >= totalPages;
}

txPrevPage.addEventListener("click", () => {
  currentPage -= 1;
  renderPage();
});
txNextPage.addEventListener("click", () => {
  currentPage += 1;
  renderPage();
});

function csvCell(value) {
  const text = String(value ?? "");
  return `"${text.replaceAll('"', '""')}"`;
}

function exportTransactions() {
  if (filteredEntries.length === 0) {
    window.toast?.("There are no matching transactions to export.", "error");
    return;
  }
  const headings = ["Entry ID", "Date", "Type", "Amount (PT)", "User", "Email", "Bin", "Voucher", "Description"];
  const rows = filteredEntries.map((entry) => [
    entry.id,
    entry.createdAt,
    typeMeta(entry.type).label,
    entry.amount,
    entry.userFullName,
    entry.userEmail,
    entry.binCode,
    entry.voucherTitle,
    entry.description
  ]);
  const csv = [headings, ...rows].map((row) => row.map(csvCell).join(",")).join("\r\n");
  const url = URL.createObjectURL(new Blob(["\uFEFF", csv], { type: "text/csv;charset=utf-8" }));
  const link = document.createElement("a");
  link.href = url;
  link.download = `monomates-transactions-${new Date().toISOString().slice(0, 10)}.csv`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

exportTransactionsButton.addEventListener("click", exportTransactions);

async function load() {
  txCountLabel.textContent = "Loading…";
  try {
    allEntries = await getAdminTransactions();
    const now = new Date();
    const issuedThisMonth = allEntries
      .filter((e) => e.amount > 0 && sameMonth(e.createdAt, now))
      .reduce((sum, e) => sum + e.amount, 0);
    const redeemedThisMonth = allEntries
      .filter((e) => e.amount < 0 && sameMonth(e.createdAt, now))
      .reduce((sum, e) => sum + Math.abs(e.amount), 0);
    txIssuedValue.textContent = `${issuedThisMonth} PT`;
    txRedeemedValue.textContent = `${redeemedThisMonth} PT`;
    txTotalValue.textContent = String(allEntries.length);
    applyFilters();
    exportTransactionsButton.disabled = false;
  } catch (error) {
    const message = error instanceof ApiError ? error.message : "Could not load transactions.";
    txTableBody.innerHTML = `<tr><td colspan="7"><p class="muted">${escapeHtml(message)}</p></td></tr>`;
    txCountLabel.textContent = "Could not load transactions";
  }
}

function sameMonth(isoDate, reference) {
  const date = new Date(isoDate);
  return date.getFullYear() === reference.getFullYear() && date.getMonth() === reference.getMonth();
}

load();
