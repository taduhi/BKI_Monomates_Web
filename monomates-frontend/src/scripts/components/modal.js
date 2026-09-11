const previousFocus = new Map();

export function openModal(id) {
  const modal = document.getElementById(id);
  if (!modal) return;
  previousFocus.set(id, document.activeElement);
  modal.classList.add("open");
  modal.setAttribute("aria-hidden", "false");
  document.body.style.overflow = "hidden";
  requestAnimationFrame(() => modal.querySelector(".modal")?.focus());
}

export function closeModal(id) {
  const modal = document.getElementById(id);
  if (!modal) return;
  modal.classList.remove("open");
  modal.setAttribute("aria-hidden", "true");
  document.body.style.overflow = "";
  const trigger = previousFocus.get(id);
  if (trigger instanceof HTMLElement && trigger.isConnected) trigger.focus();
  previousFocus.delete(id);
}

// Call around an in-flight request that a closed modal would hide but not
// cancel (e.g. redeeming a voucher) so a stray Escape press or backdrop
// click can't dismiss the modal while its result is still pending.
export function lockModal(id) {
  const modal = document.getElementById(id);
  if (modal) modal.dataset.locked = "true";
}

export function unlockModal(id) {
  const modal = document.getElementById(id);
  if (modal) delete modal.dataset.locked;
}

export function initializeModals() {
  document.querySelectorAll(".modalbg").forEach((backdrop, index) => {
    backdrop.setAttribute("aria-hidden", "true");
    const dialog = backdrop.querySelector(".modal");
    if (!dialog) return;
    dialog.setAttribute("role", "dialog");
    dialog.setAttribute("aria-modal", "true");
    dialog.setAttribute("tabindex", "-1");
    const heading = dialog.querySelector("h1, h2, h3");
    if (heading) {
      if (!heading.id) heading.id = `modal-title-${index + 1}`;
      dialog.setAttribute("aria-labelledby", heading.id);
    }
  });
  document.addEventListener("click", (event) => {
    if (event.target.classList.contains("modalbg") && event.target.dataset.locked !== "true") {
      closeModal(event.target.id);
    }
  });
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") {
      document
        .querySelectorAll(".modalbg.open")
        .forEach((modal) => { if (modal.dataset.locked !== "true") closeModal(modal.id); });
    }
  });
}

window.openModal = openModal;
window.closeModal = closeModal;
