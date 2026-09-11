import { initializeModals } from "./components/modal.js";
import { getCurrentUser } from "./api/auth-api.js";
import "./components/toast.js";

initializeModals();

async function initializeUserChrome() {
  const adminLinks = document.querySelectorAll(
    'a[href*="/admin/"], a[href^="admin/"], a[href^="../admin/"]'
  );
  const userNameElements = document.querySelectorAll(".user-mini b");
  const userRoleElements = document.querySelectorAll(".user-mini .tiny");
  const avatarElements = document.querySelectorAll(".user-mini .avatar");
  if (
    adminLinks.length === 0 &&
    userNameElements.length === 0 &&
    userRoleElements.length === 0
  ) return;

  // Every account (including guests) briefly saw the static "Administrator"
  // nav link and a placeholder "LC" avatar while this check was pending.
  // Hide/blank them up front and only reveal once we actually know who's
  // signed in, instead of showing-then-hiding after the fact.
  setAdminNavVisible(adminLinks, false);
  avatarElements.forEach((element) => {
    element.textContent = "";
  });

  try {
    const user = await getCurrentUser();
    userNameElements.forEach((element) => {
      element.textContent = user.fullName;
    });
    userRoleElements.forEach((element) => {
      element.textContent = user.role === "ADMIN" ? "Administrator" : "Member";
    });
    const initials = user.fullName
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0])
      .join("")
      .toUpperCase();
    avatarElements.forEach((element) => {
      element.textContent = initials || "MM";
    });
    setAdminNavVisible(adminLinks, user.role === "ADMIN");
  } catch {
    userNameElements.forEach((element) => {
      element.textContent = "Guest";
    });
    userRoleElements.forEach((element) => {
      element.textContent = "Sign in to earn tokens";
    });
    avatarElements.forEach((element) => {
      element.textContent = "MM";
    });
  }
}

function setAdminNavVisible(adminLinks, visible) {
  const parentNavigations = new Set();
  adminLinks.forEach((link) => {
    if (link.parentElement?.tagName === "NAV") parentNavigations.add(link.parentElement);
  });
  parentNavigations.forEach((navigation) => {
    navigation.classList.toggle("hidden", !visible);
    const label = navigation.previousElementSibling;
    if (label?.classList.contains("navlabel")) label.classList.toggle("hidden", !visible);
  });
}

initializeUserChrome();
