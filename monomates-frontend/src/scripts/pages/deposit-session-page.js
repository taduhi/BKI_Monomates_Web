import { requireAuthentication } from "../guards/auth-guard.js";
import { startDepositSession } from "../api/bins-api.js";
import { getDepositSession, cancelDepositSession, secretSort } from "../api/deposit-api.js";
import { ApiError } from "../api/api-client.js";
import { queryParam, escapeHtml } from "../utils/dom.js";
import { renderTokenBalancePill } from "../components/token-balance.js";

const currentUser = await requireAuthentication();
renderTokenBalancePill();

// Mirrors the backend's app.demo.secret-account-email default. Purely
// cosmetic here: the real gate is the server-side check in
// DemoSecretSortController, so this constant being stale only hides or
// shows the three decorative dots below, nothing security-relevant.
const SECRET_ACCOUNT_EMAIL = "demo@monomates.app";

// Looks like a small decorative "sensor status" indicator strip (a plausible
// design accent on a smart-bin product page) rather than an obvious set of
// controls. Still only ever added to the DOM for the fixed demo account —
// this styling change only makes it noticeable enough for that account
// holder to use live during a demo, it does not change who can see it.
function injectSecretSortControls() {
  if (currentUser?.email?.toLowerCase() !== SECRET_ACCOUNT_EMAIL) return;

  const card = document.querySelector(".card.session");
  if (!card || document.getElementById("secretSortDots")) return;
  card.style.position = "relative";

  const wrap = document.createElement("div");
  wrap.id = "secretSortDots";
  wrap.style.cssText = "position:absolute;bottom:12px;right:16px;display:flex;gap:7px;";

  const dot = (color, label, onClick) => {
    const b = document.createElement("button");
    b.type = "button";
    b.title = label;
    b.setAttribute("aria-label", label);
    b.style.cssText = `width:11px;height:11px;padding:0;border:1px solid rgba(0,0,0,.08);border-radius:50%;background:${color};cursor:pointer;box-shadow:0 0 0 2px #fff inset;`;
    b.addEventListener("click", onClick);
    return b;
  };

  const sort = async (outcome) => {
    if (!currentSession || currentSession.status !== "ACTIVE") return;
    try {
      await secretSort(currentSession.sessionId, outcome);
    } catch (error) {
      console.error(error);
    }
  };

  wrap.append(
    dot("#22c55e", "Đạt yêu cầu", () => sort("ACCEPTED_PET")),
    dot("#ef4444", "Không đạt yêu cầu", () => sort("VALID_UNCERTAIN")),
    dot("#eab308", "Chưa có vật phẩm", () => sort("REJECTED"))
  );
  card.appendChild(wrap);
}

const POLL_INTERVAL_MS = 1500;

let pollTimeout;
let countdownInterval;
let currentSession = null;
let pollGeneration = 0;

const ICONS = {
  waiting: '<svg aria-hidden="true" class="ico xl" viewBox="0 0 24 24"><path d="M3 7V5a2 2 0 0 1 2-2h2M17 3h2a2 2 0 0 1 2 2v2M21 17v2a2 2 0 0 1-2 2h-2M7 21H5a2 2 0 0 1-2-2v-2M7 12h10"></path></svg>',
  success: '<svg aria-hidden="true" class="ico xl" viewBox="0 0 24 24"><path d="m20 6-11 11-5-5"></path></svg>',
  fail: '<svg aria-hidden="true" class="ico xl" viewBox="0 0 24 24"><path d="M18 6 6 18M6 6l12 12"></path></svg>'
};

function clearTimers() {
  clearTimeout(pollTimeout);
  clearInterval(countdownInterval);
}

function renderError(message, options = {}) {
  clearTimers();
  badge.className = "badge red";
  badge.textContent = "Unable to start session";
  vis.className = "visual fail";
  vis.innerHTML = ICONS.fail;
  title.textContent = "Could not start this deposit session";
  msg.textContent = message;
  timer.classList.add("hidden");
  reward.classList.add("hidden");
  note.textContent = "";
  actions.innerHTML = options.retryHref
    ? `<a class="btn" href="${escapeHtml(options.retryHref)}">Back to bin</a>`
    : '<a class="btn" href="../bins/index.html">Back to bins</a>';
}

