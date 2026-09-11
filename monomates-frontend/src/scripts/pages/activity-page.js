import { requireAuthentication } from "../guards/auth-guard.js";
import { getMyDeposits } from "../api/deposit-api.js";
import { getTokenBalance, getTokenLedger } from "../api/rewards-api.js";
import { ApiError } from "../api/api-client.js";
import { renderTokenBalancePill } from "../components/token-balance.js";
import { escapeHtml } from "../utils/dom.js";
import { formatDateTime } from "../utils/date.js";
import { formatTokenAmount, formatWeightGrams } from "../utils/format.js";

await requireAuthentication();
renderTokenBalancePill();

const DEPOSIT_ICON = '<svg aria-hidden="true" class="ico lg" viewBox="0 0 24 24"><path d="M9 2h6M10 2v4L8 9v11a2 2 0 0 0 2 2h4a2 2 0 0 0 2-2V9l-2-3V2M8 13h8"></path></svg>';
const REDEMPTION_ICON = '<svg aria-hidden="true" class="ico lg" viewBox="0 0 24 24"><path d="M10 2v2M14 2v2M6 2v2M18 8h1a3 3 0 0 1 0 6h-1M4 8h14v7a5 5 0 0 1-5 5H9a5 5 0 0 1-5-5Z"></path></svg>';

function depositTitle(status) {
  if (status === "ACCEPTED") return "Accepted clear bottle detected";
  if (status === "VALID_UNCLASSIFIED") return "Valid deposit detected";
  return "Deposit not accepted";
}

function depositIconStyle(status) {
  if (status === "ACCEPTED") return "";
  if (status === "VALID_UNCLASSIFIED") return "background:#fef2f2;color:#b91c1c";
  return "background:#fffbeb;color:#b45309";
}

function buildDepositActivity(deposit, ledgerEntries) {
  const tokens = deposit.tokensAwarded ?? 0;
  const breakdown = ledgerEntries
    .map(
      (entry) => `
        <div class="drow">
          <span>${entry.type === "PET_BONUS" ? "Accepted clear bottle" : "Valid deposit"}</span>
          <strong>+${entry.amount} PT</strong>
        </div>
      `
    )
    .join("") ||
    `
      <div class="drow">
        <span>Item check</span>
        <strong>${deposit.status === "ACCEPTED" ? "Accepted" : deposit.status === "VALID_UNCLASSIFIED" ? "Accepted without bottle bonus" : "Not accepted"}</strong>
      </div>
      <div class="drow">
        <span>Bottle bonus</span>
        <strong>Not awarded</strong>
      </div>
    `;
  const title = depositTitle(deposit.status);
  const binLabel = deposit.binName ?? deposit.binCode;

  return {
    date: deposit.verifiedAt,
    direction: tokens > 0 ? "earned" : "neutral",
    searchKey: `${title} ${binLabel}`.toLowerCase(),
    html: `
      <article class="card activity">
        <button class="amain" type="button" aria-expanded="false">
          <span class="aicon" style="${depositIconStyle(deposit.status)}">${DEPOSIT_ICON}</span>
          <div>
            <b>${escapeHtml(title)}</b>
            <div class="small muted">${escapeHtml(binLabel)} · ${formatDateTime(deposit.verifiedAt)}</div>
          </div>
          <div class="amount" style="${tokens > 0 ? "color:#15803d" : ""}">${tokens > 0 ? "+" : ""}${tokens} PT</div>
        </button>
        <div class="details2">
          <div class="card" style="box-shadow:none;background:#f8fafc">${breakdown}</div>
        </div>
      </article>
    `
  };
}

function buildRedemptionActivity(entry) {
  const title = `Redeemed ${entry.voucherTitle ?? "voucher"}`;

  return {
    date: entry.createdAt,
    direction: "spent",
    searchKey: title.toLowerCase(),
    html: `
      <article class="card activity">
        <button class="amain" type="button" aria-expanded="false">
          <span class="aicon" style="background:#eff6ff;color:var(--primary)">${REDEMPTION_ICON}</span>
          <div>
            <b>${escapeHtml(title)}</b>
            <div class="small muted">${formatDateTime(entry.createdAt)}</div>
          </div>
          <div class="amount">${entry.amount} PT</div>
        </button>
        <div class="details2">
          <div class="card" style="box-shadow:none;background:#f8fafc">
            <div class="drow">
              <span>Description</span>
              <strong>${escapeHtml(entry.description ?? "")}</strong>
            </div>
          </div>
        </div>
      </article>
    `
  };
}

function renderEmpty(message) {
  alist.innerHTML = `<div class="card empty"><p class="muted">${escapeHtml(message)}</p></div>`;
}

let allItems = [];

function filterActivity() {
  if (allItems.length === 0) return;
  const query = aq.value.trim().toLowerCase();
  const direction = atype.value;
  const sorted = asort.value === "oldest" ? [...allItems].reverse() : allItems;
  const visible = sorted.filter(
    (item) =>
      item.searchKey.includes(query) &&
      (direction === "all" || item.direction === direction)
  );
  if (visible.length === 0) {
    renderEmpty("No activity matches your filters.");
    return;
  }
  alist.innerHTML = visible.map((item) => item.html).join("");
}
aq.addEventListener("input", filterActivity);
atype.addEventListener("change", filterActivity);
asort.addEventListener("change", filterActivity);
alist.addEventListener("click", (event) => {
  const button = event.target.closest(".amain");
  if (!button) return;
  const activity = button.closest(".activity");
  const expanded = activity.classList.toggle("open");
  button.setAttribute("aria-expanded", String(expanded));
});

async function load() {
  try {
    const [balance, ledger, deposits] = await Promise.all([
      getTokenBalance(),
      getTokenLedger(),
      getMyDeposits()
    ]);

    balanceValue.textContent = formatTokenAmount(balance.balance);
    balanceSummaryValue.textContent = formatTokenAmount(balance.balance);

    const earned = ledger.filter((e) => e.amount > 0).reduce((sum, e) => sum + e.amount, 0);
    const spent = ledger.filter((e) => e.amount < 0).reduce((sum, e) => sum + Math.abs(e.amount), 0);
    lifetimeEarnedValue.textContent = formatTokenAmount(earned);
    lifetimeSpentValue.textContent = formatTokenAmount(spent);

    const validDeposits = deposits.filter((d) => d.status !== "REJECTED");
    validDepositsValue.textContent = String(validDeposits.length);
    const divertedGrams = validDeposits.reduce((sum, d) => sum + (Number(d.weightGrams) || 0), 0);
    plasticDivertedValue.textContent = formatWeightGrams(divertedGrams);

    const ledgerByDeposit = new Map();
    for (const entry of ledger) {
      if (!entry.depositId) continue;
      if (!ledgerByDeposit.has(entry.depositId)) ledgerByDeposit.set(entry.depositId, []);
      ledgerByDeposit.get(entry.depositId).push(entry);
    }

    allItems = [
      ...deposits.map((d) => buildDepositActivity(d, ledgerByDeposit.get(d.id) ?? [])),
      ...ledger.filter((e) => e.type === "REDEMPTION").map(buildRedemptionActivity)
    ].sort((a, b) => new Date(b.date) - new Date(a.date));

    if (allItems.length === 0) {
      renderEmpty("No activity yet. Scan a bin to make your first deposit.");
      return;
    }
    filterActivity();
  } catch (error) {
    const message = error instanceof ApiError ? error.message : "Could not load your activity.";
    renderEmpty(message);
  }
}

load();
