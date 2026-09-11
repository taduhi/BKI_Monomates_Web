import { getTokenBalance } from "../api/rewards-api.js";
import { ApiError } from "../api/api-client.js";

export async function renderTokenBalancePill(selector = ".tokenpill") {
  const element = document.querySelector(selector);
  if (!element) return;
  try {
    const { balance } = await getTokenBalance();
    setPillText(element, `${balance} PT`);
  } catch (error) {
    // A 401 means the session ended (e.g. the cookie expired) and the auth
    // guard on this page will already be redirecting to login — hide the
    // pill rather than show a value that's about to disappear anyway. Any
    // other failure (network drop, 500) is unrelated to the user's login
    // state, so show a dash instead of silently vanishing their balance.
    if (error instanceof ApiError && error.status === 401) {
      element.classList.add("hidden");
    } else {
      setPillText(element, "— PT");
    }
  }
}

function setPillText(element, text) {
  const svg = element.querySelector("svg");
  element.textContent = "";
  if (svg) element.appendChild(svg);
  element.appendChild(document.createTextNode(` ${text}`));
}
