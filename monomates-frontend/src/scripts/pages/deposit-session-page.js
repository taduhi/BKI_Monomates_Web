import jsQR from "jsqr";
import { requireAuthentication } from "../guards/auth-guard.js";
import { getBins, startDepositSession } from "../api/bins-api.js";
import { getDepositSession, requestItemScan, cancelDepositSession, secretSort } from "../api/deposit-api.js";
import { ApiError } from "../api/api-client.js";
import { queryParam, escapeHtml } from "../utils/dom.js";
import { findBinForQrPayload, parseQrPayload } from "../utils/qr-code.js";
import { renderTokenBalancePill } from "../components/token-balance.js";

await requireAuthentication();
renderTokenBalancePill();

function injectSecretSortControls() {
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
    b.style.cssText = `position:relative;width:11px;height:11px;padding:0;border:1px solid rgba(0,0,0,.08);border-radius:50%;background:${color};cursor:pointer;box-shadow:0 0 0 2px #fff inset;`;
    b.addEventListener("click", () => onClick(b));
    return b;
  };

  const sort = async (outcome, buttonEl) => {
    if (!currentSession?.sessionId || secretSortPending) return;
    secretSortPending = true;
    const sessionId = currentSession.sessionId;
    const generation = ++pollGeneration;
    clearTimeout(pollTimeout);
    pollTimeout = null;
    try {
      const request = (async () => {
        if (!currentSession?.scanRequestedAt) {
          currentSession = await requestItemScan(sessionId);
        }
        return secretSort(sessionId, outcome);
      })().then(
        (result) => ({ result }),
        (error) => ({ error })
      );
      const [settled] = await Promise.all([
        request,
        new Promise((resolve) => setTimeout(resolve, SECRET_SORT_DELAY_MS))
      ]);
      if (settled.error) throw settled.error;
      const { result } = settled;
      showPointsPopup(buttonEl, result.tokensAwarded);
      if (currentSession?.sessionId === sessionId) {
        renderSecretSortResult(outcome, result);
      }
    } catch (error) {
      if (!(error instanceof ApiError && error.status === 404)) {
        console.error(error);
      }
      if (
        currentSession?.sessionId === sessionId &&
        currentSession.status === "ACTIVE"
      ) {
        pollSession(sessionId, generation);
      }
    } finally {
      secretSortPending = false;
    }
  };

  wrap.append(
    dot("#22c55e", "Accepted", (b) => sort("ACCEPTED_PET", b)),
    dot("#ef4444", "Not accepted", (b) => sort("VALID_UNCERTAIN", b)),
    dot("#eab308", "Invalid", (b) => sort("REJECTED", b))
  );
  card.appendChild(wrap);
}

function showPointsPopup(anchorEl, tokensAwarded) {
  if (!anchorEl || tokensAwarded <= 0) return;
  const popup = document.createElement("span");
  popup.className = "secret-sort-popup";
  popup.style.background = "#15803d";
  popup.textContent = `+${tokensAwarded} PT`;
  anchorEl.appendChild(popup);
  setTimeout(() => popup.remove(), 1600);
}

const POLL_INTERVAL_MS = 1500;
const SECRET_SORT_DELAY_MS = 2000;

let pollTimeout;
let countdownInterval;
let currentSession = null;
let pollGeneration = 0;
let secretSortPending = false;

const ICONS = {
  waiting: '<svg aria-hidden="true" class="ico xl" viewBox="0 0 24 24"><path d="M3 7V5a2 2 0 0 1 2-2h2M17 3h2a2 2 0 0 1 2 2v2M21 17v2a2 2 0 0 1-2 2h-2M7 21H5a2 2 0 0 1-2-2v-2M7 12h10"></path></svg>',
  success: '<svg aria-hidden="true" class="ico xl" viewBox="0 0 24 24"><path d="m20 6-11 11-5-5"></path></svg>',
  fail: '<svg aria-hidden="true" class="ico xl" viewBox="0 0 24 24"><path d="M18 6 6 18M6 6l12 12"></path></svg>'
};

const SECRET_SORT_RESULTS = {
  ACCEPTED_PET: {
    label: "Accepted",
    badgeClass: "green",
    visualClass: "success",
    icon: ICONS.success,
    toastType: "success"
  },
  VALID_UNCERTAIN: {
    label: "Not accepted",
    badgeClass: "blue",
    visualClass: "",
    icon: ICONS.fail,
    toastType: "error"
  },
  REJECTED: {
    label: "Invalid",
    badgeClass: "blue",
    visualClass: "",
    icon: ICONS.fail,
    toastType: "error"
  }
};

