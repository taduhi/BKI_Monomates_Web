import { requireAuthentication } from "../guards/auth-guard.js";
import { logout } from "../api/auth-api.js";
import { getProfile, updateProfile } from "../api/profile-api.js";
import { getMyDeposits } from "../api/deposit-api.js";
import { getTokenBalance } from "../api/rewards-api.js";
import { ApiError } from "../api/api-client.js";
import { renderTokenBalancePill } from "../components/token-balance.js";
import { setLoading } from "../components/loading.js";

await requireAuthentication();
renderTokenBalancePill();

let currentBalance = 0;
let currentDepositCount = 0;

function initials(fullName) {
  return fullName
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toUpperCase() || "MM";
}

function renderProfile(profile, balance, deposits) {
  currentBalance = balance;
  currentDepositCount = deposits.length;
  profileAvatar.textContent = initials(profile.fullName);
  profileName.textContent = profile.fullName;
  profileEmail.textContent = profile.email;
  profileFullName.textContent = profile.fullName;
  profileEmailDetail.textContent = profile.email;
  profileStatus.textContent = profile.status === "ACTIVE" ? "Active member" : profile.status;
  profileTokens.textContent = String(balance);
  profileDeposits.textContent = String(deposits.length);
  profileFullNameInput.value = profile.fullName;
  profileEmailInput.value = profile.email;
}

async function loadProfile() {
  try {
    const [profile, balance, deposits] = await Promise.all([
      getProfile(),
      getTokenBalance(),
      getMyDeposits()
    ]);
    renderProfile(profile, balance.balance, deposits);
  } catch (error) {
    window.toast?.(
      error instanceof ApiError ? error.message : "Could not load your profile.",
      "error"
    );
  }
}

profileSaveButton.addEventListener("click", async () => {
  profileFormError.classList.add("hidden");
  const fullName = profileFullNameInput.value.trim();
  if (!fullName) {
    profileFormError.textContent = "Full name is required.";
    profileFormError.classList.remove("hidden");
    return;
  }
  setLoading(profileSaveButton, true, "Saving…");
  try {
    const updated = await updateProfile({ fullName });
    renderProfile(
      updated,
      currentBalance,
      Array.from({ length: currentDepositCount })
    );
    closeModal("edit");
    window.toast?.("Profile saved.", "success");
  } catch (error) {
    profileFormError.textContent = error instanceof ApiError
      ? error.message
      : "Could not save your profile.";
    profileFormError.classList.remove("hidden");
  } finally {
    setLoading(profileSaveButton, false);
  }
});

document.querySelectorAll(".toggle[data-preference]").forEach((button) => {
  const key = `monomates:preference:${button.dataset.preference}`;
  const enabled = localStorage.getItem(key) !== "false";
  button.setAttribute("aria-pressed", String(enabled));
  button.addEventListener("click", () => {
    const next = button.getAttribute("aria-pressed") !== "true";
    button.setAttribute("aria-pressed", String(next));
    localStorage.setItem(key, String(next));
  });
});

const logoutLink = document.querySelector('a[href="../auth/login.html"].btndanger');
if (logoutLink) {
  logoutLink.addEventListener("click", async (event) => {
    event.preventDefault();
    try {
      await logout();
    } finally {
      window.location.href = "../auth/login.html";
    }
  });
}

loadProfile();
