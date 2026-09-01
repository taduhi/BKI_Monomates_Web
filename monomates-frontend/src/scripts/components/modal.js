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

export function initializeModals() {
  document.addEventListener("click", (event) => {
    if (event.target.classList.contains("modalbg")) closeModal(event.target.id);
  });
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") {
      document.querySelectorAll(".modalbg.open").forEach((modal) => closeModal(modal.id));
    }
  });
}

window.openModal = openModal;
window.closeModal = closeModal;
