const TOKEN_KEY = "forklift_erp_token";
const USER_KEY = "forklift_erp_user";

export function readStoredToken() {
  return localStorage.getItem(TOKEN_KEY) || "";
}

export function readStoredUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || "null");
  } catch {
    return null;
  }
}

export function saveSession(token, user) {
  localStorage.setItem(TOKEN_KEY, token || "");
  localStorage.setItem(USER_KEY, JSON.stringify(user || null));
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

export function createApiClient(getToken) {
  return async function api(path, options = {}) {
    const headers = { "Content-Type": "application/json" };
    const token = getToken();
    if (options.auth !== false && token) {
      headers.Authorization = `Bearer ${token}`;
    }

    const response = await fetch(path, {
      method: options.method || "GET",
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body)
    });

    const payload = await response.json().catch(() => null);
    if (response.status === 401 || payload?.code === 401) {
      throw new Error("未登录或登录已过期");
    }
    if (!response.ok) {
      throw new Error(payload?.message || `请求失败：${response.status}`);
    }
    if (payload && Object.prototype.hasOwnProperty.call(payload, "code")) {
      if (payload.code !== 200) {
        throw new Error(payload.message || "请求失败");
      }
      return payload.data;
    }
    return payload;
  };
}
