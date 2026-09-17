const API_BASE = (process.env.EXPO_PUBLIC_API_BASE_URL || "http://127.0.0.1:3001").replace(/\/+$/, "");

async function request(path) {
  const response = await fetch(`${API_BASE}${path}`);
  const payload = await response.json().catch(() => null);

  if (!response.ok || payload?.status !== "success") {
    throw new Error(payload?.error?.message || `Request failed (${response.status})`);
  }

  return payload.data;
}

export const api = {
  health: () => request("/health"),
  search: query => request(`/api/search?q=${encodeURIComponent(query)}`),
  title: id => request(`/api/title/${encodeURIComponent(id)}`),
  episodes: (id, season) => request(`/api/title/${encodeURIComponent(id)}/episodes?season=${encodeURIComponent(season)}`),
  sources: (id, options = {}) => {
    const params = new URLSearchParams();
    if (options.season) params.set("season", String(options.season));
    if (options.episode) params.set("episode", String(options.episode));
    if (options.quality) params.set("quality", String(options.quality));
    const qs = params.toString();
    return request(`/api/source/${encodeURIComponent(id)}${qs ? `?${qs}` : ""}`);
  }
};

export { API_BASE };
