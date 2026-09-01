export function setLoading(element, isLoading, label = "Loading…") {
  if (!element) return;
  element.toggleAttribute("disabled", isLoading);
  element.setAttribute("aria-busy", String(isLoading));
  if (!element.dataset.defaultLabel) element.dataset.defaultLabel = element.textContent.trim();
  element.textContent = isLoading ? label : element.dataset.defaultLabel;
}
