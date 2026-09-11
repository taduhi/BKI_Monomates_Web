import { apiRequest } from "./api-client.js";
export const getDepositSession = (sessionId) => apiRequest(`/sessions/${encodeURIComponent(sessionId)}`);
export const requestItemScan = (sessionId) => apiRequest(`/sessions/${encodeURIComponent(sessionId)}/scan`, { method: "POST" });
export const cancelDepositSession = (sessionId) => apiRequest(`/sessions/${encodeURIComponent(sessionId)}/cancel`, { method: "POST" });
export const getMyDeposits = () => apiRequest("/users/me/deposits");
export const secretSort = (sessionId, outcome) => apiRequest("/demo/secret-sort", { method: "POST", body: { sessionId, outcome } });
