export function openModal(id) {
  const modal = document.getElementById(id);
  if (!modal) return;
  modal.classList.add("open");
  document.body.style.overflow = "hidden";
}

export function closeModal(id) {
  const modal = document.getElementById(id);
  if (!modal) return;
  modal.classList.remove("open");
  document.body.style.overflow = "";
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
