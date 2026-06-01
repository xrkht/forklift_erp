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
    const isFormData = options.body instanceof FormData;
    const headers = isFormData ? {} : { "Content-Type": "application/json" };
    const token = getToken();
    if (options.auth !== false && token) {
      headers.Authorization = `Bearer ${token}`;
    }

    const response = await fetch(path, {
      method: options.method || "GET",
      headers,
      body: options.body === undefined ? undefined : (isFormData ? options.body : JSON.stringify(options.body))
    });

    const payload = await response.json().catch(() => null);
    if (response.status === 401 || payload?.code === 401) {
      const error = new Error("未登录或登录已过期");
      error.authExpired = true;
      error.status = response.status;
      error.code = payload?.code;
      throw error;
    }
    if (!response.ok) {
      const error = new Error(payload?.message || `请求失败：${response.status}`);
      error.status = response.status;
      error.code = payload?.code;
      throw error;
    }
    if (payload && Object.prototype.hasOwnProperty.call(payload, "code")) {
      if (payload.code !== 200) {
        const error = new Error(payload.message || "请求失败");
        error.status = response.status;
        error.code = payload.code;
        throw error;
      }
      return payload.data;
    }
    return payload;
  };
}
