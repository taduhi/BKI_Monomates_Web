export function byId(id) { return document.getElementById(id); }
export function escapeHtml(value) { const element = document.createElement("div"); element.textContent = String(value ?? ""); return element.innerHTML; }
export function queryParam(name) { return new URLSearchParams(window.location.search).get(name); }
