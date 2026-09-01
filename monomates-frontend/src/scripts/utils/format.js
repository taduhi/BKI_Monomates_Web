export function formatTokenAmount(amount) { return `${amount} PT`; }
export function formatWeightGrams(grams) { return grams >= 1000 ? `${(grams / 1000).toFixed(2)} kg` : `${grams.toFixed(0)} g`; }
