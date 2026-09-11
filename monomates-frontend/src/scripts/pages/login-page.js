import { login, register } from "../api/auth-api.js";
import { ApiError } from "../api/api-client.js";
import { setLoading } from "../components/loading.js";
import { POST_LOGIN_REDIRECT_KEY } from "../utils/auth-intent.js";

const DEFAULT_REDIRECT = "../bins/index.html";
const loginTab = document.getElementById("lt");
const signupTab = document.getElementById("st");
const nameField = document.getElementById("nf");
const fullNameInput = document.getElementById("fullName");
const emailInput = document.getElementById("email");
const passwordInput = document.getElementById("pw");
const passwordToggle = document.getElementById("passwordToggle");
const authTitle = document.getElementById("at");
const authSubtitle = document.getElementById("as");
const submitButton = document.getElementById("sb");
const authForm = document.getElementById("authForm");
const formError = document.getElementById("formError");

function auth(mode) {
  const isSignup = mode === "signup";
  loginTab.classList.toggle("active", !isSignup);
  signupTab.classList.toggle("active", isSignup);
  loginTab.setAttribute("aria-selected", String(!isSignup));
  signupTab.setAttribute("aria-selected", String(isSignup));
  nameField.classList.toggle("hidden", !isSignup);
  fullNameInput.required = isSignup;
  passwordInput.autocomplete = isSignup ? "new-password" : "current-password";
  authTitle.textContent = isSignup ? "Create your account" : "Welcome back";
  authSubtitle.textContent = isSignup
    ? "Start earning Plastique Tokens through verified deposits."
    : "Sign in to manage your tokens and deposit sessions.";
  submitButton.innerHTML = `${isSignup ? "Create account" : "Log in"} →`;
  submitButton.dataset.defaultLabel = submitButton.textContent.trim();
  hideFormError();
}
loginTab.addEventListener("click", () => auth("login"));
signupTab.addEventListener("click", () => auth("signup"));
passwordToggle.addEventListener("click", () => {
  const show = passwordInput.type === "password";
  passwordInput.type = show ? "text" : "password";
  passwordToggle.setAttribute("aria-pressed", String(show));
  passwordToggle.setAttribute("aria-label", show ? "Hide password" : "Show password");
});

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

  const isSignup = signupTab.classList.contains("active");
  setLoading(submitButton, true, isSignup ? "Creating account…" : "Signing in…");

  try {
    if (isSignup) {
      await register({
        fullName: fullNameInput.value.trim(),
        email: emailInput.value.trim(),
        password: passwordInput.value
      });
    } else {
      await login({ email: emailInput.value.trim(), password: passwordInput.value });
    }
    redirectAfterAuth();
  } catch (error) {
    const message =
      error instanceof ApiError
        ? error.message
        : "Something went wrong. Please try again.";
    showFormError(message);
  } finally {
    setLoading(submitButton, false);
  }
});
