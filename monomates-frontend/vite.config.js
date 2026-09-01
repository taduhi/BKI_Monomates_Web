import { resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vite";

const root = fileURLToPath(new URL(".", import.meta.url));

export default defineConfig({
  build: {
    rolldownOptions: {
      input: {
        landing: resolve(root, "index.html"),
        login: resolve(root, "pages/auth/login.html"),
        bins: resolve(root, "pages/bins/index.html"),
        binDetail: resolve(root, "pages/bins/detail.html"),
        depositSession: resolve(root, "pages/deposit/session.html"),
        rewards: resolve(root, "pages/rewards/index.html"),
        activity: resolve(root, "pages/activity/index.html"),
        profile: resolve(root, "pages/profile/index.html"),
        adminBins: resolve(root, "pages/admin/bins.html"),
        adminVouchers: resolve(root, "pages/admin/vouchers.html"),
        adminTransactions: resolve(root, "pages/admin/transactions.html")
      }
    }
  }
});
