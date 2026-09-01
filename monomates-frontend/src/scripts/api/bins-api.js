import { apiRequest } from "./api-client.js";
export const getBins = (query = "") => apiRequest(`/bins${query ? `?${query}` : ""}`);
export const getBin = (publicCode) => apiRequest(`/bins/${encodeURIComponent(publicCode)}`);
export const startDepositSession = (publicCode) => apiRequest(`/bins/${encodeURIComponent(publicCode)}/sessions`, { method: "POST" });
