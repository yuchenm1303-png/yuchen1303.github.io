export function modelMeta(provider, model, label) {
  return {
    provider: String(provider || "").trim(),
    model: String(model || "").trim(),
    modelLabel: String(label || model || provider || "Cloud Model").trim(),
  };
}

export function appendRunLabel(version, label) {
  const cleanLabel = String(label || "").trim();
  if (!cleanLabel) return version;
  if (String(version || "").includes(cleanLabel)) return version;
  return `${version} · ${cleanLabel}`;
}

export function normalizeModelPreference(value) {
  const v = String(value || "auto").toLowerCase().trim();
  if (["auto", "gemini", "kimi", "mistral", "nvidia", "workers", "workers_ai"].includes(v)) {
    if (v === "nvidia") return "kimi";
    if (v === "workers_ai") return "workers";
    return v;
  }
  return "auto";
}
