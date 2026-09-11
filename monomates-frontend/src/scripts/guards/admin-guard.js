import { requireAuthentication } from "./auth-guard.js";

export async function requireAdmin(fallbackUrl = "../bins/index.html") {
  // requireAuthentication already sends an unauthenticated visitor to the
  // login page (remembering this URL so they land back here after signing
  // in). Only a signed-in non-admin gets bounced to fallbackUrl instead.
  const user = await requireAuthentication();
  if (!user) return null;
  if (user.role !== "ADMIN") {
    window.location.replace(fallbackUrl);
    return null;
  }
  return user;
}
