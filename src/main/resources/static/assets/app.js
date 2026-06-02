import { clearSession, createApiClient, readStoredToken, readStoredUser, saveSession } from "./modules/session.js";
import { endpoints } from "./modules/routes.js";
import { resetPage } from "./modules/paging.js";
import { createInitialState, LOG_PAGE_SIZE, LIST_PAGE_SIZE } from "./modules/app-state.js";
import { activeTabPageKeys, pagedTabs, summaryTabs } from "./modules/list-config.js";
import { icons, tabs } from "./modules/ui-config.js";
import { createFields } from "./modules/field-config.js";
import { dateTime, dateValue, display, emptyState, escapeAttr, escapeHtml, fileSize, filterRows, money, normalizeText, nowInputDateTime, sortById, sortLogs, todayInputDate, toInputValue } from "./modules/display-utils.js";
import { badge, jobTagBadge, jobTagLabel, jobTagType, logCategoryBadge, modificationStatusBadge, nextJobTag, normalizeJobTag, operationActionBadge, quantityChange, rentalStatusBadge, repairStatusText, resourceTypeLabel, roleBadges, statusBadge, stockBadge, stockStatusBadge, stockStatusLabel, stockText, yesNoBadge } from "./modules/status-ui.js";
import { createDataLoaders, referencePageUrl, rowsFromPayload } from "./modules/data-loader.js";

const LIST_STATE_STORAGE_KEY = "forklift-erp:list-state:v1";
const DETAIL_DRAWER_TRANSITION_MS = 240;
const REMOTE_COMBO_DEBOUNCE_MS = 250;
const REMOTE_COMBO_PAGE_SIZE = 30;

const state = createInitialState({
  token: readStoredToken(),
  user: normalizeUser(readStoredUser())
});
restorePersistedListState();

const api = createApiClient(() => state.token);

const {
  ensureConfigData,
  loadAllData,
  loadConfigValues,
  loadCurrentTab,
  loadPagedTab,
  loadStatistics,
  loadVehicleModelData
} = createDataLoaders({
  state,
  api,
  endpoints,
  pagedTabs,
  summaryTabs,
  activePagedTab,
  hasPermission,
  sortById,
  sortLogs,
  prepareVehicleModelSummary,
  loadVehicleDetail,
  loadVehicleModelDetail,
  renderCurrentTab,
  restoreConfigItemScroll
});

const fields = createFields({
  nowInputDateTime,
  todayInputDate,
  powerTypeOptions,
  stockStatusOptions,
  configValueOptions,
  configPartCategoryOptions,
  vehicleOptions,
  customerOptions,
  supplierOptions,
  repairPersonOptions,
  repairPartOptions,
  statusOptions,
  vehicleCategoryOptions,
  configSubCategoryOptions,
  nextConfigItemCode,
  inputTypeOptions,
  configItemOptions,
  customerEntryModeOptions,
  vehicleOutboundOptions,
  partCodeOptions,
  machineConfigOptions,
  compatiblePartOptions,
  discountConfigValueOptions,
  oldPartActionOptions,
  installPartCategoryOptions,
  installPartOptions,
  vehicleModelOptions,
  vehicleRentalOptions,
  vehicleNumberOptions,
  userRoleOptions,
  jobTagOptions,
  rentalStatusOptions,
  purchaseConfigValueOptions,
  purchaseStatusOptions,
  stocktakingResourceTypeOptions,
  stocktakingResourceOptions,
  stocktakingStatusOptions,
  warehouseOptions,
  transferResourceTypeOptions,
  stockTransferResourceOptions
});

let els;
let searchReloadTimer = null;
let remoteComboSearchTimer = null;
let remoteComboRequestSequence = 0;
let detailDrawerCloseTimer = null;
let modalPointerDownStartedOnOverlay = false;
let detailPointerDownStartedOnOverlay = false;

document.addEventListener("DOMContentLoaded", () => {
  els = {
    loginScreen: document.getElementById("loginScreen"),
    appScreen: document.getElementById("appScreen"),
    loginForm: document.getElementById("loginForm"),
    mainNav: document.getElementById("mainNav"),
    pageTitle: document.getElementById("pageTitle"),
    pageSubtitle: document.getElementById("pageSubtitle"),
    currentUser: document.getElementById("currentUser"),
    switchUserBtn: document.getElementById("switchUserBtn"),
    logoutBtn: document.getElementById("logoutBtn"),
    content: document.getElementById("moduleContent"),
    modalOverlay: document.getElementById("modalOverlay"),
    modalCard: document.getElementById("modalCard"),
    detailDrawerOverlay: document.getElementById("detailDrawerOverlay"),
    detailDrawer: document.getElementById("detailDrawer"),
    toastHost: document.getElementById("toastHost")
  };

  els.loginForm.addEventListener("submit", handleLogin);
  els.switchUserBtn.addEventListener("click", () => openEntityModal("switchUser", { username: "" }));
  els.logoutBtn.addEventListener("click", () => logout("已退出登录"));
  els.mainNav.addEventListener("click", handleNav);
  els.content.addEventListener("click", handleContentClick);
  els.content.addEventListener("keydown", handleContentKeydown);
  els.content.addEventListener("pointerover", handleContentPointerOver);
  els.content.addEventListener("pointerout", handleContentPointerOut);
  els.content.addEventListener("focusin", handleContentFocusIn);
  els.content.addEventListener("focusout", handleContentFocusOut);
  els.content.addEventListener("input", handleContentInput);
  els.content.addEventListener("change", handleContentChange);
  els.modalOverlay.addEventListener("pointerdown", handleModalOverlayPointerDown);
  els.modalOverlay.addEventListener("click", handleOverlayClick);
  els.modalCard.addEventListener("click", handleModalClick);
  els.modalCard.addEventListener("input", handleModalInput);
  els.modalCard.addEventListener("focusin", handleModalFocusIn);
  els.modalCard.addEventListener("change", handleModalChange);
  els.modalCard.addEventListener("submit", handleModalSubmit);
  els.detailDrawerOverlay.addEventListener("pointerdown", handleDetailDrawerOverlayPointerDown);
  els.detailDrawerOverlay.addEventListener("click", handleDetailDrawerOverlayClick);
  els.detailDrawer.addEventListener("click", handleDetailDrawerClick);
  document.addEventListener("keydown", event => {
    if (event.key === "Escape" && state.modal) {
      closeModal();
      return;
    }
    if (event.key === "Escape" && state.detailDrawer) {
      closeDetailDrawer();
      return;
    }
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k" && !state.modal && !isTypingTarget(event.target)) {
      event.preventDefault();
      focusPrimarySearch();
    }
  });

  if (state.token) {
    enterApp();
  } else {
    showLogin();
  }

  registerClientWorker();
});

function registerClientWorker() {
  if (!("serviceWorker" in navigator)) return;
  navigator.serviceWorker.register("/sw.js").catch(() => {});
}

function restorePersistedListState() {
  let persisted = null;
  try {
    persisted = JSON.parse(localStorage.getItem(LIST_STATE_STORAGE_KEY) || "null");
  } catch (error) {
    return;
  }
  if (!persisted || typeof persisted !== "object") return;
  if (persisted.activeTab && tabs[persisted.activeTab]) {
    state.activeTab = persisted.activeTab;
  }
  if (persisted.search && typeof persisted.search === "object") {
    state.search = { ...state.search, ...pickKnownKeys(persisted.search, state.search) };
  }
  if (persisted.filters && typeof persisted.filters === "object") {
    state.filters = {
      ...state.filters,
      ...Object.fromEntries(Object.entries(persisted.filters).map(([key, value]) => [
        key,
        { ...(state.filters[key] || {}), ...(value || {}) }
      ]))
    };
  }
  if (persisted.pages && typeof persisted.pages === "object") {
    for (const [key, value] of Object.entries(persisted.pages)) {
      if (!state.pages[key] || !value) continue;
      state.pages[key].page = Math.max(0, Number(value.page || 0));
      state.pages[key].size = Math.max(1, Number(value.size || state.pages[key].size));
    }
  }
  if (persisted.sorts && typeof persisted.sorts === "object") {
    state.sorts = persisted.sorts;
  }
  if (persisted.tableDensity === "compact" || persisted.tableDensity === "comfortable") {
    state.tableDensity = persisted.tableDensity;
  }
  if (persisted.tableColumns && typeof persisted.tableColumns === "object") {
    state.tableColumns = persisted.tableColumns;
  }
  if (persisted.filterViews && typeof persisted.filterViews === "object") {
    state.filterViews = persisted.filterViews;
  }
  if (Number.isFinite(Number(persisted.selectedStatsYear))) {
    state.selectedStatsYear = Number(persisted.selectedStatsYear);
  }
  if (Number.isFinite(Number(persisted.visibleLogRows))) {
    state.visibleLogRows = Math.max(LOG_PAGE_SIZE, Number(persisted.visibleLogRows));
  }
}

function persistListState() {
  try {
    const pages = Object.fromEntries(Object.entries(state.pages).map(([key, page]) => [
      key,
      { page: Number(page.page || 0), size: Number(page.size || LIST_PAGE_SIZE) }
    ]));
    localStorage.setItem(LIST_STATE_STORAGE_KEY, JSON.stringify({
      activeTab: state.activeTab,
      search: state.search,
      filters: state.filters,
      pages,
      sorts: state.sorts || {},
      tableDensity: state.tableDensity,
      tableColumns: state.tableColumns || {},
      filterViews: state.filterViews || {},
      selectedStatsYear: state.selectedStatsYear,
      visibleLogRows: state.visibleLogRows
    }));
  } catch (error) {
    // Storage may be unavailable in private browsing or locked-down WebViews.
  }
}

function pickKnownKeys(source, targetShape) {
  return Object.fromEntries(Object.entries(source).filter(([key]) => Object.prototype.hasOwnProperty.call(targetShape, key)));
}

async function handleLogin(event) {
  event.preventDefault();
  const submitButton = event.currentTarget.querySelector("button[type='submit']");
  const form = new FormData(event.currentTarget);
  const username = String(form.get("username") || "").trim();
  const password = String(form.get("password") || "");

  setLoginPending(submitButton, true);
  try {
    const data = await api("/api/auth/login", {
      method: "POST",
      body: { username, password },
      auth: false
    });
    state.token = data.token;
    state.user = normalizeUser(data);
    saveSession(state.token, state.user);
    showToast("登录成功", "success");
    await enterApp();
  } catch (error) {
    showToast(error.message || "登录失败", "error");
  } finally {
    setLoginPending(submitButton, false);
  }
}

function setLoginPending(button, pending) {
  if (!button) return;
  button.disabled = pending;
  button.setAttribute("aria-busy", pending ? "true" : "false");
  const label = button.querySelector("[data-login-label]");
  if (label) label.textContent = pending ? "登录中..." : "登录";
}

async function enterApp() {
  els.loginScreen.classList.add("is-hidden");
  els.appScreen.classList.remove("is-hidden");
  els.content.innerHTML = renderLoading();
  syncShell();
  try {
    await loadAllData();
    renderCurrentTab();
  } catch (error) {
    if (isAuthExpiredError(error)) {
      logout("登录已过期，请重新登录");
      return;
    }
    console.error("初始化数据失败", error);
    els.content.innerHTML = renderLoadError(error);
    showToast(error.message || "初始化数据失败", "error");
  }
}

function showLogin() {
  els.appScreen.classList.add("is-hidden");
  els.loginScreen.classList.remove("is-hidden");
}

function logout(message) {
  state.token = "";
  state.user = null;
  state.vehicleDetail = null;
  state.selectedVehicleId = null;
  state.detailDrawer = null;
  state.batchSelections = {};
  clearSession();
  closeModal();
  closeDetailDrawer();
  showLogin();
  if (message) showToast(message, "info");
}

function restoreConfigItemScroll() {
  requestAnimationFrame(() => {
    const list = els.content.querySelector(".config-items-pane .config-list");
    if (list) list.scrollTop = state.configItemScrollTop || 0;
  });
}

async function loadVehicleDetail(id, shouldRender = true) {
  state.selectedVehicleId = Number(id);
  ensureVehicleDetailTab();
  const [detail, logs, workOrders, parts] = await Promise.all([
    api(`/api/inventory/${id}/detail`),
    api(`/api/replace/machine/${id}`),
    hasPermission("replace:write") ? api(`/api/modification-work-orders/machine/${id}`) : Promise.resolve([]),
    api(`/api/parts/sourceMachine/${id}`)
  ]);
  state.vehicleDetail = { ...detail, logs: sortById(logs), workOrders: sortById(workOrders), parts: sortById(parts) };
  if (shouldRender) renderCurrentTab();
}

async function loadVehicleModelDetail(modelKey, selectedMachineId = null, shouldRender = true) {
  ensureVehicleDetailTab();
  const vehicles = await fetchVehicleModelVehicles(modelKey);
  const model = modelSummaryForKey(modelKey) || { ...decodeVehicleModelKey(modelKey), modelKey, vehicles };
  const selectedId = Number(selectedMachineId || state.selectedVehicleId || vehicles[0]?.id || 0);
  const selected = vehicles.find(vehicle => Number(vehicle.id) === selectedId) || vehicles[0];
  if (!selected) {
    state.selectedVehicleId = null;
    state.vehicleDetail = {
      modelKey,
      model,
      vehicles,
      selectedMachineId: null,
      selectedDetail: { machine: null, configs: [], logs: [], workOrders: [] }
    };
    if (shouldRender) renderCurrentTab();
    return;
  }
  state.selectedVehicleId = Number(selected.id);
  const [detail, logs, workOrders, parts] = await Promise.all([
    api(`/api/inventory/${selected.id}/detail`),
    api(`/api/replace/machine/${selected.id}`),
    hasPermission("replace:write") ? api(`/api/modification-work-orders/machine/${selected.id}`) : Promise.resolve([]),
    api(`/api/parts/sourceMachine/${selected.id}`)
  ]);
  state.vehicleDetail = {
    modelKey,
    model,
    vehicles,
    selectedMachineId: Number(selected.id),
    selectedDetail: { ...detail, logs: sortById(logs), workOrders: sortById(workOrders), parts: sortById(parts) }
  };
  if (shouldRender) renderCurrentTab();
}

async function handleNav(event) {
  const actionButton = event.target.closest("[data-action], [data-select-action]");
  if (actionButton) {
    await handleContentClick(event);
    return;
  }
  const button = event.target.closest("[data-tab]");
  if (!button) return;
  if (button.dataset.tab === state.activeTab) {
    scrollToWorkspaceTop();
    return;
  }
  state.activeTab = button.dataset.tab;
  els.content.innerHTML = renderLoading();
  syncShell();
  try {
    await loadCurrentTab({ force: true });
    renderCurrentTab();
  } catch (error) {
    handleActionError(error);
  }
  scrollToWorkspaceTop();
}

async function handleContentClick(event) {
  const control = event.target.closest("[data-action], [data-select-action]");
  if (!control) return;

  const action = control.dataset.action || control.dataset.selectAction;
  const kind = control.dataset.kind;
  const id = control.dataset.id ? Number(control.dataset.id) : null;

  try {
    if (action === "refresh") {
      els.content.innerHTML = renderLoading();
      await loadAllData();
      renderCurrentTab();
      showToast("数据已刷新", "success");
      return;
    }
    if (action === "clear-search") {
      const key = control.dataset.searchFor;
      if (!key) return;
      state.search[key] = "";
      const tab = tabForSearchKey(key);
      if (key === "logs") state.visibleLogRows = LOG_PAGE_SIZE;
      if (tab && tab === activePagedTab()) {
        resetPage(state.pages[tab]);
        els.content.innerHTML = renderLoading();
        if (tab === "vehicles") {
          await loadVehicleModelData({ force: true });
        } else {
          await loadPagedTab(tab, { force: true });
        }
      }
      renderCurrentTab();
      const nextInput = els.content.querySelector(`[data-search-for="${key}"]`);
      if (nextInput) nextInput.focus();
      return;
    }
    if (action === "open-detail") {
      openDetailDrawer(kind, id);
      return;
    }
    if (action === "close-detail") {
      closeDetailDrawer();
      return;
    }
    if (action === "apply-filter") {
      const key = control.dataset.filterFor;
      const name = control.dataset.filterName;
      if (!key || !name) return;
      state.filters[key] = {
        ...(state.filters[key] || {}),
        [name]: control.dataset.filterValue || ""
      };
      if (state.pages[key]) resetPage(state.pages[key]);
      renderCurrentTab();
      return;
    }
    if (action === "sort-table") {
      applyTableSort(control.dataset.tableKey, control.dataset.sortKey);
      persistListState();
      renderCurrentTab();
      return;
    }
    if (action === "toggle-table-density") {
      state.tableDensity = state.tableDensity === "compact" ? "comfortable" : "compact";
      persistListState();
      renderCurrentTab();
      return;
    }
    if (action === "toggle-table-column") {
      setTableColumnVisibility(control.dataset.tableKey, control.dataset.columnKey, control.checked);
      persistListState();
      renderCurrentTab();
      return;
    }
    if (action === "save-filter-view") {
      saveFilterView(control.dataset.tableKey);
      persistListState();
      showToast("筛选方案已保存", "success");
      renderCurrentTab();
      return;
    }
    if (action === "apply-filter-view") {
      await applyFilterView(control.dataset.tableKey);
      return;
    }
    if (action === "delete-filter-view") {
      deleteFilterView(control.dataset.tableKey);
      persistListState();
      showToast("筛选方案已清除", "info");
      renderCurrentTab();
      return;
    }
    if (action === "toggle-row-select") {
      toggleBatchSelection(control.dataset.batchKind, id, control.checked);
      renderCurrentTab();
      return;
    }
    if (action === "toggle-visible-select") {
      toggleVisibleBatchSelection(control.dataset.batchKind, (control.dataset.visibleIds || "").split(",").filter(Boolean), control.checked);
      renderCurrentTab();
      return;
    }
    if (action === "clear-batch-selection") {
      clearBatchSelection(control.dataset.batchKind);
      renderCurrentTab();
      return;
    }
    if (action === "batch-export") {
      exportSelectedRows(control.dataset.batchKind);
      return;
    }
    if (action === "batch-complete-repairs") {
      await batchCompleteRepairs();
      return;
    }
    if (action === "batch-receive-purchases") {
      await batchReceivePurchases();
      return;
    }
    if (action === "batch-complete-stocktakes") {
      await batchCompleteStocktakes();
      return;
    }
    if (action === "batch-delete-stocktake-drafts") {
      await batchDeleteStocktakeDrafts();
      return;
    }
    if (action === "export-excel") {
      await downloadExcel(control.dataset.exportType);
      return;
    }
    if (action === "download-backup") {
      await downloadDataBackup();
      return;
    }
    if (action === "restore-backup") {
      await openEntityModal("dataRestore", { confirmation: "RESTORE-DATA-BACKUP" });
      return;
    }
    if (action === "stock-transfer") {
      await openEntityModal("stockTransfer", {});
      return;
    }
    if (action === "quick-vehicle-inbound") {
      await openEntityModal("vehicleInbound", vehicleInboundDefaultsForModel({}));
      return;
    }
    if (action === "page-list") {
      const tab = control.dataset.pageTab;
      if (!pagedTabs[tab]) return;
      state.pages[tab].page = Math.max(0, Number(control.dataset.page || 0));
      if (tab === "vehicles") {
        els.content.innerHTML = renderLoading();
        await loadVehicleModelData({ force: true });
        renderCurrentTab();
        return;
      }
      els.content.innerHTML = renderLoading();
      await loadPagedTab(tab, { force: true });
      renderCurrentTab();
      return;
    }
    if (action === "load-more-logs") {
      state.visibleLogRows += LOG_PAGE_SIZE;
      renderCurrentTab();
      return;
    }
    if (action === "create-vehicle-model") {
      await openEntityModal("vehicleModel", vehicleModelDefaults(control.dataset.powerType));
      return;
    }
    if (action === "create") {
      await openEntityModal(kind, defaultEntity(kind));
      return;
    }
    if (action === "model-inbound") {
      const group = modelSummaryForKey(control.dataset.modelKey);
      await openEntityModal("vehicleInbound", vehicleInboundDefaultsForModel(group));
      return;
    }
    if (action === "model-outbound") {
      const group = modelSummaryForKey(control.dataset.modelKey);
      await openEntityModal("vehicleOutbound", vehicleOutboundDefaultsForModel(group));
      return;
    }
    if (action === "vehicle-outbound-direct") {
      const machine = findEntity("vehicle", Number(control.dataset.machineId || id || 0));
      await openEntityModal("vehicleOutbound", vehicleOutboundDefaultsForMachine(machine));
      return;
    }
    if (action === "vehicle-rental-direct") {
      const machine = findEntity("vehicle", Number(control.dataset.machineId || id || 0));
      await openEntityModal("rental", rentalDefaultsForMachine(machine));
      return;
    }
    if (action === "vehicle-stock") {
      const vehicle = findEntity("vehicle", id || Number(control.dataset.machineId || ""));
      const modalItem = {
        version: vehicle.version,
        direction: control.dataset.direction
      };
      setPrefill(modalItem, "machineId", id || Number(control.dataset.machineId || ""), `预填：${vehicleNumberLabel(vehicle)}`);
      await openEntityModal("vehicleStock", modalItem);
      return;
    }
    if (action === "part-stock") {
      const part = state.data.parts.find(item => item.partCode === control.dataset.partCode) || {};
      const modalItem = {
        version: part.version,
        direction: control.dataset.direction
      };
      setPrefill(modalItem, "partCode", control.dataset.partCode || "", `预填：${part.partCode || ""} · ${part.partName || ""}`);
      if (control.dataset.direction === "outbound") {
        const price = part.settlementPrice || part.salePrice || "";
        setPrefill(modalItem, "settlementPrice", price, price ? `预填：${money(price)}` : undefined);
      }
      await openEntityModal("partStock", modalItem);
      return;
    }
    if (action === "edit") {
      await openEntityModal(kind, findEntity(kind, id));
      return;
    }
    if (action === "upload-invoice") {
      const order = findEntity("outboundOrder", id);
      if (!isInvoiceUploadReady(order)) {
        showToast("请先将发票跟进改为已申请发票", "error");
        return;
      }
      await openEntityModal("invoiceUpload", order);
      return;
    }
    if (action === "download-invoice") {
      await downloadInvoice(findEntity("outboundOrder", id));
      return;
    }
    if (action === "upload-contract") {
      const order = findEntity("outboundOrder", id);
      if (!isContractUploadReady(order)) {
        showToast("请先将合同状态标记为有合同", "error");
        return;
      }
      await openEntityModal("contractUpload", order);
      return;
    }
    if (action === "download-contract") {
      await downloadContract(findEntity("outboundOrder", id));
      return;
    }
    if (action === "go-tab") {
      await goToTab(control.dataset.tab, control.dataset);
      return;
    }
    if (action === "toggle-order-status") {
      await toggleOutboundOrderStatus(id, control.dataset.field);
      return;
    }
    if (action === "toggle-repair-status") {
      await toggleRepairStatus(id);
      return;
    }
    if (action === "toggle-user-job-tag") {
      await toggleUserJobTag(id);
      return;
    }
    if (action === "toggle-user-enabled") {
      await toggleUserEnabled(id);
      return;
    }
    if (action === "toggle-order-lock") {
      await toggleOutboundOrderLock(id, control.dataset.locked === "true");
      return;
    }
    if (action === "toggle-purchase-received") {
      await togglePurchaseReceived(id);
      return;
    }
    if (action === "purchase-freight") {
      await openEntityModal("purchaseFreight", findEntity("purchaseOrder", id));
      return;
    }
    if (action === "complete-stocktaking") {
      await completeStocktaking(id);
      return;
    }
    if (action === "user-username") {
      await openEntityModal("userUsername", findEntity("user", id));
      return;
    }
    if (action === "user-password") {
      await openEntityModal("userPassword", findEntity("user", id));
      return;
    }
    if (action === "delete") {
      await deleteEntity(kind, id);
      return;
    }
    if (action === "detail-vehicle") {
      if (state.activeTab !== "vehicles") {
        state.activeTab = "vehicles";
        els.content.innerHTML = renderLoading();
        await loadCurrentTab({ force: true });
      }
      if (control.dataset.modelKey) {
        await loadVehicleModelDetail(control.dataset.modelKey);
        scrollToVehicleDetail();
      } else {
        await loadVehicleDetail(id);
        scrollToVehicleDetail();
      }
      return;
    }
    if (action === "select-model-vehicle") {
      await loadVehicleModelDetail(control.dataset.modelKey, Number(control.dataset.machineId || 0));
      scrollToVehicleDetail();
      return;
    }
    if (action === "set-vehicle-detail-tab") {
      state.vehicleDetailTab = control.dataset.tab || "archive";
      ensureVehicleDetailTab();
      renderCurrentTab();
      scrollToVehicleDetail();
      return;
    }
    if (action === "select-config-item") {
      const configList = control.closest(".config-list");
      state.configItemScrollTop = configList ? configList.scrollTop : 0;
      await loadConfigValues(id, { restoreConfigScroll: true });
      return;
    }
    if (action === "part-replace") {
      const machineId = Number(control.dataset.machineId || state.selectedVehicleId || "");
      const machine = findEntity("vehicle", machineId);
      await openEntityModal("partReplace", {
        machineId,
        machineVersion: machine.version,
        machineConfigId: control.dataset.machineConfigId ? Number(control.dataset.machineConfigId) : null
      });
      return;
    }
    if (action === "create-modification-order") {
      const machineId = Number(control.dataset.machineId || state.selectedVehicleId || "");
      const machine = findEntity("vehicle", machineId);
      const configId = control.dataset.machineConfigId ? Number(control.dataset.machineConfigId) : null;
      await openEntityModal("modificationOrder", modificationOrderDefaults(machine, configId));
      return;
    }
    if (action === "create-vehicle-part") {
      const machineId = Number(control.dataset.machineId || state.selectedVehicleId || "");
      const machine = findEntity("vehicle", machineId);
      await openEntityModal("vehiclePartInstall", vehiclePartInstallDefaultsForMachine(machine));
      return;
    }
    if (action === "complete-modification-order") {
      await completeModificationOrder(id);
      return;
    }
    if (action === "cancel-modification-order") {
      await cancelModificationOrder(id);
      return;
    }
  } catch (error) {
    handleActionError(error);
  }
}

function handleContentKeydown(event) {
  if (event.key !== "Enter" && event.key !== " ") return;
  if (event.target.closest("button, a, input, select, textarea")) return;
  const selectable = event.target.closest("[data-select-action], [data-action]");
  if (!selectable || !els.content.contains(selectable)) return;
  event.preventDefault();
  selectable.click();
}

function handleContentPointerOver(event) {
  const column = event.target.closest(".finance-chart-column");
  if (!column || !els.content.contains(column)) return;
  setActiveFinanceColumn(column);
}

function handleContentPointerOut(event) {
  const chart = event.target.closest(".finance-chart");
  if (!chart) return;
  if (event.relatedTarget && chart.contains(event.relatedTarget)) return;
  clearActiveFinanceChart(chart);
}

function handleContentFocusIn(event) {
  const column = event.target.closest(".finance-chart-column");
  if (!column || !els.content.contains(column)) return;
  setActiveFinanceColumn(column);
}

function handleContentFocusOut(event) {
  const chart = event.target.closest(".finance-chart");
  if (!chart) return;
  window.setTimeout(() => {
    if (chart.contains(document.activeElement)) return;
    clearActiveFinanceChart(chart);
  }, 0);
}

function setActiveFinanceColumn(column) {
  const chart = column.closest(".finance-chart");
  if (!chart) return;
  const activeIndex = column.dataset.financeColumnIndex || "";
  if (chart.dataset.activeIndex === activeIndex) return;
  chart.dataset.activeIndex = activeIndex;
  chart.querySelectorAll(".finance-chart-column.is-active, .finance-point.is-active").forEach(item => {
    item.classList.remove("is-active");
  });
  column.classList.add("is-active");
  chart.querySelectorAll("[data-finance-point-index]").forEach(point => {
    if (point.dataset.financePointIndex === activeIndex) {
      point.classList.add("is-active");
    }
  });
}

function clearActiveFinanceChart(chart) {
  delete chart.dataset.activeIndex;
  chart.querySelectorAll(".finance-chart-column.is-active, .finance-point.is-active").forEach(item => {
    item.classList.remove("is-active");
  });
  if (chart.contains(document.activeElement) && typeof document.activeElement.blur === "function") {
    document.activeElement.blur();
  }
}

function handleContentInput(event) {
  const input = event.target.closest("[data-search-for]");
  if (!input) return;
  state.search[input.dataset.searchFor] = input.value;
  if (input.dataset.searchFor === "logs") {
    state.visibleLogRows = LOG_PAGE_SIZE;
  }
  const tab = tabForSearchKey(input.dataset.searchFor);
  if (tab && tab === activePagedTab()) {
    resetPage(state.pages[tab]);
    schedulePagedReload(tab);
    return;
  }
  renderCurrentTab();
  const nextInput = els.content.querySelector(`[data-search-for="${input.dataset.searchFor}"]`);
  if (nextInput) {
    nextInput.focus();
    nextInput.setSelectionRange(input.value.length, input.value.length);
  }
}

function tabForSearchKey(key) {
  return Object.entries(pagedTabs)
    .find(([, config]) => config.searchKey === key)?.[0] || null;
}

function activePagedTab() {
  return activeTabPageKeys[state.activeTab] || (pagedTabs[state.activeTab] ? state.activeTab : null);
}

function schedulePagedReload(tab) {
  window.clearTimeout(searchReloadTimer);
  searchReloadTimer = window.setTimeout(async () => {
    try {
      if (tab === "vehicles") {
        await loadVehicleModelData({ force: true });
      } else {
        await loadPagedTab(tab, { force: true });
      }
      renderCurrentTab();
      const input = els.content.querySelector(`[data-search-for="${pagedTabs[tab].searchKey}"]`);
      if (input) {
        input.focus();
        input.setSelectionRange(input.value.length, input.value.length);
      }
    } catch (error) {
      handleActionError(error);
    }
  }, 250);
}

async function handleContentChange(event) {
  const filter = event.target.closest("[data-filter-for]");
  if (filter) {
    const key = filter.dataset.filterFor;
    const name = filter.dataset.filterName;
    state.filters[key] = {
      ...(state.filters[key] || {}),
      [name]: filter.value
    };
    if (state.pages[key]) resetPage(state.pages[key]);
    renderCurrentTab();
    return;
  }

  const statsSelect = event.target.closest("[data-action='select-stats-year']");
  if (statsSelect) {
    try {
      els.content.innerHTML = renderLoading();
      await loadStatistics(statsSelect.value);
      renderCurrentTab();
    } catch (error) {
      handleActionError(error);
    }
    return;
  }

  const select = event.target.closest("[data-action='select-config-item']");
  if (!select) return;
  try {
    await loadConfigValues(select.value);
  } catch (error) {
    handleActionError(error);
  }
}

function handleModalOverlayPointerDown(event) {
  modalPointerDownStartedOnOverlay = event.target === els.modalOverlay;
}

function handleOverlayClick(event) {
  if (event.target === els.modalOverlay && modalPointerDownStartedOnOverlay) {
    closeModal();
  }
  modalPointerDownStartedOnOverlay = false;
}

function handleDetailDrawerOverlayPointerDown(event) {
  detailPointerDownStartedOnOverlay = event.target === els.detailDrawerOverlay;
}

function handleDetailDrawerOverlayClick(event) {
  if (event.target === els.detailDrawerOverlay && detailPointerDownStartedOnOverlay) {
    closeDetailDrawer();
  }
  detailPointerDownStartedOnOverlay = false;
}

function handleDetailDrawerClick(event) {
  if (event.target.closest("[data-action='close-detail']")) {
    closeDetailDrawer();
    return;
  }
  if (event.target.closest("[data-action], [data-select-action]")) {
    handleContentClick(event);
  }
}

async function handleModalClick(event) {
  const configAction = event.target.closest("[data-config-action]");
  if (configAction) {
    event.preventDefault();
    if (configAction.dataset.configAction === "remove") {
      const confirmed = await confirmDanger({
        title: "确认移除配置",
        target: "当前入库配置行",
        impact: "该配置行会从当前表单中移除；保存前不会写入后台。"
      });
      if (!confirmed) return;
    }
    updateConfigSelectionRows(configAction);
    return;
  }
  const comboOption = event.target.closest("[data-combo-option]");
  if (comboOption) {
    event.preventDefault();
    selectComboOption(comboOption);
    return;
  }
  const comboToggle = event.target.closest("[data-combo-toggle]");
  if (comboToggle) {
    event.preventDefault();
    toggleCombo(comboToggle.closest("[data-combo]"));
    return;
  }
  const comboInput = event.target.closest("[data-combo-input]");
  if (comboInput) {
    const combo = comboInput.closest("[data-combo]");
    openCombo(combo, comboInput.value.trim());
    scheduleRemoteComboSearch(combo, comboInput.value.trim(), { immediate: combo.dataset.remoteLoaded !== "true" });
    return;
  }
  const toggleOption = event.target.closest("[data-toggle-option]");
  if (toggleOption) {
    event.preventDefault();
    setToggleFieldValue(toggleOption);
    return;
  }
  if (!event.target.closest("[data-combo]")) {
    closeAllCombos();
  }
  if (event.target.closest("[data-close-modal]")) {
    closeModal();
  }
}

function handleModalInput(event) {
  const input = event.target.closest("[data-combo-input]");
  if (!input) {
    const form = event.target.closest("form");
    if (form?.dataset.kind === "repair" && ["repairFee", "partsFee"].includes(event.target.name)) {
      syncRepairTotalFee(form);
    }
    if (["vehicleOutbound", "partStock", "outboundOrder"].includes(form?.dataset.kind) && isPaymentField(event.target.name)) {
      syncPaymentFields(form, event.target.name);
    }
    if (form?.dataset.kind === "purchaseOrder" && ["quantity", "unitPrice", "totalAmount"].includes(event.target.name)) {
      syncPurchaseAmount(form, event.target.name);
    }
    return;
  }
  const combo = input.closest("[data-combo]");
  const hidden = combo.querySelector("input[type='hidden']");
  const query = input.value.trim();
  const allowCustom = combo.dataset.allowCustom === "true";
  const exact = findComboOption(combo, query);
  input.dataset.userEdited = "true";
  if (exact) {
    setComboValue(combo, exact.dataset.value, exact.dataset.label, exact.dataset.meta, false);
  } else if (allowCustom) {
    hidden.value = query;
    hidden.dataset.selectedLabel = query;
    hidden.dataset.selectedMeta = "";
  } else {
    hidden.value = "";
    hidden.dataset.selectedLabel = "";
    hidden.dataset.selectedMeta = "";
  }
  openCombo(combo, query);
  scheduleRemoteComboSearch(combo, query);
}

function handleModalFocusIn(event) {
  const input = event.target.closest("[data-combo-input]");
  if (!input) return;
  const combo = input.closest("[data-combo]");
  openCombo(combo, input.value.trim());
  scheduleRemoteComboSearch(combo, input.value.trim(), { immediate: combo.dataset.remoteLoaded !== "true" });
}

function updateConfigSelectionRows(button) {
  if (state.modal?.kind !== "vehicleInbound") return;
  const rows = ensureConfigSelections(state.modal.item);
  if (button.dataset.configAction === "add") {
    rows.push({ configItemId: "", configValueId: "" });
  }
  if (button.dataset.configAction === "remove") {
    const index = Number(button.dataset.configIndex);
    rows.splice(index, 1);
    if (!rows.length) rows.push({ configItemId: "", configValueId: "" });
  }
  renderModal();
}

async function handleModalChange(event) {
  const form = event.target.closest("form");
  if (!form) return;
  const kind = form.dataset.kind;
  if (kind === "vehicleInbound" && event.target.dataset.configField) {
    const rows = ensureConfigSelections(state.modal.item);
    const index = Number(event.target.dataset.configIndex);
    const fieldName = event.target.dataset.configField;
    rows[index] = {
      ...(rows[index] || {}),
      [fieldName]: event.target.value
    };
    if (fieldName === "configItemId") {
      rows[index].configValueId = "";
      renderModal();
    }
    return;
  }
  if (state.modal?.item && event.target.name) {
    state.modal.item[event.target.name] = event.target.type === "checkbox" ? event.target.checked : event.target.value;
  }

  if (kind === "configItem") {
    syncConfigItemFields(form, event.target.name);
  }

  if (kind === "part") {
    syncPartDictionaryFields(form, event.target.name);
  }

  if (kind === "repair") {
    if (event.target.name === "repairFee" || event.target.name === "partsFee") {
      syncRepairTotalFee(form);
    }
    if (event.target.name === "machineId") {
      syncRepairVehicleSelection(form, Number(event.target.value || 0));
    }
    if (event.target.name === "customerId") {
      syncCustomerAddress(form, Number(event.target.value || 0));
    }
    if (event.target.name === "usedPartIds") {
      syncRepairPartFee(form, event.target.value);
    }
  }

  if (kind === "rental" && event.target.name === "customerId") {
    syncRentalCustomerDestination(form, Number(event.target.value || 0));
  }

  if (kind === "rental" && event.target.name === "machineId") {
    syncRentalVehicleDefaults(form, Number(event.target.value || 0));
  }

  if (kind === "stocktaking" && event.target.name === "resourceType") {
    state.modal.item.resourceId = null;
    state.modal.item.actualQuantity = 0;
    renderModal();
    return;
  }

  if (kind === "stocktaking" && event.target.name === "resourceId") {
    syncStocktakingQuantity(form, Number(event.target.value || 0));
  }

  if (kind === "stockTransfer" && event.target.name === "resourceType") {
    state.modal.item.resourceId = null;
    state.modal.item.fromWarehouseId = null;
    state.modal.item.version = null;
    renderModal();
    return;
  }

  if (kind === "stockTransfer" && event.target.name === "resourceId") {
    syncStockTransferResource(form, Number(event.target.value || 0));
  }

  if (kind === "purchaseOrder" && ["quantity", "unitPrice", "totalAmount"].includes(event.target.name)) {
    syncPurchaseAmount(form, event.target.name);
  }

  if (kind === "purchaseOrder" && event.target.name === "configItemId") {
    state.modal.item.configValueId = null;
    renderModal();
    return;
  }

  if (kind === "purchaseOrder" && event.target.name === "configValueId") {
    syncPurchaseResourceDefaults(form);
  }

  if (kind === "vehicleInbound" && event.target.name === "machineType") {
    renderModal();
    return;
  }

  if (kind === "vehicleOutbound" && event.target.name === "customerMode") {
    if (event.target.value === "quickCreate") {
      state.modal.item.customerId = null;
    }
    renderModal();
    return;
  }

  if (kind === "vehicleOutbound" && event.target.name === "machineId") {
    syncVehicleOutboundDefaults(Number(event.target.value || 0));
    renderModal();
    return;
  }

  if (["vehicleOutbound", "partStock", "outboundOrder"].includes(kind) && isPaymentField(event.target.name)) {
    syncPaymentFields(form, event.target.name);
  }

  if (kind === "partStock" && state.modal?.item?.direction === "outbound" && event.target.name === "partCode") {
    syncPartOutboundDefaults(event.target.value);
    renderModal();
    return;
  }

  if (kind === "modificationOrder" && event.target.name === "oldPartAction") {
    state.modal.item.newPartId = null;
    state.modal.item.newConfigValueId = null;
    renderModal();
    return;
  }

  if (kind === "vehiclePartInstall" && event.target.name === "configItemId") {
    state.modal.item.configItemId = Number(event.target.value || 0) || null;
    state.modal.item.newPartId = null;
    syncComboOptionsForField(form, "newPartId", installPartOptions(), "");
    return;
  }

  if (kind === "modificationOrder" && event.target.name === "vehicleModelKey") {
    state.modal.item.machineId = null;
    clearPrefill(state.modal.item, "machineId");
    clearPrefill(state.modal.item, "machineConfigId");
    state.modal.item.newPartId = null;
    state.modal.item.newConfigValueId = null;
    state.modal.item.__placeholders = {};
    state.modal.context.machine = null;
    state.modal.context.machineConfigs = [];
    state.modal.context.compatibleParts = [];
    renderModal();
    return;
  }

  if (kind === "partReplace" || kind === "modificationOrder") {
    if (event.target.name === "machineId") {
      if (kind === "modificationOrder") {
        state.modal.item.newConfigValueId = null;
      }
      await preparePartReplaceContext(Number(event.target.value || 0), null, { placeholderConfig: kind === "modificationOrder" });
      renderModal();
      return;
    }
    if (event.target.name === "machineConfigId") {
      updateCompatibleParts(Number(event.target.value || 0));
      if (kind === "modificationOrder" && isModificationDiscountMode()) {
        state.modal.item.newConfigValueId = null;
        renderModal();
      } else {
        syncComboOptionsForField(form, "newPartId", compatiblePartOptions(), "");
      }
      return;
    }
  }

  if (kind !== "replace") return;
}

async function handleModalSubmit(event) {
  event.preventDefault();
  const form = event.target;
  const kind = form.dataset.kind;
  if (!validateCombos(form)) return;
  const item = state.modal?.item || {};
  const activeModelKey = state.vehicleDetail?.modelKey || item.__modelKey || "";

  if (kind === "invoiceUpload" || kind === "contractUpload") {
    if (!(await confirmDanger(modalDangerConfirmation(kind, item, {}) || {
      title: kind === "invoiceUpload" ? "确认上传发票" : "确认上传合同",
      target: entityDisplayName("outboundOrder", item),
      impact: "如已有文件，本次上传会替换原文件。"
    }))) return;
    try {
      if (kind === "invoiceUpload") {
        await uploadInvoiceForOrder(item, form);
      } else {
        await uploadContractForOrder(item, form);
      }
      closeModal();
      markReferenceDataStale(referenceKindsForMutation(kind));
      await loadAllData();
      renderCurrentTab();
    } catch (error) {
      handleActionError(error);
    }
    return;
  }

  if (kind === "dataRestore") {
    if (!(await confirmDanger({
      title: "恢复数据备份",
      target: "数据库业务数据",
      impact: "恢复会清空当前应用表并用备份文件回填，请先确认已经下载了最新备份。"
    }))) return;
    try {
      await restoreDataBackup(form);
      closeModal();
      markReferenceDataStale();
      await loadAllData({ force: true });
      renderCurrentTab();
      showToast("数据恢复完成", "success");
    } catch (error) {
      handleActionError(error);
    }
    return;
  }

  const payload = serializeForm(kind, form);
  if (kind === "purchaseOrder") {
    enrichPurchaseOrderPayload(payload, form);
  }
  attachVersion(payload, item);
  const dangerConfirmation = modalDangerConfirmation(kind, item, payload);
  if (dangerConfirmation && !(await confirmDanger(dangerConfirmation))) return;

  try {
    if (kind === "switchUser") {
      const data = await api("/api/auth/login", {
        method: "POST",
        body: payload,
        auth: false
      });
      state.token = data.token;
      state.user = normalizeUser(data);
      saveSession(state.token, state.user);
      closeModal();
      showToast("已切换用户", "success");
      await enterApp();
      return;
    }

    if (kind === "vehicleModel") {
      await api(endpoints.vehicle.create, {
        method: "POST",
        body: {
          ...payload,
          modelOnly: true,
          inventoryCount: 0,
          stockStatus: "PENDING_INBOUND"
        }
      });
      showContextSuccess("车型已新增", entityDisplayName("vehicle", payload), "可继续入库具体车号");
    } else if (kind === "vehicleInbound") {
      const configs = buildInboundConfigs(item);
      const savedVehicle = await api("/api/inventory/inbound", {
        method: "POST",
        body: {
          machineInventory: buildVehicleInboundPayload(payload, item),
          configs
        }
      });
      if (savedVehicle?.id) {
        state.selectedVehicleId = Number(savedVehicle.id);
      }
      showContextSuccess("车型入库成功", entityDisplayName("vehicle", { ...payload, id: savedVehicle?.id }), "车辆详情已更新");
    } else if (kind === "vehicleOutbound") {
      const machine = findEntity("vehicle", Number(payload.machineId || 0));
      const customerResult = await ensureVehicleOutboundCustomer(payload);
      if (!customerResult?.customerId) {
        return;
      }
      await api(endpoints.outboundOrder.vehicle, {
        method: "POST",
        body: {
          machineId: payload.machineId,
          machineVersion: machine.version,
          customerId: customerResult.customerId,
          salesDate: payload.salesDate,
          settlementPrice: payload.settlementPrice,
          salePrice: payload.salePrice,
          receivableAmount: payload.receivableAmount,
          receivedAmount: payload.receivedAmount,
          paymentDueDate: payload.paymentDueDate,
          lastPaymentDate: payload.lastPaymentDate,
          paymentSettled: payload.paymentSettled,
          paymentRemark: payload.paymentRemark,
          salesReported: payload.salesReported,
          salesReportDate: payload.salesReportDate,
          invoiceApplied: payload.invoiceApplied,
          invoiceApplicationDate: payload.invoiceApplicationDate,
          invoiceStatus: payload.invoiceStatus,
          invoiceIssuedDate: payload.invoiceIssuedDate,
          registrationStatus: payload.registrationStatus,
          contractType: payload.contractType,
          operator: payload.operator,
          orderRemark: payload.orderRemark
        }
      });
      showContextSuccess(customerResult.created ? "已新建客户并创建整车出库订单" : "整车出库订单已创建", entityDisplayName("vehicle", machine), "收款、报销售和发票跟进已生成");
    } else if (kind === "rental") {
      const machine = findEntity("vehicle", Number(payload.machineId || item.machineId || 0));
      const body = {
        machineId: payload.machineId || item.machineId,
        machineVersion: machine.version,
        customerId: payload.customerId,
        destination: payload.destination,
        monthlyRentalPrice: payload.monthlyRentalPrice,
        startDate: payload.startDate,
        endDate: payload.endDate,
        status: payload.status,
        operator: payload.operator,
        remark: payload.remark,
        version: payload.version
      };
      if (item.id) {
        await api(endpoints.rental.update(item.id), { method: "PUT", body });
        showContextSuccess("租赁记录已更新", entityDisplayName("vehicle", machine), body.destination || "租赁列表已刷新");
      } else {
        await api(endpoints.rental.create, { method: "POST", body });
        showContextSuccess("租赁记录已创建", entityDisplayName("vehicle", machine), body.destination || "租赁列表已刷新");
      }
    } else if (kind === "vehicleStock") {
      attachStockVersion(payload, "vehicle");
      await api(`/api/inventory/${payload.machineId}/${item.direction}`, {
        method: "PUT",
        body: { quantity: payload.quantity, operator: payload.operator, remark: payload.remark, version: payload.version }
      });
      showContextSuccess(item.direction === "inbound" ? "整车入库成功" : "整车出库成功", entityDisplayName("vehicle", findEntity("vehicle", Number(payload.machineId || 0))), `数量：${payload.quantity || 0}`);
    } else if (kind === "partStock") {
      attachStockVersion(payload, "part");
      if (item.direction === "outbound") {
        await api(endpoints.outboundOrder.part, {
          method: "POST",
          body: {
            partCode: payload.partCode,
            partVersion: payload.version,
            quantity: payload.quantity,
            customerId: payload.customerId,
            settlementPrice: payload.settlementPrice,
            receivableAmount: payload.receivableAmount,
            receivedAmount: payload.receivedAmount,
            paymentDueDate: payload.paymentDueDate,
            lastPaymentDate: payload.lastPaymentDate,
            paymentSettled: payload.paymentSettled,
            paymentRemark: payload.paymentRemark,
            operator: payload.operator,
            orderRemark: payload.orderRemark
          }
        });
        showContextSuccess("配件出库订单已创建", payload.partCode, `数量：${payload.quantity || 0}`);
      } else {
        await api(`/api/parts/${item.direction}`, {
          method: "PUT",
          body: { partCode: payload.partCode, quantity: payload.quantity, operator: payload.operator, remark: payload.remark, version: payload.version }
        });
        showContextSuccess("配件入库成功", payload.partCode, `数量：${payload.quantity || 0}`);
      }
    } else if (kind === "partReplace") {
      enrichPartReplacePayload(payload);
      await api(endpoints.partReplace.create, { method: "POST", body: payload });
      showContextSuccess("配件替换成功", entityDisplayName("vehicle", findEntity("vehicle", Number(payload.machineId || 0))), "旧件已自动入库");
    } else if (kind === "vehiclePartInstall") {
      enrichVehiclePartInstallPayload(payload);
      await api(endpoints.partReplace.install, { method: "POST", body: payload });
      showContextSuccess("配件装车成功", entityDisplayName("vehicle", findEntity("vehicle", Number(payload.machineId || 0))), `数量：${payload.quantity || 0}`);
    } else if (kind === "modificationOrder") {
      enrichPartReplacePayload(payload);
      await api(endpoints.modificationOrder.create, {
        method: "POST",
        body: buildModificationOrderPayload(payload)
      });
      showContextSuccess("改装工单已创建", entityDisplayName("vehicle", findEntity("vehicle", Number(payload.machineId || 0))), "待完成后同步库存和车辆配置");
    } else if (kind === "user") {
      await api(endpoints.user.create, { method: "POST", body: payload });
      showContextSuccess("用户创建成功", payload.username, "权限与职务已保存");
    } else if (kind === "userUsername") {
      await api(endpoints.user.updateUsername(item.id), { method: "PUT", body: payload });
      showContextSuccess("用户名修改成功", payload.username, "用户列表已刷新");
    } else if (kind === "userPassword") {
      await api(endpoints.user.updatePassword(item.id), { method: "PUT", body: payload });
      showContextSuccess("用户密码修改成功", entityDisplayName("user", item), "请使用新密码登录");
    } else if (kind === "outboundOrder") {
      await api(endpoints.outboundOrder.update(item.id), { method: "PUT", body: payload });
      showContextSuccess("订单状态已更新", entityDisplayName("outboundOrder", item), "收款、报销售和发票状态已保存");
    } else if (kind === "purchaseFreight") {
      const baseUrl = `${endpoints.purchaseOrder.freight(item.id)}?freightAmount=${encodeURIComponent(payload.freightAmount ?? 0)}`;
      await api(withVersion(baseUrl, item), { method: "PUT" });
      showContextSuccess("采购运费已更新", entityDisplayName("purchaseOrder", item), money(payload.freightAmount ?? 0));
    } else if (kind === "stockTransfer") {
      await api(endpoints.warehouse.transfer, { method: "POST", body: payload });
      showContextSuccess("库存调拨完成", stockTransferResourceName(payload), `数量：${payload.quantity || 0}`);
    } else if (item.id && endpoints[kind].update) {
      await api(endpoints[kind].update(item.id), { method: "PUT", body: payload });
      showContextSuccess(`${entityLabel(kind)}已保存`, entityDisplayName(kind, { ...item, ...payload }), "当前筛选和页码已保留");
    } else {
      await api(endpoints[kind].create, { method: "POST", body: payload });
      showContextSuccess(`${entityLabel(kind)}已新增`, entityDisplayName(kind, payload), "当前列表已刷新");
    }
    closeModal();
    resetPageAfterMutation(kind);
    markReferenceDataStale(referenceKindsForMutation(kind));
    await loadAllData();
    if ((kind === "vehicle" || kind === "vehicleInbound" || kind === "vehicleOutbound") && activeModelKey) {
      await loadVehicleModelDetail(activeModelKey, state.selectedVehicleId, false);
    } else if ((kind === "partReplace" || kind === "modificationOrder" || kind === "vehiclePartInstall") && payload.machineId) {
      if (activeModelKey) {
        await loadVehicleModelDetail(activeModelKey, payload.machineId, false);
      } else {
        await loadVehicleDetail(payload.machineId, false);
      }
    }
    renderCurrentTab();
  } catch (error) {
    handleActionError(error);
  }
}

async function deleteEntity(kind, id) {
  if (!id || !endpoints[kind]?.delete) return;
  const item = findEntity(kind, id);
  if (!(await confirmDanger({
    title: `删除${entityLabel(kind)}`,
    target: entityDisplayName(kind, item),
    impact: "删除后将从当前列表移除；如该数据被业务单据引用，后端会阻止删除。"
  }))) return;
  await api(withVersion(endpoints[kind].delete(id), item), { method: "DELETE" });
  showContextSuccess(`${entityLabel(kind)}已删除`, entityDisplayName(kind, item), "相关列表已刷新");
  if (state.detailDrawer?.kind === kind && Number(state.detailDrawer.id) === Number(id)) {
    closeDetailDrawer();
  }
  toggleBatchSelection(kind, id, false);
  markReferenceDataStale(referenceKindsForMutation(kind));
  if (kind === "configValue") {
    await loadConfigValues(state.selectedConfigItemId);
  } else {
    await loadAllData();
    renderCurrentTab();
  }
}

async function completeModificationOrder(id) {
  const order = findModificationOrder(id);
  if (!order.id) {
    showToast("工单数据已刷新，请再点一次完成", "info");
    await loadAllData();
    renderCurrentTab();
    return;
  }
  if (!(await confirmDanger({
    title: "完成改装工单",
    target: entityDisplayName("modificationOrder", order),
    impact: "将扣减新配件库存、旧件入库，并更新车辆配置。"
  }))) return;
  const activeModelKey = state.vehicleDetail?.modelKey;
  const targetMachineId = order.machineId || state.selectedVehicleId;
  await api(endpoints.modificationOrder.complete(id), {
    method: "PUT",
    body: { version: order.version }
  });
  showContextSuccess("改装工单已完成", entityDisplayName("modificationOrder", order), "车辆配置与配件库存已同步");
  markReferenceDataStale(["vehicle", "part"]);
  await loadAllData();
  if (targetMachineId) {
    if (activeModelKey) {
      await loadVehicleModelDetail(activeModelKey, targetMachineId, false);
    } else {
      await loadVehicleDetail(targetMachineId, false);
    }
  }
  renderCurrentTab();
}

async function completeStocktaking(id) {
  const record = findEntity("stocktaking", id);
  if (!record.id) {
    showToast("盘点记录已刷新，请再试一次", "info");
    await loadAllData();
    renderCurrentTab();
    return;
  }
  if (!(await confirmDanger({
    title: "盘点入账",
    target: entityDisplayName("stocktaking", record),
    impact: "将按实盘数量同步库存，入账后不能作为草稿继续编辑。"
  }))) return;
  await api(withVersion(endpoints.stocktaking.complete(id), record), { method: "PUT" });
  showContextSuccess("盘点已入账", entityDisplayName("stocktaking", record), "库存数量已同步");
  markReferenceDataStale(["vehicle", "part"]);
  resetPageAfterMutation("stocktaking");
  await loadAllData();
  renderCurrentTab();
}

async function completeStocktakingDirect(record) {
  await api(withVersion(endpoints.stocktaking.complete(record.id), record), { method: "PUT" });
}

async function togglePurchaseReceived(id) {
  const order = findEntity("purchaseOrder", id);
  if (!order.id) {
    showToast("采购订单已刷新，请再试一次", "info");
    await loadAllData();
    renderCurrentTab();
    return;
  }
  const nextReceived = order.status !== "RECEIVED";
  if (!(await confirmDanger({
    title: nextReceived ? "确认采购收货" : "撤销采购收货",
    target: entityDisplayName("purchaseOrder", order),
    impact: nextReceived ? "订单会标记为已收货，并可能影响采购库存和成本跟踪。" : "订单会回到待收货状态，请确认业务状态仍允许撤销。"
  }))) return;
  const url = withVersion(`${endpoints.purchaseOrder.received(id)}?received=${nextReceived ? "true" : "false"}`, order);
  await api(url, { method: "PUT" });
  showToast(nextReceived ? "采购订单已收货，运费默认为 0" : "采购订单已改为待收货", "success");
  resetPageAfterMutation("purchaseOrder");
  await loadAllData();
  renderCurrentTab();
}

async function setPurchaseReceivedDirect(order, received) {
  const url = withVersion(`${endpoints.purchaseOrder.received(order.id)}?received=${received ? "true" : "false"}`, order);
  await api(url, { method: "PUT" });
}

async function cancelModificationOrder(id) {
  const order = findModificationOrder(id);
  if (!order.id) {
    showToast("工单数据已刷新，请再点一次取消", "info");
    await loadAllData();
    renderCurrentTab();
    return;
  }
  if (!(await confirmDanger({
    title: "取消改装工单",
    target: entityDisplayName("modificationOrder", order),
    impact: "取消后该工单不会再进入完成流转。"
  }))) return;
  const activeModelKey = state.vehicleDetail?.modelKey;
  const targetMachineId = order.machineId || state.selectedVehicleId;
  await api(endpoints.modificationOrder.cancel(id), {
    method: "PUT",
    body: { version: order.version }
  });
  showContextSuccess("改装工单已取消", entityDisplayName("modificationOrder", order), "车辆和配件数据已刷新");
  markReferenceDataStale(["vehicle", "part"]);
  await loadAllData();
  if (targetMachineId) {
    if (activeModelKey) {
      await loadVehicleModelDetail(activeModelKey, targetMachineId, false);
    } else {
      await loadVehicleDetail(targetMachineId, false);
    }
  }
  renderCurrentTab();
}

function attachVersion(payload, item) {
  if (payload.version !== undefined && payload.version !== null) return;
  if (item?.version === undefined || item.version === null) return;
  payload.version = item.version;
}

function withVersion(url, item) {
  if (item?.version === undefined || item.version === null) return url;
  const separator = url.includes("?") ? "&" : "?";
  return `${url}${separator}version=${encodeURIComponent(item.version)}`;
}

function attachStockVersion(payload, type) {
  if (payload.version !== undefined && payload.version !== null) return;
  const source = type === "vehicle"
    ? findEntity("vehicle", Number(payload.machineId || 0))
    : state.data.parts.find(item => item.partCode === payload.partCode);
  attachVersion(payload, source || {});
}

function enrichPartReplacePayload(payload) {
  const context = state.modal?.context || {};
  const machine = context.machine || findEntity("vehicle", Number(payload.machineId || 0));
  const config = (context.machineConfigs || [])
    .find(item => String(item.id) === String(payload.machineConfigId));
  const part = state.data.parts.find(item => String(item.id) === String(payload.newPartId));
  const configValue = (state.data.configValueMap[config?.configItemId] || [])
    .find(item => String(item.id) === String(payload.newConfigValueId));

  if (payload.machineVersion === undefined && machine?.version !== undefined) {
    payload.machineVersion = machine.version;
  }
  if (payload.machineConfigVersion === undefined && config?.version !== undefined) {
    payload.machineConfigVersion = config.version;
  }
  if (payload.newPartVersion === undefined && part?.version !== undefined) {
    payload.newPartVersion = part.version;
  }
  if (payload.newConfigValueVersion === undefined && configValue?.version !== undefined) {
    payload.newConfigValueVersion = configValue.version;
  }
}

function enrichVehiclePartInstallPayload(payload) {
  const machine = state.modal?.context?.machine || findEntity("vehicle", Number(payload.machineId || 0));
  const part = state.data.parts.find(item => String(item.id) === String(payload.newPartId));
  if (payload.machineVersion === undefined && machine?.version !== undefined) {
    payload.machineVersion = machine.version;
  }
  if (payload.newPartVersion === undefined && part?.version !== undefined) {
    payload.newPartVersion = part.version;
  }
}

function buildModificationOrderPayload(payload) {
  return {
    machineId: payload.machineId,
    machineVersion: payload.machineVersion,
    customerName: payload.customerName,
    salesOrderNo: payload.salesOrderNo,
    operator: payload.operator,
    remark: payload.remark,
    lines: [
      {
        machineConfigId: payload.machineConfigId,
        machineConfigVersion: payload.machineConfigVersion,
        newPartId: payload.newPartId,
        newPartVersion: payload.newPartVersion,
        newConfigValueId: payload.newConfigValueId,
        newConfigValueVersion: payload.newConfigValueVersion,
        quantity: payload.quantity || 1,
        oldPartAction: payload.oldPartAction || "STOCK_IN",
        priceDifference: payload.priceDifference || 0,
        remark: payload.remark
      }
    ]
  };
}

function buildInboundConfigs(item) {
  return ensureConfigSelections(item)
    .filter(row => row.configItemId && row.configValueId)
    .map(row => {
      const configItem = state.data.configItems.find(item => String(item.id) === String(row.configItemId));
      const configValue = (state.data.configValueMap[row.configItemId] || [])
        .find(value => String(value.id) === String(row.configValueId));
      return {
        configItemId: Number(row.configItemId),
        configValueId: Number(row.configValueId),
        itemName: configItem?.itemName || "",
        selectedValue: configValue?.valueLabel || "",
        isStandard: true,
        configSource: "FACTORY_STANDARD"
      };
    });
}

function buildVehicleInboundPayload(payload, item = {}) {
  const model = item.__modelIdentity || {};
  return {
    ...payload,
    name: model.name || payload.name,
    specificationModel: model.specificationModel || payload.specificationModel,
    machineType: model.machineType || payload.machineType,
    modelOnly: false
  };
}

async function ensureVehicleOutboundCustomer(payload) {
  if (payload.customerMode !== "quickCreate") {
    if (!payload.customerId) {
      showToast("请选择客户", "error");
      return null;
    }
    return { customerId: payload.customerId, created: false };
  }

  const companyName = String(payload.customerCompanyName || "").trim();
  if (!companyName) {
    showToast("请填写客户公司", "error");
    return null;
  }

  const existingCustomer = state.data.customers.find(item => normalizeText(item.companyName) === normalizeText(companyName));
  if (existingCustomer) {
    return { customerId: existingCustomer.id, created: false };
  }

  const createdCustomer = await api(endpoints.customer.create, {
    method: "POST",
    body: {
      companyName,
      address: payload.customerAddress,
      contactName: payload.customerContactName || companyName,
      contactPhone: payload.customerContactPhone,
      taxOrIdNumber: payload.customerTaxOrIdNumber,
      remarks: payload.customerRemarks
    }
  });
  return { customerId: createdCustomer.id, created: true };
}

async function uploadInvoiceForOrder(order, form) {
  if (!order?.id) {
    throw new Error("未找到订单");
  }
  const file = form.elements.invoiceFile?.files?.[0];
  if (!file) {
    throw new Error("请选择发票文件");
  }
  const formData = new FormData();
  formData.append("file", file);
  await api(withVersion(endpoints.outboundOrder.uploadInvoice(order.id), order), {
    method: "POST",
    body: formData
  });
  showContextSuccess("发票已上传", entityDisplayName("outboundOrder", order), file.name);
}

async function uploadContractForOrder(order, form) {
  if (!order?.id) {
    throw new Error("未找到订单");
  }
  const file = form.elements.contractFile?.files?.[0];
  if (!file) {
    throw new Error("请选择合同文件");
  }
  const formData = new FormData();
  formData.append("file", file);
  await api(withVersion(endpoints.outboundOrder.uploadContract(order.id), order), {
    method: "POST",
    body: formData
  });
  showContextSuccess("合同已上传", entityDisplayName("outboundOrder", order), file.name);
}

async function downloadInvoice(order) {
  if (!order?.id || !order.invoiceFileAvailable) {
    showToast("该订单还没有发票文件", "error");
    return;
  }
  const headers = {};
  if (state.token) {
    headers.Authorization = `Bearer ${state.token}`;
  }
  const response = await fetch(endpoints.outboundOrder.downloadInvoice(order.id), { headers });
  if (!response.ok) {
    const payload = await response.json().catch(() => null);
    throw new Error(payload?.message || `下载失败：${response.status}`);
  }
  const blob = await response.blob();
  const filename = filenameFromDisposition(response.headers.get("Content-Disposition"))
    || order.invoiceOriginalName
    || `${order.orderNo || "invoice"}-发票`;
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

async function downloadContract(order) {
  if (!order?.id || !order.contractFileAvailable) {
    showToast("该订单还没有合同文件", "error");
    return;
  }
  const headers = {};
  if (state.token) {
    headers.Authorization = `Bearer ${state.token}`;
  }
  const response = await fetch(endpoints.outboundOrder.downloadContract(order.id), { headers });
  if (!response.ok) {
    const payload = await response.json().catch(() => null);
    throw new Error(payload?.message || `下载失败：${response.status}`);
  }
  const blob = await response.blob();
  const filename = filenameFromDisposition(response.headers.get("Content-Disposition"))
    || order.contractOriginalName
    || `${order.orderNo || "contract"}-合同`;
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

async function downloadExcel(type) {
  const url = endpoints.export?.[type];
  if (!url) {
    showToast("暂不支持该列表导出", "error");
    return;
  }
  await downloadProtectedFile(url, `${exportFileLabel(type)}-${todayInputDate()}.xlsx`);
  showToast("导出已开始", "success");
}

async function downloadProtectedFile(url, fallbackName) {
  const headers = {};
  if (state.token) {
    headers.Authorization = `Bearer ${state.token}`;
  }
  const response = await fetch(url, { headers });
  if (response.status === 401) {
    const error = new Error("未登录或登录已过期");
    error.authExpired = true;
    throw error;
  }
  if (!response.ok) {
    const payload = await response.json().catch(() => null);
    throw new Error(payload?.message || `下载失败：${response.status}`);
  }
  const blob = await response.blob();
  const filename = filenameFromDisposition(response.headers.get("Content-Disposition")) || fallbackName;
  const objectUrl = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = objectUrl;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
}

async function downloadDataBackup() {
  await downloadProtectedFile(endpoints.admin.backup, `forklift-erp-backup-${todayInputDate()}.json`);
}

async function restoreDataBackup(form) {
  const file = form.elements.backupFile?.files?.[0];
  const confirmation = String(form.elements.confirmation?.value || "").trim();
  if (!file) {
    throw new Error("请选择备份文件");
  }
  const formData = new FormData();
  formData.append("file", file);
  formData.append("confirmation", confirmation);
  await api(endpoints.admin.restore, {
    method: "POST",
    body: formData
  });
}

function exportFileLabel(type) {
  return {
    vehicles: "车辆库存",
    parts: "配件库存",
    customers: "客户档案",
    outboundOrders: "出库订单",
    rentals: "租赁记录",
    repairs: "维修记录"
  }[type] || "导出数据";
}

function filenameFromDisposition(disposition = "") {
  const encodedMatch = /filename\*=UTF-8''([^;]+)/i.exec(disposition);
  if (encodedMatch) {
    try {
      return decodeURIComponent(encodedMatch[1]);
    } catch {
      return encodedMatch[1];
    }
  }
  const plainMatch = /filename="?([^";]+)"?/i.exec(disposition);
  return plainMatch ? plainMatch[1] : "";
}

async function toggleOutboundOrderStatus(id, field) {
  const order = findEntity("outboundOrder", id);
  if (!order?.id || !field) return;
  const overrides = orderStatusToggleOverrides(order, field);
  if (!overrides) return;
  if (!(await confirmDanger({
    title: "确认切换订单状态",
    target: entityDisplayName("outboundOrder", order),
    impact: "该操作会更新收款、报销售、发票或合同等关键跟进状态，请确认与实际业务一致。"
  }))) return;

  await api(endpoints.outboundOrder.update(order.id), {
    method: "PUT",
    body: buildOutboundOrderStatusPayload(order, overrides)
  });
  showToast("订单状态已更新", "success");
  markReferenceDataStale(["vehicle", "outboundOrder"]);
  await loadAllData();
  renderCurrentTab();
}

async function toggleRepairStatus(id) {
  const repair = findEntity("repair", id);
  if (!repair?.id || !hasPermission("repair:write")) return;
  const nextStatus = repair.status === "COMPLETED" ? "PENDING" : "COMPLETED";
  if (!(await confirmDanger({
    title: nextStatus === "COMPLETED" ? "确认完成维修" : "撤回维修完成状态",
    target: entityDisplayName("repair", repair),
    impact: nextStatus === "COMPLETED" ? "该维修记录会标记为已完成。" : "该维修记录会重新标记为待处理。"
  }))) return;
  await api(endpoints.repair.updateStatus(repair.id), {
    method: "PUT",
    body: {
      version: repair.version,
      status: nextStatus
    }
  });
  showToast("维修状态已更新", "success");
  markReferenceDataStale(["repair"]);
  await loadAllData();
  renderCurrentTab();
}

async function setRepairStatusDirect(repair, status) {
  await api(endpoints.repair.updateStatus(repair.id), {
    method: "PUT",
    body: {
      version: repair.version,
      status
    }
  });
}

async function batchCompleteRepairs() {
  const rows = selectedRows("repair").filter(row => row.status !== "COMPLETED");
  if (!rows.length) {
    showToast("没有可完成的维修记录", "info");
    return;
  }
  if (!(await confirmDanger({
    title: "批量完成维修",
    target: `${rows.length} 条维修记录`,
    impact: "所选记录会统一标记为已完成。"
  }))) return;
  await Promise.all(rows.map(row => setRepairStatusDirect(row, "COMPLETED")));
  showContextSuccess("维修记录已批量完成", `${rows.length} 条`, "列表已刷新");
  clearBatchSelection("repair");
  markReferenceDataStale(["repair"]);
  await loadAllData();
  renderCurrentTab();
}

async function batchReceivePurchases() {
  const rows = selectedRows("purchaseOrder").filter(row => row.status !== "RECEIVED" && row.status !== "CANCELED");
  if (!rows.length) {
    showToast("没有可收货的采购订单", "info");
    return;
  }
  if (!(await confirmDanger({
    title: "批量收货",
    target: `${rows.length} 条采购订单`,
    impact: "所选订单会统一标记为已收货，运费默认为 0。"
  }))) return;
  await Promise.all(rows.map(row => setPurchaseReceivedDirect(row, true)));
  showContextSuccess("采购订单已批量收货", `${rows.length} 条`, "采购列表已刷新");
  clearBatchSelection("purchaseOrder");
  resetPageAfterMutation("purchaseOrder");
  await loadAllData();
  renderCurrentTab();
}

async function batchCompleteStocktakes() {
  const rows = selectedRows("stocktaking").filter(row => row.status !== "COMPLETED");
  if (!rows.length) {
    showToast("没有可入账的盘点记录", "info");
    return;
  }
  if (!(await confirmDanger({
    title: "批量盘点入账",
    target: `${rows.length} 条盘点记录`,
    impact: "所选盘点会同步库存数量，入账后不能作为草稿继续编辑。"
  }))) return;
  await Promise.all(rows.map(completeStocktakingDirect));
  showContextSuccess("盘点记录已批量入账", `${rows.length} 条`, "库存数量已同步");
  clearBatchSelection("stocktaking");
  markReferenceDataStale(["vehicle", "part"]);
  resetPageAfterMutation("stocktaking");
  await loadAllData();
  renderCurrentTab();
}

async function batchDeleteStocktakeDrafts() {
  const rows = selectedRows("stocktaking").filter(row => row.status !== "COMPLETED");
  if (!rows.length) {
    showToast("没有可删除的盘点草稿", "info");
    return;
  }
  if (!(await confirmDanger({
    title: "批量删除盘点草稿",
    target: `${rows.length} 条盘点草稿`,
    impact: "仅删除未入账草稿；删除后不可从列表恢复。"
  }))) return;
  await Promise.all(rows.map(row => api(withVersion(endpoints.stocktaking.delete(row.id), row), { method: "DELETE" })));
  showContextSuccess("盘点草稿已批量删除", `${rows.length} 条`, "盘点列表已刷新");
  clearBatchSelection("stocktaking");
  resetPageAfterMutation("stocktaking");
  await loadAllData();
  renderCurrentTab();
}

async function toggleUserJobTag(id) {
  const user = findEntity("user", id);
  if (!user?.id || !canUpdateUserJobTag(user)) return;
  const nextTag = nextJobTag(user.jobTag);
  if (!(await confirmDanger({
    title: "确认切换用户职务",
    target: entityDisplayName("user", user),
    impact: `用户职务将切换为${jobTagLabel(nextTag)}，可能影响维修人员选择和业务分配。`
  }))) return;
  await api(endpoints.user.updateJobTag(user.id), {
    method: "PUT",
    body: {
      version: user.version,
      jobTag: nextTag
    }
  });
  showToast(`职务已切换为${jobTagLabel(nextTag)}`, "success");
  markReferenceDataStale(["repairUser"]);
  await loadAllData();
  renderCurrentTab();
}

async function toggleUserEnabled(id) {
  const user = findEntity("user", id);
  if (!user?.id || !canUpdateUserEnabled(user)) return;
  const nextEnabled = !Boolean(user.enabled);
  if (!(await confirmDanger({
    title: nextEnabled ? "确认启用用户" : "确认停用用户",
    target: entityDisplayName("user", user),
    impact: nextEnabled ? "启用后该用户可以继续登录和参与业务。" : "停用后该用户将无法继续登录使用系统。"
  }))) return;
  await api(endpoints.user.updateEnabled(user.id), {
    method: "PUT",
    body: {
      version: user.version,
      enabled: nextEnabled
    }
  });
  showToast(nextEnabled ? "用户已启用" : "用户已停用", "success");
  markReferenceDataStale(["repairUser"]);
  await loadAllData();
  renderCurrentTab();
}

async function toggleOutboundOrderLock(id, locked) {
  const order = findEntity("outboundOrder", id);
  if (!order?.id || !canManageOrderLock()) return;
  if (!(await confirmDanger({
    title: locked ? "确认锁定订单" : "确认解锁订单",
    target: entityDisplayName("outboundOrder", order),
    impact: locked ? "锁定后关联记录仅管理员可见，请确认订单不再需要普通流程编辑。" : "解锁后关联记录会恢复普通流程可见性。"
  }))) return;
  const params = new URLSearchParams({ locked: String(locked) });
  if (order.version !== undefined && order.version !== null) {
    params.set("version", String(order.version));
  }
  await api(`${endpoints.outboundOrder.lock(order.id)}?${params.toString()}`, { method: "PUT" });
  showToast(locked ? "订单已锁定，关联记录仅管理员可见" : "订单已解锁", "success");
  markReferenceDataStale(["vehicle", "part", "outboundOrder"]);
  await loadAllData();
  renderCurrentTab();
}

async function goToTab(tab, data = {}) {
  if (!tabs[tab]) return;
  state.activeTab = tab;
  if (tab === "vehicles") {
    state.filters.vehicles.stock = data.stock || "";
  }
  els.content.innerHTML = renderLoading();
  await loadCurrentTab({ force: true });
  renderCurrentTab();
  scrollToWorkspaceTop();
}

function orderStatusToggleOverrides(order, field) {
  if (field === "paymentSettled") {
    return { paymentSettled: !Boolean(order.paymentSettled) };
  }
  if (field === "salesReported") {
    const next = !Boolean(order.salesReported);
    return {
      salesReported: next,
      salesReportDate: next && !order.salesReportDate ? todayInputDate() : order.salesReportDate
    };
  }
  if (field === "invoiceApplied") {
    const next = !Boolean(order.invoiceApplied);
    return {
      invoiceApplied: next,
      invoiceApplicationDate: next && !order.invoiceApplicationDate ? todayInputDate() : order.invoiceApplicationDate
    };
  }
  if (field === "registrationStatus") {
    return { registrationStatus: yesNoFromStatusText(order.registrationStatus) ? "未上牌" : "已上牌" };
  }
  if (field === "contractType") {
    return { contractType: yesNoFromStatusText(order.contractType) ? "无合同" : "有合同" };
  }
  return null;
}

function buildOutboundOrderStatusPayload(order, overrides) {
  return {
    version: order.version,
    settlementPrice: order.settlementPrice,
    salesDate: order.salesDate,
    salePrice: order.salePrice,
    receivableAmount: order.receivableAmount,
    receivedAmount: order.receivedAmount,
    paymentDueDate: order.paymentDueDate,
    lastPaymentDate: order.lastPaymentDate,
    paymentSettled: Boolean(order.paymentSettled),
    paymentRemark: order.paymentRemark,
    salesReported: Boolean(order.salesReported),
    invoiceApplied: Boolean(order.invoiceApplied),
    salesReportDate: order.salesReportDate,
    invoiceApplicationDate: order.invoiceApplicationDate,
    invoiceStatus: order.invoiceStatus,
    invoiceIssuedDate: order.invoiceIssuedDate,
    registrationStatus: order.registrationStatus,
    contractType: order.contractType,
    orderRemark: order.orderRemark,
    operator: order.operator,
    ...overrides
  };
}

async function openEntityModal(kind, item = {}) {
  await ensureModalDependencies(kind);
  if ((kind === "configValue" || kind === "vehiclePartInstall" || kind === "modificationOrder") && !state.data.configItems.length) {
    await ensureConfigData();
  }

  state.modal = { kind, item: { ...item }, context: {} };
  if (kind === "repair") {
    prepareRepairModalItem(state.modal.item);
  }
  if (kind === "rental" && (state.modal.item.monthlyRentalPrice === undefined || state.modal.item.monthlyRentalPrice === null)) {
    state.modal.item.monthlyRentalPrice = state.modal.item.rentalPrice || "";
  }
  if (kind === "vehicleInbound") {
    ensureConfigSelections(state.modal.item);
  }
  if (kind === "partReplace" || kind === "modificationOrder") {
    await preparePartReplaceContext(
      effectiveFieldValue(state.modal.item, "machineId") || state.selectedVehicleId,
      effectiveFieldValue(state.modal.item, "machineConfigId"),
      { placeholderConfig: kind === "modificationOrder" }
    );
  }
  if (kind === "vehiclePartInstall") {
    prepareVehiclePartInstallContext(effectiveFieldValue(state.modal.item, "machineId") || state.selectedVehicleId);
  }
  renderModal();
}

async function ensureModalDependencies(kind) {
  const needsVehicles = ["vehicleStock", "vehicleOutbound", "partReplace", "vehiclePartInstall", "modificationOrder", "vehicleInbound", "rental", "repair", "stocktaking", "stockTransfer"].includes(kind);
  const needsParts = ["part", "partStock", "partReplace", "vehiclePartInstall", "modificationOrder", "repair", "stocktaking", "stockTransfer"].includes(kind);
  const needsCustomers = ["vehicleOutbound", "partStock", "partOutbound", "customer", "rental", "repair"].includes(kind);
  const needsSuppliers = kind === "purchaseOrder";
  const needsRentals = ["vehicleOutbound", "rental", "repair"].includes(kind);
  const needsRepairUsers = kind === "repair";
  const needsWarehouses = ["warehouse", "stockTransfer"].includes(kind);
  const requests = [];
  if (needsVehicles && !state.reference.vehiclesLoaded) {
    requests.push(api(referencePageUrl(endpoints.vehicle.list)).then(payload => {
      state.data.vehicles = sortById(rowsFromPayload(payload));
      state.reference.vehiclesLoaded = true;
    }));
  }
  if (needsParts && !state.reference.partsLoaded) {
    requests.push(api(referencePageUrl(endpoints.part.list)).then(payload => {
      state.data.parts = sortById(rowsFromPayload(payload));
      state.reference.partsLoaded = true;
    }));
  }
  if (needsCustomers && !state.reference.customersLoaded && hasAnyPermission("stock:adjust", "vehicle:write", "repair:write")) {
    requests.push(api(referencePageUrl(endpoints.customer.list)).then(payload => {
      state.data.customers = sortById(rowsFromPayload(payload), false);
      state.reference.customersLoaded = true;
    }));
  }
  if (needsSuppliers && !state.reference.suppliersLoaded && hasPermission("stock:adjust")) {
    requests.push(api(referencePageUrl(endpoints.supplier.list)).then(payload => {
      state.data.suppliers = sortById(rowsFromPayload(payload), false);
      state.reference.suppliersLoaded = true;
    }));
  }
  if (needsRentals && !state.reference.rentalsLoaded && hasPermission("stock:adjust")) {
    requests.push(api(referencePageUrl(endpoints.rental.list)).then(payload => {
      state.data.rentals = sortById(rowsFromPayload(payload));
      state.reference.rentalsLoaded = true;
    }));
  }
  if (needsRepairUsers && hasPermission("repair:write")) {
    requests.push(api(endpoints.user.repairers).then(payload => {
      state.data.repairUsers = sortById(rowsFromPayload(payload));
      state.reference.repairUsersLoaded = true;
    }));
  }
  if (needsWarehouses && !state.reference.warehousesLoaded && hasPermission("stock:adjust")) {
    requests.push(api(referencePageUrl(endpoints.warehouse.list)).then(payload => {
      state.data.warehouses = sortById(rowsFromPayload(payload), false);
      state.reference.warehousesLoaded = true;
    }));
  }
  await Promise.all(requests);
}

function markReferenceDataStale(kinds = []) {
  if (!kinds.length || kinds.includes("vehicle")) state.reference.vehiclesLoaded = false;
  if (!kinds.length || kinds.includes("part")) state.reference.partsLoaded = false;
  if (!kinds.length || kinds.includes("customer")) state.reference.customersLoaded = false;
  if (!kinds.length || kinds.includes("supplier")) state.reference.suppliersLoaded = false;
  if (!kinds.length || kinds.includes("warehouse")) state.reference.warehousesLoaded = false;
  if (!kinds.length || kinds.includes("outboundOrder")) state.reference.outboundOrdersLoaded = false;
  if (!kinds.length || kinds.includes("rental")) state.reference.rentalsLoaded = false;
  if (!kinds.length || kinds.includes("repairUser")) state.reference.repairUsersLoaded = false;
}

function referenceKindsForMutation(kind) {
  const mapping = {
    vehicle: ["vehicle"],
    vehicleModel: ["vehicle"],
    vehicleInbound: ["vehicle"],
    vehicleOutbound: ["vehicle", "customer", "outboundOrder"],
    vehicleStock: ["vehicle"],
    part: ["part"],
    partStock: ["part", "customer", "outboundOrder"],
    partReplace: ["vehicle", "part"],
    vehiclePartInstall: ["vehicle", "part"],
    modificationOrder: ["vehicle", "part"],
    outboundOrder: ["vehicle", "part", "outboundOrder"],
    rental: ["vehicle", "rental"],
    repair: ["vehicle", "part", "customer", "rental"],
    customer: ["customer"],
    supplier: ["supplier"],
    warehouse: ["warehouse"],
    stockTransfer: ["vehicle", "part", "warehouse"],
    purchaseOrder: ["supplier"],
    purchaseFreight: [],
    stocktaking: ["vehicle", "part"],
    invoiceUpload: ["outboundOrder"],
    contractUpload: ["outboundOrder"],
    user: ["repairUser"]
  };
  return mapping[kind] || [];
}

function resetPageAfterMutation(kind) {
  const mapping = {
    vehicle: "vehicles",
    vehicleModel: "vehicles",
    vehicleInbound: "vehicles",
    modificationOrder: "modificationOrders",
    rental: "rentals",
    part: "parts",
    vehiclePartInstall: "vehicles",
    customer: "customers",
    repair: "repairs",
    outboundOrder: "outboundOrders",
    supplier: "suppliers",
    warehouse: "warehouses",
    stockTransfer: "warehouses",
    purchaseOrder: "purchases",
    purchaseFreight: "purchases",
    stocktaking: "stocktakes"
  };
  const tab = mapping[kind];
  if (tab && state.pages[tab]) {
    resetPage(state.pages[tab]);
  }
}

function closeModal() {
  state.modal = null;
  modalPointerDownStartedOnOverlay = false;
  els.modalOverlay.classList.add("is-hidden");
  els.modalOverlay.setAttribute("aria-hidden", "true");
  els.modalCard.innerHTML = "";
}

async function preparePartReplaceContext(machineId, machineConfigId, options = {}) {
  const numericMachineId = Number(machineId || 0);
  if (!numericMachineId) {
    state.modal.context.machine = null;
    state.modal.context.machineConfigs = [];
    state.modal.context.compatibleParts = [];
    return;
  }
  const detail = await api(`/api/inventory/${numericMachineId}/detail`);
  state.modal.item.machineId = numericMachineId;
  state.modal.context.machine = detail.machine || findEntity("vehicle", numericMachineId);
  state.modal.context.machineConfigs = detail.configs || [];
  const selectedConfigId = machineConfigId || effectiveFieldValue(state.modal.item, "machineConfigId") || state.modal.context.machineConfigs[0]?.id || null;
  if (options.placeholderConfig) {
    const placeholderConfig = state.modal.context.machineConfigs
      .find(item => String(item.id) === String(selectedConfigId));
    clearPrefill(state.modal.item, "machineConfigId");
    state.modal.item.newPartId = null;
    state.modal.item.__placeholders = {
      ...(state.modal.item.__placeholders || {}),
      machineConfigId: placeholderConfig ? `预填：${machineConfigOptionLabel(placeholderConfig)}` : undefined
    };
    setPrefill(
      state.modal.item,
      "machineConfigId",
      placeholderConfig?.id,
      placeholderConfig ? `预填：${machineConfigOptionLabel(placeholderConfig)}` : undefined
    );
  } else {
    state.modal.item.machineConfigId = selectedConfigId;
  }
  updateCompatibleParts(selectedConfigId);
  if (options.placeholderConfig) {
    if (state.modal.item.__prefillValues?.machineId) {
      state.modal.item.machineId = null;
    }
    state.modal.item.machineConfigId = null;
  }
}

function updateCompatibleParts(machineConfigId) {
  const config = (state.modal?.context?.machineConfigs || [])
    .find(item => String(item.id) === String(machineConfigId));
  state.modal.item.machineConfigId = machineConfigId;
  const expectedTypes = configTypeCandidates(config);
  state.modal.context.compatibleParts = state.data.parts
    .filter(part => Number(part.quantity || 0) > 0)
    .filter(part => expectedTypes.includes(normalizeText(part.partCategory)));
}

function configTypeCandidates(config) {
  if (!config) return [];
  const item = state.data.configItems.find(configItem => String(configItem.id) === String(config.configItemId));
  return uniqueOptions([
    item?.subCategory,
    item?.itemName,
    config.itemName
  ]).map(option => normalizeText(option.value));
}

function prepareVehiclePartInstallContext(machineId) {
  const numericMachineId = Number(machineId || 0);
  state.modal.context.machine = numericMachineId ? findEntity("vehicle", numericMachineId) : null;
  if (numericMachineId) {
    state.modal.item.machineId = numericMachineId;
  }
  const firstCategory = installPartCategoryOptions()[0]?.value || null;
  if (!state.modal.item.configItemId && firstCategory) {
    state.modal.item.configItemId = firstCategory;
  }
}

function renderModal() {
  if (!state.modal) return;
  const { kind, item } = state.modal;
  const title = modalTitle(kind, item);
  const modalFields = getFields(kind, item);
  els.modalCard.innerHTML = `
    <div class="modal-head">
      <div>
        <h2 class="surface-title">${escapeHtml(title)}</h2>
        <div class="helper">${escapeHtml(modalSubtitle(kind))}</div>
      </div>
      <button class="btn btn-ghost btn-sm" type="button" data-close-modal>关闭</button>
    </div>
    <form data-kind="${escapeAttr(kind)}">
      <div class="modal-body">
        <div class="modal-grid">
          ${renderModalFields(modalFields, item)}
        </div>
        ${kind === "vehicleInbound" ? renderConfigSelectionEditor(item) : ""}
      </div>
      <div class="modal-actions">
        <button class="btn btn-ghost" type="button" data-close-modal>取消</button>
        <button class="btn btn-primary" type="submit">保存</button>
      </div>
    </form>
  `;
  els.modalOverlay.classList.remove("is-hidden");
  els.modalOverlay.setAttribute("aria-hidden", "false");
}

function renderCurrentTab() {
  syncShell();
  switch (state.activeTab) {
    case "vehicles":
      els.content.innerHTML = renderVehicles();
      break;
    case "parts":
      els.content.innerHTML = renderParts();
      break;
    case "modifications":
      els.content.innerHTML = renderModificationOrders();
      break;
    case "outboundOrders":
      els.content.innerHTML = renderOutboundOrders();
      break;
    case "rentals":
      els.content.innerHTML = renderRentals();
      break;
    case "customers":
      els.content.innerHTML = renderCustomers();
      break;
    case "suppliers":
      els.content.innerHTML = renderSuppliers();
      break;
    case "purchases":
      els.content.innerHTML = renderPurchaseOrders();
      break;
    case "stocktakes":
      els.content.innerHTML = renderStocktakes();
      break;
    case "warehouses":
      els.content.innerHTML = renderWarehouses();
      break;
    case "repairs":
      els.content.innerHTML = renderRepairs();
      break;
    case "stats":
      els.content.innerHTML = renderStatistics();
      break;
    case "stockMovements":
      els.content.innerHTML = renderStockMovements();
      break;
    case "logs":
      els.content.innerHTML = renderOperationLogs();
      break;
    case "configs":
      els.content.innerHTML = renderConfigs();
      break;
    case "users":
      els.content.innerHTML = renderUsers();
      break;
    case "maintenance":
      els.content.innerHTML = renderMaintenance();
      break;
    default:
      els.content.innerHTML = renderOverview();
  }
  renderDetailDrawer();
  persistListState();
}

function scrollToWorkspaceTop() {
  window.scrollTo({ top: 0, behavior: "smooth" });
}

function scrollToVehicleDetail() {
  requestAnimationFrame(() => {
    const target = document.getElementById("vehicleDetailSurface");
    if (target) target.scrollIntoView({ block: "start", behavior: "smooth" });
  });
}

function openDetailDrawer(kind, id) {
  const item = findEntity(kind, Number(id || 0));
  if (!item?.id) return;
  state.detailDrawer = { kind, id: Number(id) };
  renderCurrentTab();
}

function closeDetailDrawer() {
  state.detailDrawer = null;
  if (!els?.detailDrawerOverlay) return;
  if (detailDrawerCloseTimer) {
    window.clearTimeout(detailDrawerCloseTimer);
    detailDrawerCloseTimer = null;
  }
  els.detailDrawerOverlay.classList.remove("is-open");
  els.detailDrawerOverlay.setAttribute("aria-hidden", "true");
  const hideDrawer = () => {
    els.detailDrawerOverlay.classList.add("is-hidden");
    els.detailDrawer.innerHTML = "";
    detailDrawerCloseTimer = null;
  };
  if (els.detailDrawerOverlay.classList.contains("is-hidden")) {
    hideDrawer();
    return;
  }
  detailDrawerCloseTimer = window.setTimeout(hideDrawer, DETAIL_DRAWER_TRANSITION_MS);
}

function renderDetailDrawer() {
  if (!els?.detailDrawerOverlay) return;
  const drawer = state.detailDrawer;
  if (!drawer) {
    closeDetailDrawer();
    return;
  }
  const item = findEntity(drawer.kind, Number(drawer.id));
  if (!item?.id) {
    closeDetailDrawer();
    return;
  }
  if (detailDrawerCloseTimer) {
    window.clearTimeout(detailDrawerCloseTimer);
    detailDrawerCloseTimer = null;
  }
  const title = detailTitle(drawer.kind, item);
  els.detailDrawer.innerHTML = `
    <div class="detail-drawer-head">
      <div>
        <div class="detail-drawer-kicker">${escapeHtml(entityLabel(drawer.kind))}</div>
        <h2>${escapeHtml(title)}</h2>
      </div>
      <button class="btn btn-ghost btn-sm" type="button" data-action="close-detail">关闭</button>
    </div>
    <div class="detail-drawer-body">
      ${renderDetailGrid(detailFields(drawer.kind, item))}
      ${renderDetailDrawerActions(drawer.kind, item)}
    </div>
  `;
  els.detailDrawerOverlay.classList.remove("is-hidden");
  els.detailDrawerOverlay.setAttribute("aria-hidden", "false");
  requestAnimationFrame(() => {
    els.detailDrawerOverlay.classList.add("is-open");
  });
}

function detailTitle(kind, item) {
  return {
    part: item.partName || item.partCode,
    modificationOrder: item.workOrderNo || item.machineProductNumber,
    outboundOrder: item.orderNo || item.resourceCode,
    rental: item.rentalNo || item.vehicleNumber,
    customer: item.companyName || item.contactName,
    supplier: item.supplierName || item.contactName,
    purchaseOrder: item.purchaseNo || item.resourceName,
    stocktaking: item.stocktakingNo || item.resourceName,
    repair: item.customerName || item.vehicleNumber,
    user: item.username
  }[kind] || `ID ${item.id}`;
}

function detailFields(kind, item) {
  const fields = {
    part: [
      ["编码", item.partCode],
      ["名称", item.partName],
      ["品牌", item.partBrand],
      ["分类", item.partCategory],
      ["适配车型", item.applicableModels],
      ["库存", `${item.quantity ?? 0}${item.unit || ""}`],
      ["采购价", money(item.purchasePrice)],
      ["销售价", money(item.salePrice)],
      ["结算价", money(item.settlementPrice)],
      ["来源", item.source],
      ["备注", item.remarks]
    ],
    modificationOrder: [
      ["工单号", item.workOrderNo],
      ["车辆", item.machineProductNumber],
      ["客户", item.customerName],
      ["销售单", item.salesOrderNo],
      ["状态", repairStatusText(item.status) || item.status],
      ["创建时间", dateTime(item.createdAt)],
      ["备注", item.remark]
    ],
    outboundOrder: [
      ["订单号", item.orderNo],
      ["出库类型", resourceTypeLabel(item.resourceType)],
      ["出库项", [item.resourceCode, item.resourceName].filter(Boolean).join(" / ")],
      ["客户", item.customerName],
      ["销售日期", dateValue(item.salesDate)],
      ["应收", money(item.receivableAmount ?? item.settlementPrice)],
      ["已收", money(item.receivedAmount)],
      ["欠款", money(receivableOutstanding(item))],
      ["车款结清", yesNoText(item.paymentSettled)],
      ["报销售", yesNoText(item.salesReported)],
      ["发票申请", yesNoText(item.invoiceApplied)],
      ["合同", item.contractType],
      ["备注", item.orderRemark]
    ],
    rental: [
      ["租赁单", item.rentalNo],
      ["车号", item.vehicleNumber],
      ["车辆", [item.machineName, item.specificationModel].filter(Boolean).join(" / ")],
      ["客户", item.customerName],
      ["去向", item.destination],
      ["月租价", money(rentalMonthlyPrice(item))],
      ["开始日期", dateValue(item.startDate)],
      ["结束日期", dateValue(item.endDate)],
      ["状态", item.status === "RETURNED" ? "已归还" : "租赁中"],
      ["备注", item.remark]
    ],
    customer: [
      ["公司名称", item.companyName],
      ["地址", item.address],
      ["联系人", item.contactName],
      ["电话", item.contactPhone],
      ["税号/身份证号", item.taxOrIdNumber],
      ["备注", item.remarks]
    ],
    supplier: [
      ["供应商", item.supplierName],
      ["类型", item.supplierType],
      ["联系人", item.contactName],
      ["电话", item.contactPhone],
      ["地址", item.address],
      ["税号", item.taxNumber],
      ["备注", item.remarks]
    ],
    purchaseOrder: [
      ["采购单", item.purchaseNo],
      ["供应商", item.supplierName],
      ["采购内容", [item.resourceCode, item.resourceName].filter(Boolean).join(" / ")],
      ["规格", item.specificationModel],
      ["数量", `${item.quantity ?? 0}${item.unit || ""}`],
      ["金额", money(item.totalAmount)],
      ["运费", money(item.freightAmount)],
      ["状态", item.status],
      ["备注", item.remark]
    ],
    stocktaking: [
      ["盘点单", item.stocktakingNo],
      ["对象类型", purchaseResourceLabel(item.resourceType)],
      ["盘点对象", [item.resourceCode, item.resourceName].filter(Boolean).join(" / ")],
      ["账面数量", stockText(item.bookQuantity)],
      ["实盘数量", stockText(item.actualQuantity)],
      ["差异", stockText(item.differenceQuantity)],
      ["状态", item.status === "COMPLETED" ? "已入账" : "草稿"],
      ["备注", item.remark]
    ],
    repair: [
      ["维修时间", dateTime(item.repairDate)],
      ["车号", item.vehicleNumber],
      ["客户", item.customerName],
      ["地址", item.customerAddress],
      ["维修人", item.repairPerson],
      ["故障描述", item.faultDescription],
      ["总费用", money(item.totalFee)],
      ["状态", repairStatusText(item.status)]
    ],
    user: [
      ["用户名", item.username],
      ["角色", (item.roles || []).join(" / ")],
      ["职务", jobTagLabel(item.jobTag)],
      ["状态", item.enabled ? "启用" : "停用"],
      ["创建时间", dateTime(item.createdAt)]
    ]
  }[kind] || Object.entries(item).slice(0, 8).map(([key, value]) => [key, value]);
  return fields.map(([label, value]) => ({ label, value }));
}

function renderDetailDrawerActions(kind, item) {
  const actions = {
    part: rowActions("part", item, ["stockIn", "stockOut", "edit", "delete"]),
    modificationOrder: modificationOrderActions(item),
    outboundOrder: outboundOrderActions(item),
    rental: rowActions("rental", item, ["edit", "delete"]),
    customer: rowActions("customer", item, ["edit", "delete"]),
    supplier: rowActions("supplier", item, ["edit", "delete"]),
    purchaseOrder: `${purchaseStatusControl(item)}${rowActions("purchaseOrder", item, ["edit", "delete"])}`,
    stocktaking: stocktakingActions(item),
    repair: `${repairStatusToggle(item)}${rowActions("repair", item, ["edit", "delete"])}`,
    user: userActions(item)
  }[kind] || "";
  return actions ? `<div class="detail-drawer-actions">${actions}</div>` : "";
}

function focusPrimarySearch() {
  const input = els.content.querySelector("[data-search-for]");
  if (!input) {
    showToast("当前页面没有搜索框", "info");
    return;
  }
  input.focus();
  input.select();
}

function syncShell() {
  if (!canAccessTab(state.activeTab)) {
    state.activeTab = "overview";
  }
  const tab = tabs[state.activeTab] || tabs.overview;
  els.pageTitle.textContent = tab.title;
  els.pageSubtitle.textContent = tab.subtitle;
  els.currentUser.textContent = state.user
    ? `${state.user.username}${state.user.roles?.length ? " · " + state.user.roles.join("/") : ""}`
    : "未登录";
  els.mainNav.querySelectorAll("[data-tab]").forEach(button => {
    const requiredRoles = (button.dataset.roles || button.dataset.role || "").split(",").map(role => role.trim()).filter(Boolean);
    const requiredPermissions = (button.dataset.permissions || button.dataset.permission || "").split(",").map(permission => permission.trim()).filter(Boolean);
    const allowed = (!requiredRoles.length || hasAnyRole(...requiredRoles))
      && (!requiredPermissions.length || hasAnyPermission(...requiredPermissions));
    button.classList.toggle("is-hidden", !allowed);
    button.classList.toggle("is-active", button.dataset.tab === state.activeTab);
    if (button.dataset.tab === state.activeTab) {
      button.setAttribute("aria-current", "page");
    } else {
      button.removeAttribute("aria-current");
    }
  });
  els.mainNav.querySelectorAll("[data-nav-group]").forEach(group => {
    const hasVisibleItem = [...group.querySelectorAll("[data-tab]")]
      .some(button => !button.classList.contains("is-hidden"));
    group.classList.toggle("is-hidden", !hasVisibleItem);
  });
}

function renderOverview() {
  const vehicles = state.data.vehicles.filter(item => !item.modelOnly);
  const vehicleRows = vehicleFlowRows();
  const parts = state.data.parts;
  const repairs = state.data.repairs;
  const orders = state.data.outboundOrders;
  const todoCenter = state.data.todoCenter || {};
  const inStockRows = vehicleRows.filter(row => !row.orderId && Number(row.inventoryCount || 0) > 0);
  const lowStockParts = parts.filter(item => Number(item.quantity || 0) <= 0);
  const missingInvoiceOrders = orders.filter(item => isInvoiceUploadReady(item) && !item.invoiceFileAvailable);
  const missingContractOrders = orders.filter(item => isContractUploadReady(item) && !item.contractFileAvailable);
  const pendingRepairs = repairs.filter(item => item.status !== "COMPLETED").length;
  const lowParts = lowStockParts.length;
  const unsettledOrders = todoCenter.pendingPaymentCount ?? orders.filter(item => !item.paymentSettled).length;
  const pendingSalesReports = todoCenter.pendingSalesReportCount ?? orders.filter(item => item.paymentSettled && !item.salesReported).length;
  const pendingInvoices = todoCenter.pendingInvoiceApplicationCount ?? orders.filter(item => item.paymentSettled && item.salesReported && !item.invoiceApplied).length;
  const missingInvoiceFiles = todoCenter.pendingInvoiceFileCount ?? missingInvoiceOrders.length;
  const missingContractFiles = todoCenter.pendingContractFileCount ?? missingContractOrders.length;
  const outstandingAmount = todoCenter.outstandingAmount ?? receivableOutstandingTotal(orders);
  const overduePaymentCount = todoCenter.overduePaymentCount ?? orders.filter(item => Number(item.overdueDays || 0) > 0).length;
  const vehicleTotal = pageTotal("vehicles", vehicles.length);
  const partTotal = pageTotal("parts", parts.length);
  const orderTotal = pageTotal("outboundOrders", orders.length);
  const workItems = (Array.isArray(todoCenter.items) && todoCenter.items.length
    ? overviewTodoItems(todoCenter)
    : overviewWorkItems(vehicleRows, orders, repairs, parts)).slice(0, 12);
  const recentVehicleRows = vehicleRows
    .filter(row => row.orderId || Number(row.inventoryCount || 0) > 0)
    .slice(0, 8);

  return `
    <div class="page overview-page">
      <section class="summary-grid">
        ${summaryCard("整车档案", vehicleTotal, `${inStockRows.length} 台在库待销售`, { action: "go-tab", data: { tab: "vehicles" } })}
        ${summaryCard("出库订单", orderTotal, `${unsettledOrders} 单待收款`, { action: "go-tab", data: { tab: "outboundOrders" } })}
        ${summaryCard("应收账款", money(outstandingAmount), `${overduePaymentCount} 单逾期`, { action: "go-tab", data: { tab: "outboundOrders" } })}
        ${summaryCard("销售闭环", pendingSalesReports + pendingInvoices, `${missingInvoiceFiles} 单待发票文件`, { action: "go-tab", data: { tab: "outboundOrders" } })}
        ${summaryCard("合同文件", missingContractFiles, `${orders.filter(isContractUploadReady).length} 单标记有合同`, { action: "go-tab", data: { tab: "outboundOrders" } })}
      </section>

      <section class="overview-stage-grid">
        ${overviewStageButton("在库待销售", inStockRows.length, "vehicles", { stock: "inStock" }, "teal")}
        ${overviewStageButton("待收款", unsettledOrders, "outboundOrders", {}, "warn")}
        ${overviewStageButton("待报销售", pendingSalesReports, "outboundOrders", {}, "primary")}
        ${overviewStageButton("待申请发票", pendingInvoices, "outboundOrders", {}, "warn")}
        ${overviewStageButton("待上传合同", missingContractFiles, "outboundOrders", {}, "teal")}
        ${overviewStageButton("维修跟进", pendingRepairs, "repairs", {}, "primary")}
        ${overviewStageButton("配件预警", lowParts, "parts", {}, "danger")}
      </section>

      <section class="overview-workbench">
        ${renderSurface("待处理事项", renderOverviewWorkItems(workItems))}
        <div class="overview-side">
          ${renderSurface("快捷入口", renderOverviewShortcuts())}
          ${renderSurface("订单附件", renderOverviewAttachmentItems(missingInvoiceOrders, missingContractOrders))}
          ${renderSurface("库存预警", compactTable([
            { label: "编码", key: "partCode" },
            { label: "名称", key: "partName" },
            { label: "数量", key: "quantity", formatter: stockText }
          ], lowStockParts.slice(0, 8)))}
        </div>
      </section>

      <section class="grid-two">
        ${renderSurface("整车流转", compactTable([
          { label: "车号", html: true, render: row => vehicleFlowMachineSummary(row) },
          { label: "车辆状态", html: true, render: row => vehicleFlowStatusSummary(row) },
          { label: "销售跟进", html: true, render: row => vehicleFlowFollowupSummary(row) },
          { label: "操作", html: true, render: row => vehicleFlowActions(row) }
        ], recentVehicleRows))}
        ${renderSurface("维修跟进", compactTable([
          { label: "客户", key: "customerName" },
          { label: "状态", html: true, render: row => repairStatusToggle(row) },
          { label: "费用", key: "totalFee", formatter: money },
          { label: "操作", html: true, render: row => hasPermission("repair:write") ? `<button class="btn btn-sm" type="button" data-action="toggle-repair-status" data-id="${escapeAttr(row.id)}">${icon("swap")}完成</button>` : "" }
        ], repairs.filter(item => item.status !== "COMPLETED").slice(0, 8)))}
      </section>
    </div>
  `;
}

function overviewStageButton(label, count, tab, data = {}, type = "primary") {
  const attrs = Object.entries(data)
    .filter(([, value]) => value !== undefined && value !== null && value !== "")
    .map(([key, value]) => `data-${toDataAttrName(key)}="${escapeAttr(value)}"`)
    .join(" ");
  return `
    <button class="overview-stage ${escapeAttr(type)}" type="button" data-action="go-tab" data-tab="${escapeAttr(tab)}" ${attrs}>
      <span>${escapeHtml(label)}</span>
      <strong>${escapeHtml(count)}</strong>
    </button>
  `;
}

function overviewTodoItems(todoCenter = {}) {
  if (!Array.isArray(todoCenter.items)) return [];
  return todoCenter.items.map(item => {
    const action = todoItemAction(item);
    return overviewItem(
      item.label,
      item.title,
      todoItemDetail(item),
      item.priority || "primary",
      action.name,
      action.data,
      item.sortTime || item.dueDate || ""
    );
  });
}

function todoItemAction(item = {}) {
  if (item.type === "ORDER_INVOICE_FILE") return { name: "upload-invoice", data: { id: item.resourceId } };
  if (item.type === "ORDER_CONTRACT_FILE") return { name: "upload-contract", data: { id: item.resourceId } };
  if (item.resourceType === "OUTBOUND_ORDER") return { name: "edit", data: { kind: "outboundOrder", id: item.resourceId } };
  if (item.resourceType === "REPAIR") return { name: "toggle-repair-status", data: { id: item.resourceId } };
  if (item.resourceType === "PART") return { name: "edit", data: { kind: "part", id: item.resourceId } };
  if (item.resourceType === "RENTAL") return { name: "edit", data: { kind: "rental", id: item.resourceId } };
  return { name: "go-tab", data: { tab: "overview" } };
}

function todoItemDetail(item = {}) {
  const fragments = [];
  if (item.detail) fragments.push(item.detail);
  if (Number(item.amount || 0) > 0) fragments.push(money(item.amount));
  if (item.dueDate) fragments.push(`到期 ${dateValue(item.dueDate)}`);
  if (Number(item.overdueDays || 0) > 0) fragments.push(`逾期 ${item.overdueDays} 天`);
  return fragments.join(" · ") || "-";
}

function overviewWorkItems(vehicleRows, orders, repairs, parts) {
  const items = [];
  orders.forEach(order => {
    const resource = `${order.resourceCode || "-"} · ${order.customerName || "-"}`;
    if (!order.paymentSettled) {
      items.push(overviewItem("待收款", resource, order.paymentRemark || order.orderNo, "warn", "edit", { kind: "outboundOrder", id: order.id }, order.createdAt));
    } else if (!order.salesReported) {
      items.push(overviewItem("待报销售", resource, dateValue(order.salesDate) || order.orderNo, "primary", "edit", { kind: "outboundOrder", id: order.id }, order.updatedAt));
    } else if (!order.invoiceApplied) {
      items.push(overviewItem("待申请发票", resource, order.invoiceStatus || order.orderNo, "warn", "edit", { kind: "outboundOrder", id: order.id }, order.updatedAt));
    }
    if (isInvoiceUploadReady(order) && !order.invoiceFileAvailable) {
      items.push(overviewItem("待上传发票", resource, order.invoiceStatus || order.orderNo, "primary", "upload-invoice", { id: order.id }, order.updatedAt));
    }
    if (isContractUploadReady(order) && !order.contractFileAvailable) {
      items.push(overviewItem("待上传合同", resource, order.contractType || order.orderNo, "teal", "upload-contract", { id: order.id }, order.updatedAt));
    }
  });
  repairs
    .filter(item => item.status !== "COMPLETED")
    .forEach(repair => items.push(overviewItem("维修跟进", repair.customerName || repair.machineInfo || "-", repairStatusText(repair.status), "primary", "toggle-repair-status", { id: repair.id }, repair.updatedAt)));
  parts
    .filter(part => Number(part.quantity || 0) <= 0)
    .forEach(part => items.push(overviewItem("库存预警", part.partCode || "-", part.partName || "-", "danger", "edit", { kind: "part", id: part.id }, part.updatedAt)));
  vehicleRows
    .filter(row => !row.orderId && Number(row.inventoryCount || 0) > 0)
    .slice(0, 5)
    .forEach(row => items.push(overviewItem("在库待销售", row.vehicleProductNumber || `ID ${row.id}`, vehicleModelLabel(row), "teal", "vehicle-outbound-direct", { machineId: row.id }, row.inboundDate)));
  return items.sort((left, right) => String(right.sortKey || "").localeCompare(String(left.sortKey || "")));
}

function overviewItem(label, title, detail, type, action, data = {}, sortKey = "") {
  return { label, title, detail, type, action, data, sortKey };
}

function renderOverviewAttachmentItems(invoiceOrders, contractOrders) {
  const rows = [
    ...invoiceOrders.map(order => overviewAttachmentItem(order, "发票", "upload-invoice")),
    ...contractOrders.map(order => overviewAttachmentItem(order, "合同", "upload-contract"))
  ].slice(0, 8);
  if (!rows.length) return emptyState("暂无待上传附件");
  return `
    <div class="overview-attachment-list">
      ${rows.map(item => `
        <div class="overview-attachment-row is-clickable" data-action="${escapeAttr(item.action)}" data-id="${escapeAttr(item.id)}" tabindex="0" role="button">
          <div class="overview-task-main">
            ${badge(item.type, item.type === "合同" ? "teal" : "primary")}
            <strong>${escapeHtml(item.title)}</strong>
            <span class="helper-inline">${escapeHtml(item.detail)}</span>
          </div>
          <button class="btn btn-sm" type="button" data-action="${escapeAttr(item.action)}" data-id="${escapeAttr(item.id)}">${icon("upload")}上传</button>
        </div>
      `).join("")}
    </div>
  `;
}

function overviewAttachmentItem(order, type, action) {
  return {
    id: order.id,
    action,
    type,
    title: order.resourceCode || order.orderNo || `订单 ${order.id}`,
    detail: order.customerName || (type === "合同" ? order.contractType : order.invoiceStatus) || "-"
  };
}

function renderOverviewWorkItems(items) {
  if (!items.length) return emptyState("暂无待处理事项");
  return `
    <div class="overview-task-list">
      ${items.map(item => `
        <div class="overview-task-row is-clickable" ${actionAttrs(item)} tabindex="0" role="button">
          <div class="overview-task-main">
            ${badge(item.label, item.type)}
            <strong>${escapeHtml(item.title || "-")}</strong>
            <span class="helper-inline">${escapeHtml(item.detail || "-")}</span>
          </div>
          ${overviewActionButton(item)}
        </div>
      `).join("")}
    </div>
  `;
}

function overviewActionButton(item) {
  const label = item.action === "upload-invoice" ? "上传发票"
    : item.action === "upload-contract" ? "上传合同"
      : item.action === "toggle-repair-status" ? "完成"
      : item.action === "vehicle-outbound-direct" ? "登记销售"
        : "处理";
  return `<button class="btn btn-sm" type="button" ${actionAttrs(item)}>${icon(item.action?.startsWith("upload") ? "upload" : item.action === "toggle-repair-status" ? "swap" : "edit")}${label}</button>`;
}

function actionAttrs(item = {}) {
  const attrs = [`data-action="${escapeAttr(item.action || "go-tab")}"`];
  Object.entries(item.data || {})
    .filter(([, value]) => value !== undefined && value !== null && value !== "")
    .forEach(([key, value]) => attrs.push(`data-${toDataAttrName(key)}="${escapeAttr(value)}"`));
  return attrs.join(" ");
}

function renderOverviewShortcuts() {
  return `
    <div class="overview-shortcuts">
      ${hasPermission("stock:adjust") ? `<button class="btn" type="button" data-action="go-tab" data-tab="outboundOrders">${icon("eye")}订单列表</button>` : ""}
      ${hasPermission("vehicle:write") ? renderVehicleModelMenu({ triggerClass: "btn hover-menu-trigger" }) : ""}
      ${hasPermission("stock:adjust") ? `<button class="btn" type="button" data-action="create" data-kind="vehicleOutbound">${icon("minus")}登记销售跟进</button>` : ""}
      <button class="btn" type="button" data-action="create" data-kind="part">${icon("plus")}新增配件</button>
      ${hasPermission("replace:write") ? `<button class="btn" type="button" data-action="create" data-kind="modificationOrder">${icon("swap")}新建改装工单</button>` : ""}
      <button class="btn btn-ghost" type="button" data-action="refresh">${icon("refresh")}刷新数据</button>
    </div>
  `;
}

function renderVehicles() {
  const modelRows = filterVehicleRows(state.data.vehicleModels || []);

  return `
    <div class="page">
      ${renderToolbar("vehicles", "搜索车型、型号、供应商或车号", null, "", null, {
        main: [vehicleFilterControls()],
        actions: [hasPermission("vehicle:write") ? renderVehicleModelMenu() : ""]
      })}
      ${renderVehicleDetail()}
      ${renderVehicleModelPanel(modelRows)}
      ${renderPagination("vehicles")}
    </div>
  `;
}

function pageTotal(tab, fallback) {
  return state.pages[tab]?.loaded ? state.pages[tab].totalElements : fallback;
}

function paginateClientRows(tab, rows) {
  const page = state.pages[tab];
  const size = Math.max(1, Number(page?.size || LIST_PAGE_SIZE));
  const totalElements = rows.length;
  const totalPages = Math.ceil(totalElements / size);
  const maxPage = Math.max(0, totalPages - 1);
  page.page = Math.min(Math.max(0, Number(page.page || 0)), maxPage);
  page.size = size;
  page.totalElements = totalElements;
  page.totalPages = totalPages;
  page.first = page.page <= 0;
  page.last = totalPages === 0 || page.page >= maxPage;
  page.loaded = true;
  page.loading = false;
  const start = page.page * size;
  return rows.slice(start, start + size);
}

function renderVehicleModelPanel(rows) {
  return renderExportableSurface("车型库存列表", "vehicles", renderTable([
    { label: "车型", render: row => vehicleModelLabel(row) },
    { label: "规格型号", key: "specificationModel" },
    { label: "车辆类型", key: "machineType" },
    { label: "供应商", key: "supplier" },
    { label: "经销商/仓位", key: "warehouseName" },
    { label: "车辆数", key: "unitCount" },
    { label: "库存", html: true, render: row => stockBadge(row.inventoryCount, "台") },
    { label: "销售单价", key: "salePrice", formatter: money },
    { label: "操作", html: true, render: row => vehicleModelActions(row) }
  ], rows, {
    tableKey: "vehicles",
    selectableRow: row => ({
      action: "detail-vehicle",
      data: { modelKey: row.modelKey },
      active: state.vehicleDetail?.modelKey === row.modelKey,
      label: `查看车型 ${vehicleModelLabel(row)} 详情`
    })
  }));
}

function vehicleFlowRows() {
  return state.data.vehicles
    .filter(item => !item.modelOnly)
    .map(machine => {
      const order = latestVehicleOutboundOrder(machine.id);
      const rental = activeRentalForMachine(machine.id);
      return {
        ...machine,
        orderId: order?.id || null,
        rentalId: rental?.id || null,
        rentalNo: rental?.rentalNo || "",
        rentalDestination: rental?.destination || "",
        rentalPrice: rentalMonthlyPrice(rental),
        rentalStartDate: rental?.startDate || "",
        rentalEndDate: rental?.endDate || "",
        customerName: order?.customerName || machine.destination1 || "",
        paymentSettled: Boolean(order?.paymentSettled),
        paymentRemark: order?.paymentRemark || "",
        salesReported: order ? Boolean(order.salesReported) : yesNoFromText(machine.isSalesReported),
        invoiceApplied: order ? Boolean(order.invoiceApplied) : yesNoFromText(machine.isInvoiceApplied),
        salesDate: order?.salesDate || machine.salesDate || "",
        salesReportDate: order?.salesReportDate || machine.salesReportDate || "",
        invoiceApplicationDate: order?.invoiceApplicationDate || "",
        invoiceStatus: order?.invoiceStatus || "",
        invoiceIssuedDate: order?.invoiceIssuedDate || "",
        invoiceFileAvailable: Boolean(order?.invoiceFileAvailable),
        invoiceOriginalName: order?.invoiceOriginalName || "",
        invoiceFileSize: order?.invoiceFileSize || null,
        invoiceUploadedAt: order?.invoiceUploadedAt || "",
        salePrice: order?.salePrice ?? machine.salePrice,
        settlementPrice: order?.settlementPrice ?? machine.settlementPrice,
        registrationStatus: order?.registrationStatus || "",
        contractType: order?.contractType || "",
        contractFileAvailable: Boolean(order?.contractFileAvailable),
        contractOriginalName: order?.contractOriginalName || "",
        contractFileSize: order?.contractFileSize || null,
        contractUploadedAt: order?.contractUploadedAt || "",
        orderRemark: order?.orderRemark || "",
        destinationText: joinVehicleDestinations(machine)
      };
    })
    .sort((left, right) => {
      const leftKey = `${left.inboundDate || ""}-${String(left.id || "").padStart(8, "0")}`;
      const rightKey = `${right.inboundDate || ""}-${String(right.id || "").padStart(8, "0")}`;
      return rightKey.localeCompare(leftKey);
    });
}

function latestVehicleOutboundOrder(machineId) {
  return state.data.outboundOrders.find(item => item.resourceType === "MACHINE" && Number(item.resourceId) === Number(machineId)) || null;
}

function activeRentalForMachine(machineId) {
  return state.data.rentals.find(item => item.status === "ACTIVE" && Number(item.machineId) === Number(machineId)) || null;
}

function joinVehicleDestinations(machine = {}) {
  return [machine.destination1, machine.destination2, machine.destination3, machine.destination4, machine.destination5]
    .filter(value => value !== null && value !== undefined && String(value).trim() !== "")
    .join(" / ");
}

function vehicleFlowStage(row) {
  if (row?.rentalId) {
    return { label: "租赁中", type: "warn" };
  }
  if (!row?.orderId) {
    return Number(row?.inventoryCount || 0) > 0
      ? { label: "在库待销售", type: "teal" }
      : { label: "仅保留入库档案", type: "primary" };
  }
  if (!row.paymentSettled) {
    return { label: "待收款", type: "warn" };
  }
  if (!row.salesReported) {
    return { label: "待报销售", type: "primary" };
  }
  if (!row.invoiceApplied) {
    return { label: "待申请发票", type: "warn" };
  }
  return { label: "销售闭环完成", type: "success" };
}

function vehicleFlowActions(row) {
  return `
    <div class="action-row">
      <button class="btn btn-sm" type="button" data-action="detail-vehicle" data-id="${escapeAttr(row.id)}">${icon("eye")}详情</button>
      ${row.orderId && hasPermission("stock:adjust") ? `<button class="btn btn-sm" type="button" data-action="edit" data-kind="outboundOrder" data-id="${escapeAttr(row.orderId)}">${icon("edit")}销售跟进</button>` : ""}
      ${row.orderId && hasPermission("stock:adjust") && isInvoiceUploadReady(row) ? `<button class="btn btn-sm" type="button" data-action="upload-invoice" data-id="${escapeAttr(row.orderId)}">${icon("upload")}发票</button>` : ""}
      ${row.orderId && hasPermission("stock:adjust") && row.invoiceFileAvailable ? `<button class="btn btn-sm" type="button" data-action="download-invoice" data-id="${escapeAttr(row.orderId)}">${icon("download")}发票</button>` : ""}
      ${row.orderId && hasPermission("stock:adjust") && isContractUploadReady(row) ? `<button class="btn btn-sm" type="button" data-action="upload-contract" data-id="${escapeAttr(row.orderId)}">${icon("upload")}合同</button>` : ""}
      ${row.orderId && hasPermission("stock:adjust") && row.contractFileAvailable ? `<button class="btn btn-sm" type="button" data-action="download-contract" data-id="${escapeAttr(row.orderId)}">${icon("download")}合同</button>` : ""}
      ${!row.orderId && !row.rentalId && Number(row.inventoryCount || 0) > 0 && hasPermission("stock:adjust") ? `<button class="btn btn-sm" type="button" data-action="vehicle-outbound-direct" data-machine-id="${escapeAttr(row.id)}">${icon("minus")}登记出库</button>` : ""}
      ${!row.orderId && !row.rentalId && Number(row.inventoryCount || 0) > 0 && hasPermission("stock:adjust") ? `<button class="btn btn-sm" type="button" data-action="vehicle-rental-direct" data-machine-id="${escapeAttr(row.id)}">${icon("plus")}登记租赁</button>` : ""}
      ${hasPermission("vehicle:write") ? `<button class="btn btn-sm" type="button" data-action="edit" data-kind="vehicle" data-id="${escapeAttr(row.id)}">${icon("edit")}编辑档案</button>` : ""}
    </div>
  `;
}

function vehicleFlowMachineSummary(row) {
  return `
    <div class="cell-stack">
      <strong>${escapeHtml(row.vehicleProductNumber || `ID ${row.id}`)}</strong>
      <span class="helper-inline">${escapeHtml(row.name || "-")} · ${escapeHtml(row.specificationModel || "-")}</span>
      ${row.configuration ? `<span class="helper-inline">${escapeHtml(row.configuration)}</span>` : ""}
    </div>
  `;
}

function vehicleFlowStatusSummary(row) {
  const stage = vehicleFlowStage(row);
  return `
    <div class="cell-stack">
      ${badge(stage.label, stage.type)}
      ${stockStatusBadge(row.stockStatus)}
      <span class="helper-inline">库存 ${escapeHtml(row.inventoryCount || 0)} 台</span>
      ${row.rentalId ? `<span class="helper-inline">租赁去向 ${escapeHtml(row.rentalDestination || "-")}</span>` : ""}
    </div>
  `;
}

function vehicleFlowFollowupSummary(row) {
  return `
    <div class="cell-stack">
      <span>${escapeHtml(dateValue(row.salesDate) || "未登记销售日期")}</span>
      <span class="helper-inline">车款 ${escapeHtml(yesNoText(row.paymentSettled))} · 报销售 ${escapeHtml(yesNoText(row.salesReported))} · 发票 ${escapeHtml(yesNoText(row.invoiceApplied))}</span>
      ${row.invoiceFileAvailable ? `<span class="helper-inline">发票文件 ${escapeHtml(row.invoiceOriginalName || "已上传")}</span>` : ""}
      ${row.contractFileAvailable ? `<span class="helper-inline">合同文件 ${escapeHtml(row.contractOriginalName || "已上传")}</span>` : ""}
      ${row.paymentRemark ? `<span class="helper-inline">${escapeHtml(row.paymentRemark)}</span>` : ""}
    </div>
  `;
}

function yesNoFromText(value) {
  const normalized = String(value || "").trim();
  if (!normalized) return false;
  if (normalized.startsWith("否") || normalized.startsWith("不") || normalized === "/") return false;
  return true;
}

function yesNoText(value) {
  return value ? "是" : "否";
}

function renderParts() {
  const rows = filterRows(state.data.parts, state.search.parts, [
    "partCode", "partName", "partBrand", "partCategory", "applicableModels", "source"
  ]);

  return `
    <div class="page">
      ${renderToolbar("parts", "搜索编码、名称、品牌、分类", "part", "新增配件", "part:write", {
        actions: [
          hasPermission("stock:adjust") ? `<button class="btn" type="button" data-action="part-stock" data-direction="inbound">${icon("plus")}配件入库</button>` : "",
          hasPermission("stock:adjust") ? `<button class="btn" type="button" data-action="part-stock" data-direction="outbound">${icon("minus")}配件出库</button>` : ""
        ]
      })}
      ${renderExportableSurface("配件列表", "parts", renderTable([
        { label: "编码", key: "partCode" },
        { label: "名称", key: "partName" },
        { label: "品牌", key: "partBrand" },
        { label: "分类", key: "partCategory" },
        { label: "数量", html: true, render: row => stockBadge(row.quantity, row.unit) },
        { label: "销售价", key: "salePrice", formatter: money },
        { label: "来源", key: "source" },
        { label: "操作", html: true, render: row => rowActions("part", row, ["stockIn", "stockOut", "edit", "delete"]) }
      ], rows, listTableOptions("part", "parts")))}
      ${renderPagination("parts")}
    </div>
  `;
}

function renderModificationOrders() {
  const rows = filterRows(state.data.modificationOrders, state.search.modificationOrders, [
    "workOrderNo", "machineProductNumber", "machineName", "specificationModel", "customerName", "salesOrderNo", "status", "operator", "remark"
  ]);

  return `
    <div class="page">
      ${renderToolbar("modificationOrders", "搜索工单号、客户、销售单或状态", "modificationOrder", "新建改装工单", "replace:write", {
        exportType: false
      })}
      ${renderSurface("改装工单列表", renderTable([
        { label: "工单号", key: "workOrderNo" },
        { label: "车辆", html: true, render: row => modificationOrderMachineSummary(row) },
        { label: "客户", key: "customerName" },
        { label: "销售单", key: "salesOrderNo" },
        { label: "状态", html: true, render: row => modificationStatusBadge(row.status) },
        { label: "替换明细", html: true, render: row => modificationLineSummary(row.lines) },
        { label: "创建时间", key: "createdAt", formatter: dateTime },
        { label: "操作", html: true, render: row => modificationOrderActions(row) }
      ], rows, listTableOptions("modificationOrder", null)))}
      ${renderPagination("modificationOrders")}
    </div>
  `;
}

function modificationOrderMachineSummary(row) {
  return `
    <div class="cell-stack">
      <strong>${escapeHtml(row.machineProductNumber || `ID ${row.machineId || "-"}`)}</strong>
      <span class="helper-inline">${escapeHtml([row.machineName, row.specificationModel].filter(Boolean).join(" / ") || "-")}</span>
    </div>
  `;
}

function renderOutboundOrders() {
  const rows = filterRows(state.data.outboundOrders, state.search.outboundOrders, [
    "orderNo", "resourceType", "resourceCode", "resourceName", "customerName", "operator", "orderRemark",
    "paymentRemark", "invoiceStatus", "invoiceOriginalName", "registrationStatus", "contractType", "contractOriginalName"
  ]);

  return `
    <div class="page">
      ${renderBackendSummary("outboundOrders", () => {
        const unsettledOrders = rows.filter(item => !item.paymentSettled).length;
        const pendingReports = rows.filter(item => !item.salesReported).length;
        const pendingInvoices = rows.filter(item => !item.invoiceApplied).length;
        const settledOrders = rows.filter(item => item.paymentSettled).length;
        const uploadedInvoices = rows.filter(item => item.invoiceFileAvailable).length;
        const uploadedContracts = rows.filter(item => item.contractFileAvailable).length;
        const outstandingAmount = receivableOutstandingTotal(rows);
        const overdueOrders = rows.filter(item => Number(item.overdueDays || 0) > 0).length;
        return `<section class="summary-grid">
          ${summaryCard("出库订单", rows.length, `${unsettledOrders} 单待收款`)}
          ${summaryCard("应收欠款", money(outstandingAmount), `${overdueOrders} 单逾期`)}
          ${summaryCard("待报销售", pendingReports, `${rows.filter(item => item.resourceType === "MACHINE").length} 单整机订单`)}
          ${summaryCard("待申请发票", pendingInvoices, `${uploadedInvoices} 单已上传发票`)}
          ${summaryCard("已结清车款", settledOrders, `${uploadedContracts} 单已上传合同`)}
        </section>`;
      })}
      ${renderToolbar("outboundOrders", "搜索订单号、客户、车号、收款情况、开票情况或备注", null, "", null)}
      ${renderExportableSurface("出库订单列表", "outboundOrders", renderTable([
        { label: "订单号", html: true, render: row => orderNoSummary(row) },
        { label: "出库项", html: true, render: row => orderResourceSummary(row) },
        { label: "客户", key: "customerName" },
        { label: "销售日期", key: "salesDate", formatter: dateValue },
        { label: "价格", html: true, render: row => orderPriceSummary(row) },
        { label: "收款跟进", html: true, render: row => orderPaymentSummary(row) },
        { label: "报销售", html: true, render: row => orderSalesReportSummary(row) },
        { label: "发票跟进", html: true, render: row => orderInvoiceSummary(row) },
        { label: "上牌/合同", html: true, render: row => orderContractSummary(row) },
        { label: "订单备注", key: "orderRemark" },
        { label: "操作", html: true, render: row => outboundOrderActions(row) }
      ], rows, listTableOptions("outboundOrder", "outboundOrders")))}
      ${renderPagination("outboundOrders")}
    </div>
  `;
}

function renderRentals() {
  const rows = filterRows(state.data.rentals, state.search.rentals, [
    "rentalNo", "vehicleNumber", "machineName", "specificationModel", "customerName", "customerAddress", "destination", "status", "operator", "remark"
  ]);

  return `
    <div class="page">
      ${renderBackendSummary("rentals", () => {
        const activeRows = rows.filter(item => item.status === "ACTIVE");
        const returnedRows = rows.filter(item => item.status === "RETURNED");
        const rentalIncome = rows.reduce((sum, item) => sum + Number(rentalMonthlyPrice(item) || 0), 0);
        return `<section class="summary-grid">
          ${summaryCard("租赁记录", rows.length, `${activeRows.length} 台租赁中`)}
          ${summaryCard("月租收入", money(rentalIncome), `${returnedRows.length} 单已归还`)}
          ${summaryCard("租赁车辆", new Set(rows.map(item => item.machineId).filter(Boolean)).size, "按具体车号记录")}
          ${summaryCard("待归还", activeRows.length, activeRows.length ? "请持续跟进租赁去向" : "暂无进行中租赁")}
        </section>`;
      })}
      ${renderToolbar("rentals", "搜索租赁单、车号、客户、去向或经办人", "rental", "新增租赁", "stock:adjust")}
      ${renderExportableSurface("租赁记录", "rentals", renderTable([
        { label: "租赁单", html: true, render: row => rentalNoSummary(row) },
        { label: "车辆", html: true, render: row => rentalVehicleSummary(row) },
        { label: "去向", html: true, render: row => rentalCustomerSummary(row) },
        { label: "月租价", html: true, render: row => money(rentalMonthlyPrice(row)) },
        { label: "租期", html: true, render: row => rentalPeriodSummary(row) },
        { label: "状态", html: true, render: row => rentalStatusBadge(row.status) },
        { label: "经办人", key: "operator" },
        { label: "备注", key: "remark" },
        { label: "操作", html: true, render: row => rowActions("rental", row, ["edit", "delete"]) }
      ], rows, listTableOptions("rental", "rentals")))}
      ${renderPagination("rentals")}
    </div>
  `;
}

function rentalNoSummary(row) {
  return `
    <div class="cell-stack">
      <strong>${escapeHtml(row.rentalNo || "-")}</strong>
      <span class="helper-inline">${escapeHtml(dateTime(row.createdAt) || "-")}</span>
    </div>
  `;
}

function rentalVehicleSummary(row) {
  return `
    <div class="cell-stack">
      <strong>${escapeHtml(row.vehicleNumber || `ID ${row.machineId || "-"}`)}</strong>
      <span class="helper-inline">${escapeHtml([row.machineName, row.specificationModel].filter(Boolean).join(" / ") || "-")}</span>
    </div>
  `;
}

function rentalCustomerSummary(row) {
  return `
    <div class="cell-stack">
      <strong>${escapeHtml(row.customerName || row.destination || "-")}</strong>
      <span class="helper-inline">${escapeHtml(row.destination || row.customerAddress || "-")}</span>
    </div>
  `;
}

function rentalMonthlyPrice(row) {
  return row?.monthlyRentalPrice ?? row?.rentalPrice ?? 0;
}

function rentalPeriodSummary(row) {
  return `
    <div class="cell-stack">
      <span>${escapeHtml(dateValue(row.startDate) || "-")}</span>
      <span class="helper-inline">至 ${escapeHtml(dateValue(row.endDate) || "未定")}</span>
    </div>
  `;
}

function orderPriceSummary(row) {
  return `
    <div class="cell-stack">
      <span>结算 ${escapeHtml(money(row.settlementPrice) || "-")}</span>
      <span class="helper-inline">销售 ${escapeHtml(money(row.salePrice) || "-")}</span>
      <span class="helper-inline">应收 ${escapeHtml(money(row.receivableAmount ?? row.settlementPrice) || "-")}</span>
    </div>
  `;
}

function orderNoSummary(row) {
  return `
    <div class="cell-stack">
      <strong>${escapeHtml(row.orderNo || "-")}</strong>
      ${row.isLocked ? `<span>${badge("已锁定", "danger")}</span>` : ""}
    </div>
  `;
}

function orderPaymentSummary(row) {
  const outstanding = receivableOutstanding(row);
  return `
    <div class="cell-stack">
      ${orderStatusToggle(row, "paymentSettled", Boolean(row.paymentSettled), "已结清", "未结清")}
      <span class="helper-inline">已收 ${escapeHtml(money(row.receivedAmount) || "¥0.00")} · 欠款 ${escapeHtml(money(outstanding) || "¥0.00")}</span>
      <span class="helper-inline">到期 ${escapeHtml(dateValue(row.paymentDueDate) || "-")} · 最近收款 ${escapeHtml(dateValue(row.lastPaymentDate) || "-")}</span>
      ${Number(row.overdueDays || 0) > 0 ? `<span class="helper-inline danger-text">逾期 ${escapeHtml(row.overdueDays)} 天</span>` : ""}
      ${row.paymentRemark ? `<span class="helper-inline">${escapeHtml(row.paymentRemark)}</span>` : `<span class="helper-inline">未填写收款情况</span>`}
    </div>
  `;
}

function receivableOutstanding(row = {}) {
  if (row.outstandingAmount !== undefined && row.outstandingAmount !== null) {
    return Number(row.outstandingAmount);
  }
  return Math.max(0, Number(row.receivableAmount ?? row.settlementPrice ?? 0) - Number(row.receivedAmount ?? 0));
}

function receivableOutstandingTotal(rows = []) {
  return rows.reduce((sum, row) => sum + receivableOutstanding(row), 0);
}

function orderSalesReportSummary(row) {
  return `
    <div class="cell-stack">
      ${orderStatusToggle(row, "salesReported", Boolean(row.salesReported), "已报销售", "未报销售")}
      <span class="helper-inline">${escapeHtml(dateValue(row.salesReportDate) || "未登记报销售日期")}</span>
    </div>
  `;
}

function orderInvoiceSummary(row) {
  return `
    <div class="cell-stack">
      ${orderStatusToggle(row, "invoiceApplied", Boolean(row.invoiceApplied), "已申请发票", "未申请发票")}
      ${row.invoiceStatus ? `<span class="helper-inline">${escapeHtml(row.invoiceStatus)}</span>` : ""}
      <span class="helper-inline">申请 ${escapeHtml(dateValue(row.invoiceApplicationDate) || "-")} · 开票 ${escapeHtml(dateValue(row.invoiceIssuedDate) || "-")}</span>
      ${row.invoiceFileAvailable ? `<span class="helper-inline">发票 ${escapeHtml(row.invoiceOriginalName || "已上传")} · ${escapeHtml(fileSize(row.invoiceFileSize))}</span>` : `<span class="helper-inline">未上传发票文件</span>`}
    </div>
  `;
}

function isInvoiceIssued(row = {}) {
  if (row.invoiceIssuedDate) return true;
  const status = String(row.invoiceStatus || "").trim();
  const lowerStatus = status.toLowerCase();
  return status.includes("已开")
    || status.includes("开票完成")
    || status.includes("完成开票")
    || status.includes("已出票")
    || lowerStatus.includes("issued")
    || lowerStatus.includes("invoiced");
}

function isInvoiceUploadReady(row = {}) {
  return Boolean(row.invoiceApplied) || isInvoiceIssued(row);
}

function isContractUploadReady(row = {}) {
  return yesNoFromStatusText(row.contractType);
}

function orderContractSummary(row) {
  const registered = yesNoFromStatusText(row.registrationStatus);
  const contracted = yesNoFromStatusText(row.contractType);
  return `
    <div class="cell-stack">
      ${orderStatusToggle(row, "registrationStatus", registered, "已上牌", "未上牌")}
      ${orderStatusToggle(row, "contractType", contracted, "有合同", "无合同")}
      <span class="helper-inline">${escapeHtml([row.registrationStatus, row.contractType].filter(Boolean).join(" · ") || "未登记")}</span>
      ${row.contractFileAvailable ? `<span class="helper-inline">合同 ${escapeHtml(row.contractOriginalName || "已上传")} · ${escapeHtml(fileSize(row.contractFileSize))}</span>` : `<span class="helper-inline">${contracted ? "未上传合同文件" : "标记有合同后可上传"}</span>`}
    </div>
  `;
}

function orderStatusToggle(row, field, active, activeLabel, inactiveLabel) {
  if (!hasPermission("stock:adjust")) {
    return active ? badge(activeLabel, "teal") : badge(inactiveLabel, "primary");
  }
  const label = active ? activeLabel : inactiveLabel;
  const type = active ? "teal" : "primary";
  return `<button class="status-toggle ${escapeAttr(type)}" type="button" data-action="toggle-order-status" data-id="${escapeAttr(row.id)}" data-field="${escapeAttr(field)}" aria-pressed="${active ? "true" : "false"}" title="点击切换状态">${escapeHtml(label)}</button>`;
}

function yesNoFromStatusText(value) {
  const normalized = String(value || "").trim();
  if (!normalized) return false;
  const lower = normalized.toLowerCase();
  if (["否", "无", "未", "不", "no", "none", "false"].some(prefix => lower.startsWith(prefix))) return false;
  if (normalized.includes("未登记") || normalized.includes("未上牌") || normalized.includes("无合同")) return false;
  return true;
}

function renderCustomers() {
  const rows = filterRows(state.data.customers, state.search.customers, [
    "companyName", "address", "contactName", "contactPhone", "taxOrIdNumber", "remarks"
  ]);

  return `
    <div class="page">
      ${renderToolbar("customers", "搜索公司、联系人、电话或税号", "customer", "新增客户", "vehicle:write")}
      ${renderExportableSurface("客户列表", "customers", renderTable([
        { label: "公司名称", key: "companyName" },
        { label: "地址", key: "address" },
        { label: "联系人", key: "contactName" },
        { label: "电话", key: "contactPhone" },
        { label: "税号/身份证号", key: "taxOrIdNumber" },
        { label: "备注", key: "remarks" },
        { label: "操作", html: true, render: row => rowActions("customer", row, ["edit", "delete"]) }
      ], rows, listTableOptions("customer", "customers")))}
      ${renderPagination("customers")}
    </div>
  `;
}

function renderSuppliers() {
  const rows = filterRows(state.data.suppliers, state.search.suppliers, [
    "supplierName", "supplierType", "contactName", "contactPhone", "address", "taxNumber", "remarks"
  ]);

  return `
    <div class="page">
      ${renderBackendSummary("suppliers", () => {
        const typed = rows.filter(item => item.supplierType).length;
        return `<section class="summary-grid">
          ${summaryCard("供应商总数", pageTotal("suppliers", rows.length), `${typed} 家已标记类型`)}
          ${summaryCard("采购订单供应商", uniqueSupplierCount(), "已被采购订单引用")}
          ${summaryCard("联系人", rows.filter(item => item.contactName || item.contactPhone).length, "可直接联系")}
        </section>`;
      })}
      ${renderToolbar("suppliers", "搜索供应商、联系人、电话或税号", "supplier", "新增供应商", "stock:adjust")}
      ${renderExportableSurface("供应商列表", "suppliers", renderTable([
        { label: "供应商", html: true, render: row => supplierSummary(row) },
        { label: "类型", key: "supplierType" },
        { label: "联系人", key: "contactName" },
        { label: "电话", key: "contactPhone" },
        { label: "税号", key: "taxNumber" },
        { label: "备注", key: "remarks" },
        { label: "操作", html: true, render: row => rowActions("supplier", row, ["edit", "delete"]) }
      ], rows, listTableOptions("supplier", "suppliers")))}
      ${renderPagination("suppliers")}
    </div>
  `;
}

function renderPurchaseOrders() {
  const rows = filterRows(state.data.purchaseOrders, state.search.purchases, [
    "purchaseNo", "supplierName", "resourceType", "resourceCode", "resourceName", "specificationModel", "status", "operator", "remark"
  ]);

  return `
    <div class="page">
      ${renderBackendSummary("purchases", () => {
        const totalAmount = rows.reduce((sum, row) => sum + Number(row.totalAmount || 0), 0);
        const received = rows.filter(row => row.status === "RECEIVED").length;
        const pending = rows.filter(row => row.status !== "RECEIVED" && row.status !== "CANCELED").length;
        const freightTotal = rows.reduce((sum, row) => sum + Number(row.freightAmount || 0), 0);
        return `<section class="summary-grid">
          ${summaryCard("采购订单", pageTotal("purchases", rows.length), `${pending} 单待跟进`)}
          ${summaryCard("采购金额", money(totalAmount), "当前筛选合计")}
          ${summaryCard("已收货", received, `运费 ${money(freightTotal)}`)}
        </section>`;
      })}
      ${renderToolbar("purchases", "搜索采购单号、供应商、配件、配置项或经办人", "purchaseOrder", "新增配件采购", "stock:adjust")}
      ${renderExportableSurface("采购订单", "purchases", renderTable([
        { label: "采购单", html: true, render: row => purchaseOrderSummary(row) },
        { label: "供应商", key: "supplierName" },
        { label: "配件入库内容", html: true, render: row => purchaseResourceSummary(row) },
        { label: "数量", html: true, render: row => `${escapeHtml(row.quantity || 0)} <span class="helper-inline">${escapeHtml(row.unit || "")}</span>` },
        { label: "金额", key: "totalAmount", formatter: money },
        { label: "状态", html: true, render: row => purchaseStatusControl(row) },
        { label: "操作", html: true, render: row => rowActions("purchaseOrder", row, ["edit", "delete"]) }
      ], rows, listTableOptions("purchaseOrder", "purchases")))}
      ${renderPagination("purchases")}
    </div>
  `;
}
function renderStocktakes() {
  const rows = filterRows(state.data.stocktakingRecords, state.search.stocktakes, [
    "stocktakingNo", "resourceType", "resourceCode", "resourceName", "specificationModel", "status", "operator", "remark"
  ]);

  return `
    <div class="page">
      ${renderBackendSummary("stocktakes", () => {
        const drafts = rows.filter(row => row.status !== "COMPLETED").length;
        const completed = rows.filter(row => row.status === "COMPLETED").length;
        const differences = rows.filter(row => Number(row.differenceQuantity || 0) !== 0).length;
        return `<section class="summary-grid">
          ${summaryCard("盘点记录", pageTotal("stocktakes", rows.length), `${drafts} 条待入账`)}
          ${summaryCard("已入账", completed, "库存已同步")}
          ${summaryCard("有差异", differences, "账实不一致")}
        </section>`;
      })}
      ${renderToolbar("stocktakes", "搜索盘点单、资源编码、名称、型号或盘点人", "stocktaking", "新增盘点", "stock:adjust")}
      ${renderExportableSurface("库存盘点", "stocktakes", renderTable([
        { label: "盘点单", html: true, render: row => stocktakingSummary(row) },
        { label: "盘点对象", html: true, render: row => purchaseResourceSummary(row) },
        { label: "账面", key: "bookQuantity", formatter: stockText },
        { label: "实盘", key: "actualQuantity", formatter: stockText },
        { label: "差异", html: true, render: row => stocktakingDifference(row) },
        { label: "状态", html: true, render: row => stocktakingStatusBadge(row.status) },
        { label: "操作", html: true, render: row => stocktakingActions(row) }
      ], rows, listTableOptions("stocktaking", "stocktakes")))}
      ${renderPagination("stocktakes")}
    </div>
  `;
}

function renderWarehouses() {
  const rows = filterRows(state.data.warehouses, state.search.warehouses, [
    "warehouseCode", "warehouseName", "warehouseType", "address"
  ]);
  const vehicleTotal = rows.reduce((sum, row) => sum + Number(row.vehicleCount || 0), 0);
  const partQuantity = rows.reduce((sum, row) => sum + Number(row.partQuantity || 0), 0);
  const defaultWarehouse = rows.find(row => row.defaultWarehouse);

  return `
    <div class="page">
      <section class="summary-grid">
        ${summaryCard("仓库数量", pageTotal("warehouses", rows.length), defaultWarehouse ? `默认：${defaultWarehouse.warehouseName}` : "未设置默认仓")}
        ${summaryCard("整车库存", vehicleTotal, "按仓库余额汇总")}
        ${summaryCard("配件库存", partQuantity, `${rows.reduce((sum, row) => sum + Number(row.partSkuCount || 0), 0)} 个 SKU`)}
      </section>
      <div class="toolbar">
        <div class="search-box">
          ${icon("search")}
          <input type="search" placeholder="搜索仓库编码、名称、类型或地址" value="${escapeAttr(state.search.warehouses || "")}" data-search-for="warehouses">
          ${state.search.warehouses ? `<button class="icon-btn" type="button" data-action="clear-search" data-search-for="warehouses" title="清空搜索">${icon("minus")}</button>` : ""}
        </div>
        <div class="toolbar-actions">
          <button class="btn" type="button" data-action="stock-transfer">${icon("swap")}库存调拨</button>
          <button class="btn btn-primary" type="button" data-action="create" data-kind="warehouse">${icon("plus")}新增仓库</button>
        </div>
      </div>
      ${renderSurface("仓库列表", renderTable([
        { label: "仓库", html: true, render: row => warehouseSummary(row) },
        { label: "类型", key: "warehouseType" },
        { label: "默认", html: true, render: row => row.defaultWarehouse ? badge("默认", "teal") : "" },
        { label: "整车", key: "vehicleCount", formatter: stockText },
        { label: "配件 SKU", key: "partSkuCount", formatter: stockText },
        { label: "配件数量", key: "partQuantity", formatter: stockText },
        { label: "操作", html: true, render: row => rowActions("warehouse", row, ["edit", "delete"]) }
      ], rows, listTableOptions("warehouse", null)))}
      ${renderPagination("warehouses")}
    </div>
  `;
}

function renderStockMovements() {
  const rows = filterRows(state.data.stockMovements, state.search.stockMovements, [
    "movementNo", "movementType", "resourceType", "resourceCode", "resourceName", "warehouseName", "operator", "remark"
  ]);
  return `
    <div class="page">
      ${renderToolbar("stockMovements", "搜索流水号、资源编码、名称、仓库或经办人", null, "", "log:read", { exportType: false })}
      ${renderSurface("库存流水", renderTable([
        { label: "时间", key: "createdAt", formatter: dateTime },
        { label: "流水", html: true, render: row => stockMovementSummary(row) },
        { label: "资源", html: true, render: row => stockMovementResource(row) },
        { label: "仓库", key: "warehouseName" },
        { label: "变动", html: true, render: row => stockMovementDelta(row) },
        { label: "经办人", key: "operator" },
        { label: "备注", key: "remark" }
      ], rows, listTableOptions("stockMovement", null)))}
      ${renderPagination("stockMovements")}
    </div>
  `;
}

function renderMaintenance() {
  if (!hasRole("SUPER_ADMIN")) {
    return `<div class="page">${renderSurface("数据维护", emptyState("当前账号无权访问数据维护"))}</div>`;
  }
  return `
    <div class="page">
      <section class="summary-grid">
        ${summaryCard("数据库备份", "JSON", "导出当前应用表数据")}
        ${summaryCard("恢复确认码", "RESTORE-DATA-BACKUP", "恢复前请先下载备份")}
        ${summaryCard("附件文件", "未包含", "发票/合同实体文件需单独保管")}
      </section>
      ${renderSurface("备份与恢复", `
        <div class="action-grid">
          <button class="btn btn-primary" type="button" data-action="download-backup">${icon("download")}下载数据库备份</button>
          <button class="btn" type="button" data-action="restore-backup">${icon("upload")}恢复数据库备份</button>
        </div>
      `)}
    </div>
  `;
}

function warehouseSummary(row = {}) {
  return `
    <div class="cell-stack">
      <strong>${escapeHtml(row.warehouseName || "-")}</strong>
      <span>${escapeHtml(row.warehouseCode || "-")}${row.address ? ` · ${escapeHtml(row.address)}` : ""}</span>
    </div>
  `;
}

function stockMovementSummary(row = {}) {
  return `
    <div class="cell-stack">
      <strong>${escapeHtml(row.movementNo || "-")}</strong>
      <span>${escapeHtml(movementTypeText(row.movementType))}</span>
    </div>
  `;
}

function stockMovementResource(row = {}) {
  return `
    <div class="cell-stack">
      <strong>${escapeHtml(row.resourceCode || "-")}</strong>
      <span>${escapeHtml(resourceTypeLabel(row.resourceType))} · ${escapeHtml(row.resourceName || "-")}</span>
    </div>
  `;
}

function stockMovementDelta(row = {}) {
  const delta = Number(row.quantityDelta || 0);
  const tone = delta > 0 ? "teal" : delta < 0 ? "danger" : "muted";
  return `<span>${badge(`${delta > 0 ? "+" : ""}${delta}`, tone)}</span><span class="helper-inline">${escapeHtml(row.beforeQuantity ?? 0)} -> ${escapeHtml(row.afterQuantity ?? 0)}</span>`;
}

function movementTypeText(type) {
  return {
    INITIAL: "期初",
    INITIAL_BALANCE: "期初",
    INBOUND: "入库",
    OUTBOUND: "出库",
    ADJUST: "调整",
    TRANSFER: "调拨",
    PART_INSTALL: "装车",
    REPLACE: "替换"
  }[type] || display(type);
}

function renderRepairs() {
  const rows = filterRows(state.data.repairs, state.search.repairs, [
    "vehicleNumber", "customerName", "repairPerson", "status", "faultDescription"
  ]);

  return `
    <div class="page">
      ${renderToolbar("repairs", "搜索客户、车号、维修人、状态", "repair", "新增维修", "repair:write")}
      ${renderExportableSurface("维修记录", "repairs", renderTable([
        { label: "维修时间", key: "repairDate", formatter: dateTime },
        { label: "车号", html: true, render: row => repairVehicleSummary(row) },
        { label: "客户", html: true, render: row => repairCustomerSummary(row) },
        { label: "维修人", html: true, render: row => repairPersonSummary(row) },
        { label: "状态", html: true, render: row => repairStatusToggle(row) },
        { label: "总费用", key: "totalFee", formatter: money },
        { label: "操作", html: true, render: row => rowActions("repair", row, ["edit", "delete"]) }
      ], rows, listTableOptions("repair", "repairs")))}
      ${renderPagination("repairs")}
    </div>
  `;
}

function repairVehicleSummary(row = {}) {
  const rental = activeRentalForMachine(row.machineId);
  return `
    <div class="cell-stack">
      <strong>${escapeHtml(row.vehicleNumber || "-")}</strong>
      ${rental ? `<span>${badge("租赁中", "warn")}</span>` : ""}
    </div>
  `;
}

function repairCustomerSummary(row = {}) {
  return `
    <div class="cell-stack">
      <strong>${escapeHtml(row.customerName || "-")}</strong>
      ${row.customerAddress ? `<span class="helper-inline">${escapeHtml(row.customerAddress)}</span>` : ""}
    </div>
  `;
}

function repairPersonSummary(row = {}) {
  return escapeHtml(row.repairExternal ? (row.repairPerson || "其他") : (row.repairPerson || "-"));
}

function repairStatusToggle(row = {}) {
  if (!hasPermission("repair:write")) {
    return statusBadge(row.status);
  }
  const normalized = row.status === "COMPLETED" ? "COMPLETED" : "PENDING";
  const active = normalized === "COMPLETED";
  return `<button class="status-toggle ${active ? "teal" : "warn"}" type="button" data-action="toggle-repair-status" data-id="${escapeAttr(row.id)}" aria-pressed="${active ? "true" : "false"}" title="点击切换维修状态">${escapeHtml(repairStatusText(normalized))}</button>`;
}

function renderStatistics() {
  const stats = state.data.statistics;
  if (!stats) {
    return `<div class="page">${renderSurface("统计财报", emptyState("当前账号无权查看统计数据"))}</div>`;
  }
  const annual = stats.annualSummary || {};
  const monthlyRows = stats.monthlyFinance || [];
  const yearlyRows = stats.yearlyFinance || [];
  const resourceRows = stats.resourceFlows || [];
  const topRows = stats.topOutbounds || [];
  const topRentalRows = stats.topRentals || [];
  const stockRows = stats.stockValues || [];
  const lowRows = stats.lowStocks || [];

  return `
    <div class="page">
      <div class="toolbar">
        <label class="field year-filter">
          <span>统计年份</span>
          <select data-action="select-stats-year">
            ${renderYearOptions(stats.selectedYear, yearlyRows)}
          </select>
        </label>
        <div class="toolbar-actions">
          <button class="btn btn-ghost" type="button" data-action="refresh">${icon("refresh")}刷新</button>
        </div>
      </div>

      <section class="summary-grid">
        ${summaryCard("年度总收入", money(annual.totalIncome), `出库 ${money(annual.outboundRevenue)} / 维修 ${money(annual.repairIncome)} / 租赁 ${money(annual.rentalIncome)} / 折价收入 ${money(annual.modificationIncome)}`)}
        ${summaryCard("入库成本", money(annual.inboundCost), `${annual.inboundQuantity || 0} 件/台入库`)}
        ${summaryCard("出库毛利", money(annual.grossProfit), `${annual.outboundQuantity || 0} 件/台出库`)}
        ${summaryCard("租赁收入", money(annual.rentalIncome), `${annual.rentalOrders || 0} 单租赁`)}
      </section>

      ${renderSurface("月度收支走势", renderFinanceTrend(monthlyRows))}

      ${renderSurface("年度对比", renderTable([
          { label: "年份", key: "period" },
          { label: "总收入", key: "totalIncome", formatter: money },
          { label: "入库成本", key: "inboundCost", formatter: money },
          { label: "出库毛利", key: "grossProfit", formatter: money },
          { label: "维修单", key: "repairOrders" },
          { label: "租赁单", key: "rentalOrders" },
          { label: "折价工单", key: "modificationOrders" }
        ], yearlyRows))}

      <section class="grid-two">
        ${renderSurface("出入库分类收支", renderTable([
          { label: "类型", key: "label" },
          { label: "入库数量", key: "inboundQuantity" },
          { label: "入库成本", key: "inboundCost", formatter: money },
          { label: "出库数量", key: "outboundQuantity" },
          { label: "出库收入", key: "outboundRevenue", formatter: money },
          { label: "毛利", key: "grossProfit", formatter: money }
        ], resourceRows))}
        ${renderSurface("当前库存价值", renderTable([
          { label: "类型", key: "label" },
          { label: "档案数", key: "itemCount" },
          { label: "库存数", key: "stockQuantity" },
          { label: "成本价值", key: "costValue", formatter: money },
          { label: "零售价值", key: "retailValue", formatter: money }
        ], stockRows))}
      </section>

      <section class="grid-two">
        ${renderSurface("年度出库收益 TOP", renderTable([
          { label: "类型", key: "resourceType", formatter: resourceTypeLabel },
          { label: "编码", key: "resourceCode" },
          { label: "名称", key: "resourceName" },
          { label: "数量", key: "quantity" },
          { label: "收入", key: "revenue", formatter: money },
          { label: "毛利", key: "grossProfit", formatter: money }
        ], topRows))}
        ${renderSurface("年度租赁收入 TOP", renderTable([
          { label: "租赁单", key: "rentalNo" },
          { label: "车号", key: "vehicleNumber" },
          { label: "车型", key: "machineName" },
          { label: "去向", key: "destination" },
          { label: "月租价", key: "rentalPrice", formatter: money },
          { label: "状态", html: true, render: row => rentalStatusBadge(row.status) }
        ], topRentalRows))}
      </section>

      <section class="grid-two">
        ${renderSurface("低库存预警", renderTable([
          { label: "类型", key: "resourceType", formatter: resourceTypeLabel },
          { label: "编码", key: "resourceCode" },
          { label: "名称", key: "resourceName" },
          { label: "库存", key: "quantity", formatter: (value, row) => `${value}${row.unit || ""}` },
          { label: "阈值", key: "threshold" }
        ], lowRows))}
      </section>
    </div>
  `;
}

function renderOperationLogs() {
  const rows = filterRows(state.data.operationLogs, state.search.logs, [
    "category", "action", "target", "summary", "operator", "remark"
  ]);
  const visibleRows = rows.slice(0, state.visibleLogRows);
  const hiddenCount = Math.max(0, rows.length - visibleRows.length);

  return `
    <div class="page page-logs">
      ${renderToolbar("logs", "搜索日志类型、对象、操作人或备注", null, "", null, {
        className: "logs-toolbar",
        exportType: false,
        actions: [`
          <div class="logs-summary">
            <span>共 ${escapeHtml(state.data.operationLogs.length)} 条</span>
            <span>当前显示 ${escapeHtml(visibleRows.length)} / ${escapeHtml(rows.length)} 条</span>
          </div>
        `]
      })}
      ${renderSurface("关键操作审计", renderTable([
        { label: "时间", key: "createdAt", formatter: dateTime },
        { label: "类型", html: true, render: row => logCategoryBadge(row.category) },
        { label: "动作", html: true, render: row => operationActionBadge(row.action) },
        { label: "对象", key: "target" },
        { label: "内容", key: "summary" },
        { label: "数量", html: true, render: row => quantityChange(row) },
        { label: "操作人", key: "operator" },
        { label: "备注", key: "remark" }
      ], visibleRows))}
      ${hiddenCount ? `<div class="load-more-row"><button class="btn" type="button" data-action="load-more-logs">加载更多 ${escapeHtml(Math.min(LOG_PAGE_SIZE, hiddenCount))} 条</button></div>` : ""}
      ${renderPagination("logs")}
    </div>
  `;
}

function renderConfigs() {
  const items = filterRows(state.data.configItems, state.search.configItems, [
    "category", "subCategory", "itemName", "itemCode", "inputType"
  ]);
  const values = filterRows(state.data.configValues, state.search.configValues, [
    "valueLabel", "valueCode", "remark"
  ]);
  const selectedItem = state.data.configItems.find(item => item.id === state.selectedConfigItemId);

  return `
    <div class="page">
      ${renderToolbar(null, "", null, "", null, {
        searches: [
          { key: "configItems", placeholder: "搜索配置项" },
          { key: "configValues", placeholder: "搜索配置值" }
        ],
        exportType: false,
        actions: [
          hasPermission("config:write") ? `<button class="btn btn-primary" type="button" data-action="create" data-kind="configItem">${icon("plus")}新增配置项</button>` : "",
          hasPermission("config:write") ? `<button class="btn" type="button" data-action="create" data-kind="configValue">${icon("plus")}新增配置值</button>` : ""
        ]
      })}

      <section class="config-layout">
        <div class="config-pane config-items-pane">
          ${renderSurface("配置项", `
            <div class="config-list">
              ${items.length ? items.map(renderConfigItemCard).join("") : emptyState("暂无配置项")}
            </div>
          `)}
        </div>
        <div class="config-pane config-values-pane">
          ${renderSurface(selectedItem ? `配置值 · ${escapeHtml(selectedItem.itemName || "")}` : "配置值", `
            ${selectedItem ? `<div class="helper">当前配置项：${escapeHtml(selectedItem.category || "-")} / ${escapeHtml(selectedItem.subCategory || "-")}</div>` : ""}
            ${renderTable([
              { label: "显示值", key: "valueLabel" },
              { label: "编码", key: "valueCode" },
              { label: "默认", html: true, render: row => row.isDefault ? badge("默认", "teal") : badge("普通", "primary") },
              { label: "排序", key: "sortOrder" },
              { label: "操作", html: true, render: row => rowActions("configValue", row, ["delete"]) }
            ], values)}
          `)}
        </div>
      </section>
    </div>
  `;
}

function renderUsers() {
  const rows = filterRows(state.data.users, state.search.users, [
    "username", "roles", "jobTag"
  ]);

  return `
    <div class="page">
      ${renderToolbar("users", "搜索用户名、角色或职务", "user", "新建用户", "user:write", {
        exportType: false
      })}
      ${renderSurface("用户列表", renderTable([
        { label: "用户名", key: "username" },
        { label: "角色", html: true, render: row => roleBadges(row.roles) },
        { label: "职务", html: true, render: row => jobTagControl(row) },
        { label: "状态", html: true, render: row => userEnabledControl(row) },
        { label: "创建时间", key: "createdAt", formatter: dateTime },
        { label: "操作", html: true, render: row => userActions(row) }
      ], rows, listTableOptions("user", null, { selectable: false, batch: false })))}
      ${renderPagination("users")}
    </div>
  `;
}

function renderDetailGrid(items) {
  return `
    <div class="detail-grid detail-grid-compact">
      ${items.map(item => detailItem(item.label, item.value)).join("")}
    </div>
  `;
}

function renderVehicleArchiveSurface(machine = {}) {
  const actions = machine?.id && hasPermission("vehicle:write")
    ? `<button class="btn btn-sm" type="button" data-action="edit" data-kind="vehicle" data-id="${escapeAttr(machine.id)}">${icon("edit")}编辑档案</button>`
    : "";
  return renderSurface("入库档案", `
    ${renderDetailGrid([
    { label: "名称", value: machine.name },
    { label: "规格型号", value: machine.specificationModel },
    { label: "配置", value: machine.configuration },
    { label: "动力", value: machine.machineType },
    { label: "供应商", value: machine.supplier },
    { label: "经销商/仓位", value: machine.warehouseName },
    { label: "样机申请单号", value: machine.applicationNumber },
    { label: "物料号", value: machine.materialNumber },
    { label: "发动机号", value: machine.engineNumber },
    { label: "车架号", value: machine.frameNumber },
    { label: "保修卡号", value: machine.warrantyCardNumber },
    { label: "制造日期", value: dateValue(machine.manufacturingDate) },
    { label: "入库时间", value: dateTime(machine.inboundDate) },
    { label: "库存状态", value: stockStatusLabel(machine.stockStatus) },
    { label: "结算价", value: money(machine.settlementPrice) },
    { label: "销售单价", value: money(machine.salePrice) }
  ])}
  `, actions);
}

function renderVehicleSalesSurface(machine = {}, order = null) {
  return renderSurface("销售跟进", `
    ${renderDetailGrid([
      { label: "销售日期", value: dateValue(order?.salesDate || machine.salesDate) },
      { label: "车款结清", value: yesNoText(order?.paymentSettled) },
      { label: "应收金额", value: money(order?.receivableAmount ?? order?.settlementPrice) },
      { label: "已收金额", value: money(order?.receivedAmount) },
      { label: "欠款金额", value: money(order ? receivableOutstanding(order) : 0) },
      { label: "收款到期日", value: dateValue(order?.paymentDueDate) },
      { label: "最近收款日", value: dateValue(order?.lastPaymentDate) },
      { label: "收款情况", value: order?.paymentRemark },
      { label: "报销售状态", value: machine.isSalesReported || yesNoText(order?.salesReported) },
      { label: "报销售日期", value: dateValue(order?.salesReportDate || machine.salesReportDate) },
      { label: "发票申请", value: machine.isInvoiceApplied || yesNoText(order?.invoiceApplied) },
      { label: "发票申请日期", value: dateValue(order?.invoiceApplicationDate) },
      { label: "开票情况", value: order?.invoiceStatus },
      { label: "开票日期", value: dateValue(order?.invoiceIssuedDate) },
      { label: "发票文件", value: order?.invoiceFileAvailable ? `${order.invoiceOriginalName || "已上传"} · ${fileSize(order.invoiceFileSize)}` : "" },
      { label: "发票上传时间", value: dateTime(order?.invoiceUploadedAt) },
      { label: "上牌情况", value: order?.registrationStatus },
      { label: "合同", value: order?.contractType },
      { label: "合同文件", value: order?.contractFileAvailable ? `${order.contractOriginalName || "已上传"} · ${fileSize(order.contractFileSize)}` : "" },
      { label: "合同上传时间", value: dateTime(order?.contractUploadedAt) },
      { label: "订单备注", value: order?.orderRemark }
    ])}
    ${order?.id && hasPermission("stock:adjust") ? `<div class="toolbar-actions detail-action-row">
      <button class="btn" type="button" data-action="edit" data-kind="outboundOrder" data-id="${escapeAttr(order.id)}">${icon("edit")}编辑销售跟进</button>
      ${isInvoiceUploadReady(order) ? `<button class="btn" type="button" data-action="upload-invoice" data-id="${escapeAttr(order.id)}">${icon("upload")}上传发票</button>` : ""}
      ${order.invoiceFileAvailable ? `<button class="btn" type="button" data-action="download-invoice" data-id="${escapeAttr(order.id)}">${icon("download")}下载发票</button>` : ""}
      ${isContractUploadReady(order) ? `<button class="btn" type="button" data-action="upload-contract" data-id="${escapeAttr(order.id)}">${icon("upload")}上传合同</button>` : ""}
      ${order.contractFileAvailable ? `<button class="btn" type="button" data-action="download-contract" data-id="${escapeAttr(order.id)}">${icon("download")}下载合同</button>` : ""}
    </div>` : ""}
  `);
}

function renderVehicleDestinationSurface(machine = {}) {
  return renderSurface("去向追踪", renderDetailGrid([
    { label: "去向1", value: machine.destination1 },
    { label: "去向2", value: machine.destination2 },
    { label: "去向3", value: machine.destination3 },
    { label: "去向4", value: machine.destination4 },
    { label: "去向5", value: machine.destination5 },
    { label: "备注", value: machine.remarks }
  ]));
}

function renderVehiclePartsSurface(machine = {}, parts = []) {
  const actions = hasPermission("replace:write")
    ? `<button class="btn btn-sm" type="button" data-action="create-vehicle-part" data-machine-id="${escapeAttr(machine.id || "")}">${icon("plus")}新增配件</button>`
    : "";
  const body = parts.length ? renderTable([
    { label: "编码", key: "partCode" },
    { label: "名称", key: "partName" },
    { label: "分类", key: "partCategory" },
    { label: "库存", render: row => `${row.quantity ?? 0}${row.unit || ""}` },
    { label: "操作", html: true, render: row => hasPermission("part:write") ? `<button class="btn btn-sm" type="button" data-action="edit" data-kind="part" data-id="${escapeAttr(row.id)}">${icon("edit")}编辑</button>` : "" }
  ], parts) : emptyState("此车还没有关联配件");
  return renderSurface(`关联配件 · ${escapeHtml(machine.vehicleProductNumber || "-")}`, `
    ${body}
  `, actions);
}

function vehicleDetailTabs() {
  return [
    { key: "archive", label: "档案" },
    { key: "config", label: "配置" },
    { key: "outbound", label: "出库" },
    { key: "rental", label: "租赁" },
    { key: "repair", label: "维修" },
    { key: "modification", label: "改装" },
    { key: "attachments", label: "附件" }
  ];
}

function ensureVehicleDetailTab() {
  const keys = vehicleDetailTabs().map(item => item.key);
  if (!keys.includes(state.vehicleDetailTab)) state.vehicleDetailTab = "archive";
  return state.vehicleDetailTab;
}

function renderVehicleDetailTabs(activeKey) {
  return `
    <div class="detail-tabs" role="tablist" aria-label="车辆详情">
      ${vehicleDetailTabs().map(tab => `
        <button class="detail-tab${tab.key === activeKey ? " is-active" : ""}" type="button" role="tab" aria-selected="${tab.key === activeKey ? "true" : "false"}" data-action="set-vehicle-detail-tab" data-tab="${escapeAttr(tab.key)}">
          ${escapeHtml(tab.label)}
        </button>
      `).join("")}
    </div>
  `;
}

function renderVehicleDetailTabContent(activeKey, machine, detail) {
  const configs = detail.configs || [];
  const logs = detail.logs || [];
  const workOrders = detail.workOrders || [];
  const vehicleParts = detail.vehicleParts || [];
  const order = detail.order || null;
  if (!machine?.id && activeKey !== "archive") return emptyState("请先选择具体车号");
  if (activeKey === "config") return `<div class="grid-two">${renderVehicleConfigSurface(machine, configs)}${renderVehiclePartsSurface(machine, vehicleParts)}</div>`;
  if (activeKey === "outbound") return renderVehicleSalesSurface(machine, order);
  if (activeKey === "rental") return renderVehicleRentalSurface(machine);
  if (activeKey === "repair") return renderVehicleRepairSurface(machine);
  if (activeKey === "modification") return `<div class="grid-two">${renderVehicleModificationOrdersSurface(workOrders)}${renderVehicleReplaceLogsSurface(logs)}</div>`;
  if (activeKey === "attachments") return renderVehicleAttachmentsSurface(machine, order);
  return `<div class="grid-two">${renderVehicleArchiveSurface(machine)}${renderVehicleDestinationSurface(machine)}</div>`;
}

function renderVehicleConfigSurface(machine = {}, configs = []) {
  return renderSurface("当前配置", renderTable([
    { label: "配置项", key: "itemName" },
    { label: "当前值", key: "selectedValue" },
    { label: "来源", key: "configSource" },
    { label: "操作", html: true, render: row => `
      <div class="action-row">
        ${hasPermission("replace:write") ? `<button class="btn btn-sm" type="button" data-action="create-modification-order" data-machine-id="${escapeAttr(machine.id)}" data-machine-config-id="${escapeAttr(row.id)}">${icon("swap")}生成工单</button>` : ""}
      </div>
    ` }
  ], configs));
}

function renderVehicleModificationOrdersSurface(workOrders = []) {
  return renderSurface("改装工单", renderTable([
    { label: "工单号", key: "workOrderNo" },
    { label: "客户", key: "customerName" },
    { label: "状态", html: true, render: row => modificationStatusBadge(row.status) },
    { label: "替换明细", html: true, render: row => modificationLineSummary(row.lines) },
    { label: "操作", html: true, render: row => modificationOrderActions(row) }
  ], workOrders));
}

function renderVehicleReplaceLogsSurface(logs = []) {
  return renderSurface("替换记录", renderTable([
    { label: "配置项", key: "itemName" },
    { label: "旧值", key: "oldValue" },
    { label: "新值", key: "newValue" },
    { label: "类型", key: "replaceType" },
    { label: "时间", key: "createdAt", formatter: dateTime }
  ], logs));
}

function renderVehicleRentalSurface(machine = {}) {
  const rental = activeRentalForMachine(machine.id);
  const actions = machine?.id && hasPermission("stock:adjust")
    ? `<button class="btn btn-sm" type="button" data-action="vehicle-rental-direct" data-machine-id="${escapeAttr(machine.id)}">${icon("plus")}租赁登记</button>`
    : "";
  const body = rental ? renderDetailGrid([
    { label: "租赁单", value: rental.rentalNo },
    { label: "客户", value: rental.customerName },
    { label: "去向", value: rental.destination },
    { label: "月租价", value: money(rentalMonthlyPrice(rental)) },
    { label: "开始日期", value: dateValue(rental.startDate) },
    { label: "预计结束", value: dateValue(rental.endDate) },
    { label: "状态", value: rentalStatusBadge(rental.status), html: true }
  ]) : emptyState("当前车辆暂无进行中的租赁");
  return renderSurface("租赁记录", body, actions);
}

function renderVehicleRepairSurface(machine = {}) {
  const repairs = vehicleRepairsForMachine(machine);
  const actions = hasPermission("repair:write")
    ? `<button class="btn btn-sm" type="button" data-action="create" data-kind="repair">${icon("plus")}维修登记</button>`
    : "";
  const body = repairs.length ? renderTable([
    { label: "维修时间", key: "repairDate", formatter: dateTime },
    { label: "客户", html: true, render: row => repairCustomerSummary(row) },
    { label: "维修人", html: true, render: row => repairPersonSummary(row) },
    { label: "状态", html: true, render: row => repairStatusToggle(row) },
    { label: "总费用", key: "totalFee", formatter: money },
    { label: "操作", html: true, render: row => rowActions("repair", row, ["edit", "delete"]) }
  ], repairs) : emptyState("当前车辆暂无维修记录");
  return renderSurface("维修记录", body, actions);
}

function vehicleRepairsForMachine(machine = {}) {
  const id = Number(machine.id || 0);
  const vehicleNumber = String(machine.vehicleProductNumber || "").trim();
  return (state.data.repairs || []).filter(item => {
    if (id && Number(item.machineId || 0) === id) return true;
    return vehicleNumber && String(item.vehicleNumber || "").trim() === vehicleNumber;
  });
}

function renderVehicleAttachmentsSurface(machine = {}, order = null) {
  const hasOrder = Boolean(order?.id);
  const body = hasOrder ? renderDetailGrid([
    { label: "订单号", value: order.orderNo },
    { label: "发票", value: order.invoiceFileAvailable ? `${order.invoiceOriginalName || "已上传"} ${fileSize(order.invoiceFileSize)}` : "未上传" },
    { label: "发票上传时间", value: dateTime(order.invoiceUploadedAt) },
    { label: "合同", value: order.contractFileAvailable ? `${order.contractOriginalName || "已上传"} ${fileSize(order.contractFileSize)}` : "未上传" },
    { label: "合同上传时间", value: dateTime(order.contractUploadedAt) }
  ]) : emptyState("暂无销售出库订单附件");
  const actions = hasOrder ? `
    <div class="toolbar-actions">
      ${isInvoiceUploadReady(order) ? `<button class="btn btn-sm" type="button" data-action="upload-invoice" data-id="${escapeAttr(order.id)}">${icon("upload")}上传发票</button>` : ""}
      ${order.invoiceFileAvailable ? `<button class="btn btn-sm" type="button" data-action="download-invoice" data-id="${escapeAttr(order.id)}">${icon("download")}下载发票</button>` : ""}
      ${isContractUploadReady(order) ? `<button class="btn btn-sm" type="button" data-action="upload-contract" data-id="${escapeAttr(order.id)}">${icon("upload")}上传合同</button>` : ""}
      ${order.contractFileAvailable ? `<button class="btn btn-sm" type="button" data-action="download-contract" data-id="${escapeAttr(order.id)}">${icon("download")}下载合同</button>` : ""}
    </div>
  ` : "";
  return renderSurface(`附件资料 · ${escapeHtml(machine.vehicleProductNumber || "-")}`, body, actions);
}

function renderVehicleDetail() {
  if (!state.vehicleDetail) return "";
  if (state.vehicleDetail.modelKey) {
    return renderVehicleModelDetail();
  }
  const machine = state.vehicleDetail.machine || {};
  const configs = state.vehicleDetail.configs || [];
  const logs = state.vehicleDetail.logs || [];
  const workOrders = state.vehicleDetail.workOrders || [];
  const vehicleParts = state.vehicleDetail.parts || [];
  const order = latestVehicleOutboundOrder(machine.id);
  const activeTab = ensureVehicleDetailTab();
  return `
    <section class="surface detail-surface-pop" id="vehicleDetailSurface">
      <div class="surface-head">
        <h2 class="surface-title">车辆详情 · ${escapeHtml(machine.vehicleProductNumber || "")}</h2>
        <div class="toolbar-actions">
          ${order?.id && hasPermission("stock:adjust") ? `<button class="btn" type="button" data-action="edit" data-kind="outboundOrder" data-id="${escapeAttr(order.id)}">${icon("edit")}销售跟进</button>` : ""}
          ${!order?.id && Number(machine.inventoryCount || 0) > 0 && hasPermission("stock:adjust") ? `<button class="btn" type="button" data-action="vehicle-outbound-direct" data-machine-id="${escapeAttr(machine.id || "")}">${icon("minus")}登记出库</button>` : ""}
          ${hasPermission("replace:write") ? `<button class="btn" type="button" data-action="create-vehicle-part" data-machine-id="${escapeAttr(machine.id || "")}">${icon("plus")}新增配件</button>` : ""}
          ${hasPermission("replace:write") ? `<button class="btn btn-primary" type="button" data-action="create-modification-order" data-machine-id="${escapeAttr(machine.id || "")}">${icon("swap")}新建改装工单</button>` : ""}
        </div>
      </div>
      <div class="surface-body detail-tab-body">
        ${renderVehicleDetailTabs(activeTab)}
        <div class="detail-tab-panel">
          ${renderVehicleDetailTabContent(activeTab, machine, { configs, logs, workOrders, vehicleParts, order })}
        </div>
      </div>
    </section>
  `;
}

function renderVehicleModelDetail() {
  const detail = state.vehicleDetail;
  const model = detail.model || {};
  const selected = detail.selectedDetail?.machine || {};
  const configs = detail.selectedDetail?.configs || [];
  const logs = detail.selectedDetail?.logs || [];
  const workOrders = detail.selectedDetail?.workOrders || [];
  const vehicleParts = detail.selectedDetail?.parts || [];
  const manualSelected = isManualForklift(selected.machineType || model.machineType);
  const order = latestVehicleOutboundOrder(selected.id);
  const activeTab = ensureVehicleDetailTab();
  return `
    <section class="surface detail-surface-pop" id="vehicleDetailSurface">
      <div class="surface-head">
        <h2 class="surface-title">车型详情 · ${escapeHtml(vehicleModelLabel(model))}</h2>
        <div class="toolbar-actions">
          ${hasPermission("vehicle:write") ? `<button class="btn btn-primary" type="button" data-action="model-inbound" data-model-key="${escapeAttr(detail.modelKey)}">${icon("plus")}此车型入库</button>` : ""}
          ${hasPermission("stock:adjust") ? `<button class="btn" type="button" data-action="model-outbound" data-model-key="${escapeAttr(detail.modelKey)}">${icon("minus")}整车出库</button>` : ""}
        </div>
      </div>
      <div class="surface-body detail-tab-body">
        <div class="detail-grid">
          ${detailItem("车型名称", model.name)}
          ${detailItem("规格型号", model.specificationModel)}
          ${detailItem("默认配置", model.configuration)}
          ${detailItem("车辆类型", model.machineType)}
          ${detailItem("供应商", model.supplier)}
          ${detailItem("经销商/仓位", model.warehouseName)}
          ${detailItem("库存台数", `${model.inventoryCount || 0} 台`)}
          ${detailItem("销售单价", money(model.salePrice))}
        </div>
        <div class="grid-two vehicle-model-detail-grid">
          ${renderSurface("库存车号", `
            <div class="vehicle-unit-grid">
              ${detail.vehicles.length ? detail.vehicles.map(vehicle => renderVehicleUnitCard(vehicle, detail.modelKey, vehicle.id === selected.id)).join("") : emptyState("此车型还没有库存车")}
            </div>
          `)}
          ${renderSurface(`当前车号 · ${escapeHtml(selected.vehicleProductNumber || "请选择车号")}`, `
            <div class="detail-grid detail-grid-compact">
              ${detailItem("车号", selected.vehicleProductNumber)}
              ${detailItem("库存状态", stockStatusLabel(selected.stockStatus))}
              ${manualSelected ? "" : detailItem("发动机号", selected.engineNumber)}
              ${manualSelected ? "" : detailItem("车架号", selected.frameNumber)}
              ${manualSelected ? "" : detailItem("保修卡号", selected.warrantyCardNumber)}
            </div>
            <div class="toolbar-actions detail-action-row">
              ${order?.id && hasPermission("stock:adjust") ? `<button class="btn" type="button" data-action="edit" data-kind="outboundOrder" data-id="${escapeAttr(order.id)}">${icon("edit")}销售跟进</button>` : ""}
              ${!order?.id && selected.id && Number(selected.inventoryCount || 0) > 0 && hasPermission("stock:adjust") ? `<button class="btn" type="button" data-action="vehicle-outbound-direct" data-machine-id="${escapeAttr(selected.id || "")}">${icon("minus")}登记出库</button>` : ""}
              ${selected.id && hasPermission("replace:write") ? `<button class="btn" type="button" data-action="create-vehicle-part" data-machine-id="${escapeAttr(selected.id || "")}">${icon("plus")}新增配件</button>` : ""}
              ${selected.id && hasPermission("replace:write") ? `<button class="btn btn-primary" type="button" data-action="create-modification-order" data-machine-id="${escapeAttr(selected.id || "")}">${icon("swap")}新建改装工单</button>` : ""}
            </div>
          `)}
        </div>
        ${renderVehicleDetailTabs(activeTab)}
        <div class="detail-tab-panel">
          ${renderVehicleDetailTabContent(activeTab, selected, { configs, logs, workOrders, vehicleParts, order })}
        </div>
      </div>
    </section>
  `;
}

function renderVehicleUnitCard(vehicle, modelKey, active) {
  const manual = isManualForklift(vehicle.machineType);
  return `
    <button class="vehicle-unit-card${active ? " is-active" : ""}" type="button" data-action="select-model-vehicle" data-model-key="${escapeAttr(modelKey)}" data-machine-id="${escapeAttr(vehicle.id)}">
      <span class="vehicle-unit-title">${escapeHtml(vehicle.vehicleProductNumber || `ID ${vehicle.id}`)}</span>
      <span class="vehicle-unit-meta">${escapeHtml(stockStatusLabel(vehicle.stockStatus))}${manual ? " · 系统车号" : ` · ${escapeHtml(vehicle.engineNumber || "未填发动机号")}`}</span>
      ${manual ? "" : `<span class="vehicle-unit-meta">${escapeHtml(vehicle.frameNumber || "未填车架号")}</span>`}
    </button>
  `;
}

function renderToolbar(moduleKey, placeholder, kind, createLabel, createPermission, options = {}) {
  const searches = options.searches || (moduleKey && placeholder ? [{ key: moduleKey, placeholder }] : []);
  const mainItems = [
    ...searches.map(item => searchBox(item.key, item.placeholder)),
    ...(options.main || [])
  ].filter(Boolean);
  const canCreate = options.create !== false
    && kind
    && createLabel
    && (!createPermission || hasPermission(createPermission));
  const actions = [
    canCreate ? `<button class="btn btn-primary" type="button" data-action="create" data-kind="${escapeAttr(kind)}">${icon("plus")}${escapeHtml(createLabel)}</button>` : "",
    ...(options.actions || []),
    options.exportType === false ? "" : exportButton(options.exportType || moduleKey),
    options.refresh === false ? "" : `<button class="btn btn-ghost" type="button" data-action="refresh">${icon("refresh")}刷新</button>`
  ].filter(Boolean);
  const className = ["toolbar", options.className || ""].filter(Boolean).join(" ");
  return `
    <div class="${escapeAttr(className)}">
      <div class="toolbar-main">${mainItems.join("")}</div>
      <div class="toolbar-actions">${actions.join("")}</div>
    </div>
  `;
}

function searchBox(key, placeholder) {
  const value = state.search[key] || "";
  return `
    <label class="search-box">
      <span class="search-icon">${icons.search}</span>
      <input class="input" type="search" data-search-for="${escapeAttr(key)}" value="${escapeAttr(value)}" placeholder="${escapeAttr(placeholder)}" aria-label="${escapeAttr(placeholder)}" autocomplete="off" spellcheck="false">
      ${value ? `<button class="search-clear" type="button" data-action="clear-search" data-search-for="${escapeAttr(key)}" aria-label="清空搜索">×</button>` : ""}
    </label>
  `;
}

function vehicleFilterControls() {
  const filters = state.filters.vehicles;
  return `
    <div class="filter-row">
      ${filterButtonGroup("vehicles", "power", filters.power, "动力", "全部动力", powerTypeOptions())}
      ${filterButtonGroup("vehicles", "stock", filters.stock, "库存", "全部库存", stockFilterOptions())}
      ${filterButtonGroup("vehicles", "supplier", filters.supplier, "供应商", "全部供应商", supplierFilterOptions())}
    </div>
  `;
}

function filterButtonGroup(key, name, value, label, placeholder, options) {
  const normalizedOptions = [{ value: "", label: placeholder }, ...(options || [])];
  return `
    <div class="filter-group" role="group" aria-label="${escapeAttr(label)}">
      <span class="filter-label">${escapeHtml(label)}</span>
      ${normalizedOptions.map(option => {
        const active = String(option.value) === String(value || "");
        return `<button class="filter-chip${active ? " is-active" : ""}" type="button" data-action="apply-filter" data-filter-for="${escapeAttr(key)}" data-filter-name="${escapeAttr(name)}" data-filter-value="${escapeAttr(option.value)}" aria-pressed="${active ? "true" : "false"}">${escapeHtml(option.label)}</button>`;
      }).join("")}
    </div>
  `;
}

function renderVehicleModelMenu(options = {}) {
  const triggerClass = options.triggerClass || "btn btn-primary hover-menu-trigger";
  return `
    <div class="hover-menu">
      <button class="${escapeAttr(triggerClass)}" type="button">${icon("plus")}新增车型</button>
      <div class="hover-menu-panel">
        ${powerTypeOptions().map(option => `
          <button type="button" data-action="create-vehicle-model" data-power-type="${escapeAttr(option.value)}">${escapeHtml(`新增${option.label}`)}</button>
        `).join("")}
      </div>
    </div>
  `;
}

function renderSurface(title, body, actions = "") {
  return `
    <section class="surface">
      <div class="surface-head">
        <h2 class="surface-title">${title}</h2>
        ${actions ? `<div class="section-actions">${actions}</div>` : ""}
      </div>
      <div class="surface-body">${body}</div>
    </section>
  `;
}

function renderYearOptions(selectedYear, yearlyRows) {
  const currentYear = new Date().getFullYear();
  const years = new Set([selectedYear || currentYear, currentYear]);
  for (const row of yearlyRows || []) {
    if (row.period) years.add(Number(row.period));
  }
  return [...years]
    .filter(year => !Number.isNaN(year))
    .sort((a, b) => b - a)
    .map(year => `<option value="${escapeAttr(year)}"${Number(year) === Number(selectedYear) ? " selected" : ""}>${escapeHtml(year)} 年</option>`)
    .join("");
}

function renderFinanceTrend(rows) {
  return `
    <div class="finance-trend-layout">
      ${renderFinanceBars(rows)}
      <section class="finance-inline-report" aria-label="月度财报">
        <h3>月度财报</h3>
        ${renderMonthlyFinanceTable(rows)}
      </section>
    </div>
  `;
}

function renderMonthlyFinanceTable(rows) {
  return renderTable([
    { label: "月份", key: "period" },
    { label: "入库成本", key: "inboundCost", formatter: money },
    { label: "出库收入", key: "outboundRevenue", formatter: money },
    { label: "出库成本", key: "outboundCost", formatter: money },
    { label: "维修收入", key: "repairIncome", formatter: money },
    { label: "租赁收入", key: "rentalIncome", formatter: money },
    { label: "折价收入", key: "modificationIncome", formatter: money },
    { label: "折价支出", key: "modificationExpense", formatter: money },
    { label: "毛利", key: "grossProfit", formatter: money }
  ], rows);
}

function renderFinanceBars(rows) {
  const chartRows = (rows || []).map(row => ({
    ...row,
    periodLabel: row.period?.slice(5) || row.period || "-",
    totalIncome: financeNumber(row.totalIncome),
    inboundCost: financeNumber(row.inboundCost),
    outboundRevenue: financeNumber(row.outboundRevenue),
    outboundCost: financeNumber(row.outboundCost),
    repairIncome: financeNumber(row.repairIncome),
    rentalIncome: financeNumber(row.rentalIncome),
    modificationIncome: financeNumber(row.modificationIncome),
    modificationExpense: financeNumber(row.modificationExpense),
    grossProfit: financeNumber(row.grossProfit)
  }));

  if (!chartRows.length) {
    return emptyState("暂无月度收支走势数据");
  }

  const totalIncome = chartRows.reduce((sum, row) => sum + row.totalIncome, 0);
  const totalCost = chartRows.reduce((sum, row) => sum + row.inboundCost, 0);
  const totalProfit = chartRows.reduce((sum, row) => sum + row.grossProfit, 0);
  const rawMax = Math.max(1, ...chartRows.flatMap(row => [row.totalIncome, row.inboundCost]));
  const max = niceFinanceMax(rawMax);
  const chart = { width: 720, height: 300, left: 58, right: 24, top: 24, bottom: 42 };
  const plotWidth = chart.width - chart.left - chart.right;
  const plotHeight = chart.height - chart.top - chart.bottom;
  const baseY = chart.top + plotHeight;
  const xFor = index => chartRows.length === 1
    ? chart.left + plotWidth / 2
    : chart.left + (index / (chartRows.length - 1)) * plotWidth;
  const yFor = value => chart.top + plotHeight - (Math.max(0, value) / max) * plotHeight;
  const incomePath = financeLinePath(chartRows, "totalIncome", xFor, yFor);
  const costPath = financeLinePath(chartRows, "inboundCost", xFor, yFor);
  const incomeArea = financeAreaPath(chartRows, "totalIncome", xFor, yFor, baseY);
  const costArea = financeAreaPath(chartRows, "inboundCost", xFor, yFor, baseY);
  const ticks = [0, 0.25, 0.5, 0.75, 1].map(ratio => max * ratio);
  const labelEvery = Math.max(1, Math.ceil(chartRows.length / 8));

  return `
    <div class="finance-chart-card">
      <div class="finance-chart-stats" aria-label="收支汇总">
        <span class="finance-chart-stat primary"><small>收入合计</small><strong>${escapeHtml(formatChartMoney(totalIncome))}</strong></span>
        <span class="finance-chart-stat warn"><small>入库成本</small><strong>${escapeHtml(formatChartMoney(totalCost))}</strong></span>
        <span class="finance-chart-stat teal"><small>毛利</small><strong>${escapeHtml(formatChartMoney(totalProfit))}</strong></span>
      </div>
      <div class="finance-chart" role="img" aria-label="月度收入与入库成本折线图">
        <svg class="finance-chart-svg" viewBox="0 0 ${chart.width} ${chart.height}" aria-hidden="true">
          ${ticks.map(value => {
            const y = yFor(value);
            return `
              <g>
                <line class="finance-grid-line" x1="${chart.left}" y1="${roundChart(y)}" x2="${chart.width - chart.right}" y2="${roundChart(y)}"></line>
                <text class="finance-axis-label" x="${chart.left - 10}" y="${roundChart(y + 4)}" text-anchor="end">${escapeHtml(formatAxisMoney(value))}</text>
              </g>
            `;
          }).join("")}
          <line class="finance-axis-line" x1="${chart.left}" y1="${baseY}" x2="${chart.width - chart.right}" y2="${baseY}"></line>
          <path class="finance-area income" d="${escapeAttr(incomeArea)}"></path>
          <path class="finance-area cost" d="${escapeAttr(costArea)}"></path>
          <path class="finance-line income" d="${escapeAttr(incomePath)}"></path>
          <path class="finance-line cost" d="${escapeAttr(costPath)}"></path>
          ${chartRows.map((row, index) => {
            const x = xFor(index);
            return `
              <circle class="finance-point income" data-finance-point-index="${escapeAttr(index)}" cx="${roundChart(x)}" cy="${roundChart(yFor(row.totalIncome))}" r="4"></circle>
              <circle class="finance-point cost" data-finance-point-index="${escapeAttr(index)}" cx="${roundChart(x)}" cy="${roundChart(yFor(row.inboundCost))}" r="4"></circle>
              ${(index % labelEvery === 0 || index === chartRows.length - 1) ? `<text class="finance-x-label" x="${roundChart(x)}" y="${chart.height - 14}" text-anchor="middle">${escapeHtml(row.periodLabel)}</text>` : ""}
            `;
          }).join("")}
        </svg>
        ${chartRows.map((row, index) => {
          const x = xFor(index);
          const leftBoundary = index === 0 ? chart.left : (xFor(index - 1) + x) / 2;
          const rightBoundary = index === chartRows.length - 1 ? chart.width - chart.right : (x + xFor(index + 1)) / 2;
          const left = (leftBoundary / chart.width) * 100;
          const width = ((rightBoundary - leftBoundary) / chart.width) * 100;
          const guideLeft = ((x - leftBoundary) / Math.max(1, rightBoundary - leftBoundary)) * 100;
          const edgeClass = index === 0 ? " is-start" : index === chartRows.length - 1 ? " is-end" : "";
          return `
            <div class="finance-chart-column${edgeClass}" data-finance-column-index="${escapeAttr(index)}" tabindex="0" style="left:${roundChart(left)}%;width:${roundChart(width)}%;--finance-guide-left:${roundChart(guideLeft)}%" aria-label="${escapeAttr(row.periodLabel)} 收支详情">
              <span class="finance-guide"></span>
              <span class="finance-tooltip">
                <strong>${escapeHtml(row.periodLabel)}</strong>
                ${financeTooltipRow("总收入", row.totalIncome, "primary")}
                ${financeTooltipRow("入库成本", row.inboundCost, "warn")}
                ${financeTooltipRow("出库收入", row.outboundRevenue)}
                ${financeTooltipRow("出库成本", row.outboundCost)}
                ${financeTooltipRow("维修收入", row.repairIncome)}
                ${financeTooltipRow("租赁收入", row.rentalIncome)}
                ${financeTooltipRow("折价收入", row.modificationIncome)}
                ${financeTooltipRow("折价支出", row.modificationExpense)}
                ${financeTooltipRow("毛利", row.grossProfit, "teal")}
              </span>
            </div>
          `;
        }).join("")}
      </div>
      <div class="finance-legend">
        <span><i class="legend-dot income"></i>收入</span>
        <span><i class="legend-dot cost"></i>入库成本</span>
      </div>
    </div>
  `;
}

function financeNumber(value) {
  const numeric = Number(value || 0);
  return Number.isFinite(numeric) ? numeric : 0;
}

function niceFinanceMax(value) {
  const numeric = Math.max(1, Number(value || 0));
  const exponent = Math.floor(Math.log10(numeric));
  const base = 10 ** exponent;
  const scaled = numeric / base;
  const niceScaled = scaled <= 1 ? 1 : scaled <= 2 ? 2 : scaled <= 5 ? 5 : 10;
  return niceScaled * base;
}

function financeLinePath(rows, key, xFor, yFor) {
  if (rows.length === 1) {
    const x = xFor(0);
    const y = yFor(rows[0][key]);
    return `M ${roundChart(x - 18)} ${roundChart(y)} L ${roundChart(x + 18)} ${roundChart(y)}`;
  }
  return rows.map((row, index) => `${index ? "L" : "M"} ${roundChart(xFor(index))} ${roundChart(yFor(row[key]))}`).join(" ");
}

function financeAreaPath(rows, key, xFor, yFor, baseY) {
  if (rows.length === 1) {
    const x = xFor(0);
    const y = yFor(rows[0][key]);
    return `M ${roundChart(x - 18)} ${roundChart(baseY)} L ${roundChart(x - 18)} ${roundChart(y)} L ${roundChart(x + 18)} ${roundChart(y)} L ${roundChart(x + 18)} ${roundChart(baseY)} Z`;
  }
  const line = rows.map((row, index) => `${index ? "L" : "M"} ${roundChart(xFor(index))} ${roundChart(yFor(row[key]))}`).join(" ");
  return `${line} L ${roundChart(xFor(rows.length - 1))} ${roundChart(baseY)} L ${roundChart(xFor(0))} ${roundChart(baseY)} Z`;
}

function financeTooltipRow(label, value, tone = "") {
  return `<span class="finance-tooltip-row ${escapeAttr(tone)}"><em>${escapeHtml(label)}</em><b>${escapeHtml(money(value) || "¥0.00")}</b></span>`;
}

function formatChartMoney(value) {
  const numeric = financeNumber(value);
  const abs = Math.abs(numeric);
  if (abs >= 100000000) return `¥${trimChartNumber(numeric / 100000000)}亿`;
  if (abs >= 10000) return `¥${trimChartNumber(numeric / 10000)}万`;
  return money(numeric) || "¥0.00";
}

function formatAxisMoney(value) {
  const numeric = financeNumber(value);
  const abs = Math.abs(numeric);
  if (abs >= 100000000) return `${trimChartNumber(numeric / 100000000)}亿`;
  if (abs >= 10000) return `${trimChartNumber(numeric / 10000)}万`;
  return trimChartNumber(numeric);
}

function trimChartNumber(value) {
  return Number(value).toFixed(1).replace(/\.0$/, "");
}

function roundChart(value) {
  return Math.round(Number(value || 0) * 100) / 100;
}

function summaryCard(label, value, foot, options = {}) {
  const attrs = options.action
    ? `type="button" class="summary-card summary-card-button${options.active ? " is-active" : ""}" data-action="${escapeAttr(options.action)}" ${Object.entries(options.data || {}).map(([key, itemValue]) => `data-${toDataAttrName(key)}="${escapeAttr(itemValue)}"`).join(" ")}`
    : `class="summary-card${options.active ? " is-active" : ""}"`;
  const tag = options.action ? "button" : "article";
  return `
    <${tag} ${attrs}>
      <div class="summary-label">${escapeHtml(label)}</div>
      <div class="summary-value">${escapeHtml(value)}</div>
      <div class="summary-foot">${escapeHtml(foot)}</div>
    </${tag}>
  `;
}

function renderBackendSummary(tab, fallbackFactory) {
  const cards = state.data.summaries?.[tab]?.cards;
  if (Array.isArray(cards) && cards.length) {
    return `<section class="summary-grid">${cards.map(card => summaryCard(card.label, summaryValue(card), card.foot || "")).join("")}</section>`;
  }
  return typeof fallbackFactory === "function" ? fallbackFactory() : "";
}

function summaryValue(card = {}) {
  return card.format === "money" ? money(card.value) : display(card.value);
}

function listEmptyStateMeta(kind) {
  return {
    part: {
      title: "暂无配件",
      message: "先新增配件，后续入库、出库和采购都可直接引用。",
      actionKind: "part",
      actionLabel: "新增配件",
      permission: "part:write"
    },
    modificationOrder: {
      title: "暂无改装工单",
      message: "新建改装工单后，可以跟踪替换明细和完成入账。",
      actionKind: "modificationOrder",
      actionLabel: "新建改装工单",
      permission: "replace:write"
    },
    outboundOrder: {
      title: "暂无出库订单",
      message: "完成车辆或配件出库后，这里会生成销售与发票跟进记录。"
    },
    rental: {
      title: "暂无租赁记录",
      message: "新增租赁后，可以记录车辆去向、租期和月租。",
      actionKind: "rental",
      actionLabel: "新增租赁",
      permission: "stock:adjust"
    },
    customer: {
      title: "暂无客户",
      message: "先新增客户，后续销售、维修和租赁可直接带出资料。",
      actionKind: "customer",
      actionLabel: "新增客户",
      permission: "vehicle:write"
    },
    supplier: {
      title: "暂无供应商",
      message: "先新增供应商，后续采购单可直接引用。",
      actionKind: "supplier",
      actionLabel: "新增供应商",
      permission: "stock:adjust"
    },
    purchaseOrder: {
      title: "暂无采购订单",
      message: "新增采购后，可以同步跟踪收货状态和采购成本。",
      actionKind: "purchaseOrder",
      actionLabel: "新增配件采购",
      permission: "stock:adjust"
    },
    stocktaking: {
      title: "暂无盘点记录",
      message: "新增盘点后，可以核对账面和实盘数量并入账。",
      actionKind: "stocktaking",
      actionLabel: "新增盘点",
      permission: "stock:adjust"
    },
    repair: {
      title: "暂无维修记录",
      message: "新增维修后，可以跟踪故障、用料和结算状态。",
      actionKind: "repair",
      actionLabel: "新增维修",
      permission: "repair:write"
    },
    user: {
      title: "暂无用户",
      message: "新建用户后，可以分配角色、职务和账号状态。",
      actionKind: "user",
      actionLabel: "新建用户",
      permission: "user:write"
    }
  }[kind] || {
    title: "暂无数据",
    message: `暂无${entityLabel(kind)}`
  };
}

function createListEmptyState(kind) {
  const meta = listEmptyStateMeta(kind);
  const canCreate = meta.actionKind && (!meta.permission || hasPermission(meta.permission));
  return `
    <div class="empty-state empty-state-actionable">
      <div class="empty-state-main">
        <strong>${escapeHtml(meta.title)}</strong>
        ${canCreate ? `<button class="btn btn-primary" type="button" data-action="create" data-kind="${escapeAttr(meta.actionKind)}">${icon("plus")}${escapeHtml(meta.actionLabel)}</button>` : ""}
      </div>
      <span>${escapeHtml(meta.message)}</span>
    </div>
  `;
}

function renderTableEmptyState(options) {
  if (typeof options.emptyState === "function") return options.emptyState();
  if (options.emptyState) return options.emptyState;
  return emptyState("暂无数据");
}

function listTableOptions(kind, exportType, options = {}) {
  return {
    tableKey: options.tableKey || exportType || kind,
    emptyState: options.emptyState === undefined ? () => createListEmptyState(kind) : options.emptyState,
    selectableRow: options.selectable === false ? null : row => ({
      action: "open-detail",
      data: { kind, id: row.id },
      active: state.detailDrawer?.kind === kind && Number(state.detailDrawer?.id) === Number(row.id),
      label: `查看${entityLabel(kind)}详情`
    }),
    batch: options.batch === false ? null : {
      kind,
      exportType,
      actions: batchActionsForKind(kind)
    }
  };
}

function renderTable(columns, rows, options = {}) {
  const tableKey = options.tableKey || null;
  const preparedColumns = prepareTableColumns(columns);
  const visibleRows = tableKey ? sortRowsForTable(tableKey, preparedColumns, rows) : rows;
  if (!visibleRows.length) return renderTableEmptyState(options);
  const batch = options.batch;
  const visibleIds = visibleRows.map(row => String(row.id)).filter(Boolean);
  const selectedIds = batch ? selectedIdSet(batch.kind) : new Set();
  const allVisibleSelected = batch && visibleIds.length > 0 && visibleIds.every(id => selectedIds.has(id));
  const dataColumns = tableKey ? visibleTableColumns(tableKey, preparedColumns) : preparedColumns;
  const tableColumns = batch ? [
    {
      label: `<input class="row-check" type="checkbox" data-action="toggle-visible-select" data-batch-kind="${escapeAttr(batch.kind)}" data-visible-ids="${escapeAttr(visibleIds.join(","))}" aria-label="选择当前页" ${allVisibleSelected ? "checked" : ""}>`,
      htmlLabel: true,
      html: true,
      render: row => `<input class="row-check" type="checkbox" data-action="toggle-row-select" data-batch-kind="${escapeAttr(batch.kind)}" data-id="${escapeAttr(row.id)}" aria-label="选择此行" ${selectedIds.has(String(row.id)) ? "checked" : ""}>`
    },
    ...dataColumns
  ] : dataColumns;
  const densityClass = state.tableDensity === "compact" ? " is-compact" : "";
  return `
    ${tableKey ? renderTableControls(tableKey, preparedColumns) : ""}
    ${batch ? renderBatchToolbar(batch, visibleRows) : ""}
    <div class="table-wrap${densityClass}" tabindex="0" aria-label="数据表格，可横向滚动">
      <table>
        <thead>
          <tr>${tableColumns.map(column => renderTableHeader(column, tableKey)).join("")}</tr>
        </thead>
        <tbody>
          ${visibleRows.map(row => {
            const selectable = options.selectableRow ? options.selectableRow(row) : null;
            const rowClass = [
              selectable ? "table-row-selectable" : "",
              selectable?.active ? "table-row-active" : "",
              batch && selectedIds.has(String(row.id)) ? "table-row-checked" : ""
            ].filter(Boolean).join(" ");
            return `
              <tr${rowClass ? ` class="${rowClass}"` : ""}${renderSelectableAttrs(selectable)}>
                ${tableColumns.map(column => `<td data-label="${escapeAttr(tableCellLabel(column))}">${renderCell(column, row)}</td>`).join("")}
              </tr>
            `;
          }).join("")}
        </tbody>
      </table>
    </div>
  `;
}

function prepareTableColumns(columns) {
  return (columns || []).map((column, index) => ({
    ...column,
    sortId: column.sortKey || column.key || `column-${index}`,
    sortable: column.sortable !== false && !column.htmlLabel && !column.html && Boolean(column.sortValue || column.key || column.render)
  }));
}

function renderTableControls(tableKey, columns) {
  const hideableColumns = columns.filter(column => isHideableTableColumn(column));
  const savedView = state.filterViews?.[tableKey] || null;
  const densityLabel = state.tableDensity === "compact" ? "紧凑" : "舒适";
  return `
    <div class="table-control-bar">
      <div class="table-control-group">
        <button class="btn btn-sm" type="button" data-action="toggle-table-density" aria-label="切换表格密度">${icon("swap")}${densityLabel}</button>
        <details class="column-menu">
          <summary class="btn btn-sm">${icon("eye")}列显示</summary>
          <div class="column-menu-panel">
            ${hideableColumns.map(column => `
              <label class="column-option">
                <input type="checkbox" data-action="toggle-table-column" data-table-key="${escapeAttr(tableKey)}" data-column-key="${escapeAttr(column.sortId)}" ${isColumnVisible(tableKey, column) ? "checked" : ""}>
                <span>${escapeHtml(tableColumnControlLabel(column))}</span>
              </label>
            `).join("")}
          </div>
        </details>
      </div>
      <div class="table-control-group">
        <button class="btn btn-sm" type="button" data-action="save-filter-view" data-table-key="${escapeAttr(tableKey)}">${icon("download")}保存筛选</button>
        <button class="btn btn-sm" type="button" data-action="apply-filter-view" data-table-key="${escapeAttr(tableKey)}" ${savedView ? "" : "disabled"}>${icon("refresh")}应用方案</button>
        <button class="btn btn-sm btn-ghost" type="button" data-action="delete-filter-view" data-table-key="${escapeAttr(tableKey)}" ${savedView ? "" : "disabled"}>清除方案</button>
      </div>
    </div>
  `;
}

function visibleTableColumns(tableKey, columns) {
  return columns.filter(column => isColumnVisible(tableKey, column));
}

function isColumnVisible(tableKey, column) {
  if (!isHideableTableColumn(column)) return true;
  const tableColumns = state.tableColumns?.[tableKey] || {};
  return tableColumns[column.sortId] !== false;
}

function setTableColumnVisibility(tableKey, columnKey, visible) {
  if (!tableKey || !columnKey) return;
  state.tableColumns = {
    ...(state.tableColumns || {}),
    [tableKey]: {
      ...(state.tableColumns?.[tableKey] || {}),
      [columnKey]: Boolean(visible)
    }
  };
}

function isHideableTableColumn(column) {
  if (!column || column.htmlLabel) return false;
  const label = String(column.label || "");
  return label !== "操作" && label !== "鎿嶄綔";
}

function tableColumnControlLabel(column) {
  return String(column.label || column.key || column.sortId || "列");
}

function saveFilterView(tableKey) {
  if (!tableKey) return;
  const searchKey = searchKeyForTable(tableKey);
  state.filterViews = {
    ...(state.filterViews || {}),
    [tableKey]: {
      searchKey,
      searchValue: state.search?.[searchKey] || "",
      filters: { ...(state.filters?.[searchKey] || {}) },
      sort: state.sorts?.[tableKey] || null,
      savedAt: Date.now()
    }
  };
}

async function applyFilterView(tableKey) {
  const view = state.filterViews?.[tableKey];
  if (!view) {
    showToast("当前模块还没有保存筛选方案", "info");
    return;
  }
  const searchKey = view.searchKey || searchKeyForTable(tableKey);
  if (Object.prototype.hasOwnProperty.call(state.search, searchKey)) {
    state.search[searchKey] = view.searchValue || "";
  }
  state.filters = {
    ...(state.filters || {}),
    [searchKey]: { ...(view.filters || {}) }
  };
  state.sorts = { ...(state.sorts || {}) };
  if (view.sort) {
    state.sorts[tableKey] = view.sort;
  } else {
    delete state.sorts[tableKey];
  }
  if (state.pages?.[searchKey]) resetPage(state.pages[searchKey]);
  persistListState();
  if (searchKey === activePagedTab()) {
    els.content.innerHTML = renderLoading();
    if (searchKey === "vehicles") {
      await loadVehicleModelData({ force: true });
    } else {
      await loadPagedTab(searchKey, { force: true });
    }
  }
  renderCurrentTab();
  showToast("筛选方案已应用", "success");
}

function deleteFilterView(tableKey) {
  if (!tableKey || !state.filterViews?.[tableKey]) return;
  const next = { ...(state.filterViews || {}) };
  delete next[tableKey];
  state.filterViews = next;
}

function searchKeyForTable(tableKey) {
  if (Object.prototype.hasOwnProperty.call(state.search, tableKey)) return tableKey;
  const aliases = {
    modificationOrder: "modificationOrders",
    outboundOrder: "outboundOrders",
    purchaseOrder: "purchases",
    stocktaking: "stocktakes",
    warehouse: "warehouses",
    stockMovement: "stockMovements",
    repair: "repairs"
  };
  if (aliases[tableKey]) return aliases[tableKey];
  return Object.prototype.hasOwnProperty.call(state.search, state.activeTab) ? state.activeTab : tableKey;
}

function renderTableHeader(column, tableKey) {
  if (column.htmlLabel) return `<th>${column.label}</th>`;
  const label = escapeHtml(column.label);
  if (!tableKey || !column.sortable) return `<th>${label}</th>`;
  const sort = state.sorts?.[tableKey];
  const active = sort?.key === column.sortId;
  const direction = active ? sort.direction : "";
  const indicator = active ? (direction === "desc" ? "↓" : "↑") : "↕";
  return `
    <th>
      <button class="table-sort-button${active ? " is-active" : ""}" type="button" data-action="sort-table" data-table-key="${escapeAttr(tableKey)}" data-sort-key="${escapeAttr(column.sortId)}" aria-sort="${active ? (direction === "desc" ? "descending" : "ascending") : "none"}">
        <span>${label}</span><span class="sort-indicator" aria-hidden="true">${indicator}</span>
      </button>
    </th>
  `;
}

function tableCellLabel(column) {
  if (column.cardLabel !== undefined) return column.cardLabel;
  if (column.htmlLabel) return "选择";
  return String(column.label || "");
}

function applyTableSort(tableKey, sortKey) {
  if (!tableKey || !sortKey) return;
  const current = state.sorts?.[tableKey];
  const nextDirection = current?.key === sortKey && current.direction === "asc" ? "desc" : "asc";
  state.sorts = {
    ...(state.sorts || {}),
    [tableKey]: { key: sortKey, direction: nextDirection }
  };
}

function sortRowsForTable(tableKey, columns, rows) {
  const sort = state.sorts?.[tableKey];
  if (!sort?.key) return rows;
  const column = columns.find(item => item.sortId === sort.key);
  if (!column) return rows;
  const direction = sort.direction === "desc" ? -1 : 1;
  return [...rows].sort((left, right) => compareTableValues(tableSortValue(column, left), tableSortValue(column, right)) * direction);
}

function tableSortValue(column, row) {
  if (column.sortValue) return column.sortValue(row);
  if (column.key) return row[column.key];
  if (column.render && !column.html) return column.render(row);
  return "";
}

function compareTableValues(left, right) {
  const leftEmpty = left === null || left === undefined || left === "";
  const rightEmpty = right === null || right === undefined || right === "";
  if (leftEmpty && rightEmpty) return 0;
  if (leftEmpty) return 1;
  if (rightEmpty) return -1;
  const leftNumber = Number(left);
  const rightNumber = Number(right);
  if (Number.isFinite(leftNumber) && Number.isFinite(rightNumber)) {
    return leftNumber - rightNumber;
  }
  const leftTime = Date.parse(left);
  const rightTime = Date.parse(right);
  if (Number.isFinite(leftTime) && Number.isFinite(rightTime)) {
    return leftTime - rightTime;
  }
  return String(left).localeCompare(String(right), "zh-CN", { numeric: true, sensitivity: "base" });
}

function renderExportableSurface(title, exportType, body) {
  return `
    <section class="surface">
      <div class="surface-head">
        <h2 class="surface-title">${title}</h2>
      </div>
      <div class="surface-body">${body}</div>
    </section>
  `;
}

function exportButton(type) {
  if (!endpoints.export?.[type]) return "";
  return `<button class="btn" type="button" data-action="export-excel" data-export-type="${escapeAttr(type)}">${icon("download")}导出 Excel</button>`;
}

function renderPagination(tab) {
  const page = state.pages[tab];
  if (!page?.loaded) return "";
  const current = page.totalPages ? page.page + 1 : 0;
  const totalPages = page.totalPages || 0;
  return `
    <div class="pagination-bar">
      <div class="pagination-meta">共 ${escapeHtml(page.totalElements)} 条 · 第 ${escapeHtml(current)} / ${escapeHtml(totalPages)} 页</div>
      <div class="pagination-actions">
        <button class="btn btn-sm" type="button" data-action="page-list" data-page-tab="${escapeAttr(tab)}" data-page="0"${page.first ? " disabled" : ""}>首页</button>
        <button class="btn btn-sm" type="button" data-action="page-list" data-page-tab="${escapeAttr(tab)}" data-page="${escapeAttr(Math.max(0, page.page - 1))}"${page.first ? " disabled" : ""}>上一页</button>
        <button class="btn btn-sm" type="button" data-action="page-list" data-page-tab="${escapeAttr(tab)}" data-page="${escapeAttr(page.page + 1)}"${page.last ? " disabled" : ""}>下一页</button>
        <button class="btn btn-sm" type="button" data-action="page-list" data-page-tab="${escapeAttr(tab)}" data-page="${escapeAttr(Math.max(0, totalPages - 1))}"${page.last ? " disabled" : ""}>末页</button>
      </div>
    </div>
  `;
}

function compactTable(columns, rows) {
  return `<div class="compact-table">${renderTable(columns, rows)}</div>`;
}

function renderCell(column, row) {
  if (column.html) return column.render(row);
  const raw = column.render ? column.render(row) : row[column.key];
  const value = column.formatter ? column.formatter(raw, row) : raw;
  return escapeHtml(display(value));
}

function renderSelectableAttrs(selectable) {
  if (!selectable?.action) return "";
  const attrs = [
    `data-select-action="${escapeAttr(selectable.action)}"`,
    'tabindex="0"',
    'role="button"'
  ];
  if (typeof selectable.active === "boolean") {
    attrs.push(`aria-pressed="${selectable.active ? "true" : "false"}"`);
  }
  if (selectable.label) {
    attrs.push(`aria-label="${escapeAttr(selectable.label)}"`);
  }
  for (const [key, value] of Object.entries(selectable.data || {})) {
    if (value === undefined || value === null) continue;
    attrs.push(`data-${toDataAttrName(key)}="${escapeAttr(value)}"`);
  }
  return ` ${attrs.join(" ")}`;
}

function toDataAttrName(key) {
  return String(key).replace(/([A-Z])/g, "-$1").toLowerCase();
}

function renderConfigItemCard(item) {
  const active = item.id === state.selectedConfigItemId;
  return `
    <article class="config-item is-selectable${active ? " is-active" : ""}"${renderSelectableAttrs({
      action: "select-config-item",
      data: { id: item.id },
      active,
      label: `查看配置项 ${item.itemName || item.itemCode || item.id} 的值列表`
    })}>
      <div class="config-item-head">
        <div>
          <div class="config-item-title">${escapeHtml(item.itemName || "-")}</div>
          <div class="helper">${escapeHtml(item.category || "-")} / ${escapeHtml(item.subCategory || "-")} · ${escapeHtml(item.itemCode || "-")}</div>
        </div>
        ${item.isRequired ? badge("必填", "teal") : badge("可选", "primary")}
      </div>
      <div class="toolbar-actions">
        <button class="btn btn-sm" type="button" data-action="select-config-item" data-id="${escapeAttr(item.id)}">${icon("eye")}值列表</button>
        ${hasPermission("config:write") ? `<button class="btn btn-sm" type="button" data-action="edit" data-kind="configItem" data-id="${escapeAttr(item.id)}">${icon("edit")}编辑</button>` : ""}
        ${hasPermission("config:write") ? `<button class="btn btn-sm btn-danger" type="button" data-action="delete" data-kind="configItem" data-id="${escapeAttr(item.id)}">${icon("trash")}删除</button>` : ""}
      </div>
    </article>
  `;
}

function rowActions(kind, row, actions) {
  const canWrite = canWriteEntity(kind);
  const canAdjustStock = hasPermission("stock:adjust");
  return `
    <div class="action-row">
      ${actions.includes("detail") ? `<button class="btn btn-sm" type="button" data-action="detail-vehicle" data-id="${escapeAttr(row.id)}">${icon("eye")}详情</button>` : ""}
      ${actions.includes("stockIn") && kind === "vehicle" && canAdjustStock ? `<button class="btn btn-sm" type="button" data-action="vehicle-stock" data-direction="inbound" data-id="${escapeAttr(row.id)}">${icon("plus")}入库</button>` : ""}
      ${actions.includes("stockOut") && kind === "vehicle" && canAdjustStock ? `<button class="btn btn-sm" type="button" data-action="vehicle-stock" data-direction="outbound" data-id="${escapeAttr(row.id)}">${icon("minus")}出库</button>` : ""}
      ${actions.includes("stockIn") && kind === "part" && canAdjustStock ? `<button class="btn btn-sm" type="button" data-action="part-stock" data-direction="inbound" data-part-code="${escapeAttr(row.partCode)}">${icon("plus")}入库</button>` : ""}
      ${actions.includes("stockOut") && kind === "part" && canAdjustStock ? `<button class="btn btn-sm" type="button" data-action="part-stock" data-direction="outbound" data-part-code="${escapeAttr(row.partCode)}">${icon("minus")}出库</button>` : ""}
      ${actions.includes("edit") && canWrite ? `<button class="btn btn-sm" type="button" data-action="edit" data-kind="${kind}" data-id="${escapeAttr(row.id)}">${icon("edit")}编辑</button>` : ""}
      ${actions.includes("delete") && canWrite ? `<button class="btn btn-sm btn-danger" type="button" data-action="delete" data-kind="${kind}" data-id="${escapeAttr(row.id)}">${icon("trash")}删除</button>` : ""}
    </div>
  `;
}

function vehicleModelActions(row) {
  return `
    <div class="action-row">
      <button class="btn btn-sm" type="button" data-action="detail-vehicle" data-model-key="${escapeAttr(row.modelKey)}">${icon("eye")}详情</button>
      ${hasPermission("vehicle:write") ? `<button class="btn btn-sm btn-primary" type="button" data-action="model-inbound" data-model-key="${escapeAttr(row.modelKey)}">${icon("plus")}入库</button>` : ""}
      ${hasPermission("stock:adjust") ? `<button class="btn btn-sm" type="button" data-action="model-outbound" data-model-key="${escapeAttr(row.modelKey)}">${icon("minus")}整车出库</button>` : ""}
    </div>
  `;
}

function outboundOrderActions(row) {
  if (!hasPermission("stock:adjust")) return "";
  const uploadReady = isInvoiceUploadReady(row);
  const contractReady = isContractUploadReady(row);
  const nextLocked = !Boolean(row.isLocked);
  return `
    <div class="action-row">
      <button class="btn btn-sm" type="button" data-action="edit" data-kind="outboundOrder" data-id="${escapeAttr(row.id)}">${icon("edit")}编辑</button>
      ${canManageOrderLock() ? `<button class="btn btn-sm ${row.isLocked ? "" : "btn-danger"}" type="button" data-action="toggle-order-lock" data-id="${escapeAttr(row.id)}" data-locked="${escapeAttr(nextLocked)}">${icon(row.isLocked ? "unlock" : "lock")}${row.isLocked ? "解锁" : "锁定"}</button>` : ""}
      ${uploadReady ? `<button class="btn btn-sm" type="button" data-action="upload-invoice" data-id="${escapeAttr(row.id)}">${icon("upload")}上传发票</button>` : ""}
      ${row.invoiceFileAvailable ? `<button class="btn btn-sm" type="button" data-action="download-invoice" data-id="${escapeAttr(row.id)}">${icon("download")}下载发票</button>` : ""}
      ${contractReady ? `<button class="btn btn-sm" type="button" data-action="upload-contract" data-id="${escapeAttr(row.id)}">${icon("upload")}上传合同</button>` : ""}
      ${row.contractFileAvailable ? `<button class="btn btn-sm" type="button" data-action="download-contract" data-id="${escapeAttr(row.id)}">${icon("download")}下载合同</button>` : ""}
    </div>
  `;
}

function orderResourceSummary(row) {
  return `
    <div>
      <div>${escapeHtml(row.resourceCode || "-")}</div>
      <div class="helper-inline">${escapeHtml(row.resourceName || "-")}${row.specificationModel ? ` · ${escapeHtml(row.specificationModel)}` : ""}</div>
    </div>
  `;
}

function modificationOrderActions(row) {
  if (!hasPermission("replace:write")) return "";
  const closed = row.status === "COMPLETED" || row.status === "CANCELED";
  return `
    <div class="action-row">
      ${!closed ? `<button class="btn btn-sm btn-primary" type="button" data-action="complete-modification-order" data-id="${escapeAttr(row.id)}">${icon("swap")}完成</button>` : ""}
      ${!closed ? `<button class="btn btn-sm btn-danger" type="button" data-action="cancel-modification-order" data-id="${escapeAttr(row.id)}">${icon("trash")}取消</button>` : ""}
    </div>
  `;
}

function modificationLineSummary(lines = []) {
  if (!lines.length) return "-";
  return lines.map(line => `
    <div>${escapeHtml(line.itemName || "-")}：${escapeHtml(line.oldValue || "-")} -> ${escapeHtml(line.newValue || "-")}
      <span class="helper-inline">x${escapeHtml(line.quantity || 1)}</span>
      ${line.oldPartAction === "DISCOUNT" ? `<span class="helper-inline">${Number(line.priceDifference || 0) < 0 ? "折价收入" : "折价支出"} ${escapeHtml(money(Math.abs(Number(line.priceDifference || 0))))}</span>` : ""}
    </div>
  `).join("");
}

function supplierSummary(row = {}) {
  return `
    <div class="cell-stack">
      <strong>${escapeHtml(row.supplierName || "-")}</strong>
      ${row.address ? `<span class="helper-inline">${escapeHtml(row.address)}</span>` : ""}
    </div>
  `;
}

function renderBatchToolbar(batch, rows) {
  const selected = selectedRows(batch.kind);
  if (!selected.length) return "";
  return `
    <div class="batch-toolbar">
      <div class="batch-meta">已选择 ${escapeHtml(selected.length)} 条</div>
      <div class="batch-actions">
        <button class="btn btn-sm" type="button" data-action="batch-export" data-batch-kind="${escapeAttr(batch.kind)}">${icon("download")}导出所选</button>
        ${(batch.actions || []).map(action => `
          <button class="btn btn-sm${action.danger ? " btn-danger" : ""}" type="button" data-action="${escapeAttr(action.action)}" data-batch-kind="${escapeAttr(batch.kind)}">${icon(action.icon || "swap")}${escapeHtml(action.label)}</button>
        `).join("")}
        <button class="btn btn-sm btn-ghost" type="button" data-action="clear-batch-selection" data-batch-kind="${escapeAttr(batch.kind)}">清空选择</button>
      </div>
    </div>
  `;
}

function selectedIdSet(kind) {
  return new Set((state.batchSelections?.[kind] || []).map(String));
}

function selectedRows(kind) {
  const ids = selectedIdSet(kind);
  return entityRows(kind).filter(row => ids.has(String(row.id)));
}

function toggleBatchSelection(kind, id, selected) {
  if (!kind || !id) return;
  const ids = selectedIdSet(kind);
  if (selected) {
    ids.add(String(id));
  } else {
    ids.delete(String(id));
  }
  state.batchSelections = {
    ...(state.batchSelections || {}),
    [kind]: [...ids]
  };
}

function toggleVisibleBatchSelection(kind, visibleIds, selected) {
  if (!kind) return;
  const ids = selectedIdSet(kind);
  visibleIds.forEach(id => {
    if (selected) ids.add(String(id));
    else ids.delete(String(id));
  });
  state.batchSelections = {
    ...(state.batchSelections || {}),
    [kind]: [...ids]
  };
}

function clearBatchSelection(kind) {
  if (!kind) return;
  state.batchSelections = {
    ...(state.batchSelections || {}),
    [kind]: []
  };
}

function batchActionsForKind(kind) {
  if (kind === "repair" && hasPermission("repair:write")) {
    return [{ action: "batch-complete-repairs", label: "标记完成", icon: "swap" }];
  }
  if (kind === "purchaseOrder" && hasPermission("stock:adjust")) {
    return [{ action: "batch-receive-purchases", label: "标记收货", icon: "download" }];
  }
  if (kind === "stocktaking" && hasPermission("stock:adjust")) {
    return [
      { action: "batch-complete-stocktakes", label: "批量入账", icon: "swap" },
      { action: "batch-delete-stocktake-drafts", label: "删除草稿", icon: "trash", danger: true }
    ];
  }
  return [];
}

function exportSelectedRows(kind) {
  const rows = selectedRows(kind);
  if (!rows.length) {
    showToast("请先选择要导出的数据", "info");
    return;
  }
  const columns = exportColumnsForKind(kind);
  const csv = "\uFEFF" + [
    columns.map(column => csvCell(column.label)).join(","),
    ...rows.map(row => columns.map(column => csvCell(resolveExportValue(row, column))).join(","))
  ].join("\r\n");
  const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `${entityLabel(kind)}-所选-${todayInputDate()}.csv`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
  showToast("所选数据已导出", "success");
}

function csvCell(value) {
  return `"${String(display(value)).replaceAll('"', '""')}"`;
}

function resolveExportValue(row, column) {
  const value = typeof column.value === "function" ? column.value(row) : row[column.key];
  return column.formatter ? column.formatter(value, row) : value;
}

function exportColumnsForKind(kind) {
  const common = {
    part: [
      { label: "编码", key: "partCode" },
      { label: "名称", key: "partName" },
      { label: "分类", key: "partCategory" },
      { label: "数量", value: row => `${row.quantity ?? 0}${row.unit || ""}` },
      { label: "销售价", key: "salePrice", formatter: money }
    ],
    outboundOrder: [
      { label: "订单号", key: "orderNo" },
      { label: "出库项", value: row => `${row.resourceCode || ""} ${row.resourceName || ""}`.trim() },
      { label: "客户", key: "customerName" },
      { label: "销售日期", key: "salesDate", formatter: dateValue },
      { label: "应收", value: row => row.receivableAmount ?? row.settlementPrice, formatter: money }
    ],
    rental: [
      { label: "租赁单", key: "rentalNo" },
      { label: "车号", key: "vehicleNumber" },
      { label: "客户", key: "customerName" },
      { label: "去向", key: "destination" },
      { label: "状态", key: "status" }
    ],
    customer: [
      { label: "公司名称", key: "companyName" },
      { label: "联系人", key: "contactName" },
      { label: "电话", key: "contactPhone" },
      { label: "税号/身份证号", key: "taxOrIdNumber" }
    ],
    supplier: [
      { label: "供应商", key: "supplierName" },
      { label: "类型", key: "supplierType" },
      { label: "联系人", key: "contactName" },
      { label: "电话", key: "contactPhone" },
      { label: "税号", key: "taxNumber" }
    ],
    purchaseOrder: [
      { label: "采购单", key: "purchaseNo" },
      { label: "供应商", key: "supplierName" },
      { label: "内容", value: row => `${row.resourceCode || ""} ${row.resourceName || ""}`.trim() },
      { label: "数量", value: row => `${row.quantity ?? 0}${row.unit || ""}` },
      { label: "金额", key: "totalAmount", formatter: money },
      { label: "状态", key: "status" }
    ],
    stocktaking: [
      { label: "盘点单", key: "stocktakingNo" },
      { label: "对象", value: row => `${row.resourceCode || ""} ${row.resourceName || ""}`.trim() },
      { label: "账面", key: "bookQuantity" },
      { label: "实盘", key: "actualQuantity" },
      { label: "差异", key: "differenceQuantity" },
      { label: "状态", key: "status" }
    ],
    warehouse: [
      { label: "仓库编码", key: "warehouseCode" },
      { label: "仓库名称", key: "warehouseName" },
      { label: "联系人", key: "contactPerson" },
      { label: "联系电话", key: "contactPhone" },
      { label: "状态", value: row => row.active ? "启用" : "停用" }
    ],
    stockMovement: [
      { label: "流水号", key: "movementNo" },
      { label: "类型", key: "movementType", formatter: movementTypeText },
      { label: "资源", value: stockMovementResource },
      { label: "仓库", value: row => `${row.fromWarehouseName || ""}${row.toWarehouseName ? ` -> ${row.toWarehouseName}` : ""}`.trim() },
      { label: "数量", value: stockMovementDelta },
      { label: "时间", key: "createdAt", formatter: dateTime }
    ],
    repair: [
      { label: "维修时间", key: "repairDate", formatter: dateTime },
      { label: "车号", key: "vehicleNumber" },
      { label: "客户", key: "customerName" },
      { label: "维修人", key: "repairPerson" },
      { label: "费用", key: "totalFee", formatter: money },
      { label: "状态", key: "status" }
    ],
    modificationOrder: [
      { label: "工单号", key: "workOrderNo" },
      { label: "车辆", key: "machineProductNumber" },
      { label: "客户", key: "customerName" },
      { label: "状态", key: "status" }
    ]
  };
  return common[kind] || [{ label: "ID", key: "id" }];
}

function purchaseOrderSummary(row = {}) {
  return `
    <div class="cell-stack">
      <strong>${escapeHtml(row.purchaseNo || "-")}</strong>
      <span class="helper-inline">${escapeHtml(dateValue(row.orderDate) || "-")}${row.expectedArrivalDate ? ` / 预计 ${escapeHtml(dateValue(row.expectedArrivalDate))}` : ""}</span>
    </div>
  `;
}

function purchaseResourceSummary(row = {}) {
  return `
    <div class="cell-stack">
      <strong>${escapeHtml(row.resourceCode || row.resourceName || "-")}</strong>
      <span class="helper-inline">${escapeHtml(purchaseResourceLabel(row.resourceType))}${row.resourceName && row.resourceCode ? ` / ${escapeHtml(row.resourceName)}` : ""}${row.specificationModel ? ` / ${escapeHtml(row.specificationModel)}` : ""}</span>
    </div>
  `;
}

function stocktakingSummary(row = {}) {
  return `
    <div class="cell-stack">
      <strong>${escapeHtml(row.stocktakingNo || "-")}</strong>
      <span class="helper-inline">${escapeHtml(dateValue(row.stocktakingDate) || "-")}${row.operator ? ` / ${escapeHtml(row.operator)}` : ""}</span>
    </div>
  `;
}

function stocktakingDifference(row = {}) {
  const value = Number(row.differenceQuantity || 0);
  return badge(value > 0 ? `+${value}` : String(value), value === 0 ? "teal" : value > 0 ? "warn" : "danger");
}

function stocktakingActions(row = {}) {
  if (!hasPermission("stock:adjust")) return "";
  const completed = row.status === "COMPLETED";
  return `
    <div class="action-row">
      ${!completed ? `<button class="btn btn-sm btn-primary" type="button" data-action="complete-stocktaking" data-id="${escapeAttr(row.id)}">${icon("swap")}入账</button>` : ""}
      ${!completed ? `<button class="btn btn-sm" type="button" data-action="edit" data-kind="stocktaking" data-id="${escapeAttr(row.id)}">${icon("edit")}编辑</button>` : ""}
      ${!completed ? `<button class="btn btn-sm btn-danger" type="button" data-action="delete" data-kind="stocktaking" data-id="${escapeAttr(row.id)}">${icon("trash")}删除</button>` : ""}
    </div>
  `;
}

function purchaseStatusBadge(status) {
  const map = {
    ORDERED: ["已下单", "primary"],
    PARTIAL: ["部分到货", "warn"],
    ARRIVED: ["已到货", "teal"],
    RECEIVED: ["已收货", "teal"],
    CANCELED: ["已取消", "danger"]
  };
  const [label, type] = map[status] || [status || "未设置", "primary"];
  return badge(label, type);
}

function purchaseStatusControl(row = {}) {
  const received = row.status === "RECEIVED";
  if (!hasPermission("stock:adjust")) {
    return purchaseStatusBadge(row.status);
  }
  return `
    <div class="cell-stack">
      <button class="status-toggle ${received ? "teal" : "primary"}" type="button" data-action="toggle-purchase-received" data-id="${escapeAttr(row.id)}" aria-pressed="${received ? "true" : "false"}" title="点击切换收货状态">${escapeHtml(received ? "已收货" : "待收货")}</button>
      ${received ? `<span class="helper-inline">运费 ${escapeHtml(money(row.freightAmount ?? 0))}</span>` : ""}
      ${received ? `<button class="btn btn-sm" type="button" data-action="purchase-freight" data-id="${escapeAttr(row.id)}">${icon("edit")}修改运费</button>` : ""}
    </div>
  `;
}
function stocktakingStatusBadge(status) {
  const map = {
    DRAFT: ["草稿", "primary"],
    COMPLETED: ["已入账", "teal"]
  };
  const [label, type] = map[status] || [status || "未设置", "primary"];
  return badge(label, type);
}

function purchaseResourceLabel(value) {
  const labels = {
    MACHINE: "整车",
    PART: "配件"
  };
  return labels[value] || value || "-";
}

function uniqueSupplierCount() {
  return new Set((state.data.purchaseOrders || [])
    .map(row => row.supplierId || row.supplierName)
    .filter(Boolean)).size;
}

function jobTagControl(row = {}) {
  const tag = normalizeJobTag(row.jobTag, row.roles);
  if (!canUpdateUserJobTag(row)) {
    return jobTagBadge(tag);
  }
  const next = nextJobTag(tag);
  return `<button class="status-toggle ${escapeAttr(jobTagType(tag))}" type="button" data-action="toggle-user-job-tag" data-id="${escapeAttr(row.id)}" title="点击切换为${escapeAttr(jobTagLabel(next))}">${escapeHtml(jobTagLabel(tag))}</button>`;
}

function userEnabledControl(row = {}) {
  const enabled = Boolean(row.enabled);
  if (!canUpdateUserEnabled(row)) {
    return badge(enabled ? "启用" : "停用", enabled ? "teal" : "danger");
  }
  return `<button class="status-toggle ${enabled ? "teal" : "danger"}" type="button" data-action="toggle-user-enabled" data-id="${escapeAttr(row.id)}" aria-pressed="${enabled ? "true" : "false"}" title="点击切换启用状态">${escapeHtml(enabled ? "启用" : "停用")}</button>`;
}

function userActions(row) {
  if (!hasPermission("user:admin") || row.roles?.includes("SUPER_ADMIN")) return "";
  return `
    <div class="action-row">
      <button class="btn btn-sm" type="button" data-action="user-username" data-id="${escapeAttr(row.id)}">${icon("edit")}改用户名</button>
      <button class="btn btn-sm" type="button" data-action="user-password" data-id="${escapeAttr(row.id)}">${icon("swap")}改密码</button>
      <button class="btn btn-sm btn-danger" type="button" data-action="delete" data-kind="user" data-id="${escapeAttr(row.id)}">${icon("trash")}删除</button>
    </div>
  `;
}

function renderModalFields(modalFields, item) {
  let activeSection = null;
  return modalFields.map(field => {
    const section = field.type === "hidden" ? activeSection : (field.section || "");
    const sectionTitle = section && section !== activeSection
      ? `<div class="form-section-title">${escapeHtml(section)}</div>`
      : "";
    if (field.type !== "hidden") {
      activeSection = section;
    }
    return `${sectionTitle}${renderField(field, item)}`;
  }).join("");
}

function renderField(field, data) {
  const rawValue = Object.prototype.hasOwnProperty.call(data || {}, field.name) ? data[field.name] : "";
  const prefillValue = fieldPrefillValue(field, data);
  const value = toInputValue(rawValue, field);
  const span = field.span ? ` data-span="${field.span}"` : "";
  const required = field.required && prefillValue === undefined ? " required" : "";
  const coerce = field.coerce || field.type || "string";

  if (field.type === "hidden") {
    return `<input name="${escapeAttr(field.name)}" type="hidden" data-coerce="${escapeAttr(coerce)}" value="${escapeAttr(value)}"${renderPrefillAttrs(field, prefillValue)}>`;
  }

  if (field.type === "checkbox") {
    return `
      <label class="field checkbox-field"${span}>
        <input name="${escapeAttr(field.name)}" type="checkbox" data-coerce="${escapeAttr(coerce)}"${rawValue ? " checked" : ""}>
        <span>${escapeHtml(field.label)}</span>
      </label>
    `;
  }

  if (field.type === "select") {
    const options = resolveOptions(field);
    const selected = findOptionByValue(options, value);
    const displayValue = selected ? selected.label : (field.allowCustom ? value : "");
    const hiddenValue = selected ? selected.value : (field.allowCustom ? value : "");
    const placeholder = fieldPlaceholder(field, options, data);
    const prefillAttrs = renderPrefillAttrs(field, prefillValue, options);
    const remoteSource = remoteComboSourceForField(state.modal?.kind, field.name);
    const remoteAttrs = remoteSource ? ` data-remote-source="${escapeAttr(remoteSource)}"` : "";
    return `
      <label class="field"${span}>
        <span>${escapeHtml(field.label)}</span>
        <div class="combo${options.length ? "" : " is-empty"}" data-combo data-name="${escapeAttr(field.name)}" data-allow-custom="${field.allowCustom ? "true" : "false"}" data-required="${field.required ? "true" : "false"}"${remoteAttrs}>
          <input class="combo-input" data-combo-input type="text" value="${escapeAttr(displayValue)}" placeholder="${escapeAttr(placeholder)}" autocomplete="off"${required}>
          <button class="combo-toggle" type="button" data-combo-toggle aria-label="展开选项"></button>
          <input name="${escapeAttr(field.name)}" type="hidden" data-coerce="${escapeAttr(coerce)}" value="${escapeAttr(hiddenValue)}" data-selected-label="${escapeAttr(displayValue)}" data-selected-meta="${selected?.meta ? escapeAttr(JSON.stringify(selected.meta)) : ""}"${prefillAttrs}>
          <div class="combo-menu" data-combo-menu>
            ${renderComboOptions(options, hiddenValue)}
          </div>
        </div>
      </label>
    `;
  }

  if (field.type === "textarea") {
    return `
      <label class="field"${span}>
        <span>${escapeHtml(field.label)}</span>
        <textarea name="${escapeAttr(field.name)}" data-coerce="${escapeAttr(coerce)}" placeholder="${escapeAttr(fieldPlaceholder(field, [], data))}"${renderPrefillAttrs(field, prefillValue)}${required}>${escapeHtml(value)}</textarea>
      </label>
    `;
  }

  if (field.type === "toggle") {
    const options = resolveOptions(field);
    const selectedValue = value || prefillValue || field.defaultValue || options[0]?.value || "";
    return `
      <label class="field"${span}>
        <span>${escapeHtml(field.label)}</span>
        <div class="status-toggle-group" data-toggle-group="${escapeAttr(field.name)}">
          ${options.map(option => {
            const active = String(option.value) === String(selectedValue);
            return `<button class="status-toggle ${active ? "teal" : "primary"}" type="button" data-toggle-option data-name="${escapeAttr(field.name)}" data-value="${escapeAttr(option.value)}" aria-pressed="${active ? "true" : "false"}">${escapeHtml(option.label)}</button>`;
          }).join("")}
          <input name="${escapeAttr(field.name)}" type="hidden" data-coerce="${escapeAttr(coerce)}" value="${escapeAttr(selectedValue)}"${renderPrefillAttrs(field, prefillValue)}>
        </div>
      </label>
    `;
  }

  if (field.type === "file") {
    return `
      <label class="field"${span}>
        <span>${escapeHtml(field.label)}</span>
        <input name="${escapeAttr(field.name)}" type="file"${field.accept ? ` accept="${escapeAttr(field.accept)}"` : ""}${required}>
      </label>
    `;
  }

  return `
    <label class="field"${span}>
      <span>${escapeHtml(field.label)}</span>
      <input name="${escapeAttr(field.name)}" type="${escapeAttr(field.type || "text")}" data-coerce="${escapeAttr(coerce)}" value="${escapeAttr(value)}" placeholder="${escapeAttr(fieldPlaceholder(field, [], data))}"${renderPrefillAttrs(field, prefillValue)}${field.step ? ` step="${escapeAttr(field.step)}"` : ""}${field.readOnly ? " readonly" : ""}${required}>
    </label>
  `;
}

function renderConfigSelectionEditor(item) {
  const rows = ensureConfigSelections(item);
  return `
    <section class="config-selection-editor">
      <div class="config-editor-head">
        <div class="config-editor-copy">
          <strong>结构化配置（选填）</strong>
          <span class="helper-inline">入库时可先只填“配置”文本；需要拆解到配置字典时再补充这里。</span>
        </div>
        <button class="btn btn-sm" type="button" data-config-action="add">${icon("plus")}添加配置</button>
      </div>
      <div class="config-selection-list">
        ${rows.map((row, index) => renderConfigSelectionRow(row, index)).join("")}
      </div>
    </section>
  `;
}

function renderConfigSelectionRow(row, index) {
  const itemOptions = configItemOptions();
  const valueOptions = row.configItemId ? configValueOptionsForItem(row.configItemId) : [];
  return `
    <div class="config-selection-row" data-config-index="${index}">
      ${renderConfigCombo("配置类型", `configItemId-${index}`, itemOptions, row.configItemId, "configItemId", index)}
      ${renderConfigCombo("具体配置", `configValueId-${index}`, valueOptions, row.configValueId, "configValueId", index)}
      <button class="btn btn-sm btn-danger" type="button" data-config-action="remove" data-config-index="${index}">${icon("trash")}移除</button>
    </div>
  `;
}

function renderConfigCombo(label, name, options, selectedValue, fieldName, index) {
  const selected = findOptionByValue(options, selectedValue);
  const displayValue = selected?.label || "";
  const hiddenValue = selected?.value || "";
  return `
    <label class="field">
      <span>${escapeHtml(label)}</span>
      <div class="combo${options.length ? "" : " is-empty"}" data-combo data-name="${escapeAttr(name)}" data-required="false">
        <input class="combo-input" data-combo-input type="text" value="${escapeAttr(displayValue)}" placeholder="${escapeAttr(options.length ? "请选择或输入筛选" : "请先选择配置类型")}" autocomplete="off">
        <button class="combo-toggle" type="button" data-combo-toggle aria-label="展开选项"></button>
        <input name="${escapeAttr(name)}" type="hidden" data-config-field="${escapeAttr(fieldName)}" data-config-index="${index}" value="${escapeAttr(hiddenValue)}" data-selected-label="${escapeAttr(displayValue)}">
        <div class="combo-menu" data-combo-menu>
          ${renderComboOptions(options, hiddenValue)}
        </div>
      </div>
    </label>
  `;
}

function renderOptions(options, selectedValue) {
  const optionList = [{ label: "请选择", value: "" }, ...options];
  return optionList.map(option => {
    const selected = String(option.value) === String(selectedValue ?? "") ? " selected" : "";
    return `<option value="${escapeAttr(option.value)}"${selected}>${escapeHtml(option.label)}</option>`;
  }).join("");
}

function renderComboOptions(options, selectedValue) {
  if (!options.length) {
    return `<div class="combo-empty">暂无可选项</div>`;
  }
  return options.map(option => {
    const selected = String(option.value) === String(selectedValue ?? "") ? " is-selected" : "";
    const meta = option.meta ? JSON.stringify(option.meta) : "";
    return `
      <button class="combo-option${selected}" type="button" data-combo-option data-value="${escapeAttr(option.value)}" data-label="${escapeAttr(option.label)}" data-meta="${escapeAttr(meta)}">
        ${escapeHtml(option.label)}
      </button>
    `;
  }).join("") + `<div class="combo-empty">无匹配选项</div>`;
}

function findOptionByValue(options, value) {
  return (options || []).find(option => String(option.value) === String(value ?? ""));
}

function findComboOption(combo, query) {
  const normalized = normalizeText(query);
  if (!normalized) return null;
  return [...combo.querySelectorAll("[data-combo-option]")]
    .find(option => normalizeText(option.dataset.label) === normalized || normalizeText(option.dataset.value) === normalized);
}

function selectComboOption(option) {
  const combo = option.closest("[data-combo]");
  setComboValue(combo, option.dataset.value, option.dataset.label, option.dataset.meta, true);
  closeAllCombos();
}

function setToggleFieldValue(button) {
  const group = button.closest("[data-toggle-group]");
  const hidden = group?.querySelector(`input[name="${button.dataset.name}"]`);
  if (!group || !hidden) return;
  hidden.value = button.dataset.value || "";
  group.querySelectorAll("[data-toggle-option]").forEach(option => {
    const active = option === button;
    option.classList.toggle("teal", active);
    option.classList.toggle("primary", !active);
    option.setAttribute("aria-pressed", active ? "true" : "false");
  });
  hidden.dispatchEvent(new Event("change", { bubbles: true }));
}

function setComboValue(combo, value, label, meta, shouldNotify) {
  const input = combo.querySelector("[data-combo-input]");
  const hidden = combo.querySelector("input[type='hidden']");
  const previous = hidden.value;
  input.value = label || "";
  delete input.dataset.userEdited;
  hidden.value = value || "";
  hidden.dataset.selectedLabel = label || "";
  hidden.dataset.selectedMeta = meta || "";
  combo.querySelectorAll("[data-combo-option]").forEach(option => {
    option.classList.toggle("is-selected", String(option.dataset.value) === String(hidden.value));
    option.hidden = false;
  });
  if (shouldNotify || previous !== hidden.value) {
    hidden.dispatchEvent(new Event("change", { bubbles: true }));
  }
}

function filterComboOptions(combo, query) {
  const normalized = normalizeText(query);
  let visibleCount = 0;
  combo.querySelectorAll("[data-combo-option]").forEach(option => {
    const haystack = normalizeText(`${option.dataset.label || ""} ${option.dataset.value || ""}`);
    const visible = !normalized || haystack.includes(normalized);
    option.hidden = !visible;
    if (visible) visibleCount += 1;
  });
  combo.classList.toggle("is-empty", visibleCount === 0);
}

function openCombo(combo, query = "") {
  if (!combo) return;
  closeAllCombos(combo);
  filterComboOptions(combo, query);
  combo.classList.add("is-open");
}

function toggleCombo(combo) {
  if (!combo) return;
  if (combo.classList.contains("is-open")) {
    combo.classList.remove("is-open");
    return;
  }
  const input = combo.querySelector("[data-combo-input]");
  openCombo(combo, input?.value.trim() || "");
  scheduleRemoteComboSearch(combo, input?.value.trim() || "", { immediate: combo.dataset.remoteLoaded !== "true" });
  input?.focus();
}

function closeAllCombos(except = null) {
  els.modalCard.querySelectorAll("[data-combo].is-open").forEach(combo => {
    if (combo !== except) combo.classList.remove("is-open");
  });
}

function syncComboOptionsForField(form, name, options, selectedValue) {
  const combo = form.querySelector(`[data-combo][data-name="${name}"]`);
  if (!combo) return;
  const menu = combo.querySelector("[data-combo-menu]");
  const hidden = combo.querySelector(`input[name="${name}"]`);
  const input = combo.querySelector("[data-combo-input]");
  const selected = findOptionByValue(options, selectedValue);
  menu.innerHTML = renderComboOptions(options, selected?.value ?? "");
  hidden.value = selected?.value ?? "";
  hidden.dataset.selectedLabel = selected?.label ?? "";
  hidden.dataset.selectedMeta = selected?.meta ? JSON.stringify(selected.meta) : "";
  input.value = selected?.label ?? "";
  filterComboOptions(combo, "");
}

function remoteComboSourceForField(kind, fieldName) {
  if (!kind || !fieldName) return "";
  if (fieldName === "customerId" && ["vehicleOutbound", "partStock", "partOutbound", "rental", "repair"].includes(kind)) return "customers";
  if (fieldName === "supplierId" && kind === "purchaseOrder") return "suppliers";
  if (["fromWarehouseId", "toWarehouseId"].includes(fieldName) && kind === "stockTransfer") return "warehouses";
  if (fieldName === "partCode" && ["partStock", "partOutbound"].includes(kind)) return "partCodes";
  if (fieldName === "usedPartIds" && kind === "repair") return "repairParts";
  if (fieldName === "machineId") {
    if (kind === "vehicleOutbound") return "vehicleOutbound";
    if (kind === "rental") return "vehicleRental";
    if (["vehicleStock", "partReplace", "vehiclePartInstall", "repair"].includes(kind)) return "vehicles";
  }
  if (fieldName === "resourceId" && kind === "stocktaking") {
    return resourceTypeForModal() === "MACHINE" ? "stocktakingVehicles" : "stocktakingParts";
  }
  if (fieldName === "resourceId" && kind === "stockTransfer") {
    return resourceTypeForModal() === "MACHINE" ? "stockTransferVehicles" : "stockTransferParts";
  }
  if (fieldName === "newPartId" && kind === "vehiclePartInstall") return "installParts";
  return "";
}

function resourceTypeForModal() {
  return String(effectiveFieldValue(state.modal?.item || {}, "resourceType") || "PART").toUpperCase();
}

function scheduleRemoteComboSearch(combo, query = "", options = {}) {
  if (!combo?.dataset.remoteSource) return;
  window.clearTimeout(remoteComboSearchTimer);
  const run = () => searchRemoteComboOptions(combo, query);
  if (options.immediate) {
    run();
    return;
  }
  remoteComboSearchTimer = window.setTimeout(run, REMOTE_COMBO_DEBOUNCE_MS);
}

async function searchRemoteComboOptions(combo, query = "") {
  const source = combo?.dataset.remoteSource;
  if (!source || !combo.isConnected) return;
  const requestId = String(++remoteComboRequestSequence);
  combo.dataset.remoteRequestId = requestId;
  setComboRemoteStatus(combo, "loading");
  try {
    const options = await fetchRemoteComboOptions(source, query);
    if (!combo.isConnected || combo.dataset.remoteRequestId !== requestId) return;
    updateComboMenuOptions(combo, options, query);
    combo.dataset.remoteLoaded = "true";
    combo.dataset.remoteQuery = query;
  } catch (error) {
    if (!combo.isConnected || combo.dataset.remoteRequestId !== requestId) return;
    console.warn("Remote combo search failed", error);
    setComboRemoteStatus(combo, "error");
  }
}

function setComboRemoteStatus(combo, status) {
  const menu = combo.querySelector("[data-combo-menu]");
  if (!menu) return;
  const text = status === "error" ? "搜索失败，请重试" : "正在搜索...";
  menu.innerHTML = `<div class="combo-empty">${escapeHtml(text)}</div>`;
  combo.classList.add("is-open", "is-empty");
}

function updateComboMenuOptions(combo, options, query = "") {
  const menu = combo.querySelector("[data-combo-menu]");
  const hidden = combo.querySelector("input[type='hidden']");
  const input = combo.querySelector("[data-combo-input]");
  if (!menu || !hidden) return;
  menu.innerHTML = renderComboOptions(options, hidden.value);
  filterComboOptions(combo, query);
  combo.classList.add("is-open");
  const typed = String(input?.value || "").trim();
  if (typed && !hidden.value) {
    const exact = findComboOption(combo, typed);
    if (exact) {
      setComboValue(combo, exact.dataset.value, exact.dataset.label, exact.dataset.meta, false);
    }
  }
}

async function fetchRemoteComboOptions(source, query = "") {
  const meta = remoteComboMeta(source);
  if (!meta) return [];
  const payload = await api(referencePageUrl(meta.endpoint, query, REMOTE_COMBO_PAGE_SIZE));
  const rows = rowsFromPayload(payload);
  mergeReferenceRows(meta.dataKey, rows);
  return optionsForRemoteComboSource(source);
}

function remoteComboMeta(source) {
  if (["vehicles", "vehicleOutbound", "vehicleRental", "stocktakingVehicles", "stockTransferVehicles"].includes(source)) {
    return { endpoint: endpoints.vehicle.list, dataKey: "vehicles" };
  }
  if (["partCodes", "repairParts", "stocktakingParts", "stockTransferParts", "installParts"].includes(source)) {
    return { endpoint: endpoints.part.list, dataKey: "parts" };
  }
  if (source === "customers") return { endpoint: endpoints.customer.list, dataKey: "customers" };
  if (source === "suppliers") return { endpoint: endpoints.supplier.list, dataKey: "suppliers" };
  if (source === "warehouses") return { endpoint: endpoints.warehouse.list, dataKey: "warehouses" };
  return null;
}

function mergeReferenceRows(dataKey, rows = []) {
  if (!dataKey || !Array.isArray(rows) || !rows.length) return;
  const current = Array.isArray(state.data[dataKey]) ? state.data[dataKey] : [];
  const byId = new Map(current.map(row => [String(row.id ?? ""), row]));
  for (const row of rows) {
    if (row?.id === undefined || row?.id === null) continue;
    byId.set(String(row.id), { ...(byId.get(String(row.id)) || {}), ...row });
  }
  const asc = ["customers", "suppliers", "warehouses"].includes(dataKey);
  state.data[dataKey] = sortById([...byId.values()], !asc);
}

function optionsForRemoteComboSource(source) {
  return {
    vehicles: vehicleOptions,
    vehicleOutbound: vehicleOutboundOptions,
    vehicleRental: vehicleRentalOptions,
    stocktakingVehicles: stocktakingResourceOptions,
    stocktakingParts: stocktakingResourceOptions,
    stockTransferVehicles: stockTransferResourceOptions,
    stockTransferParts: stockTransferResourceOptions,
    partCodes: partCodeOptions,
    repairParts: repairPartOptions,
    installParts: installPartOptions,
    customers: customerOptions,
    suppliers: supplierOptions,
    warehouses: warehouseOptions
  }[source]?.() || [];
}

function validateCombos(form) {
  const invalid = [...form.querySelectorAll("[data-combo][data-required='true']")]
    .find(combo => {
      const hidden = combo.querySelector("input[type='hidden']");
      return !comboHasSubmittableValue(combo, hidden);
    });
  if (!invalid) return true;
  const input = invalid.querySelector("[data-combo-input]");
  invalid.classList.add("is-open");
  input.focus();
  showToast("请从下拉列表中选择有效选项", "error");
  return false;
}

function comboHasSubmittableValue(combo, hidden = combo?.querySelector("input[type='hidden']")) {
  if (!combo || !hidden) return false;
  if (String(hidden.value || "").trim()) return true;
  const input = combo.querySelector("[data-combo-input]");
  const typedValue = String(input?.value || "").trim();
  const prefillLabel = String(hidden.dataset.prefillLabel || "").trim();
  return hidden.dataset.prefillValue !== undefined && (typedValue === "" || typedValue === prefillLabel);
}

function resolveOptions(field) {
  if (typeof field.options === "function") return field.options();
  return field.options || [];
}

function renderPrefillAttrs(field, value, options = []) {
  if (value === undefined || value === null || value === "") return "";
  const inputValue = toInputValue(value, field);
  const option = findOptionByValue(options, inputValue);
  const label = option?.label ?? inputValue;
  return ` data-prefill-value="${escapeAttr(inputValue)}" data-prefill-label="${escapeAttr(label)}"`;
}

function fieldPrefillValue(field, data = {}) {
  if (Object.prototype.hasOwnProperty.call(data?.__prefillValues || {}, field.name)) {
    return data.__prefillValues[field.name];
  }
  if (field.placeholderValue !== undefined) {
    return typeof field.placeholderValue === "function" ? field.placeholderValue() : field.placeholderValue;
  }
  if (field.defaultValue !== undefined) {
    return typeof field.defaultValue === "function" ? field.defaultValue() : field.defaultValue;
  }
  return undefined;
}

function fieldPlaceholder(field, options = [], data = {}) {
  const placeholder = data?.__placeholders?.[field.name];
  if (placeholder) return placeholder;
  if (field.placeholder) return typeof field.placeholder === "function" ? field.placeholder() : field.placeholder;
  const placeholderValue = fieldPrefillValue(field, data);
  if (placeholderValue !== undefined) {
    const option = findOptionByValue(options, placeholderValue);
    return `默认：${option?.label ?? placeholderValue}`;
  }
  return field.type === "select" ? "请选择或输入筛选" : "";
}

function serializeForm(kind, form) {
  const payload = {};
  for (const field of getFields(kind)) {
    const input = form.elements[field.name];
    if (!input) continue;
    const coerce = input.dataset.coerce || "string";
    let raw = input.type === "checkbox" ? input.checked : String(input.value || "").trim();
    const combo = input.closest?.("[data-combo]");
    const comboTypedValue = combo ? String(combo.querySelector("[data-combo-input]")?.value || "").trim() : "";
    const prefillLabel = String(input.dataset.prefillLabel || "").trim();
    if (raw === "" && input.dataset.prefillValue !== undefined && (!combo || comboTypedValue === "" || comboTypedValue === prefillLabel)) {
      raw = input.dataset.prefillValue;
    }
    const value = coerceValue(raw, coerce, input.type);
    if (value !== null || field.required || input.type === "checkbox") {
      payload[field.name] = value;
    }
  }
  if (kind === "user") {
    payload.roles = payload.role ? [payload.role] : ["USER"];
    delete payload.role;
  }
  return payload;
}

function coerceValue(value, coerce, inputType) {
  if (inputType === "checkbox") return Boolean(value);
  if (value === "") return null;
  if (coerce === "int") return Number.parseInt(value, 10);
  if (coerce === "intList") return String(value).split(",").map(item => Number.parseInt(item.trim(), 10)).filter(Number.isFinite);
  if (coerce === "datetime") return value.length === 16 ? `${value}:00` : value;
  if (coerce === "decimal") return value;
  return value;
}

function defaultEntity(kind) {
  if (kind === "vehicleOutbound") {
    return vehicleOutboundDefaultsForMachine();
  }
  if (kind === "rental") {
    return rentalDefaultsForMachine();
  }
  if (kind === "repair") {
    return repairDefaults();
  }
  if (kind === "purchaseOrder") {
    return { resourceType: "PART", quantity: 1, unit: "件", status: "ORDERED", orderDate: todayInputDate() };
  }
  if (kind === "stocktaking") {
    return { resourceType: "PART", actualQuantity: 0, status: "DRAFT", stocktakingDate: todayInputDate() };
  }
  if (kind === "warehouse") {
    return { warehouseType: "MAIN" };
  }
  if (kind === "stockTransfer") {
    return { resourceType: "PART", quantity: 1 };
  }
  if (kind === "dataRestore") {
    return { confirmation: "RESTORE-DATA-BACKUP" };
  }
  const entity = {};
  if (kind === "configValue" && state.selectedConfigItemId) {
    const selectedItem = state.data.configItems.find(item => Number(item.id) === Number(state.selectedConfigItemId));
    setPrefill(entity, "configItemId", state.selectedConfigItemId, selectedItem ? `预填：${configItemLabel(selectedItem)}` : undefined);
  }
  return entity;
}

function setPrefill(entity, name, value, placeholder) {
  if (value === undefined || value === null || value === "") return entity;
  entity.__prefillValues = {
    ...(entity.__prefillValues || {}),
    [name]: value
  };
  if (placeholder) {
    entity.__placeholders = {
      ...(entity.__placeholders || {}),
      [name]: placeholder
    };
  }
  return entity;
}

function clearPrefill(entity, name) {
  if (!entity) return entity;
  if (entity.__prefillValues) {
    delete entity.__prefillValues[name];
  }
  if (entity.__placeholders) {
    delete entity.__placeholders[name];
  }
  return entity;
}

function ensureConfigSelections(item = state.modal?.item || {}) {
  if (!Array.isArray(item.configSelections) || !item.configSelections.length) {
    item.configSelections = [{ configItemId: "", configValueId: "" }];
  }
  return item.configSelections;
}

function vehicleModelDefaults(powerType) {
  const entity = {};
  setPrefill(entity, "machineType", normalizePowerType(powerType), `预填：${normalizePowerType(powerType)}`);
  return entity;
}

function vehicleInboundDefaultsForModel(group = {}) {
  const entity = {
    __modelKey: group.modelKey || "",
    __modelIdentity: {
      name: group.name || "",
      specificationModel: group.specificationModel || "",
      machineType: group.machineType || ""
    },
    configSelections: [{ configItemId: "", configValueId: "" }],
    __placeholders: {
      vehicleProductNumber: "请输入此辆整车唯一车号",
      engineNumber: "请输入发动机号",
      frameNumber: "请输入车架号",
      warrantyCardNumber: "请输入保修卡号",
      applicationNumber: "可填写样机申请单号",
      materialNumber: "可填写物料号",
      settlementPrice: "请输入此辆整车结算价",
      remarks: "可记录仓储车季返、特殊说明或原表备注"
    }
  };
  setPrefill(entity, "name", group.name || "", group.name ? `预填：${group.name}` : undefined);
  setPrefill(entity, "specificationModel", group.specificationModel || "", group.specificationModel ? `预填：${group.specificationModel}` : undefined);
  setPrefill(entity, "configuration", group.configuration || "", group.configuration ? `预填：${group.configuration}` : undefined);
  setPrefill(entity, "machineType", group.machineType || "", group.machineType ? `预填：${group.machineType}` : undefined);
  setPrefill(entity, "supplier", group.supplier || "", group.supplier ? `预填：${group.supplier}` : undefined);
  setPrefill(entity, "warehouseName", group.warehouseName || "", group.warehouseName ? `预填：${group.warehouseName}` : undefined);
  setPrefill(entity, "purchasePrice", group.purchasePrice || "", group.purchasePrice ? `预填：${money(group.purchasePrice)}` : undefined);
  setPrefill(entity, "salePrice", group.salePrice || "", group.salePrice ? `预填：${money(group.salePrice)}` : undefined);
  if (isManualForklift(group.machineType)) {
    entity.__placeholders = {
      ...entity.__placeholders,
      name: group.name ? `预填：${group.name}` : "请输入车型名称",
      specificationModel: group.specificationModel ? `预填：${group.specificationModel}` : "请输入规格型号"
    };
  }
  return entity;
}

function vehicleOutboundDefaultsForMachine(machine = {}, modelKey = null) {
  const entity = {
    __modelKey: modelKey || (machine?.id ? vehicleModelKey(machine) : ""),
    customerMode: "existing",
    salesDate: todayInputDate(),
    paymentSettled: false,
    salesReported: false,
    invoiceApplied: false,
    __placeholders: {
      customerId: "请选择客户列表中的公司",
      paymentRemark: "例如：已收订金 / 尾款待结",
      invoiceStatus: "例如：含税已开票 / 不含税",
      registrationStatus: "例如：包上牌 / 已上牌",
      contractType: "例如：纸质合同 / 电子合同"
    }
  };
  if (machine?.id) {
    setPrefill(entity, "machineId", machine.id, `预填：${vehicleNumberLabel(machine)}`);
    const settlement = machine.settlementPrice || machine.salePrice || "";
    const salePrice = machine.salePrice || machine.settlementPrice || "";
    setPrefill(entity, "settlementPrice", settlement, settlement ? `预填：${money(settlement)}` : undefined);
    setPrefill(entity, "salePrice", salePrice, salePrice ? `预填：${money(salePrice)}` : undefined);
    setPrefill(entity, "receivableAmount", settlement, settlement ? `预填：${money(settlement)}` : undefined);
  } else {
    entity.__placeholders = {
      ...entity.__placeholders,
      machineId: "请选择准确出库车辆"
    };
  }
  return entity;
}

function vehicleOutboundDefaultsForModel(group = {}) {
  const availableVehicles = vehiclesForModelKey(group.modelKey).filter(canOutboundVehicle);
  if (availableVehicles.length === 1) {
    return vehicleOutboundDefaultsForMachine(availableVehicles[0], group.modelKey);
  }
  return {
    __modelKey: group.modelKey,
    customerMode: "existing",
    salesDate: todayInputDate(),
    paymentSettled: false,
    salesReported: false,
    invoiceApplied: false,
    __placeholders: {
      machineId: "请选择此车型库存车号",
      customerId: "请选择客户列表中的公司",
      paymentRemark: "例如：已收订金 / 尾款待结",
      invoiceStatus: "例如：含税已开票 / 不含税",
      registrationStatus: "例如：包上牌 / 已上牌",
      contractType: "例如：纸质合同 / 电子合同"
    }
  };
}

function syncVehicleOutboundDefaults(machineId) {
  const machine = findEntity("vehicle", Number(machineId || 0));
  clearPrefill(state.modal.item, "settlementPrice");
  clearPrefill(state.modal.item, "salePrice");
  clearPrefill(state.modal.item, "receivableAmount");
  const settlement = machine.settlementPrice || machine.salePrice || "";
  const salePrice = machine.salePrice || machine.settlementPrice || "";
  setPrefill(state.modal.item, "settlementPrice", settlement, settlement ? `预填：${money(settlement)}` : undefined);
  setPrefill(state.modal.item, "salePrice", salePrice, salePrice ? `预填：${money(salePrice)}` : undefined);
  setPrefill(state.modal.item, "receivableAmount", settlement, settlement ? `预填：${money(settlement)}` : undefined);
}

function syncPartOutboundDefaults(partCode) {
  const part = state.data.parts.find(item => String(item.partCode) === String(partCode)) || {};
  if (part.version !== undefined) {
    state.modal.item.version = part.version;
  }
  clearPrefill(state.modal.item, "settlementPrice");
  clearPrefill(state.modal.item, "receivableAmount");
  const price = part.settlementPrice || part.salePrice || "";
  setPrefill(state.modal.item, "settlementPrice", price, price ? `预填：${money(price)}` : undefined);
  setPrefill(state.modal.item, "receivableAmount", price, price ? `预填：${money(price)}` : undefined);
}

function modificationOrderDefaults(machine = {}, configId = null) {
  const entity = {};
  if (!machine?.id) return entity;
  const modelKey = vehicleModelKey(machine);
  const detailConfigs = state.vehicleDetail?.selectedDetail?.machine?.id === machine.id
    ? (state.vehicleDetail.selectedDetail.configs || [])
    : (state.vehicleDetail?.machine?.id === machine.id ? (state.vehicleDetail.configs || []) : []);
  const config = detailConfigs.find(item => String(item.id) === String(configId))
    || detailConfigs[0]
    || null;
  setPrefill(entity, "vehicleModelKey", modelKey, `预填：${vehicleModelLabel(machine)}`);
  setPrefill(entity, "machineId", machine.id, `预填：${machine.vehicleProductNumber || machine.id}`);
  if (config) {
    setPrefill(entity, "machineConfigId", config.id, `预填：${machineConfigOptionLabel(config)}`);
  }
  entity.__placeholders = {
    ...(entity.__placeholders || {}),
    quantity: "默认：1",
    oldPartAction: "默认：拆下件入库"
  };
  return entity;
}

function rentalDefaultsForMachine(machine = {}) {
  const entity = {
    status: "ACTIVE",
    startDate: todayInputDate(),
    __placeholders: {
      machineId: "请选择具体在库车号",
      customerId: "请选择客户列表中的租赁去向",
      destination: "可填写具体工地、收货地址或临时周转说明",
      monthlyRentalPrice: "请输入每月租赁价格",
      endDate: "未确定可先留空"
    }
  };
  if (machine?.id) {
    setPrefill(entity, "machineId", machine.id, `预填：${vehicleNumberLabel(machine)}`);
  }
  return entity;
}

function repairDefaults() {
  const entity = {
    status: "PENDING",
    repairDate: nowInputDateTime(),
    __placeholders: {
      machineId: "请从车辆库存中选择车号",
      customerId: "请从客户列表中选择客户",
      usedPartIds: "可从配件库存中选择使用配件",
      totalFee: "维修费 + 配件费自动计算"
    }
  };
  setPrefill(entity, "repairPersonChoice", "OTHER", "预填：其他");
  return entity;
}

function prepareRepairModalItem(item = {}) {
  if (!item.repairPersonChoice) {
    item.repairPersonChoice = item.repairExternal ? "OTHER" : (item.repairPersonUserId || "");
  }
  if (item.usedPartIds && typeof item.usedPartIds === "string") {
    item.usedPartIds = item.usedPartIds.split(",").map(value => value.trim()).filter(Boolean)[0] || "";
  }
  if (item.totalFee === undefined || item.totalFee === null || item.totalFee === "") {
    item.totalFee = decimalSum(item.repairFee, item.partsFee);
  }
  return item;
}

function effectiveFieldValue(entity = {}, name) {
  const value = entity?.[name];
  if (value !== undefined && value !== null && value !== "") return value;
  if (Object.prototype.hasOwnProperty.call(entity?.__prefillValues || {}, name)) {
    return entity.__prefillValues[name];
  }
  return value;
}

function syncConfigItemFields(form, changedName) {
  if (changedName === "subCategory" && !form.elements.itemName?.value) {
    setFormFieldValue(form, "itemName", form.elements.subCategory.value);
  }
  if (!form.elements.itemCode?.value) {
    setFormFieldValue(form, "itemCode", nextConfigItemCode());
  }
}

function syncPartDictionaryFields(form, changedName) {
  if (changedName === "partCategory") {
    const selectedPartName = form.elements.partName?.value || "";
    const options = configValueOptions();
    syncComboOptionsForField(form, "partName", options, findOptionByValue(options, selectedPartName) ? selectedPartName : "");
    return;
  }
  if (changedName !== "partName") return;
  const meta = selectedComboMeta(form, "partName");
  if (meta?.partCategory && !form.elements.partCategory?.value) {
    setFormFieldValue(form, "partCategory", meta.partCategory, false);
  }
}

function syncRepairTotalFee(form) {
  setFormFieldValue(form, "totalFee", decimalSum(form.elements.repairFee?.value, form.elements.partsFee?.value), false);
}

function decimalSum(...values) {
  const total = values.reduce((sum, value) => sum + Number(value || 0), 0);
  return total ? total.toFixed(2) : "0.00";
}

function syncRepairVehicleSelection(form, machineId) {
  const machine = state.data.vehicles.find(item => Number(item.id) === Number(machineId));
  if (!machine) return;
  state.modal.item.vehicleNumber = machine.vehicleProductNumber || "";
}

function syncCustomerAddress(form, customerId) {
  const customer = state.data.customers.find(item => Number(item.id) === Number(customerId));
  if (!customer) return;
  state.modal.item.customerName = customer.companyName || "";
  state.modal.item.customerAddress = customer.address || "";
}

function syncRentalCustomerDestination(form, customerId) {
  const customer = state.data.customers.find(item => Number(item.id) === Number(customerId));
  if (!customer) return;
  if (!String(form.elements.destination?.value || "").trim()) {
    setFormFieldValue(form, "destination", customer.address || customer.companyName || "", false);
  }
}

function syncRentalVehicleDefaults(form, machineId) {
  const machine = state.data.vehicles.find(item => Number(item.id) === Number(machineId));
  if (!machine) return;
  if (!String(form.elements.destination?.value || "").trim() && machine.destination1) {
    setFormFieldValue(form, "destination", machine.destination1, false);
  }
}

function syncRepairPartFee(form, partId) {
  const part = state.data.parts.find(item => Number(item.id) === Number(partId));
  if (!part) return;
  const price = firstAmount(part.settlementPrice, part.salePrice, part.purchasePrice);
  if (price !== null && !Number(form.elements.partsFee?.value || 0)) {
    setFormFieldValue(form, "partsFee", formatDecimal(price), false);
    syncRepairTotalFee(form);
  }
}

function syncStocktakingQuantity(form, resourceId) {
  const type = String(form.elements.resourceType?.value || "PART").toUpperCase();
  const rows = type === "MACHINE" ? state.data.vehicles : state.data.parts;
  const resource = rows.find(item => Number(item.id) === Number(resourceId));
  if (!resource) return;
  const quantity = type === "MACHINE" ? resource.inventoryCount : resource.quantity;
  setFormFieldValue(form, "actualQuantity", quantity ?? 0, false);
}

function syncStockTransferResource(form, resourceId) {
  const type = String(form.elements.resourceType?.value || "PART").toUpperCase();
  const rows = type === "MACHINE" ? state.data.vehicles : state.data.parts;
  const resource = rows.find(item => Number(item.id) === Number(resourceId));
  if (!resource) return;
  if (resource.warehouseId) {
    setFormFieldValue(form, "fromWarehouseId", resource.warehouseId, false);
  }
  state.modal.item.version = resource.version;
  if (type === "MACHINE") {
    setFormFieldValue(form, "quantity", 1, false);
  }
}

function syncPurchaseResourceDefaults(form) {
  const meta = selectedComboMeta(form, "configValueId");
  if (!meta) return;
  if (meta.unit && !String(form.elements.unit?.value || "").trim()) {
    setFormFieldValue(form, "unit", meta.unit, false);
  }
}

function syncPurchaseAmount(form, changedName) {
  const quantity = amountValue(form.elements.quantity?.value);
  const unitPrice = amountValue(form.elements.unitPrice?.value);
  const totalAmount = amountValue(form.elements.totalAmount?.value);
  if (changedName === "totalAmount" && quantity > 0 && totalAmount > 0) {
    setFormFieldValue(form, "unitPrice", formatDecimal(totalAmount / quantity), false);
    return;
  }
  if (quantity > 0 && unitPrice > 0) {
    setFormFieldValue(form, "totalAmount", formatDecimal(quantity * unitPrice), false);
  }
}

function isPaymentField(name) {
  return ["settlementPrice", "salePrice", "receivableAmount", "receivedAmount", "paymentSettled", "lastPaymentDate", "quantity"].includes(name);
}

function syncPaymentFields(form, changedName) {
  if (!form) return;
  const quantity = Math.max(1, amountValue(form.elements.quantity?.value || 1));
  const settlement = amountValue(form.elements.settlementPrice?.value);
  const sale = amountValue(form.elements.salePrice?.value);
  const currentReceivable = amountValue(form.elements.receivableAmount?.value);
  const unitOrTotal = settlement || sale;
  if (["settlementPrice", "salePrice", "quantity"].includes(changedName) && unitOrTotal > 0) {
    const nextReceivable = form.dataset.kind === "partStock" ? unitOrTotal * quantity : unitOrTotal;
    if (!currentReceivable || changedName === "quantity") {
      setFormFieldValue(form, "receivableAmount", formatDecimal(nextReceivable), false);
    }
  }
  const receivable = amountValue(form.elements.receivableAmount?.value);
  const received = amountValue(form.elements.receivedAmount?.value);
  if (received > 0 && !String(form.elements.lastPaymentDate?.value || "").trim()) {
    setFormFieldValue(form, "lastPaymentDate", todayInputDate(), false);
  }
  if (receivable > 0 && received >= receivable) {
    setFormFieldValue(form, "paymentSettled", true, false);
  } else if (changedName === "receivedAmount" && received > 0 && receivable > received) {
    setFormFieldValue(form, "paymentSettled", false, false);
  }
}

function firstAmount(...values) {
  for (const value of values) {
    const amount = amountValue(value);
    if (amount > 0) return amount;
  }
  return null;
}

function amountValue(value) {
  const amount = Number(value || 0);
  return Number.isFinite(amount) ? amount : 0;
}

function formatDecimal(value) {
  const amount = Number(value || 0);
  if (!Number.isFinite(amount)) return "";
  return amount ? amount.toFixed(2) : "0.00";
}

function enrichPurchaseOrderPayload(payload, form) {
  payload.resourceType = "PART";
  const meta = selectedComboMeta(form, "configValueId");
  if (meta) {
    payload.resourceCode = meta.valueCode || null;
    payload.resourceName = meta.valueLabel || null;
    payload.specificationModel = meta.itemLabel || null;
    if (!payload.unit && meta.unit) {
      payload.unit = meta.unit;
    }
  }
}

function selectedComboMeta(form, name) {
  const input = form.elements[name];
  if (!input?.dataset?.selectedMeta) return null;
  try {
    return JSON.parse(input.dataset.selectedMeta);
  } catch (error) {
    return null;
  }
}

function setFormFieldValue(form, name, value, shouldNotify = true) {
  const combo = form.querySelector(`[data-combo][data-name="${name}"]`);
  if (combo) {
    const options = [...combo.querySelectorAll("[data-combo-option]")].map(option => ({
      value: option.dataset.value,
      label: option.dataset.label,
      meta: option.dataset.meta ? JSON.parse(option.dataset.meta) : null
    }));
    const selected = findOptionByValue(options, value);
    if (selected) {
      setComboValue(combo, selected.value, selected.label, selected.meta ? JSON.stringify(selected.meta) : "", shouldNotify);
    } else {
      const input = combo.querySelector("[data-combo-input]");
      const hidden = combo.querySelector(`input[name="${name}"]`);
      input.value = value || "";
      hidden.value = value || "";
      hidden.dataset.selectedLabel = value || "";
      hidden.dataset.selectedMeta = "";
      if (shouldNotify) {
        hidden.dispatchEvent(new Event("change", { bubbles: true }));
      }
    }
    return;
  }
  if (form.elements[name]) {
    if (form.elements[name].type === "checkbox") {
      form.elements[name].checked = Boolean(value);
    } else {
      form.elements[name].value = value ?? "";
    }
    if (state.modal?.item) {
      state.modal.item[name] = form.elements[name].type === "checkbox" ? form.elements[name].checked : form.elements[name].value;
    }
    if (shouldNotify) {
      form.elements[name].dispatchEvent(new Event("change", { bubbles: true }));
    }
  }
}

function defaultValue(field) {
  if (typeof field.defaultValue === "function") return field.defaultValue();
  if (field.defaultValue !== undefined) return field.defaultValue;
  if (field.type === "checkbox") return false;
  return "";
}

function isModificationDiscountMode(item = state.modal?.item || {}) {
  return fieldOrPrefillValue(item, "oldPartAction") === "DISCOUNT";
}

function getFields(kind, item = state.modal?.item || {}) {
  if (kind === "vehicleInbound" && isManualForklift(fieldOrPrefillValue(item, "machineType"))) {
    const hiddenNames = new Set(["vehicleProductNumber", "engineNumber", "frameNumber", "warrantyCardNumber", "machineType"]);
    return [
      ...fields.vehicleInbound.filter(field => !hiddenNames.has(field.name)),
      { name: "machineType", type: "hidden" }
    ];
  }
  if (kind === "vehicleOutbound") {
    const customerMode = fieldOrPrefillValue(item, "customerMode") === "quickCreate" ? "quickCreate" : "existing";
    if (customerMode === "quickCreate") {
      const baseFields = fields.vehicleOutbound.filter(field => field.name !== "customerId");
      const insertIndex = baseFields.findIndex(field => field.name === "customerMode") + 1;
      return [
        ...baseFields.slice(0, insertIndex),
        ...fields.vehicleOutboundQuickCustomer,
        ...baseFields.slice(insertIndex)
      ];
    }
  }
  if (kind === "partStock" && item.direction === "outbound") {
    return fields.partOutbound;
  }
  if (kind === "modificationOrder" && isModificationDiscountMode(item)) {
    return fields.modificationOrder.flatMap(field => {
      if (field.name === "newPartId") {
        return [
          { name: "newConfigValueId", label: "目标新配件", type: "select", coerce: "int", required: true, options: discountConfigValueOptions },
          { name: "priceDifference", label: "新件差价", type: "number", coerce: "decimal", step: "0.01", required: true, placeholder: "正数为支出，负数为收入" }
        ];
      }
      if (field.name === "quantity") {
        return [{ name: "quantity", type: "hidden", coerce: "int", defaultValue: 1 }];
      }
      return [field];
    });
  }
  return fields[kind] || [];
}

function fieldOrPrefillValue(item, name) {
  if (Object.prototype.hasOwnProperty.call(item || {}, name)) return item[name];
  return item?.__prefillValues?.[name];
}

function nextConfigItemCode() {
  const max = state.data.configItems
    .map(item => String(item.itemCode || ""))
    .map(code => /^CFG-(\d+)$/.exec(code))
    .filter(Boolean)
    .map(match => Number.parseInt(match[1], 10))
    .filter(Number.isFinite)
    .reduce((currentMax, value) => Math.max(currentMax, value), 0);
  return `CFG-${String(max + 1).padStart(4, "0")}`;
}

function findEntity(kind, id) {
  return entityRows(kind).find(item => item.id === id) || {};
}

function entityRows(kind) {
  const key = {
    vehicle: "vehicles",
    part: "parts",
    modificationOrder: "modificationOrders",
    outboundOrder: "outboundOrders",
    rental: "rentals",
    customer: "customers",
    supplier: "suppliers",
    purchaseOrder: "purchaseOrders",
    stocktaking: "stocktakingRecords",
    warehouse: "warehouses",
    stockMovement: "stockMovements",
    repair: "repairs",
    configItem: "configItems",
    configValue: "configValues",
    user: "users"
  }[kind];
  return state.data[key] || [];
}

function entityLabel(kind) {
  return {
    vehicle: "车辆",
    part: "配件",
    modificationOrder: "改装工单",
    outboundOrder: "出库订单",
    rental: "租赁记录",
    customer: "客户",
    supplier: "供应商",
    purchaseOrder: "采购订单",
    stocktaking: "盘点记录",
    warehouse: "仓库",
    stockTransfer: "库存调拨",
    stockMovement: "库存流水",
    repair: "维修记录",
    configItem: "配置项",
    configValue: "配置值",
    user: "用户"
  }[kind] || "数据";
}

function entityDisplayName(kind, item = {}) {
  const value = {
    vehicle: item.vehicleProductNumber || item.name || item.id,
    part: [item.partCode, item.partName].filter(Boolean).join(" / "),
    modificationOrder: item.workOrderNo || item.machineProductNumber || item.id,
    outboundOrder: item.orderNo || item.resourceCode || item.id,
    rental: item.rentalNo || item.vehicleNumber || item.id,
    customer: item.companyName || item.contactName || item.id,
    supplier: item.supplierName || item.contactName || item.id,
    purchaseOrder: item.purchaseNo || item.resourceName || item.id,
    stocktaking: item.stocktakingNo || item.resourceName || item.id,
    warehouse: item.warehouseName || item.warehouseCode || item.id,
    stockMovement: item.movementNo || item.resourceCode || item.id,
    repair: item.vehicleNumber || item.customerName || item.id,
    configItem: item.itemName || item.itemCode || item.id,
    configValue: item.valueLabel || item.valueCode || item.id,
    user: item.username || item.id
  }[kind];
  return display(value || item.id || "-");
}

function findModificationOrder(id) {
  const numericId = Number(id || 0);
  const candidates = [
    ...(state.data.modificationOrders || []),
    ...(state.vehicleDetail?.workOrders || []),
    ...(state.vehicleDetail?.selectedDetail?.workOrders || [])
  ];
  return candidates.find(item => Number(item.id) === numericId) || {};
}

function modalTitle(kind, item) {
  const names = {
    vehicle: "整车档案",
    vehicleModel: "车型",
    vehicleInbound: "车型入库",
    vehicleOutbound: "销售跟进",
    part: "配件",
    customer: "客户",
    rental: "租赁记录",
    outboundOrder: "出库订单",
    supplier: "采购供应商",
    purchaseOrder: "采购订单",
    purchaseFreight: "修改运费",
    stocktaking: "库存盘点",
    warehouse: "仓库",
    stockTransfer: "库存调拨",
    dataRestore: "恢复数据备份",
    invoiceUpload: "上传发票",
    contractUpload: "上传合同",
    repair: "维修记录",
    configItem: "配置项",
    configValue: "配置值",
    vehicleStock: item?.direction === "outbound" ? "整车出库" : "整车入库",
    partStock: item?.direction === "outbound" ? "配件出库" : "配件入库",
    partReplace: "配件替换",
    vehiclePartInstall: "新增装车配件",
    modificationOrder: "改装工单",
    user: "用户",
    userUsername: "修改用户名",
    userPassword: "修改密码",
    switchUser: "切换用户"
  };
  if (kind === "switchUser" || kind === "vehicleStock" || kind === "vehicleInbound" || kind === "vehicleOutbound" || kind === "rental" || kind === "partStock" || kind === "partReplace" || kind === "vehiclePartInstall" || kind === "modificationOrder" || kind === "stockTransfer" || kind === "dataRestore" || kind === "userUsername" || kind === "userPassword" || kind === "invoiceUpload" || kind === "contractUpload") {
    return (kind === "invoiceUpload" || kind === "contractUpload") && item?.orderNo ? `${names[kind]}：${item.orderNo}` : names[kind];
  }
  if (kind === "configValue" || kind === "user" || kind === "vehicleModel") return `新增${names[kind]}`;
  return item?.id ? `编辑${names[kind]}` : `新增${names[kind]}`;
}

function modalSubtitle(kind) {
  const subtitles = {
    vehicle: "按进出库明细表直接录入单台整机的入库、库存、去向和后续跟进字段。",
    vehicleModel: "只维护车型、型号和动力信息；具体配置在入库时选择。",
    vehicleInbound: "结构化配置现在改为选填；可先录文本配置，后续再补配置字典也可以。",
    vehicleOutbound: "可直接选择现有客户，也可以在当前弹窗里按销售表字段临时录入并新建客户。",
    part: "维护配件编码、库存和价格。",
    customer: "维护出库时可直接下拉选择的客户公司信息。",
    rental: "选择具体在库车号，记录租赁去向、租赁价格和归还状态。",
    outboundOrder: "维护车款结清、报销售、发票申请和订单备注。",
    supplier: "维护采购供应商、联系人、税号、账号和备注。",
    purchaseOrder: "从配置字典选择配置项和配置值，自动带入配件名称、编码与规格信息。",
    purchaseFreight: "运费默认为 0；只在实际产生运费时修改。",
    stocktaking: "先创建盘点草稿；确认入账后才会同步库存数量。",
    warehouse: "维护仓库编码、名称、类型和默认仓设置。",
    stockTransfer: "选择整车或配件，从一个仓库调拨到另一个仓库并生成库存流水。",
    dataRestore: "上传 JSON 备份文件并输入确认码后恢复数据库业务数据。",
    invoiceUpload: "已申请发票或已开票的订单可上传；再次上传会替换原发票文件。",
    contractUpload: "标记为有合同的订单可上传；再次上传会替换原合同文件。",
    repair: "记录维修过程、费用与处理状态。",
    configItem: "定义可维护的车辆配置项。",
    configValue: "为当前配置项添加可选值。",
    vehicleStock: "调整已有整车的库存数量。",
    partStock: "入库只调整库存；出库会选择客户并生成出库订单。",
    partReplace: "选择车辆上的旧配件，并用同类型库存配件替换；拆下件会自动入库。",
    vehiclePartInstall: "从配件仓库领料装到当前整车，只需选择分类、库存配件和数量。",
    modificationOrder: "只填写这次客户要求替换的配置；完成工单时才会生成库存流水并更新车辆配置。",
    user: "由超级管理员创建管理员或普通用户。",
    userUsername: "仅超级管理员可修改管理员或普通用户的登录名。",
    userPassword: "仅超级管理员可重置管理员或普通用户的登录密码。",
    switchUser: "输入另一个账号后立即进入对应权限。"
  };
  return subtitles[kind] || "";
}

function configItemOptions() {
  return [...state.data.configItems]
    .sort(compareConfigItems)
    .map(item => ({
    value: item.id,
    label: configItemLabel(item)
  }));
}

function compareConfigItems(a, b) {
  return [
    String(a.category || "").localeCompare(String(b.category || ""), "zh-CN"),
    String(a.subCategory || "").localeCompare(String(b.subCategory || ""), "zh-CN"),
    String(a.itemName || "").localeCompare(String(b.itemName || ""), "zh-CN"),
    Number(a.sortOrder || 0) - Number(b.sortOrder || 0)
  ].find(result => result !== 0) || 0;
}

function configValueOptionsForItem(configItemId) {
  const item = state.data.configItems.find(configItem => String(configItem.id) === String(configItemId));
  return (state.data.configValueMap[configItemId] || [])
    .map(value => ({
      value: value.id,
      label: value.valueLabel || "-",
      meta: {
        configItemId: item?.id,
        configValueId: value.id,
        valueLabel: value.valueLabel,
        valueCode: value.valueCode,
        itemLabel: item ? configItemLabel(item) : "",
        unit: item?.unit
      }
    }));
}

function powerTypeOptions() {
  return [
    { value: "内燃叉车", label: "内燃叉车" },
    { value: "电动叉车", label: "电动叉车" },
    { value: "手动叉车", label: "手动叉车" }
  ];
}

function supplierFilterOptions() {
  return uniqueOptions(state.data.vehicles.map(item => item.supplier));
}

function stockFilterOptions() {
  return [
    { value: "inStock", label: "有库存" },
    { value: "empty", label: "库存为 0" }
  ];
}

function filterVehicleRows(rows) {
  const filters = state.filters.vehicles || {};
  return rows.filter(row => {
    if (filters.power && normalizePowerType(row.machineType) !== filters.power) return false;
    if (filters.supplier && row.supplier !== filters.supplier) return false;
    if (filters.stock === "inStock" && Number(row.inventoryCount || 0) <= 0) return false;
    if (filters.stock === "empty" && Number(row.inventoryCount || 0) !== 0) return false;
    return true;
  });
}

function vehicleCategoryOptions() {
  return uniqueOptions([
    "电动叉车",
    "手动叉车",
    "内燃叉车",
    ...state.data.configItems.map(item => item.category)
  ]);
}

function configSubCategoryOptions() {
  return uniqueOptions([
    "货叉",
    "电池",
    "轮胎",
    ...state.data.configItems.map(item => item.subCategory),
    ...state.data.configItems.map(item => item.itemName)
  ]);
}

function configPartCategoryOptions() {
  return uniqueOptions([
    ...state.data.configItems.map(item => item.subCategory || item.itemName),
    ...state.data.parts.map(item => item.partCategory),
    "货叉",
    "电池",
    "轮胎"
  ]);
}

function configValueOptions() {
  const selectedCategory = normalizeText(state.modal?.item?.partCategory);
  return configValuesWithItems()
    .filter(entry => !selectedCategory || normalizeText(configPartCategory(entry.item)) === selectedCategory)
    .map(entry => ({
      value: entry.value.valueLabel,
      label: `${entry.value.valueLabel || "-"} · ${configItemLabel(entry.item)}`,
      meta: {
        configItemId: entry.item.id,
        configValueId: entry.value.id,
        partCategory: configPartCategory(entry.item)
      }
    }));
}

function configValuesWithItems() {
  return state.data.configItems.flatMap(item => {
    const values = state.data.configValueMap[item.id] || [];
    return values.map(value => ({ item, value }));
  });
}

function configItemLabel(item) {
  return [
    item.category,
    item.subCategory,
    item.itemName
  ].filter(Boolean).join(" / ") || "-";
}

function configPartCategory(item) {
  return item?.subCategory || item?.itemName || "";
}

function uniqueOptions(values) {
  return [...new Set((values || []).map(value => String(value || "").trim()).filter(Boolean))]
    .map(value => ({ value, label: value }));
}

function normalizePowerType(machineType) {
  const value = String(machineType || "").trim();
  if (value.includes("手动")) return "手动叉车";
  if (value.includes("电动") || value.toUpperCase().includes("CPD")) return "电动叉车";
  if (value.includes("内燃") || value.includes("燃油") || value.toUpperCase().includes("CPC")) return "内燃叉车";
  return value;
}

function isManualForklift(machineType) {
  return normalizePowerType(machineType) === "手动叉车";
}

function vehicleModelKey(item) {
  return encodeURIComponent(JSON.stringify([
    String(item?.name || "").trim(),
    String(item?.specificationModel || "").trim(),
    normalizePowerType(item?.machineType)
  ]));
}

function decodeVehicleModelKey(key) {
  try {
    const [name, specificationModel, machineType] = JSON.parse(decodeURIComponent(key || ""));
    return { name, specificationModel, machineType };
  } catch (error) {
    return { name: "", specificationModel: "", machineType: "" };
  }
}

function vehicleModelLabel(model) {
  return `${model?.name || "-"} · ${model?.specificationModel || "-"}`;
}

function vehicleNumberLabel(item) {
  return `${item?.vehicleProductNumber || item?.id || "-"} · ${item?.name || "-"} / ${item?.specificationModel || "-"}`;
}

function vehiclePartDefaultsForMachine(machine = {}) {
  const entity = {
    __placeholders: {
      partCode: machine.vehicleProductNumber ? `请输入 ${machine.vehicleProductNumber} 的配件编码` : "请输入配件编码",
      partName: "请选择或输入配件名称",
      partCategory: "例如：轮胎 / 电池 / 属具 / 安全附件",
      remarks: machine.vehicleProductNumber ? `来自整车 ${vehicleNumberLabel(machine)}` : "记录配件来源或安装说明"
    }
  };
  const modelLabel = machine.name || machine.specificationModel ? vehicleModelLabel(machine) : "";
  setPrefill(entity, "source", "整车新增", "预填：整车新增");
  setPrefill(entity, "sourceMachineId", machine.id, machine.id ? `预填：${vehicleNumberLabel(machine)}` : undefined);
  setPrefill(entity, "applicableModels", modelLabel, modelLabel ? `预填：${modelLabel}` : undefined);
  setPrefill(entity, "quantity", 1, "默认：1");
  return entity;
}

function vehiclePartInstallDefaultsForMachine(machine = {}) {
  const entity = {};
  setPrefill(entity, "machineId", machine.id, machine.id ? `预填：${vehicleNumberLabel(machine)}` : undefined);
  setPrefill(entity, "quantity", 1, "默认：1");
  return entity;
}

function vehicleModelGroups() {
  const groups = new Map();
  for (const vehicle of state.data.vehicles) {
    const modelKey = vehicleModelKey(vehicle);
    const modelOnly = Boolean(vehicle.modelOnly);
    if (!groups.has(modelKey)) {
      groups.set(modelKey, {
        id: modelKey,
        modelKey,
        name: vehicle.name,
        specificationModel: vehicle.specificationModel,
        machineType: normalizePowerType(vehicle.machineType),
        supplier: vehicle.supplier,
        warehouseName: vehicle.warehouseName,
        purchasePrice: vehicle.purchasePrice,
        salePrice: vehicle.salePrice,
        settlementPrice: vehicle.settlementPrice,
        modelTemplateId: modelOnly ? vehicle.id : null,
        vehicles: [],
        vehicleNumbers: "",
        unitCount: 0,
        inventoryCount: 0
      });
    }
    const group = groups.get(modelKey);
    if (!group.supplier && vehicle.supplier) group.supplier = vehicle.supplier;
    if (!group.warehouseName && vehicle.warehouseName) group.warehouseName = vehicle.warehouseName;
    if (!group.purchasePrice && vehicle.purchasePrice) group.purchasePrice = vehicle.purchasePrice;
    if (!group.salePrice && vehicle.salePrice) group.salePrice = vehicle.salePrice;
    if (!group.settlementPrice && vehicle.settlementPrice) group.settlementPrice = vehicle.settlementPrice;
    if (modelOnly) {
      group.modelTemplateId = vehicle.id;
      continue;
    }
    group.vehicles.push(vehicle);
    group.unitCount += 1;
    group.inventoryCount += Number(vehicle.inventoryCount || 0);
  }
  return [...groups.values()]
    .map(group => ({
      ...group,
      vehicles: [...group.vehicles].sort((a, b) => String(a.vehicleProductNumber || "").localeCompare(String(b.vehicleProductNumber || ""), "zh-CN")),
      vehicleNumbers: group.vehicles.map(vehicle => vehicle.vehicleProductNumber).filter(Boolean).join(" ")
    }))
    .sort((a, b) => vehicleModelLabel(a).localeCompare(vehicleModelLabel(b), "zh-CN"));
}

function vehiclesForModelKey(modelKey) {
  if (!modelKey) return [];
  return state.data.vehicles
    .filter(vehicle => vehicleModelKey(vehicle) === modelKey && !vehicle.modelOnly)
    .sort((a, b) => String(a.vehicleProductNumber || "").localeCompare(String(b.vehicleProductNumber || ""), "zh-CN"));
}

function prepareVehicleModelSummary(row = {}) {
  const model = {
    ...row,
    modelQueryMachineType: String(row.machineType || "").trim(),
    machineType: normalizePowerType(row.machineType),
    unitCount: Number(row.unitCount || 0),
    inventoryCount: Number(row.inventoryCount || 0),
    vehicleNumbers: row.vehicleNumbers || "",
    vehicles: []
  };
  model.modelKey = vehicleModelKey(model);
  model.id = model.modelKey;
  return model;
}

function modelSummaryForKey(modelKey) {
  if (!modelKey) return null;
  return (state.data.vehicleModels || []).find(item => item.modelKey === modelKey)
    || vehicleModelGroups().find(item => item.modelKey === modelKey)
    || null;
}

async function fetchVehicleModelVehicles(modelKey) {
  if (!modelKey) return [];
  const model = modelSummaryForKey(modelKey) || decodeVehicleModelKey(modelKey);
  const params = new URLSearchParams();
  params.set("name", model.name || "");
  params.set("specificationModel", model.specificationModel || "");
  params.set("machineType", model.modelQueryMachineType ?? model.machineType ?? "");
  const rows = await api(`${endpoints.vehicle.modelVehicles}?${params.toString()}`);
  return sortById(rows, false)
    .sort((a, b) => String(a.vehicleProductNumber || "").localeCompare(String(b.vehicleProductNumber || ""), "zh-CN"));
}

function vehicleModelOptions() {
  return vehicleModelGroups().map(group => ({
    value: group.modelKey,
    label: `${vehicleModelLabel(group)}（${group.machineType || "未分类"} / ${group.inventoryCount} 台库存）`
  }));
}

function vehicleNumberOptions() {
  const modelKey = effectiveFieldValue(state.modal?.item, "vehicleModelKey");
  return vehiclesForModelKey(modelKey).map(item => ({
    value: item.id,
    label: `${item.vehicleProductNumber || item.id}（${stockStatusLabel(item.stockStatus)}）`,
    meta: {
      modelKey,
      vehicleProductNumber: item.vehicleProductNumber,
      frameNumber: item.frameNumber,
      engineNumber: item.engineNumber
    }
  }));
}

function vehicleOutboundOptions() {
  const modelKey = state.modal?.item?.__modelKey;
  const vehicles = modelKey ? vehiclesForModelKey(modelKey) : state.data.vehicles.filter(item => !item.modelOnly);
  return vehicles
    .filter(canOutboundVehicle)
    .map(item => ({
      value: item.id,
      label: `${item.vehicleProductNumber || item.id} · ${item.name || "-"} / ${item.specificationModel || "-"}（在库 ${item.inventoryCount || 0} 台）`,
      meta: {
        settlementPrice: item.settlementPrice,
        salePrice: item.salePrice,
        stockStatus: item.stockStatus
      }
    }));
}

function canOutboundVehicle(item) {
  return !item.modelOnly && Number(item.inventoryCount || 0) > 0 && item.stockStatus !== "OUTBOUND" && !activeRentalForMachine(item.id);
}

function vehicleRentalOptions() {
  const currentRentalId = state.modal?.item?.id;
  return state.data.vehicles
    .filter(item => !item.modelOnly)
    .filter(item => Number(item.inventoryCount || 0) > 0 && item.stockStatus !== "OUTBOUND")
    .filter(item => {
      const rental = activeRentalForMachine(item.id);
      return !rental || Number(rental.id) === Number(currentRentalId);
    })
    .map(item => ({
      value: item.id,
      label: `${item.vehicleProductNumber || item.id} · ${item.name || "-"} / ${item.specificationModel || "-"}`
    }));
}

function vehicleOptions() {
  return state.data.vehicles.filter(item => !item.modelOnly).map(item => ({
    value: item.id,
    label: `${vehicleNumberLabel(item)}${activeRentalForMachine(item.id) ? " · 租赁中" : ""}`
  }));
}

function customerOptions() {
  return state.data.customers.map(item => ({
    value: item.id,
    label: `${item.companyName || "-"}${item.contactName ? " · " + item.contactName : ""}`,
    meta: {
      contactPhone: item.contactPhone,
      taxOrIdNumber: item.taxOrIdNumber
    }
  }));
}

function customerEntryModeOptions() {
  return [
    { value: "existing", label: "选择现有客户" },
    { value: "quickCreate", label: "直接录入并新建客户" }
  ];
}

function supplierOptions() {
  return state.data.suppliers.map(item => ({
    value: item.id,
    label: `${item.supplierName || "-"}${item.contactName ? " / " + item.contactName : ""}`,
    meta: {
      contactPhone: item.contactPhone,
      supplierType: item.supplierType
    }
  }));
}

function purchaseStatusOptions() {
  return [
    { value: "ORDERED", label: "已下单" },
    { value: "PARTIAL", label: "部分到货" },
    { value: "ARRIVED", label: "已到货" },
    { value: "RECEIVED", label: "已收货" },
    { value: "CANCELED", label: "已取消" }
  ];
}
function purchaseConfigValueOptions() {
  const configItemId = effectiveFieldValue(state.modal?.item || {}, "configItemId");
  if (!configItemId) return [];
  return configValueOptionsForItem(configItemId);
}

function stocktakingResourceTypeOptions() {
  return [
    { value: "PART", label: "配件" },
    { value: "MACHINE", label: "整车" }
  ];
}

function stocktakingResourceOptions() {
  const type = String(effectiveFieldValue(state.modal?.item || {}, "resourceType") || "PART").toUpperCase();
  if (type === "MACHINE") {
    return state.data.vehicles
      .filter(item => !item.modelOnly)
      .map(item => ({
        value: item.id,
        label: `${vehicleNumberLabel(item)} / 账面 ${item.inventoryCount ?? 0}`
      }));
  }
  return state.data.parts.map(item => ({
    value: item.id,
    label: `${item.partCode || "-"} / ${item.partName || "-"} / 账面 ${item.quantity ?? 0}${item.unit || ""}`
  }));
}

function stocktakingStatusOptions() {
  return [
    { value: "DRAFT", label: "草稿" }
  ];
}

function warehouseOptions() {
  return state.data.warehouses.map(item => ({
    value: item.id,
    label: `${item.warehouseName || "-"}（${item.warehouseCode || "-"}）`
  }));
}

function warehouseNameById(id) {
  return state.data.warehouses.find(item => Number(item.id) === Number(id))?.warehouseName || "未分配仓库";
}

function transferResourceTypeOptions() {
  return [
    { value: "PART", label: "配件" },
    { value: "MACHINE", label: "整车" }
  ];
}

function stockTransferResourceOptions() {
  const type = String(effectiveFieldValue(state.modal?.item || {}, "resourceType") || "PART").toUpperCase();
  if (type === "MACHINE") {
    return state.data.vehicles
      .filter(item => !item.modelOnly)
      .filter(item => Number(item.inventoryCount || 0) > 0)
      .map(item => ({
        value: item.id,
        label: `${vehicleNumberLabel(item)} / ${warehouseNameById(item.warehouseId)} / 库存 ${item.inventoryCount ?? 0}`,
        meta: {
          warehouseId: item.warehouseId,
          version: item.version
        }
      }));
  }
  return state.data.parts
    .filter(item => Number(item.quantity || 0) > 0)
    .map(item => ({
      value: item.id,
      label: `${item.partCode || "-"} · ${item.partName || "-"} / ${warehouseNameById(item.warehouseId)} / 库存 ${item.quantity ?? 0}${item.unit || ""}`,
      meta: {
        warehouseId: item.warehouseId,
        version: item.version
      }
    }));
}

function stockTransferResourceName(payload = {}) {
  const type = String(payload.resourceType || "PART").toUpperCase();
  const rows = type === "MACHINE" ? state.data.vehicles : state.data.parts;
  const resource = rows.find(item => Number(item.id) === Number(payload.resourceId));
  if (!resource) return "调拨对象";
  return type === "MACHINE" ? vehicleNumberLabel(resource) : `${resource.partCode || "-"} · ${resource.partName || "-"}`;
}

function partCodeOptions() {
  return state.data.parts.map(item => ({
    value: item.partCode,
    label: `${item.partCode || "-"} · ${item.partName || "-"}（库存 ${item.quantity ?? 0}${item.unit || ""}）`
  }));
}

function machineConfigOptions() {
  return (state.modal?.context?.machineConfigs || []).map(item => ({
    value: item.id,
    label: machineConfigOptionLabel(item)
  }));
}

function machineConfigOptionLabel(item) {
  return `${machineConfigDictionaryLabel(item)} · ${item.selectedValue || "-"}`;
}

function machineConfigDictionaryLabel(config) {
  const item = state.data.configItems.find(configItem => String(configItem.id) === String(config.configItemId));
  return item ? configItemLabel(item) : (config.itemName || "-");
}

function compatiblePartOptions() {
  return (state.modal?.context?.compatibleParts || []).map(item => ({
    value: item.id,
    label: `${item.partCode || "-"} · ${item.partName || "-"}（库存 ${item.quantity ?? 0}${item.unit || ""}）`
  }));
}

function discountConfigValueOptions() {
  const configId = effectiveFieldValue(state.modal?.item || {}, "machineConfigId");
  const config = (state.modal?.context?.machineConfigs || [])
    .find(item => String(item.id) === String(configId));
  return (state.data.configValueMap[config?.configItemId] || []).map(value => ({
    value: value.id,
    label: value.valueLabel || "-"
  }));
}

function installPartCategoryOptions() {
  const availableCategories = new Set(state.data.parts
    .filter(part => Number(part.quantity || 0) > 0)
    .map(part => normalizeText(part.partCategory))
    .filter(Boolean));
  return state.data.configItems
    .filter(item => availableCategories.has(normalizeText(configPartCategory(item))))
    .sort(compareConfigItems)
    .map(item => ({
      value: item.id,
      label: configItemLabel(item)
    }));
}

function installPartOptions() {
  const selectedItem = state.data.configItems.find(item => String(item.id) === String(state.modal?.item?.configItemId));
  const selectedCategory = normalizeText(configPartCategory(selectedItem));
  return state.data.parts
    .filter(part => Number(part.quantity || 0) > 0)
    .filter(part => !selectedCategory || normalizeText(part.partCategory) === selectedCategory)
    .map(part => ({
      value: part.id,
      label: `${part.partCode || "-"} · ${part.partName || "-"}（库存 ${part.quantity ?? 0}${part.unit || ""}）`
    }));
}

function statusOptions() {
  return [
    { value: "PENDING", label: "待处理" },
    { value: "COMPLETED", label: "已完成" }
  ];
}

function stockStatusOptions() {
  return [
    { value: "PENDING_INBOUND", label: "待入库" },
    { value: "IN_STOCK", label: "在库" },
    { value: "PENDING_MODIFICATION", label: "待改装" },
    { value: "MODIFYING", label: "改装中" },
    { value: "PENDING_OUTBOUND", label: "待出库" },
    { value: "OUTBOUND", label: "已出库" },
    { value: "OUT_OF_STOCK", label: "已出库" },
    { value: "RESERVED", label: "已预留" },
    { value: "LOCKED", label: "已锁定" }
  ];
}

function oldPartActionOptions() {
  return [
    { value: "DISCOUNT", label: "旧件折价" },
    { value: "STOCK_IN", label: "拆下件入库" },
    { value: "DISCARD", label: "不入库" }
  ];
}

function inputTypeOptions() {
  return [
    { value: "SELECT", label: "下拉选择" },
    { value: "TEXT", label: "文本输入" },
    { value: "NUMBER", label: "数字输入" },
    { value: "BOOLEAN", label: "开关选项" }
  ];
}

function userRoleOptions() {
  const options = [{ value: "USER", label: "普通用户" }];
  if (hasPermission("user:admin")) {
    options.push({ value: "ADMIN", label: "管理员" });
  }
  return options;
}

function jobTagOptions() {
  return [
    { value: "MANAGEMENT", label: "管理" },
    { value: "CLERK", label: "文员" },
    { value: "REPAIR", label: "维修" }
  ];
}

function normalizeUser(data) {
  if (!data) return null;
  const roles = data.roles || [];
  return {
    username: data.username,
    jobTag: data.jobTag,
    roles,
    permissions: data.permissions || defaultPermissionsForRoles(roles)
  };
}

function hasRole(role) {
  return Boolean(state.user?.roles?.includes(role));
}

function hasAnyRole(...roles) {
  return roles.some(role => hasRole(role));
}

function repairPartOptions() {
  return state.data.parts.map(item => ({
    value: item.id,
    label: `${item.partCode || "-"} · ${item.partName || "-"}（库存 ${item.quantity ?? 0}${item.unit || ""}）`
  }));
}

function repairPersonOptions() {
  return [
    ...state.data.repairUsers.map(item => ({
      value: item.id,
      label: item.username || `用户 ${item.id}`
    })),
    { value: "OTHER", label: "其他" }
  ];
}

function rentalStatusOptions() {
  return [
    { value: "ACTIVE", label: "租赁中" },
    { value: "RETURNED", label: "已归还" }
  ];
}

function canManageOrderLock() {
  return hasAnyRole("ADMIN", "SUPER_ADMIN");
}

function hasPermission(permission) {
  return hasRole("SUPER_ADMIN") || Boolean(state.user?.permissions?.includes(permission));
}

function hasAnyPermission(...permissions) {
  return permissions.some(permission => hasPermission(permission));
}

function defaultPermissionsForRoles(roles = []) {
  const permissionMap = {
    SUPER_ADMIN: [
      "vehicle:write",
      "part:write",
      "repair:write",
      "config:write",
      "replace:write",
      "stock:adjust",
      "log:read",
      "user:read",
      "user:write",
      "user:admin"
    ],
    ADMIN: [
      "vehicle:write",
      "part:write",
      "repair:write",
      "config:write",
      "replace:write",
      "stock:adjust",
      "log:read",
      "user:read",
      "user:write"
    ],
    USER: [
      "vehicle:write",
      "part:write",
      "repair:write",
      "config:write",
      "replace:write",
      "stock:adjust"
    ]
  };
  return [...new Set(roles.flatMap(role => permissionMap[role] || []))];
}

function canAccessTab(tab) {
  const permissionsByTab = {
    modifications: "replace:write",
    outboundOrders: "stock:adjust",
    rentals: "stock:adjust",
    customers: "vehicle:write",
    suppliers: "stock:adjust",
    purchases: "stock:adjust",
    stocktakes: "stock:adjust",
    warehouses: "stock:adjust",
    stats: "log:read",
    stockMovements: "log:read",
    logs: "log:read",
    users: "user:read",
    maintenance: "user:admin"
  };
  if (tab === "maintenance" && !hasRole("SUPER_ADMIN")) return false;
  return !permissionsByTab[tab] || hasPermission(permissionsByTab[tab]);
}

function canWriteEntity(kind) {
  const permissionsByKind = {
    vehicle: "vehicle:write",
    part: "part:write",
    repair: "repair:write",
    rental: "stock:adjust",
    supplier: "stock:adjust",
    warehouse: "stock:adjust",
    purchaseOrder: "stock:adjust",
    stocktaking: "stock:adjust",
    configItem: "config:write",
    configValue: "config:write",
    customer: "vehicle:write"
  };
  return !permissionsByKind[kind] || hasPermission(permissionsByKind[kind]);
}

function detailItem(label, value) {
  return `
    <div class="detail-item">
      <div class="label">${escapeHtml(label)}</div>
      <div class="value">${escapeHtml(display(value))}</div>
    </div>
  `;
}

function canUpdateUserJobTag(row = {}) {
  if (row.roles?.includes("SUPER_ADMIN")) return false;
  if (hasPermission("user:admin")) return true;
  return hasPermission("user:write") && row.roles?.every(role => role === "USER");
}

function canUpdateUserEnabled(row = {}) {
  if (row.roles?.includes("SUPER_ADMIN")) return false;
  if (row.username && state.user?.username && row.username === state.user.username) return false;
  if (hasPermission("user:admin")) return true;
  return hasPermission("user:write") && row.roles?.every(role => role === "USER");
}

function icon(name) {
  return `<span class="btn-icon" aria-hidden="true">${icons[name] || ""}</span>`;
}

function renderLoading() {
  return `<div class="surface"><div class="surface-body"><div class="loading-state"><span class="loading-spinner" aria-hidden="true"></span><span>正在加载数据...</span></div></div></div>`;
}

function renderLoadError(error) {
  return `
    <div class="surface">
      <div class="surface-body">
        <div class="empty-state">
          <strong>数据加载失败</strong>
          <span>${escapeHtml(error?.message || "请刷新后重试")}</span>
          <button class="btn btn-primary" type="button" data-action="refresh">${icon("refresh")}重新加载</button>
        </div>
      </div>
    </div>
  `;
}

function showToast(message, type = "info") {
  const toast = document.createElement("div");
  toast.className = `toast ${type}`;
  toast.textContent = message;
  els.toastHost.appendChild(toast);
  window.setTimeout(() => toast.remove(), 3600);
}

function showContextSuccess(message, target = "", detail = "") {
  const parts = [message, target && `对象：${target}`, detail].filter(Boolean);
  showToast(parts.join(" · "), "success");
}

function modalDangerConfirmation(kind, item = {}, payload = {}) {
  if (kind === "vehicleOutbound") {
    return {
      title: "确认整车出库",
      target: entityDisplayName("vehicle", findEntity("vehicle", Number(payload.machineId || 0))),
      impact: "将创建整车出库订单并进入收款、报销售和发票跟进流程。"
    };
  }
  if (kind === "vehicleStock") {
    const outbound = item.direction === "outbound";
    return {
      title: outbound ? "确认整车出库调整" : "确认整车入库调整",
      target: entityDisplayName("vehicle", findEntity("vehicle", Number(payload.machineId || 0))),
      impact: `库存数量将${outbound ? "减少" : "增加"} ${payload.quantity || 0}，请确认车号和数量无误。`
    };
  }
  if (kind === "partStock") {
    const outbound = item.direction === "outbound";
    return {
      title: outbound ? "确认配件出库" : "确认配件入库调整",
      target: payload.partCode || entityDisplayName("part", item),
      impact: outbound ? "将创建配件出库订单并扣减库存。" : `配件库存将增加 ${payload.quantity || 0}，请确认编码和数量无误。`
    };
  }
  if (kind === "partReplace") {
    return {
      title: "确认配件替换",
      target: entityDisplayName("vehicle", findEntity("vehicle", Number(payload.machineId || 0))),
      impact: "将扣减新配件库存，旧件会按规则处理，并更新车辆配置记录。"
    };
  }
  if (kind === "vehiclePartInstall") {
    return {
      title: "确认配件装车",
      target: entityDisplayName("vehicle", findEntity("vehicle", Number(payload.machineId || 0))),
      impact: "将从配件库存领料并关联到当前车辆。"
    };
  }
  if (kind === "outboundOrder") {
    return {
      title: "确认更新出库订单",
      target: entityDisplayName("outboundOrder", item),
      impact: "会更新收款、报销售、发票或合同等关键跟进字段。"
    };
  }
  if (kind === "purchaseFreight") {
    return {
      title: "确认修改采购运费",
      target: entityDisplayName("purchaseOrder", item),
      impact: "运费会影响采购成本统计，请确认金额无误。"
    };
  }
  if (kind === "userUsername") {
    return {
      title: "确认修改用户名",
      target: entityDisplayName("user", item),
      impact: "用户名修改后，用户需要使用新用户名登录。"
    };
  }
  if (kind === "userPassword") {
    return {
      title: "确认修改用户密码",
      target: entityDisplayName("user", item),
      impact: "密码修改后，旧密码将无法继续登录。"
    };
  }
  if (kind === "invoiceUpload") {
    return {
      title: "确认上传发票",
      target: entityDisplayName("outboundOrder", item),
      impact: item.invoiceFileAvailable ? "本次上传会替换已有发票文件。" : "发票文件会绑定到当前出库订单。"
    };
  }
  if (kind === "contractUpload") {
    return {
      title: "确认上传合同",
      target: entityDisplayName("outboundOrder", item),
      impact: item.contractFileAvailable ? "本次上传会替换已有合同文件。" : "合同文件会绑定到当前出库订单。"
    };
  }
  return null;
}

function confirmDanger({ title, target, impact, confirmText = "确认继续", cancelText = "取消" } = {}) {
  if (typeof document === "undefined" || !document.body) {
    return Promise.resolve(window.confirm([
      title || "确认操作",
      target ? `对象：${target}` : "",
      impact ? `影响：${impact}` : "",
      "",
      `${confirmText}？`
    ].filter(line => line !== "").join("\n")));
  }

  return new Promise(resolve => {
    let settled = false;
    let pointerDownStartedOnOverlay = false;
    const overlay = document.createElement("div");
    overlay.className = "confirm-overlay";
    overlay.innerHTML = `
      <section class="confirm-dialog" role="dialog" aria-modal="true" aria-labelledby="dangerConfirmTitle">
        <div class="confirm-icon">${icon("trash")}</div>
        <div class="confirm-content">
          <h2 id="dangerConfirmTitle">${escapeHtml(title || "确认操作")}</h2>
          ${target ? `<p class="confirm-target">对象：${escapeHtml(target)}</p>` : ""}
          ${impact ? `<p class="confirm-impact">${escapeHtml(impact)}</p>` : ""}
        </div>
        <div class="confirm-actions">
          <button class="btn btn-ghost" type="button" data-confirm-cancel>${escapeHtml(cancelText)}</button>
          <button class="btn btn-danger" type="button" data-confirm-ok>${icon("trash")}${escapeHtml(confirmText)}</button>
        </div>
      </section>
    `;
    document.body.appendChild(overlay);

    const okButton = overlay.querySelector("[data-confirm-ok]");
    const cancelButton = overlay.querySelector("[data-confirm-cancel]");
    const finish = confirmed => {
      if (settled) return;
      settled = true;
      overlay.classList.remove("is-open");
      window.setTimeout(() => overlay.remove(), 180);
      document.removeEventListener("keydown", onKeydown, true);
      resolve(confirmed);
    };
    const onKeydown = event => {
      if (event.key === "Escape") {
        event.preventDefault();
        finish(false);
      }
    };

    okButton?.addEventListener("click", () => finish(true), { once: true });
    cancelButton?.addEventListener("click", () => finish(false), { once: true });
    overlay.addEventListener("pointerdown", event => {
      pointerDownStartedOnOverlay = event.target === overlay;
    });
    overlay.addEventListener("click", event => {
      if (event.target === overlay && pointerDownStartedOnOverlay) finish(false);
      pointerDownStartedOnOverlay = false;
    });
    document.addEventListener("keydown", onKeydown, true);
    requestAnimationFrame(() => {
      overlay.classList.add("is-open");
      cancelButton?.focus();
    });
  });
}

function handleActionError(error) {
  if (isAuthExpiredError(error)) {
    logout("登录已过期，请重新登录");
    return;
  }
  if (isConflictError(error)) {
    showToast(error.message || "数据已被其他用户更新，请刷新后重试", "error");
    void refreshAfterConflict();
    return;
  }
  showToast(error.message || "操作失败", "error");
}

async function refreshAfterConflict() {
  if (state.modal) {
    closeModal();
  }
  try {
    await loadAllData();
    renderCurrentTab();
    showToast("已刷新最新数据，请重新打开编辑界面后再保存", "info");
  } catch (error) {
    if (isAuthExpiredError(error)) {
      logout("登录已过期，请重新登录");
    }
  }
}

function isTypingTarget(target) {
  return Boolean(target?.closest?.("input, select, textarea, [contenteditable='true']"));
}

function isAuthExpiredError(error) {
  return Boolean(error?.authExpired);
}

function isConflictError(error) {
  return Number(error?.status) === 409 || Number(error?.code) === 409;
}
