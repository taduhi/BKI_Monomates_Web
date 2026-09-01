import { getCurrentUser } from "../api/auth-api.js";
import { POST_LOGIN_REDIRECT_KEY } from "../utils/auth-intent.js";

export async function requireAuthentication(loginUrl = "../auth/login.html") {
  try {
    return await getCurrentUser();
  } catch {
    const intent = `${window.location.pathname}${window.location.search}`;
    sessionStorage.setItem(POST_LOGIN_REDIRECT_KEY, intent);
    window.location.replace(loginUrl);
    return null;
  }
}
