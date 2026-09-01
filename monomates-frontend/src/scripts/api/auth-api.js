import { apiRequest } from "./api-client.js";
export const login = (credentials) => apiRequest("/auth/login", { method: "POST", body: credentials });
export const register = (account) => apiRequest("/auth/register", { method: "POST", body: account });
export const logout = () => apiRequest("/auth/logout", { method: "POST" });
export const getCurrentUser = () => apiRequest("/auth/me");
