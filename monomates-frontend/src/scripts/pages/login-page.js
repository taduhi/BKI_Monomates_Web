import { login, register } from "../api/auth-api.js";
import { ApiError } from "../api/api-client.js";
import { setLoading } from "../components/loading.js";
import { POST_LOGIN_REDIRECT_KEY } from "../utils/auth-intent.js";

const DEFAULT_REDIRECT = "../bins/index.html";

function auth(mode) {
  const isSignup = mode === "signup";
  lt.classList.toggle("active", !isSignup);
  st.classList.toggle("active", isSignup);
  nf.classList.toggle("hidden", !isSignup);
  fullName.required = isSignup;
  at.textContent = isSignup ? "Create your account" : "Welcome back";
  as.textContent = isSignup
    ? "Start earning Plastique Tokens through verified deposits."
    : "Sign in to manage your tokens and deposit sessions.";
  sb.innerHTML = `${isSignup ? "Create account" : "Log in"} →`;
  sb.dataset.defaultLabel = sb.textContent.trim();
  hideFormError();
}
window.auth = auth;

function hideFormError() {
  formError.classList.add("hidden");
  formError.textContent = "";
}

function showFormError(message) {
  formError.textContent = message;
  formError.classList.remove("hidden");
}

function redirectAfterAuth() {
  const target = sessionStorage.getItem(POST_LOGIN_REDIRECT_KEY);
  sessionStorage.removeItem(POST_LOGIN_REDIRECT_KEY);
  window.location.href = target || DEFAULT_REDIRECT;
}

authForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  hideFormError();

  const isSignup = st.classList.contains("active");
  setLoading(sb, true, isSignup ? "Creating account…" : "Signing in…");

  try {
    if (isSignup) {
      await register({
        fullName: fullName.value.trim(),
        email: email.value.trim(),
        password: pw.value
      });
    } else {
      await login({ email: email.value.trim(), password: pw.value });
    }
    redirectAfterAuth();
  } catch (error) {
    const message =
      error instanceof ApiError
        ? error.message
        : "Something went wrong. Please try again.";
    showFormError(message);
  } finally {
    setLoading(sb, false);
  }
});
