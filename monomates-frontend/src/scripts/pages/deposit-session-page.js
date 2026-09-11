import jsQR from "jsqr";
import { requireAuthentication } from "../guards/auth-guard.js";
import { getBins, startDepositSession } from "../api/bins-api.js";
import { getDepositSession, cancelDepositSession, secretSort } from "../api/deposit-api.js";
import { ApiError } from "../api/api-client.js";
import { queryParam, escapeHtml } from "../utils/dom.js";
import { findBinForQrPayload, parseQrPayload } from "../utils/qr-code.js";
import { renderTokenBalancePill } from "../components/token-balance.js";

await requireAuthentication();
renderTokenBalancePill();

// Always rendered for every logged-in account, identically — there is no
// client-side identity check left here on purpose. Real authorization lives
// entirely server-side in DemoSecretSortController (only the account whose
// email matches app.demo.secret-account-email gets a real effect; any other
// account gets a 404 and the click is silently a no-op). Looking like a
// small decorative "sensor status" indicator strip on every account, with
// no code path that reveals which account is special, is stronger secrecy
// than hiding the markup only for one account.
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
    b.style.cssText = `width:11px;height:11px;padding:0;border:1px solid rgba(0,0,0,.08);border-radius:50%;background:${color};cursor:pointer;box-shadow:0 0 0 2px #fff inset;`;
    b.addEventListener("click", onClick);
    return b;
  };

  // No client-side status gate: whichever session is currently loaded is
  // sent as-is, and the backend alone decides whether it is still valid.
  // This is deliberate — the button must keep working for the one account
  // that is allowed to use it regardless of session/scan state, and it must
  // stay a harmless no-op (a 404 swallowed here) for every other account.
  const sort = async (outcome) => {
    if (!currentSession?.sessionId) return;
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
  note.textContent = scanError || "A session only starts after a specific bin has been selected.";
  actions.innerHTML =
    '<button class="btn" id="startScanBtn" type="button">Scan with camera</button>' +
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
        // Freeze on the exact frame the code was decoded from — a still,
        // visible capture the person can see matched — before releasing the
        // camera and comparing the decoded payload with the real bin list.
        scanVideo.pause();
        badge.className = "badge blue";
        badge.textContent = "QR captured";
        title.textContent = "Reading the QR content…";
        msg.textContent = "The QR code was decoded. Matching it to a MonoMates bin…";
        note.textContent = "Checking this bin's records…";
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
    renderDecodedQrResult(payload, bin);
  } catch (error) {
    stopCameraScan();
    const message = error instanceof ApiError
      ? error.message
      : "Could not check the decoded QR against the bin list.";
    renderScanChooser(message);
  }
}

function renderDecodedQrResult(payload, bin) {
  const candidate = payload.candidates[0] ?? "—";
  const isAvailable = bin?.status === "ACTIVE";

  vis.className = `visual ${bin ? "success" : "partial"}`;
  vis.innerHTML = bin ? ICONS.success : ICONS.fail;
  badge.className = `badge ${bin ? "green" : "orange"}`;
  badge.textContent = bin ? "Bin matched" : "QR decoded";
  title.textContent = bin ? "MonoMates bin found" : "No matching bin found";
  msg.textContent = bin
    ? "The decoded value matches a bin currently registered in MonoMates."
    : "The camera read this QR, but its value does not match any registered bin.";
  timer.classList.add("hidden");
  reward.classList.add("hidden");

  note.innerHTML = `
    <div class="scan-result" aria-live="polite">
      <div class="scan-result__row">
        <span>Decoded text</span>
        <code>${escapeHtml(payload.text)}</code>
      </div>
      <div class="scan-result__row">
        <span>Compared value</span>
        <code>${escapeHtml(candidate)}</code>
      </div>
      ${bin ? `
        <div class="scan-result__row">
          <span>Bin QR ID</span>
          <strong>${escapeHtml(bin.publicCode)}</strong>
        </div>
        <div class="scan-result__row">
          <span>Bin record ID</span>
          <code>${escapeHtml(bin.id)}</code>
        </div>
        <div class="scan-result__row">
          <span>Bin</span>
          <strong>${escapeHtml(bin.name)} · ${escapeHtml(bin.status)}</strong>
        </div>
      ` : `
        <div class="scan-result__row">
          <span>Match result</span>
          <strong>Not found in the current bin list</strong>
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

  line1.textContent = "QR decoded";
  line1m.textContent = payload.text;
  line2.textContent = bin ? "Bin matched" : "No bin match";
  line2m.textContent = bin ? bin.publicCode : "Try another QR code";
  line3.textContent = isAvailable ? "Ready to continue" : "Session not started";
  line3m.textContent = isAvailable
    ? "Confirm the matched bin first"
    : bin
      ? `Bin status: ${bin.status}`
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