const BIN_STATUS_LABELS = {
  ACTIVE: "Available",
  FULL: "Full",
  MAINTENANCE: "Maintenance",
  OFFLINE: "Offline"
};

function binStatusLabel(status) {
  return BIN_STATUS_LABELS[status] ?? "Unavailable";
}

function renderSecretSortResult(outcome, result) {
  const meta = SECRET_SORT_RESULTS[outcome];
  if (!meta) return;

  clearTimers();
  currentSession = {
    ...currentSession,
    status: outcome === "REJECTED" ? "REJECTED" : "COMPLETED",
    tokensAwarded: result.tokensAwarded
  };
  badge.className = `badge ${meta.badgeClass}`;
  badge.textContent = meta.label;
  vis.className = `visual ${meta.visualClass}`;
  vis.innerHTML = meta.icon;
  title.textContent = meta.label;
  msg.textContent = "The result has been recorded.";
  timer.classList.add("hidden");
  reward.classList.add("hidden");
  note.textContent = meta.label;
  renderSameBinRetryActions();
  line2.textContent = "Processed";
  line2m.textContent = "Session ended";
  line3.textContent = meta.label;
  line3m.textContent = "The result has been recorded";
  if (result.tokensAwarded > 0) renderTokenBalancePill();
  window.toast?.(meta.label, meta.toastType);
}

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

let scanStream = null;
let scanAnimationFrameId = null;

function stopCameraScan() {
  if (scanAnimationFrameId) cancelAnimationFrame(scanAnimationFrameId);
  scanAnimationFrameId = null;
  if (scanStream) scanStream.getTracks().forEach((track) => track.stop());
  scanStream = null;
  scanVideo.srcObject = null;
  scanVideo.classList.add("hidden");
  vis.classList.remove("hidden");
}

function renderScanChooser(scanError) {
  clearTimers();
  stopCameraScan();
  badge.className = "badge gray";
  badge.textContent = "Choose a bin";
  vis.className = "visual waiting";
  vis.innerHTML = ICONS.waiting;
  title.textContent = "Scan a bin's QR code";
  msg.textContent = "Use your phone or laptop camera to scan the QR code printed on a bin, or open a bin from the list.";
  timer.classList.add("hidden");
  reward.classList.add("hidden");
  note.textContent = scanError || "Choose a bin before starting a deposit.";
  actions.innerHTML =
    '<button class="btn" id="startScanBtn" type="button">Scan</button>' +
    '<a class="btn2" href="../bins/index.html">Browse available bins</a>';
  document.getElementById("startScanBtn").addEventListener("click", startCameraScan);
  line1.textContent = "Choose a bin";
  line1m.textContent = "Scan its QR code or pick it from the list";
  line2.textContent = "Insert one item";
  line2m.textContent = "Available after a session starts";
  line3.textContent = "Deposit and reward";
  line3m.textContent = "Available after a session starts";
}

async function startCameraScan() {
  note.textContent = "Requesting camera access…";
  try {
    scanStream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: { ideal: "environment" } },
      audio: false
    });
  } catch {
    renderScanChooser("Could not access a camera. Check permissions, or use the bins list instead.");
    return;
  }
  scanVideo.srcObject = scanStream;
  await scanVideo.play();
  vis.classList.add("hidden");
  scanVideo.classList.remove("hidden");
  badge.className = "badge blue";
  badge.textContent = "Scanning…";
  title.textContent = "Point the camera at a bin's QR code";
  msg.textContent = "Hold steady and keep the whole QR code inside the frame.";
  note.textContent = "Looking for a QR code…";
  actions.innerHTML = '<button class="btn2" id="cancelScanBtn" type="button">Cancel</button>';
  document.getElementById("cancelScanBtn").addEventListener("click", () => renderScanChooser());
  const canvasContext = scanCanvas.getContext("2d");
  const scanFrame = () => {
    if (scanVideo.readyState === scanVideo.HAVE_ENOUGH_DATA) {
      scanCanvas.width = scanVideo.videoWidth;
      scanCanvas.height = scanVideo.videoHeight;
      canvasContext.drawImage(scanVideo, 0, 0, scanCanvas.width, scanCanvas.height);
      const frame = canvasContext.getImageData(0, 0, scanCanvas.width, scanCanvas.height);
      const result = jsQR(frame.data, frame.width, frame.height);
      if (result?.data) {
        scanVideo.pause();
        badge.className = "badge blue";
        badge.textContent = "QR captured";
        title.textContent = "Reading the QR content…";
        msg.textContent = "We found the QR code and are checking the bin…";
        note.textContent = "Checking bin details…";
        actions.innerHTML = "";
        showDecodedQrResult(result.data);
        return;
      }
      note.textContent = "Looking for a QR code…";
    }
    scanAnimationFrameId = requestAnimationFrame(scanFrame);
  };
  scanAnimationFrameId = requestAnimationFrame(scanFrame);
}

