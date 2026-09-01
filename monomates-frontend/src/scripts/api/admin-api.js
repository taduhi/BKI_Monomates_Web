import { apiRequest } from "./api-client.js";
export const getAdminBins = () => apiRequest("/admin/bins");
export const saveAdminBin = (bin) => apiRequest("/admin/bins", { method: "POST", body: bin });
export const updateAdminBin = (id, bin) => apiRequest(`/admin/bins/${encodeURIComponent(id)}`, { method: "PATCH", body: bin });
export const getAdminVouchers = () => apiRequest("/admin/vouchers");
export const saveAdminVoucher = (voucher) => apiRequest("/admin/vouchers", { method: "POST", body: voucher });
export const updateAdminVoucher = (id, voucher) => apiRequest(`/admin/vouchers/${encodeURIComponent(id)}`, { method: "PATCH", body: voucher });
export const getAdminTransactions = () => apiRequest("/admin/transactions");