function startCountdown(startedAtIso, expiresAtIso) {
  clearInterval(countdownInterval);
  const startedAt = new Date(startedAtIso).getTime();
  const expiresAt = new Date(expiresAtIso).getTime();
  const durationSeconds = Math.max(1, (expiresAt - startedAt) / 1000);
  const tick = () => {
    const remainingSeconds = Math.max(0, Math.ceil((expiresAt - Date.now()) / 1000));
    ttext.textContent = `${remainingSeconds} seconds`;
    tbar.style.width = `${Math.min(100, (remainingSeconds / durationSeconds) * 100)}%`;
  };
  tick();
  countdownInterval = setInterval(tick, 1000);
}

async function handleCancel() {
  if (!currentSession) return;
  const sessionId = currentSession.sessionId;
  const generation = ++pollGeneration;
  clearTimers();
  document.getElementById("cancelBtn")?.setAttribute("disabled", "");
  try {
    const session = await cancelDepositSession(sessionId);
    renderSession(session);
  } catch (error) {
    const message = error instanceof ApiError ? error.message : "Could not cancel the session.";
    window.toast?.(message, "error");
    pollSession(sessionId, generation);
  }
}

function persistSessionUrl(session) {
  const url = new URL(window.location.href);
  url.searchParams.set("code", session.binCode);
  url.searchParams.set("sessionId", session.sessionId);
  window.history.replaceState(null, "", url);
  backToBinLink.href = `../bins/detail.html?code=${encodeURIComponent(session.binCode)}`;
}

