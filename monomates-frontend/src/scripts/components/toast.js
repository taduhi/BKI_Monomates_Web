let toastTimer;

export function showToast(message, type = "") {
  const element = document.getElementById("toast");
  if (!element) return;
  element.className = `toast ${type}`.trim();
  element.textContent = message;
  requestAnimationFrame(() => element.classList.add("show"));
  clearTimeout(toastTimer);
  toastTimer = window.setTimeout(() => element.classList.remove("show"), 2800);
}

window.toast = showToast;
