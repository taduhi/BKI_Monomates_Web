import { getTokenBalance } from "../api/rewards-api.js";

export async function renderTokenBalancePill(selector = ".tokenpill") {
  const element = document.querySelector(selector);
  if (!element) return;
  try {
    const { balance } = await getTokenBalance();
    const svg = element.querySelector("svg");
    element.textContent = "";
    if (svg) element.appendChild(svg);
    element.appendChild(document.createTextNode(` ${balance} PT`));
  } catch {
    element.classList.add("hidden");
  }
}
