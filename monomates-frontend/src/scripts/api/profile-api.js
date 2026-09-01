import { apiRequest } from "./api-client.js";
export const getProfile = () => apiRequest("/users/me");
export const updateProfile = (profile) => apiRequest("/users/me", { method: "PATCH", body: profile });
