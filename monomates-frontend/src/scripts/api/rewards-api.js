import { apiRequest } from "./api-client.js";
export const getVouchers = () => apiRequest("/vouchers");
export const redeemVoucher = (voucherId) => apiRequest(`/vouchers/${encodeURIComponent(voucherId)}/redeem`, { method: "POST" });
export const getTokenLedger = () => apiRequest("/users/me/token-ledger");
export const getTokenBalance = () => apiRequest("/users/me/token-balance");
export const getRedemptions = () => apiRequest("/users/me/redemptions");
