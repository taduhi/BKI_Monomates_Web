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
    if (user.role !== "ADMIN") removeAdminLinks(adminLinks);
  } catch {
    removeAdminLinks(adminLinks);
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

function removeAdminLinks(adminLinks) {
  const parentNavigations = new Set();
  adminLinks.forEach((link) => {
    if (link.parentElement?.tagName === "NAV") parentNavigations.add(link.parentElement);
    link.remove();
  });
  parentNavigations.forEach((navigation) => {
    if (navigation.querySelector("a")) return;
    const label = navigation.previousElementSibling;
    if (label?.classList.contains("navlabel")) label.remove();
    navigation.remove();
  });
}

initializeUserChrome();
