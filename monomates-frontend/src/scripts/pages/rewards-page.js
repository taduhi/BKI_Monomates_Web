import { requireAuthentication } from "../guards/auth-guard.js";
import { getVouchers, redeemVoucher, getTokenBalance, getRedemptions } from "../api/rewards-api.js";
import { ApiError } from "../api/api-client.js";
import { renderTokenBalancePill } from "../components/token-balance.js";
import { setLoading } from "../components/loading.js";
import { escapeHtml } from "../utils/dom.js";
import { formatDateTime } from "../utils/date.js";

await requireAuthentication();
renderTokenBalancePill();

const VOUCHER_ICON = '<svg aria-hidden="true" class="ico xl" viewBox="0 0 24 24"><path d="M10 2v2M14 2v2M6 2v2M18 8h1a3 3 0 0 1 0 6h-1M4 8h14v7a5 5 0 0 1-5 5H9a5 5 0 0 1-5-5Z"></path></svg>';

let currentBalance = 0;
let selectedVoucher = null;

function filterRewards() {
  const query = rq.value.trim().toLowerCase();
  const availability = ra.value;
  document.querySelectorAll(".voucher").forEach((card) => {
    const cost = Number(card.dataset.cost);
    const canRedeem = card.dataset.canRedeem === "true";
    const matches = card.dataset.q.includes(query) && (
      availability === "all" || (availability === "afford" ? canRedeem : !canRedeem)
    );
    card.classList.toggle("hidden", !matches);
  });
}
window.fr = filterRewards;

function updateBalanceHeading() {
  rewardsBalanceHeading.textContent = `${currentBalance} PT available`;
}

function renderVoucherCard(voucher, redemptions) {
  const redeemedCount = redemptions.filter((item) => item.voucherId === voucher.id).length;
  const limitReached = redeemedCount >= (voucher.maxRedemptionsPerUser ?? 1);
  const canAfford = currentBalance >= voucher.tokenCost;
  const canRedeem = canAfford && !limitReached;
  const searchKey = `${voucher.title} ${voucher.partnerName} ${voucher.description ?? ""}`.toLowerCase();

  const article = document.createElement("article");
  article.className = "card voucher";
  article.dataset.cost = voucher.tokenCost;
  article.dataset.q = searchKey;
  article.dataset.canRedeem = String(canRedeem);
  article.innerHTML = `
    <div class="vtop" style="background:#f8fafc">
      <span class="vicon" style="color:var(--primary)">${VOUCHER_ICON}</span>
      <span class="badge ${canRedeem ? "green" : "gray"}">${limitReached ? "Limit reached" : canAfford ? "Redeem now" : `Need ${voucher.tokenCost} PT`}</span>
    </div>
    <div class="vbody">
      <div>
        <div class="tiny muted">${escapeHtml(voucher.partnerName.toUpperCase())}</div>
        <h3 class="ctitle">${escapeHtml(voucher.title)}</h3>
        <p class="csub">${escapeHtml(voucher.description ?? "")}</p>
        <p class="small muted">${escapeHtml(voucher.redemptionInstructions ?? "Code instructions are shown after redemption.")}</p>
      </div>
      <div class="badge gray">${voucher.validUntil ? `Expires ${formatDateTime(voucher.validUntil)}` : "No expiry"} · ${voucher.inventory} left</div>
      <div style="font-size:25px;font-weight:900">${voucher.tokenCost} PT</div>
      ${canRedeem
        ? '<button class="btn" type="button">Redeem</button>'
        : `<button class="btn2 disabled" type="button" disabled>${limitReached ? "Already redeemed" : "Need more tokens"}</button>`}
    </div>
  `;
  if (canRedeem) {
    article.querySelector(".vbody > button").addEventListener("click", () => openRedemption(voucher));
  }
  return article;
}

function openRedemption(voucher) {
  selectedVoucher = voucher;
  rname.textContent = voucher.title;
  rcurrentBalance.textContent = `${currentBalance} PT`;
  rcost.textContent = `${voucher.tokenCost} PT`;
  rremain.textContent = `${currentBalance - voucher.tokenCost} PT`;
  rExpiry.textContent = voucher.validUntil ? formatDateTime(voucher.validUntil) : "No expiry";
  rCondition.textContent = voucher.redemptionInstructions ?? "Show the issued code at the partner counter.";
  confirm.style.display = "block";
  done.classList.add("hidden");
  rf.innerHTML = '<button class="btn2" onclick="closeModal(\'redeem\')">Cancel</button><button class="btn" id="confirm-redemption">Confirm</button>';
  document.getElementById("confirm-redemption").addEventListener("click", handleConfirmRedeem);
  openModal("redeem");
}

async function handleConfirmRedeem() {
  const button = document.getElementById("confirm-redemption");
  setLoading(button, true, "Redeeming…");
  try {
    const redemption = await redeemVoucher(selectedVoucher.id);
    confirm.style.display = "none";
    done.classList.remove("hidden");
    rcode.textContent = redemption.redemptionCode;
    rDoneInstructions.textContent = redemption.redemptionInstructions ?? "Show this code at the partner counter.";
    rf.innerHTML = '<button class="btn" onclick="closeModal(\'redeem\')">Done</button>';
    currentBalance = redemption.remainingBalance;
    updateBalanceHeading();
    renderTokenBalancePill();
    await loadVouchers();
  } catch (error) {
    const message = error instanceof ApiError ? error.message : "Could not redeem this voucher.";
    window.toast?.(message, "error");
    setLoading(button, false);
  }
}
window.redeem = openRedemption;

function renderRedemptions(redemptions) {
  if (redemptions.length === 0) {
    myRedemptions.innerHTML = '<div class="card empty"><p class="muted">No redeemed vouchers yet.</p></div>';
    return;
  }
  myRedemptions.innerHTML = redemptions
    .map((redemption) => `
      <article class="card">
        <div class="tiny muted">${escapeHtml(redemption.status)} · ${formatDateTime(redemption.createdAt)}</div>
        <h3 class="ctitle">${escapeHtml(redemption.voucherTitle)}</h3>
        <div class="card green" style="box-shadow:none;font-family:monospace;font-weight:900;margin-top:12px">
          ${escapeHtml(redemption.redemptionCode)}
        </div>
        <p class="small muted">${escapeHtml(redemption.redemptionInstructions ?? "Show this code at the partner counter.")}</p>
      </article>
    `)
    .join("");
}

async function loadVouchers() {
  try {
    const [balance, vouchers, redemptions] = await Promise.all([
      getTokenBalance(),
      getVouchers(),
      getRedemptions()
    ]);
    currentBalance = balance.balance;
    updateBalanceHeading();
    if (vouchers.length === 0) {
      vg.innerHTML = '<div class="card empty"><p class="muted">No rewards are available right now.</p></div>';
      renderRedemptions(redemptions);
      return;
    }
    vg.innerHTML = "";
    vouchers.forEach((voucher) => vg.appendChild(renderVoucherCard(voucher, redemptions)));
    filterRewards();
    renderRedemptions(redemptions);
  } catch (error) {
    const message = error instanceof ApiError ? error.message : "Could not load rewards.";
    vg.innerHTML = `<div class="card empty"><p class="muted">${escapeHtml(message)}</p></div>`;
  }
}

loadVouchers();
