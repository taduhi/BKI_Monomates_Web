function normalized(value) {
  return String(value ?? "").trim().toLowerCase();
}

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
  } catch {}

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