function renderSession(session) {
  currentSession = session;

  if (session.status === "ACTIVE") {
    badge.className = "badge blue";
    badge.innerHTML = `<span class="dot"></span>Session active · ${escapeHtml(session.binCode)}`;
    vis.className = "visual waiting";
    vis.innerHTML = ICONS.waiting;
    title.textContent = "Insert one empty clear bottle";
    msg.textContent = "QR scanned successfully. Make one deposit within the active session window.";
    timer.classList.remove("hidden");
    reward.classList.add("hidden");
    note.textContent = "QR only starts this session. No token is awarded until a valid physical deposit event is linked.";
    actions.innerHTML = '<button class="btndanger" id="cancelBtn">Cancel session</button>';
    document.getElementById("cancelBtn").addEventListener("click", handleCancel);
    line2.textContent = "Waiting for item";
    line2m.textContent = "Polling every 1.5 seconds";
    line3.textContent = "Reward decision";
    line3m.textContent = "Pending hardware event";
    startCountdown(session.startedAt, session.expiresAt);
    return;
  }

  clearTimers();
  timer.classList.add("hidden");
  line2.textContent = "Item check finished";
  line2m.textContent = "Session closed";

  if (session.status === "COMPLETED") {
    const tokens = session.tokensAwarded;
    reward.classList.remove("hidden");
    amount.textContent = `+${tokens} PT`;
    if (tokens >= 2) {
      badge.className = "badge green";
      badge.textContent = "Rewarded";
      vis.className = "visual success";
      vis.innerHTML = ICONS.success;
      title.textContent = "Accepted clear bottle detected";
      msg.textContent = "The valid deposit and accepted-bottle reward are grouped into one activity.";
      amount.style.color = "#15803d";
      breakdown.innerHTML = '<div class="drow"><span>Valid deposit</span><strong>+1 PT</strong></div><div class="drow"><span>Accepted clear bottle</span><strong>+1 PT</strong></div>';
    } else {
      badge.className = "badge orange";
      badge.textContent = "Valid deposit";
      vis.className = "visual partial";
      vis.innerHTML = ICONS.success;
      title.textContent = "Deposit detected";
      msg.textContent = "You earned the valid-deposit token. The bottle check was uncertain.";
      amount.style.color = "#b45309";
      breakdown.innerHTML = '<div class="drow"><span>Valid deposit</span><strong>+1 PT</strong></div><div class="drow"><span>Camera result</span><strong>Uncertain</strong></div>';
    }
    note.textContent = "This result has been recorded to your activity.";
    actions.innerHTML = '<a class="btn" href="../activity/index.html">View activity</a><a class="btn2" href="../bins/index.html">Find another bin</a>';
    line3.textContent = `${tokens} PT awarded`;
    line3m.textContent = "Transaction confirmed";
    if (tokens > 0) renderTokenBalancePill();
  } else if (session.status === "EXPIRED") {
    badge.className = "badge red";
    badge.textContent = "Expired";
    vis.className = "visual fail";
    vis.innerHTML = ICONS.fail;
    title.textContent = "No item was detected";
    msg.textContent = "The session window ended. No token was awarded.";
    note.textContent = "A QR scan alone is never enough to earn a reward.";
    actions.innerHTML = '<a class="btn" href="../bins/index.html">Try again</a>';
    line3.textContent = "0 PT awarded";
    line3m.textContent = "Session expired";
  } else if (session.status === "REJECTED") {
    badge.className = "badge red";
    badge.textContent = "Rejected";
    vis.className = "visual fail";
    vis.innerHTML = ICONS.fail;
    title.textContent = "Item not accepted";
    msg.textContent = "The system could not confirm a valid deposit. No token was awarded.";
    note.textContent = "A QR scan alone is never enough to earn a reward.";
    actions.innerHTML = '<a class="btn" href="../bins/index.html">Try again</a>';
    line3.textContent = "0 PT awarded";
    line3m.textContent = "No transaction created";
  } else if (session.status === "CANCELLED") {
    badge.className = "badge gray";
    badge.textContent = "Cancelled";
    vis.className = "visual fail";
    vis.innerHTML = ICONS.fail;
    title.textContent = "Session cancelled";
    msg.textContent = "You cancelled this deposit session. No token was awarded.";
    note.textContent = "";
    actions.innerHTML = '<a class="btn" href="../bins/index.html">Find another bin</a>';
    line3.textContent = "0 PT awarded";
    line3m.textContent = "Cancelled by user";
  }
}

async function pollSession(sessionId, generation = pollGeneration) {
  try {
    const session = await getDepositSession(sessionId);
    if (generation !== pollGeneration) return;
    renderSession(session);
    if (session.status === "ACTIVE") {
      pollTimeout = setTimeout(
        () => pollSession(sessionId, generation),
        POLL_INTERVAL_MS
      );
    }
  } catch (error) {
    if (generation !== pollGeneration) return;
    const message = error instanceof ApiError ? error.message : "Lost connection to this session. Please refresh the page.";
    renderError(message);
  }
}

async function init() {
  const publicCode = queryParam("code");
  const existingSessionId = queryParam("sessionId");
  if (!publicCode && !existingSessionId) {
    renderError("No bin was specified. Scan a bin's QR code or open it from the bins list.");
    return;
  }
  if (publicCode) {
    backToBinLink.href = `../bins/detail.html?code=${encodeURIComponent(publicCode)}`;
  }
  try {
    if (existingSessionId) {
      const session = await getDepositSession(existingSessionId);
      persistSessionUrl(session);
      renderSession(session);
      if (session.status === "ACTIVE") pollSession(session.sessionId);
      return;
    }
    const session = await startDepositSession(publicCode);
    persistSessionUrl(session);
    renderSession(session);
    if (session.status === "ACTIVE") pollSession(session.sessionId);
  } catch (error) {
    const message = error instanceof ApiError ? error.message : "Could not start a deposit session for this bin.";
    renderError(message, { retryHref: `../bins/detail.html?code=${encodeURIComponent(publicCode)}` });
  }
}

init();
injectSecretSortControls();
