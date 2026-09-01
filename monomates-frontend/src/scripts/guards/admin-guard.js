import { getCurrentUser } from "../api/auth-api.js";

export async function requireAdmin(fallbackUrl = "../bins/index.html") {
  try {
    const user = await getCurrentUser();
    if (user.role !== "ADMIN") window.location.replace(fallbackUrl);
    return user;
  } catch {
    window.location.replace(fallbackUrl);
    return null;
  }
}