async function showDecodedQrResult(decodedText) {
  const payload = parseQrPayload(decodedText);
  try {
    const bins = await getBins();
    const bin = findBinForQrPayload(bins, payload);
    stopCameraScan();
    renderDecodedQrResult(bin);
  } catch (error) {
    stopCameraScan();
    const message = error instanceof ApiError
      ? error.message
      : "Could not check this QR code against available bins.";
    renderScanChooser(message);
  }
}

function renderDecodedQrResult(bin) {
  const isAvailable = bin?.status === "ACTIVE";
  const statusLabel = bin ? binStatusLabel(bin.status) : "";

  vis.className = `visual ${bin ? "success" : "partial"}`;
  vis.innerHTML = bin ? ICONS.success : ICONS.fail;
  badge.className = `badge ${bin ? "green" : "orange"}`;
  badge.textContent = bin ? "Bin found" : "Not recognized";
  title.textContent = bin ? "MonoMates bin found" : "No matching bin found";
  msg.textContent = bin
    ? "Check the bin details below before continuing."
    : "This QR code is not linked to an available MonoMates bin.";
  timer.classList.add("hidden");
  reward.classList.add("hidden");

  note.innerHTML = `
    <div class="scan-result" aria-live="polite">
      ${bin ? `
        <div class="scan-result__row">
          <span>Bin</span>
          <strong>${escapeHtml(bin.name)}</strong>
        </div>
        <div class="scan-result__row">
          <span>Bin code</span>
          <strong>${escapeHtml(bin.publicCode)}</strong>
        </div>
        <div class="scan-result__row">
          <span>Status</span>
          <strong>${escapeHtml(statusLabel)}</strong>
        </div>
      ` : `
        <div class="scan-result__row">
          <span>Result</span>
          <strong>Try another MonoMates bin QR code.</strong>
        </div>
      `}
    </div>
  `;

  if (bin) {
    backToBinLink.href = `../bins/detail.html?code=${encodeURIComponent(bin.publicCode)}`;
    actions.innerHTML = isAvailable
      ? '<button class="btn" id="useScannedBinBtn" type="button">Continue with this bin</button><button class="btn2" id="scanAgainBtn" type="button">Scan again</button>'
      : '<a class="btn" id="viewScannedBinLink">View bin details</a><button class="btn2" id="scanAgainBtn" type="button">Scan again</button>';
    if (isAvailable) {
      document.getElementById("useScannedBinBtn").addEventListener("click", () => {
        const url = new URL(window.location.href);
        url.searchParams.set("code", bin.publicCode);
        url.searchParams.delete("sessionId");
        window.history.replaceState(null, "", url);
        beginSessionForBin(bin.publicCode);
      });
    } else {
      const link = document.getElementById("viewScannedBinLink");
      link.href = `../bins/detail.html?code=${encodeURIComponent(bin.publicCode)}`;
    }
  } else {
    actions.innerHTML = '<button class="btn" id="scanAgainBtn" type="button">Scan again</button><a class="btn2" href="../bins/index.html">Browse available bins</a>';
  }
  document.getElementById("scanAgainBtn").addEventListener("click", startCameraScan);

  line1.textContent = "QR code read";
  line1m.textContent = "Code read successfully";
  line2.textContent = bin ? "Bin matched" : "No bin match";
  line2m.textContent = bin ? bin.publicCode : "Try another QR code";
  line3.textContent = isAvailable ? "Ready to continue" : "Session not started";
  line3m.textContent = isAvailable
    ? "Confirm the matched bin first"
    : bin
      ? `This bin is currently ${statusLabel.toLowerCase()}`
      : "No registered bin was selected";
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

async function handleItemScan() {
  if (!currentSession?.sessionId) return;
  const sessionId = currentSession.sessionId;
  document.getElementById("scanItemBtn")?.setAttribute("disabled", "");
  try {
    const session = await requestItemScan(sessionId);
    if (currentSession?.sessionId !== sessionId) return;
    renderSession(session);
  } catch (error) {
    const message = error instanceof ApiError
      ? error.message
      : "Could not ask this bin to scan the item.";
    window.toast?.(message, "error");
    document.getElementById("scanItemBtn")?.removeAttribute("disabled");
  }
}

function persistSessionUrl(session) {
  const url = new URL(window.location.href);
  url.searchParams.set("code", session.binCode);
  url.searchParams.set("sessionId", session.sessionId);
  window.history.replaceState(null, "", url);
  backToBinLink.href = `../bins/detail.html?code=${encodeURIComponent(session.binCode)}`;
}

async function retryCurrentBin() {
  const binCode = currentSession?.binCode || queryParam("code");
  if (!binCode) {
    renderScanChooser("The previous bin could not be identified. Scan its QR code again.");
    return;
  }

  ++pollGeneration;
  clearTimers();
  badge.className = "badge blue";
  badge.textContent = "Starting again…";
  vis.className = "visual waiting";
  vis.innerHTML = ICONS.waiting;
  title.textContent = "Starting a new session for this bin";
  msg.textContent = `Keeping ${binCode} selected so you can try the item again.`;
  timer.classList.add("hidden");
  reward.classList.add("hidden");
  note.textContent = "Opening a new deposit session…";
  actions.innerHTML = '<button class="btn" type="button" disabled>Starting…</button>';
  line2.textContent = "Restarting session";
  line2m.textContent = "Using the same bin";
  line3.textContent = "Deposit and reward";
  line3m.textContent = "Waiting for the new session";

  const url = new URL(window.location.href);
  url.searchParams.set("code", binCode);
  url.searchParams.delete("sessionId");
  window.history.replaceState(null, "", url);
  backToBinLink.href = `../bins/detail.html?code=${encodeURIComponent(binCode)}`;
  await beginSessionForBin(binCode);
}

function renderSameBinRetryActions(label = "Try this bin again") {
  actions.innerHTML = `<button class="btn" id="retryCurrentBinBtn" type="button">${escapeHtml(label)}</button><a class="btn2" href="../bins/index.html">Choose another bin</a>`;
  document.getElementById("retryCurrentBinBtn").addEventListener("click", retryCurrentBin);
}

function renderSession(session) {
  currentSession = session;

  if (session.status === "ACTIVE") {
    badge.className = "badge blue";
    badge.innerHTML = `<span class="dot"></span>Session active · ${escapeHtml(session.binCode)}`;
    vis.className = "visual waiting";
    vis.innerHTML = ICONS.waiting;
    const scanRequested = Boolean(session.scanRequestedAt);
    title.textContent = scanRequested
      ? "Scanning your item"
      : "Place one item in the scanner";
    msg.textContent = scanRequested
      ? "The bin is checking the item. Keep it in place until the result appears."
      : "When the item is ready, press Scan item to start the bin's camera.";
    reward.classList.add("hidden");
    note.textContent = scanRequested
      ? "Waiting for the bin to return Accepted, Not accepted, or Invalid."
      : "The bin will not scan until you press the button below.";
    actions.innerHTML = scanRequested
      ? '<button class="btn" type="button" disabled>Scanning item…</button><button class="btndanger" id="cancelBtn">Cancel session</button>'
      : '<button class="btn" id="scanItemBtn" type="button">Scan item</button><button class="btndanger" id="cancelBtn">Cancel session</button>';
    document.getElementById("scanItemBtn")?.addEventListener("click", handleItemScan);
    document.getElementById("cancelBtn").addEventListener("click", handleCancel);
    line2.textContent = scanRequested ? "Scanning item" : "Ready for item";
    line2m.textContent = scanRequested ? "Waiting for the bin" : "Press Scan item when ready";
    line3.textContent = "Deposit result";
    line3m.textContent = "Waiting for confirmation";
    // The countdown only appears once the person has actually pressed Scan
    // item — before that, the bin isn't watching yet, so showing a ticking
    // clock would just be counting down time nothing is happening against.
    if (scanRequested) {
      timer.classList.remove("hidden");
      startCountdown(session.startedAt, session.expiresAt);
    } else {
      timer.classList.add("hidden");
      clearInterval(countdownInterval);
    }
    return;
  }

  clearTimers();
  timer.classList.add("hidden");
  line2.textContent = "Item check finished";
  line2m.textContent = "Session closed";

  if (session.status === "COMPLETED") {
    const tokens = session.tokensAwarded;
    const accepted = session.deposit?.status === "ACCEPTED";
    reward.classList.remove("hidden");
    amount.textContent = `+${tokens} PT`;
    if (accepted) {
      badge.className = "badge green";
      badge.textContent = "Rewarded";
      vis.className = "visual success";
      vis.innerHTML = ICONS.success;
      title.textContent = "Accepted";
      msg.textContent = "Your deposit was accepted and the reward was added.";
      amount.style.color = "#15803d";
      breakdown.innerHTML = '<div class="drow"><span>Accepted clear bottle</span><strong>+1 PT</strong></div>';
    } else {
      badge.className = "badge orange";
      badge.textContent = "Invalid";
      vis.className = "visual partial";
      vis.innerHTML = ICONS.success;
      title.textContent = "Invalid";
      msg.textContent = "A deposit was detected, but it could not be confirmed as an accepted bottle, so no reward was added.";
      amount.style.color = "#b45309";
      breakdown.innerHTML = '<div class="drow"><span>Accepted clear bottle</span><strong>Not confirmed</strong></div>';
    }
    note.textContent = "This result has been recorded to your activity.";
    actions.innerHTML = '<a class="btn" href="../activity/index.html">View activity</a><a class="btn2" href="../bins/index.html">Find another bin</a>';
    line3.textContent = `${tokens} PT awarded`;
    line3m.textContent = "Reward added to balance";
    if (tokens > 0) renderTokenBalancePill();
  } else if (session.status === "EXPIRED") {
    badge.className = "badge red";
    badge.textContent = "Expired";
    vis.className = "visual fail";
    vis.innerHTML = ICONS.fail;
    title.textContent = "No item was detected";
    msg.textContent = "The session window ended. No token was awarded.";
    note.textContent = "A QR scan alone is never enough to earn a reward.";
    renderSameBinRetryActions();
    line3.textContent = "0 PT awarded";
    line3m.textContent = "Session expired";
  } else if (session.status === "REJECTED") {
    badge.className = "badge red";
    badge.textContent = "Rejected";
    vis.className = "visual fail";
    vis.innerHTML = ICONS.fail;
    title.textContent = "Not accepted";
    msg.textContent = "The bin could not confirm a valid deposit. No token was awarded.";
    note.textContent = "A QR scan alone is never enough to earn a reward.";
    renderSameBinRetryActions();
    line3.textContent = "0 PT awarded";
    line3m.textContent = "No reward was added";
  } else if (session.status === "CANCELLED") {
    badge.className = "badge gray";
    badge.textContent = "Cancelled";
    vis.className = "visual fail";
    vis.innerHTML = ICONS.fail;
    title.textContent = "Session cancelled";
    msg.textContent = "You cancelled this deposit session. No token was awarded.";
    note.textContent = "";
    renderSameBinRetryActions("Scan again");
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
    const binCode = currentSession?.binCode;
    renderError(message, binCode ? { retryHref: `../bins/detail.html?code=${encodeURIComponent(binCode)}` } : {});
  }
}

async function beginSessionForBin(publicCode) {
  try {
    const session = await startDepositSession(publicCode);
    persistSessionUrl(session);
    renderSession(session);
    if (session.status === "ACTIVE") pollSession(session.sessionId);
  } catch (error) {
    const message = error instanceof ApiError ? error.message : "Could not start a deposit session for this bin.";
    renderError(message, { retryHref: `../bins/detail.html?code=${encodeURIComponent(publicCode)}` });
  }
}

async function init() {
  const publicCode = queryParam("code");
  const existingSessionId = queryParam("sessionId");
  if (!publicCode && !existingSessionId) {
    renderScanChooser();
    return;
  }
  if (publicCode) {
    backToBinLink.href = `../bins/detail.html?code=${encodeURIComponent(publicCode)}`;
  }
  if (existingSessionId) {
    try {
      const session = await getDepositSession(existingSessionId);
      persistSessionUrl(session);
      renderSession(session);
      if (session.status === "ACTIVE") pollSession(session.sessionId);
    } catch (error) {
      const message = error instanceof ApiError ? error.message : "Could not start a deposit session for this bin.";
      renderError(message, { retryHref: `../bins/detail.html?code=${encodeURIComponent(publicCode)}` });
    }
    return;
  }
  await beginSessionForBin(publicCode);
}

window.addEventListener("pagehide", stopCameraScan);

init();
injectSecretSortControls();
