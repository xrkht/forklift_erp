export const DEFAULT_PAGE_SIZE = 20;

export function createPageState(size = DEFAULT_PAGE_SIZE) {
  return {
    page: 0,
    size,
    totalElements: 0,
    totalPages: 0,
    first: true,
    last: true,
    loaded: false,
    loading: false
  };
}

export function pageUrl(url, pageState, keyword = "") {
  const params = new URLSearchParams();
  params.set("paged", "true");
  params.set("page", String(Math.max(0, Number(pageState?.page || 0))));
  params.set("size", String(Math.max(1, Number(pageState?.size || DEFAULT_PAGE_SIZE))));
  if (keyword && keyword.trim()) {
    params.set("keyword", keyword.trim());
  }
  return `${url}?${params.toString()}`;
}

export function pageContent(payload) {
  return Array.isArray(payload?.content) ? payload.content : [];
}

export function assignPageState(target, payload) {
  target.page = Number(payload?.page || 0);
  target.size = Number(payload?.size || target.size || DEFAULT_PAGE_SIZE);
  target.totalElements = Number(payload?.totalElements || 0);
  target.totalPages = Number(payload?.totalPages || 0);
  target.first = Boolean(payload?.first);
  target.last = Boolean(payload?.last);
  target.loaded = true;
  target.loading = false;
  return target;
}

export function resetPage(target) {
  target.page = 0;
  return target;
}
