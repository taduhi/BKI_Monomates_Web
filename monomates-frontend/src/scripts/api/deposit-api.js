import { apiRequest } from "./api-client.js";
export const getDepositSession = (sessionId) => apiRequest(`/sessions/${encodeURIComponent(sessionId)}`);
export const cancelDepositSession = (sessionId) => apiRequest(`/sessions/${encodeURIComponent(sessionId)}/cancel`, { method: "POST" });
export const getMyDeposits = () => apiRequest("/users/me/deposits");
