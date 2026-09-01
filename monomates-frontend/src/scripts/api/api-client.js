import { API_BASE_URL } from "../config.js";

export class ApiError extends Error {
  constructor(message, status, details = null) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.details = details;
  }
}

const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS"]);

function readCookie(name) {
  const prefix = `${encodeURIComponent(name)}=`;
  const cookie = document.cookie
    .split("; ")
    .find((entry) => entry.startsWith(prefix));
  return cookie ? decodeURIComponent(cookie.slice(prefix.length)) : null;
}

async function getCsrfToken(signal) {
  const cookieToken = readCookie("XSRF-TOKEN");
  if (cookieToken) return cookieToken;

  const response = await fetch(`${API_BASE_URL}/auth/csrf`, {
    credentials: "include",
    headers: { Accept: "application/json" },
    signal
  });
  const result = response.headers
    .get("content-type")
    ?.includes("application/json")
    ? await response.json()
    : null;

  if (!response.ok) {
    throw new ApiError(
      result?.message ?? "Security token could not be initialized.",
      response.status,
      result
    );
  }

  return result?.token ?? readCookie("XSRF-TOKEN");
}

export async function apiRequest(path, options = {}) {
  const { method = "GET", body, headers = {}, signal } = options;
  const normalizedMethod = method.toUpperCase();
  const requestHeaders = {
    Accept: "application/json",
    ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
    ...headers
  };

  if (!SAFE_METHODS.has(normalizedMethod)) {
    const csrfToken = await getCsrfToken(signal);
    if (!csrfToken) {
      throw new ApiError("Security token could not be initialized.", 0);
    }
    requestHeaders["X-XSRF-TOKEN"] = csrfToken;
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: normalizedMethod,
    credentials: "include",
    headers: requestHeaders,
    body: body !== undefined ? JSON.stringify(body) : undefined,
    signal
  });

  if (response.status === 204) return null;

  const isJson = response.headers.get("content-type")?.includes("application/json");
  const result = isJson ? await response.json() : null;

  if (!response.ok) {
    throw new ApiError(result?.message ?? "The request could not be completed.", response.status, result);
  }

  return result;
}
