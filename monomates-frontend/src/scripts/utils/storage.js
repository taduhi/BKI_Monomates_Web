export function getStoredJson(key, fallback = null) { try { const value = localStorage.getItem(key); return value ? JSON.parse(value) : fallback; } catch { return fallback; } }
export function setStoredJson(key, value) { localStorage.setItem(key, JSON.stringify(value)); }
export function removeStoredValue(key) { localStorage.removeItem(key); }
