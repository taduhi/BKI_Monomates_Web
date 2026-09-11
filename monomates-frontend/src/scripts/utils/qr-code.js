function normalized(value) {
  return String(value ?? "").trim().toLowerCase();
}

/**
 * Keep the decoded payload visible to the user, while deriving the values
 * that can identify a bin. MonoMates has used both `code` (current routes)
 * and `qrCodeId` (the original product specification), and a physical QR may
 * contain the public code as plain text instead of a URL.
 */
export function parseQrPayload(value, baseUrl = window.location.href) {
  const text = String(value ?? "").trim();
  if (!text) return { text: "", candidates: [] };

  const candidates = [];
  const addCandidate = (candidate) => {
    const clean = String(candidate ?? "").trim();
    if (clean && !candidates.some((item) => normalized(item) === normalized(clean))) {
      candidates.push(clean);
    }
  };

  try {
    const url = new URL(text, baseUrl);
    addCandidate(url.searchParams.get("code"));
    addCandidate(url.searchParams.get("qrCodeId"));
  } catch {
    // Plain-text QR values are valid input and are handled below.
  }

  addCandidate(text);
  return { text, candidates };
}

export function findBinForQrPayload(bins, payload) {
  const candidates = payload?.candidates ?? [];
  return (
    bins.find((bin) =>
      candidates.some((candidate) => {
        const value = normalized(candidate);
        return value === normalized(bin.publicCode) || value === normalized(bin.id);
      })
    ) ?? null
  );
}
