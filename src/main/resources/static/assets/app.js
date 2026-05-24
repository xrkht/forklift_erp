import { clearSession, createApiClient, readStoredToken, readStoredUser, saveSession } from "./modules/session.js";

const LOG_PAGE_SIZE = 120;

const state = {
  token: readStoredToken(),
  user: normalizeUser(readStoredUser()),
  activeTab: "overview",
  search: {
    vehicles: "",
    parts: "",
    modificationOrders: "",
    repairs: "",
    stats: "",
    logs: "",
    configItems: "",
    configValues: "",
    users: ""
  },
  data: {
    vehicles: [],
    parts: [],
    modificationOrders: [],
    repairs: [],
    statistics: null,
    operationLogs: [],
    configItems: [],
    configValues: [],
    configValueMap: {},
    users: []
  },
  selectedVehicleId: null,
  vehicleDetail: null,
  selectedConfigItemId: null,
  selectedStatsYear: new Date().getFullYear(),
  visibleLogRows: LOG_PAGE_SIZE,
  modal: null
};

const api = createApiClient(() => state.token);

const tabs = {
  overview: { title: "总览", subtitle: "系统概览与业务入口" },
  vehicles: { title: "车辆库存", subtitle: "整车入库、库存信息与配置明细" },
  parts: { title: "配件库存", subtitle: "配件档案、库存数量与价格信息" },
  modifications: { title: "改装工单", subtitle: "客户出库前改装、配件替换与拆下件回库" },
  repairs: { title: "维修记录", subtitle: "维修工单、费用与状态追踪" },
  logs: { title: "日志查看", subtitle: "配件替换、维修和出入库流水" },
  stats: { title: "统计财报", subtitle: "按月度和年度汇总库存收支、维修收入与库存价值" },
  configs: { title: "配置字典", subtitle: "车辆配置项与可选值维护" },
  users: { title: "用户管理", subtitle: "账号、角色与客户端登录切换" }
};

const icons = {
  plus: '<svg viewBox="0 0 24 24" fill="none"><path d="M12 5v14M5 12h14" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>',
  minus: '<svg viewBox="0 0 24 24" fill="none"><path d="M5 12h14" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>',
  refresh: '<svg viewBox="0 0 24 24" fill="none"><path d="M20 12a8 8 0 1 1-2.34-5.66L20 8" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/><path d="M20 4v4h-4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  edit: '<svg viewBox="0 0 24 24" fill="none"><path d="M12 20h9" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4 12.5-12.5Z" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  trash: '<svg viewBox="0 0 24 24" fill="none"><path d="M4 7h16M10 11v6M14 11v6M6 7l1 14h10l1-14M9 7V4h6v3" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  eye: '<svg viewBox="0 0 24 24" fill="none"><path d="M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6S2 12 2 12Z" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/><circle cx="12" cy="12" r="3" stroke="currentColor" stroke-width="1.8"/></svg>',
  search: '<svg viewBox="0 0 24 24" fill="none"><circle cx="11" cy="11" r="7" stroke="currentColor" stroke-width="1.8"/><path d="m20 20-3.5-3.5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>',
  swap: '<svg viewBox="0 0 24 24" fill="none"><path d="M7 7h12l-3-3M17 17H5l3 3" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>'
};

const fields = {
  vehicle: [
    { name: "vehicleProductNumber", label: "车号", required: true },
    { name: "name", label: "车辆名称", required: true },
    { name: "specificationModel", label: "规格型号", required: true },
    { name: "machineType", label: "车辆类型" },
    { name: "configuration", label: "配置说明", type: "textarea", span: 2 },
    { name: "supplier", label: "供应商" },
    { name: "warehouseName", label: "仓库" },
    { name: "stockStatus", label: "库存状态", type: "select", options: stockStatusOptions(), defaultValue: "IN_STOCK" },
    { name: "purchasePrice", label: "采购价", type: "number", coerce: "decimal", step: "0.01" },
    { name: "salePrice", label: "销售价", type: "number", coerce: "decimal", step: "0.01" },
    { name: "settlementPrice", label: "结算价", type: "number", coerce: "decimal", step: "0.01" },
    { name: "engineNumber", label: "发动机号" },
    { name: "frameNumber", label: "车架号" },
    { name: "warrantyCardNumber", label: "保修卡号" },
    { name: "manufacturingDate", label: "生产日期", type: "date", coerce: "date" },
    { name: "inboundDate", label: "入库时间", type: "datetime-local", coerce: "datetime" },
    { name: "inventoryCount", label: "库存数量", type: "number", coerce: "int", step: "1", defaultValue: 1 }
  ],
  part: [
    { name: "partCode", label: "配件编码", required: true },
    { name: "partName", label: "配件名称", type: "select", options: configValueOptions, allowCustom: true, required: true },
    { name: "partBrand", label: "品牌" },
    { name: "specification", label: "规格" },
    { name: "partCategory", label: "分类", type: "select", options: configPartCategoryOptions, allowCustom: true },
    { name: "applicableModels", label: "适配车型", span: 2 },
    { name: "source", label: "来源" },
    { name: "sourceMachineId", label: "来源车辆 ID", type: "number", coerce: "int", step: "1" },
    { name: "quantity", label: "数量", type: "number", coerce: "int", step: "1", required: true, defaultValue: 0 },
    { name: "unit", label: "单位", defaultValue: "件" },
    { name: "purchasePrice", label: "采购价", type: "number", coerce: "decimal", step: "0.01" },
    { name: "salePrice", label: "销售价", type: "number", coerce: "decimal", step: "0.01" },
    { name: "settlementPrice", label: "结算价", type: "number", coerce: "decimal", step: "0.01" },
    { name: "manufacturingDate", label: "生产日期", type: "date", coerce: "date" },
    { name: "inboundDate", label: "入库时间", type: "datetime-local", coerce: "datetime" },
    { name: "remarks", label: "备注", type: "textarea", span: 2 }
  ],
  repair: [
    { name: "repairDate", label: "维修时间", type: "datetime-local", coerce: "datetime", required: true, defaultValue: nowInputDateTime },
    { name: "status", label: "状态", type: "select", options: statusOptions(), defaultValue: "PENDING" },
    { name: "machineId", label: "车辆 ID", type: "number", coerce: "int", step: "1" },
    { name: "vehicleNumber", label: "车号" },
    { name: "customerName", label: "客户名称", required: true },
    { name: "customerAddress", label: "客户地址" },
    { name: "faultDescription", label: "故障描述", type: "textarea", span: 2, required: true },
    { name: "repairContent", label: "维修内容", type: "textarea", span: 2 },
    { name: "repairPerson", label: "维修人员" },
    { name: "usedParts", label: "使用配件", type: "textarea", span: 2 },
    { name: "workHours", label: "工时", type: "number", coerce: "decimal", step: "0.1" },
    { name: "repairFee", label: "维修费", type: "number", coerce: "decimal", step: "0.01" },
    { name: "partsFee", label: "配件费", type: "number", coerce: "decimal", step: "0.01" },
    { name: "totalFee", label: "总费用", type: "number", coerce: "decimal", step: "0.01" },
    { name: "remarks", label: "备注", type: "textarea", span: 2 }
  ],
  configItem: [
    { name: "category", label: "大类", type: "select", options: vehicleCategoryOptions, allowCustom: true, required: true },
    { name: "subCategory", label: "子类", type: "select", options: configSubCategoryOptions, allowCustom: true },
    { name: "itemName", label: "配置项名称", type: "select", options: configSubCategoryOptions, allowCustom: true, required: true },
    { name: "itemCode", label: "配置项编码", defaultValue: nextConfigItemCode },
    { name: "inputType", label: "输入类型", type: "select", options: inputTypeOptions(), defaultValue: "SELECT" },
    { name: "unit", label: "单位" },
    { name: "isRequired", label: "是否必填", type: "checkbox", coerce: "boolean" },
    { name: "sortOrder", label: "排序", type: "number", coerce: "int", step: "1", defaultValue: 0 }
  ],
  configValue: [
    { name: "configItemId", label: "所属配置项", type: "select", coerce: "int", required: true, options: configItemOptions },
    { name: "valueLabel", label: "显示值", required: true },
    { name: "valueCode", label: "值编码" },
    { name: "isDefault", label: "默认值", type: "checkbox", coerce: "boolean" },
    { name: "sortOrder", label: "排序", type: "number", coerce: "int", step: "1", defaultValue: 0 },
    { name: "remark", label: "备注", type: "textarea", span: 2 }
  ],
  vehicleStock: [
    { name: "machineId", label: "整车", type: "select", coerce: "int", required: true, options: vehicleOptions },
    { name: "quantity", label: "数量", type: "number", coerce: "int", step: "1", required: true, defaultValue: 1 },
    { name: "operator", label: "操作人" },
    { name: "remark", label: "备注", type: "textarea", span: 2 }
  ],
  partStock: [
    { name: "partCode", label: "配件", type: "select", required: true, options: partCodeOptions },
    { name: "quantity", label: "数量", type: "number", coerce: "int", step: "1", required: true, defaultValue: 1 },
    { name: "operator", label: "操作人" },
    { name: "remark", label: "备注", type: "textarea", span: 2 }
  ],
  partReplace: [
    { name: "machineId", label: "整车", type: "select", coerce: "int", required: true, options: vehicleOptions },
    { name: "machineConfigId", label: "原车配件", type: "select", coerce: "int", required: true, options: machineConfigOptions },
    { name: "newPartId", label: "同类型库存配件", type: "select", coerce: "int", required: true, options: compatiblePartOptions },
    { name: "operator", label: "操作人" },
    { name: "remark", label: "备注", type: "textarea", span: 2 }
  ],
  modificationOrder: [
    { name: "vehicleModelKey", label: "车型", type: "select", required: true, options: vehicleModelOptions },
    { name: "machineId", label: "车号", type: "select", coerce: "int", required: true, options: vehicleNumberOptions },
    { name: "machineConfigId", label: "原配置", type: "select", coerce: "int", required: true, options: machineConfigOptions },
    { name: "newPartId", label: "新配件", type: "select", coerce: "int", required: true, options: compatiblePartOptions },
    { name: "quantity", label: "数量", type: "number", coerce: "int", step: "1", required: true, placeholderValue: 1 },
    { name: "oldPartAction", label: "旧件处理", type: "select", options: oldPartActionOptions, placeholderValue: "STOCK_IN" },
    { name: "customerName", label: "客户名称" },
    { name: "salesOrderNo", label: "销售单号" },
    { name: "operator", label: "操作人" },
    { name: "remark", label: "备注", type: "textarea", span: 2 }
  ],
  user: [
    { name: "username", label: "用户名", required: true },
    { name: "password", label: "初始密码", type: "password", required: true },
    { name: "role", label: "角色", type: "select", options: userRoleOptions, required: true, defaultValue: "USER" }
  ],
  userUsername: [
    { name: "username", label: "新用户名", required: true }
  ],
  userPassword: [
    { name: "password", label: "新密码", type: "password", required: true }
  ],
  switchUser: [
    { name: "username", label: "用户名", required: true },
    { name: "password", label: "密码", type: "password", required: true }
  ]
};

const endpoints = {
  vehicle: {
    create: "/api/inventory",
    update: id => `/api/inventory/${id}`,
    delete: id => `/api/inventory/${id}`
  },
  part: {
    create: "/api/parts",
    update: id => `/api/parts/${id}`,
    delete: id => `/api/parts/${id}`
  },
  repair: {
    create: "/api/repairs",
    update: id => `/api/repairs/${id}`,
    delete: id => `/api/repairs/${id}`
  },
  configItem: {
    create: "/api/config/items",
    update: id => `/api/config/items/${id}`,
    delete: id => `/api/config/items/${id}`
  },
  configValue: {
    create: "/api/config/values",
    delete: id => `/api/config/values/${id}`
  },
  partReplace: {
    create: "/api/replace/part"
  },
  modificationOrder: {
    list: "/api/modification-work-orders",
    create: "/api/modification-work-orders",
    complete: id => `/api/modification-work-orders/${id}/complete`,
    cancel: id => `/api/modification-work-orders/${id}/cancel`
  },
  user: {
    list: "/api/auth/users",
    create: "/api/auth/register",
    updateUsername: id => `/api/auth/users/${id}/username`,
    updatePassword: id => `/api/auth/users/${id}/password`,
    delete: id => `/api/auth/users/${id}`
  },
  logs: "/api/logs",
  statistics: "/api/statistics/finance"
};

let els;

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
    toastHost: document.getElementById("toastHost")
  };

  els.loginForm.addEventListener("submit", handleLogin);
  els.switchUserBtn.addEventListener("click", () => openEntityModal("switchUser", { username: "" }));
  els.logoutBtn.addEventListener("click", () => logout("已退出登录"));
  els.mainNav.addEventListener("click", handleNav);
  els.content.addEventListener("click", handleContentClick);
  els.content.addEventListener("input", handleContentInput);
  els.content.addEventListener("change", handleContentChange);
  els.modalOverlay.addEventListener("click", handleOverlayClick);
  els.modalCard.addEventListener("click", handleModalClick);
  els.modalCard.addEventListener("input", handleModalInput);
  els.modalCard.addEventListener("focusin", handleModalFocusIn);
  els.modalCard.addEventListener("change", handleModalChange);
  els.modalCard.addEventListener("submit", handleModalSubmit);
  document.addEventListener("keydown", event => {
    if (event.key === "Escape" && state.modal) closeModal();
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

async function handleLogin(event) {
  event.preventDefault();
  const form = new FormData(event.currentTarget);
  const username = String(form.get("username") || "").trim();
  const password = String(form.get("password") || "");

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
  }
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
    logout("登录已过期，请重新登录");
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
  clearSession();
  closeModal();
  showLogin();
  if (message) showToast(message, "info");
}

async function loadAllData() {
  const [vehicles, parts, repairs, configItems, modificationOrders] = await Promise.all([
    api("/api/inventory"),
    api("/api/parts"),
    api("/api/repairs"),
    api("/api/config/items"),
    hasPermission("replace:write") ? api(endpoints.modificationOrder.list) : Promise.resolve([])
  ]);

  state.data.vehicles = sortById(vehicles);
  state.data.parts = sortById(parts);
  state.data.repairs = sortById(repairs);
  state.data.configItems = sortById(configItems, false);
  state.data.modificationOrders = sortById(modificationOrders);
  await loadConfigValueCache(state.data.configItems);

  if (!state.selectedConfigItemId && state.data.configItems.length) {
    state.selectedConfigItemId = state.data.configItems[0].id;
  }
  if (state.selectedConfigItemId) {
    state.data.configValues = sortById(state.data.configValueMap[state.selectedConfigItemId] || await fetchConfigValues(state.selectedConfigItemId), false);
  }
  const [users, operationLogs, statistics] = await Promise.all([
    hasPermission("user:read") ? api(endpoints.user.list) : Promise.resolve([]),
    hasPermission("log:read") ? api(endpoints.logs) : Promise.resolve([]),
    hasPermission("log:read") ? api(`${endpoints.statistics}?year=${state.selectedStatsYear}`) : Promise.resolve(null)
  ]);
  state.data.users = sortById(users);
  state.data.operationLogs = sortLogs(operationLogs);
  state.data.statistics = statistics;
  if (state.selectedVehicleId) {
    await loadVehicleDetail(state.selectedVehicleId, false);
  }
}

async function loadStatistics(year) {
  state.selectedStatsYear = Number(year || new Date().getFullYear());
  state.data.statistics = await api(`${endpoints.statistics}?year=${state.selectedStatsYear}`);
}

async function fetchConfigValues(itemId) {
  if (!itemId) return [];
  return api(`/api/config/items/${itemId}/values`);
}

async function loadConfigValueCache(items = state.data.configItems) {
  const entries = await Promise.all((items || []).map(async item => {
    const values = sortById(await fetchConfigValues(item.id), false);
    return [item.id, values];
  }));
  state.data.configValueMap = Object.fromEntries(entries);
}

async function loadConfigValues(itemId) {
  state.selectedConfigItemId = Number(itemId);
  const values = sortById(await fetchConfigValues(itemId), false);
  state.data.configValueMap[state.selectedConfigItemId] = values;
  state.data.configValues = values;
  renderCurrentTab();
}

async function loadVehicleDetail(id, shouldRender = true) {
  state.selectedVehicleId = Number(id);
  const [detail, logs, workOrders] = await Promise.all([
    api(`/api/inventory/${id}/detail`),
    api(`/api/replace/machine/${id}`),
    hasPermission("replace:write") ? api(`/api/modification-work-orders/machine/${id}`) : Promise.resolve([])
  ]);
  state.vehicleDetail = { ...detail, logs: sortById(logs), workOrders: sortById(workOrders) };
  if (shouldRender) renderCurrentTab();
}

async function loadVehicleModelDetail(modelKey, selectedMachineId = null, shouldRender = true) {
  const vehicles = vehiclesForModelKey(modelKey);
  const selectedId = Number(selectedMachineId || state.selectedVehicleId || vehicles[0]?.id || 0);
  const selected = vehicles.find(vehicle => Number(vehicle.id) === selectedId) || vehicles[0];
  if (!selected) {
    state.vehicleDetail = null;
    if (shouldRender) renderCurrentTab();
    return;
  }
  state.selectedVehicleId = Number(selected.id);
  const [detail, logs, workOrders] = await Promise.all([
    api(`/api/inventory/${selected.id}/detail`),
    api(`/api/replace/machine/${selected.id}`),
    hasPermission("replace:write") ? api(`/api/modification-work-orders/machine/${selected.id}`) : Promise.resolve([])
  ]);
  state.vehicleDetail = {
    modelKey,
    model: vehicleModelGroups().find(group => group.modelKey === modelKey) || { ...decodeVehicleModelKey(modelKey), modelKey, vehicles },
    vehicles,
    selectedMachineId: Number(selected.id),
    selectedDetail: { ...detail, logs: sortById(logs), workOrders: sortById(workOrders) }
  };
  if (shouldRender) renderCurrentTab();
}

function handleNav(event) {
  const button = event.target.closest("[data-tab]");
  if (!button) return;
  state.activeTab = button.dataset.tab;
  renderCurrentTab();
  scrollToWorkspaceTop();
}

async function handleContentClick(event) {
  const button = event.target.closest("button[data-action]");
  if (!button) return;

  const action = button.dataset.action;
  const kind = button.dataset.kind;
  const id = button.dataset.id ? Number(button.dataset.id) : null;

  try {
    if (action === "refresh") {
      els.content.innerHTML = renderLoading();
      await loadAllData();
      renderCurrentTab();
      showToast("数据已刷新", "success");
      return;
    }
    if (action === "load-more-logs") {
      state.visibleLogRows += LOG_PAGE_SIZE;
      renderCurrentTab();
      return;
    }
    if (action === "create") {
      await openEntityModal(kind, defaultEntity(kind));
      return;
    }
    if (action === "model-inbound") {
      const group = vehicleModelGroups().find(item => item.modelKey === button.dataset.modelKey);
      await openEntityModal("vehicle", vehicleDefaultsForModel(group));
      return;
    }
    if (action === "vehicle-stock") {
      const vehicle = findEntity("vehicle", id || Number(button.dataset.machineId || ""));
      await openEntityModal("vehicleStock", {
        machineId: id || Number(button.dataset.machineId || ""),
        version: vehicle.version,
        quantity: 1,
        direction: button.dataset.direction
      });
      return;
    }
    if (action === "part-stock") {
      const part = state.data.parts.find(item => item.partCode === button.dataset.partCode) || {};
      await openEntityModal("partStock", {
        partCode: button.dataset.partCode || "",
        version: part.version,
        quantity: 1,
        direction: button.dataset.direction
      });
      return;
    }
    if (action === "edit") {
      await openEntityModal(kind, findEntity(kind, id));
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
      if (button.dataset.modelKey) {
        await loadVehicleModelDetail(button.dataset.modelKey);
      } else {
        await loadVehicleDetail(id);
      }
      return;
    }
    if (action === "select-model-vehicle") {
      await loadVehicleModelDetail(button.dataset.modelKey, Number(button.dataset.machineId || 0));
      return;
    }
    if (action === "select-config-item") {
      await loadConfigValues(id);
      return;
    }
    if (action === "part-replace") {
      const machineId = Number(button.dataset.machineId || state.selectedVehicleId || "");
      const machine = findEntity("vehicle", machineId);
      await openEntityModal("partReplace", {
        machineId,
        machineVersion: machine.version,
        machineConfigId: button.dataset.machineConfigId ? Number(button.dataset.machineConfigId) : null
      });
      return;
    }
    if (action === "create-modification-order") {
      const machineId = Number(button.dataset.machineId || state.selectedVehicleId || "");
      const machine = findEntity("vehicle", machineId);
      const configId = button.dataset.machineConfigId ? Number(button.dataset.machineConfigId) : null;
      await openEntityModal("modificationOrder", {
        __placeholders: modificationOrderPlaceholders(machine, configId)
      });
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

function handleContentInput(event) {
  const input = event.target.closest("[data-search-for]");
  if (!input) return;
  state.search[input.dataset.searchFor] = input.value;
  if (input.dataset.searchFor === "logs") {
    state.visibleLogRows = LOG_PAGE_SIZE;
  }
  renderCurrentTab();
  const nextInput = els.content.querySelector(`[data-search-for="${input.dataset.searchFor}"]`);
  if (nextInput) {
    nextInput.focus();
    nextInput.setSelectionRange(input.value.length, input.value.length);
  }
}

async function handleContentChange(event) {
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

function handleOverlayClick(event) {
  if (event.target === els.modalOverlay) closeModal();
}

function handleModalClick(event) {
  const comboOption = event.target.closest("[data-combo-option]");
  if (comboOption) {
    event.preventDefault();
    selectComboOption(comboOption);
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
  if (!input) return;
  const combo = input.closest("[data-combo]");
  const hidden = combo.querySelector("input[type='hidden']");
  const query = input.value.trim();
  const allowCustom = combo.dataset.allowCustom === "true";
  const exact = findComboOption(combo, query);
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
  filterComboOptions(combo, query);
  combo.classList.add("is-open");
}

function handleModalFocusIn(event) {
  const input = event.target.closest("[data-combo-input]");
  if (!input) return;
  const combo = input.closest("[data-combo]");
  filterComboOptions(combo, input.value.trim());
  combo.classList.add("is-open");
}

async function handleModalChange(event) {
  const form = event.target.closest("form");
  if (!form) return;
  const kind = form.dataset.kind;
  if (state.modal?.item && event.target.name) {
    state.modal.item[event.target.name] = event.target.value;
  }

  if (kind === "configItem") {
    syncConfigItemFields(form, event.target.name);
  }

  if (kind === "part") {
    syncPartDictionaryFields(form, event.target.name);
  }

  if (kind === "modificationOrder" && event.target.name === "vehicleModelKey") {
    state.modal.item.machineId = null;
    state.modal.item.machineConfigId = null;
    state.modal.item.newPartId = null;
    state.modal.item.__placeholders = {};
    state.modal.context.machine = null;
    state.modal.context.machineConfigs = [];
    state.modal.context.compatibleParts = [];
    renderModal();
    return;
  }

  if (kind === "partReplace" || kind === "modificationOrder") {
    if (event.target.name === "machineId") {
      await preparePartReplaceContext(Number(event.target.value || 0), null, { placeholderConfig: kind === "modificationOrder" });
      renderModal();
      return;
    }
    if (event.target.name === "machineConfigId") {
      updateCompatibleParts(Number(event.target.value || 0));
      syncComboOptionsForField(form, "newPartId", compatiblePartOptions(), "");
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
  const activeModelKey = state.vehicleDetail?.modelKey;
  const payload = serializeForm(kind, form);
  attachVersion(payload, item);

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

    if (kind === "vehicleStock") {
      attachStockVersion(payload, "vehicle");
      await api(`/api/inventory/${payload.machineId}/${item.direction}`, {
        method: "PUT",
        body: { quantity: payload.quantity, operator: payload.operator, remark: payload.remark, version: payload.version }
      });
      showToast(item.direction === "inbound" ? "整车入库成功" : "整车出库成功", "success");
    } else if (kind === "partStock") {
      attachStockVersion(payload, "part");
      await api(`/api/parts/${item.direction}`, {
        method: "PUT",
        body: { partCode: payload.partCode, quantity: payload.quantity, operator: payload.operator, remark: payload.remark, version: payload.version }
      });
      showToast(item.direction === "inbound" ? "配件入库成功" : "配件出库成功", "success");
    } else if (kind === "partReplace") {
      enrichPartReplacePayload(payload);
      await api(endpoints.partReplace.create, { method: "POST", body: payload });
      showToast("配件替换成功，旧件已自动入库", "success");
    } else if (kind === "modificationOrder") {
      enrichPartReplacePayload(payload);
      await api(endpoints.modificationOrder.create, {
        method: "POST",
        body: buildModificationOrderPayload(payload)
      });
      showToast("改装工单已创建", "success");
    } else if (kind === "user") {
      await api(endpoints.user.create, { method: "POST", body: payload });
      showToast("用户创建成功", "success");
    } else if (kind === "userUsername") {
      await api(endpoints.user.updateUsername(item.id), { method: "PUT", body: payload });
      showToast("用户名修改成功", "success");
    } else if (kind === "userPassword") {
      await api(endpoints.user.updatePassword(item.id), { method: "PUT", body: payload });
      showToast("用户密码修改成功", "success");
    } else if (item.id && endpoints[kind].update) {
      await api(endpoints[kind].update(item.id), { method: "PUT", body: payload });
      showToast("保存成功", "success");
    } else {
      await api(endpoints[kind].create, { method: "POST", body: payload });
      showToast("新增成功", "success");
    }
    closeModal();
    await loadAllData();
    if (kind === "vehicle" && activeModelKey) {
      await loadVehicleModelDetail(activeModelKey, state.selectedVehicleId, false);
    } else if ((kind === "partReplace" || kind === "modificationOrder") && payload.machineId) {
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
  const message = kind === "user" ? "确认删除这个用户？" : "确认删除这条数据？";
  if (!confirm(message)) return;
  await api(withVersion(endpoints[kind].delete(id), item), { method: "DELETE" });
  showToast("删除成功", "success");
  if (kind === "configValue") {
    await loadConfigValues(state.selectedConfigItemId);
  } else {
    await loadAllData();
    renderCurrentTab();
  }
}

async function completeModificationOrder(id) {
  const order = findEntity("modificationOrder", id);
  if (!order.id) return;
  if (!confirm("确认完成这张改装工单？完成后会扣减新配件库存、旧件入库，并更新车辆配置。")) return;
  const activeModelKey = state.vehicleDetail?.modelKey;
  await api(endpoints.modificationOrder.complete(id), {
    method: "PUT",
    body: { version: order.version }
  });
  showToast("改装工单已完成", "success");
  await loadAllData();
  if (state.selectedVehicleId) {
    if (activeModelKey) {
      await loadVehicleModelDetail(activeModelKey, state.selectedVehicleId, false);
    } else {
      await loadVehicleDetail(state.selectedVehicleId, false);
    }
  }
  renderCurrentTab();
}

async function cancelModificationOrder(id) {
  const order = findEntity("modificationOrder", id);
  if (!order.id) return;
  if (!confirm("确认取消这张改装工单？")) return;
  const activeModelKey = state.vehicleDetail?.modelKey;
  await api(endpoints.modificationOrder.cancel(id), {
    method: "PUT",
    body: { version: order.version }
  });
  showToast("改装工单已取消", "success");
  await loadAllData();
  if (state.selectedVehicleId) {
    if (activeModelKey) {
      await loadVehicleModelDetail(activeModelKey, state.selectedVehicleId, false);
    } else {
      await loadVehicleDetail(state.selectedVehicleId, false);
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

  if (payload.machineVersion === undefined && machine?.version !== undefined) {
    payload.machineVersion = machine.version;
  }
  if (payload.machineConfigVersion === undefined && config?.version !== undefined) {
    payload.machineConfigVersion = config.version;
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
        quantity: payload.quantity || 1,
        oldPartAction: payload.oldPartAction || "STOCK_IN",
        remark: payload.remark
      }
    ]
  };
}

async function openEntityModal(kind, item = {}) {
  if (kind === "configValue" && !state.data.configItems.length) {
    state.data.configItems = sortById(await api("/api/config/items"), false);
  }

  state.modal = { kind, item: { ...item }, context: {} };
  if (kind === "partReplace" || kind === "modificationOrder") {
    await preparePartReplaceContext(item.machineId || state.selectedVehicleId, item.machineConfigId, { placeholderConfig: kind === "modificationOrder" });
  }
  renderModal();
}

function closeModal() {
  state.modal = null;
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
  const selectedConfigId = machineConfigId || state.modal.context.machineConfigs[0]?.id || null;
  if (options.placeholderConfig) {
    const placeholderConfig = state.modal.context.machineConfigs
      .find(item => String(item.id) === String(selectedConfigId));
    state.modal.item.machineConfigId = null;
    state.modal.item.newPartId = null;
    state.modal.item.__placeholders = {
      ...(state.modal.item.__placeholders || {}),
      machineConfigId: placeholderConfig ? `预填：${machineConfigOptionLabel(placeholderConfig)}` : undefined
    };
  } else {
    state.modal.item.machineConfigId = selectedConfigId;
  }
  updateCompatibleParts(selectedConfigId);
  if (options.placeholderConfig) {
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

function renderModal() {
  if (!state.modal) return;
  const { kind, item } = state.modal;
  const title = modalTitle(kind, item);
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
          ${fields[kind].map(field => renderField(field, item)).join("")}
        </div>
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
    case "repairs":
      els.content.innerHTML = renderRepairs();
      break;
    case "stats":
      els.content.innerHTML = renderStatistics();
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
    default:
      els.content.innerHTML = renderOverview();
  }
}

function scrollToWorkspaceTop() {
  window.scrollTo({ top: 0, behavior: "smooth" });
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
  });
}

function renderOverview() {
  const vehicles = state.data.vehicles;
  const vehicleModels = vehicleModelGroups();
  const parts = state.data.parts;
  const repairs = state.data.repairs;
  const pendingRepairs = repairs.filter(item => item.status !== "COMPLETED").length;
  const lowParts = parts.filter(item => Number(item.quantity || 0) <= 0).length;

  return `
    <div class="page">
      <section class="summary-grid">
        ${summaryCard("车辆库存", vehicles.length, "台整车档案")}
        ${summaryCard("配件库存", parts.length, `${lowParts} 项库存不足`)}
        ${summaryCard("维修记录", repairs.length, `${pendingRepairs} 单待跟进`)}
        ${summaryCard("配置项", state.data.configItems.length, "项配置字典")}
      </section>

      <section class="grid-three">
        ${renderSurface("最近车辆", compactTable([
          { label: "车型", render: row => vehicleModelLabel(row) },
          { label: "车辆数", key: "unitCount" },
          { label: "库存", key: "inventoryCount", formatter: stockText }
        ], vehicleModels.slice(0, 6)))}
        ${renderSurface("最近配件", compactTable([
          { label: "编码", key: "partCode" },
          { label: "名称", key: "partName" },
          { label: "数量", key: "quantity", formatter: stockText }
        ], parts.slice(0, 6)))}
        ${renderSurface("维修跟进", compactTable([
          { label: "客户", key: "customerName" },
          { label: "状态", html: true, render: row => statusBadge(row.status) },
          { label: "费用", key: "totalFee", formatter: money }
        ], repairs.slice(0, 6)))}
      </section>

      <section class="surface">
        <div class="surface-head">
          <h2 class="surface-title">快捷操作</h2>
          <button class="btn btn-ghost" type="button" data-action="refresh">${icon("refresh")}刷新数据</button>
        </div>
        <div class="surface-body toolbar-actions">
          <button class="btn btn-primary" type="button" data-action="create" data-kind="vehicle">${icon("plus")}新增车辆</button>
          <button class="btn" type="button" data-action="create" data-kind="part">${icon("plus")}新增配件</button>
          ${hasPermission("replace:write") ? `<button class="btn" type="button" data-action="create" data-kind="modificationOrder">${icon("swap")}新建改装工单</button>` : ""}
          <button class="btn" type="button" data-action="create" data-kind="repair">${icon("plus")}新增维修</button>
          <button class="btn" type="button" data-action="create" data-kind="configItem">${icon("plus")}新增配置项</button>
        </div>
      </section>
    </div>
  `;
}

function renderVehicles() {
  const rows = filterRows(vehicleModelGroups(), state.search.vehicles, [
    "name", "specificationModel", "machineType", "supplier", "warehouseName", "vehicleNumbers"
  ]);

  return `
    <div class="page">
      <div class="toolbar">
        ${searchBox("vehicles", "搜索车型、型号、供应商或车号")}
        <div class="toolbar-actions">
          ${hasPermission("vehicle:write") ? `<button class="btn btn-primary" type="button" data-action="create" data-kind="vehicle">${icon("plus")}新增车型/单车</button>` : ""}
          <button class="btn btn-ghost" type="button" data-action="refresh">${icon("refresh")}刷新</button>
        </div>
      </div>
      ${renderSurface("车型库存列表", renderTable([
        { label: "车型", render: row => vehicleModelLabel(row) },
        { label: "规格型号", key: "specificationModel" },
        { label: "车辆类型", key: "machineType" },
        { label: "供应商", key: "supplier" },
        { label: "仓库", key: "warehouseName" },
        { label: "车辆数", key: "unitCount" },
        { label: "库存", html: true, render: row => stockBadge(row.inventoryCount, "台") },
        { label: "销售价", key: "salePrice", formatter: money },
        { label: "操作", html: true, render: row => vehicleModelActions(row) }
      ], rows))}
      ${renderVehicleDetail()}
    </div>
  `;
}

function renderParts() {
  const rows = filterRows(state.data.parts, state.search.parts, [
    "partCode", "partName", "partBrand", "partCategory", "applicableModels", "source"
  ]);

  return `
    <div class="page">
      <div class="toolbar">
        ${searchBox("parts", "搜索编码、名称、品牌、分类")}
        <div class="toolbar-actions">
          ${hasPermission("part:write") ? `<button class="btn btn-primary" type="button" data-action="create" data-kind="part">${icon("plus")}新增配件</button>` : ""}
          ${hasPermission("stock:adjust") ? `<button class="btn" type="button" data-action="part-stock" data-direction="inbound">${icon("plus")}配件入库</button>` : ""}
          ${hasPermission("stock:adjust") ? `<button class="btn" type="button" data-action="part-stock" data-direction="outbound">${icon("minus")}配件出库</button>` : ""}
          <button class="btn btn-ghost" type="button" data-action="refresh">${icon("refresh")}刷新</button>
        </div>
      </div>
      ${renderSurface("配件列表", renderTable([
        { label: "编码", key: "partCode" },
        { label: "名称", key: "partName" },
        { label: "品牌", key: "partBrand" },
        { label: "分类", key: "partCategory" },
        { label: "数量", html: true, render: row => stockBadge(row.quantity, row.unit) },
        { label: "销售价", key: "salePrice", formatter: money },
        { label: "来源", key: "source" },
        { label: "操作", html: true, render: row => rowActions("part", row, ["stockIn", "stockOut", "edit", "delete"]) }
      ], rows))}
    </div>
  `;
}

function renderModificationOrders() {
  const rows = filterRows(state.data.modificationOrders, state.search.modificationOrders, [
    "workOrderNo", "customerName", "salesOrderNo", "status", "operator", "remark"
  ]);

  return `
    <div class="page">
      <div class="toolbar">
        ${searchBox("modificationOrders", "搜索工单号、客户、销售单或状态")}
        <div class="toolbar-actions">
          ${hasPermission("replace:write") ? `<button class="btn btn-primary" type="button" data-action="create" data-kind="modificationOrder">${icon("plus")}新建改装工单</button>` : ""}
          <button class="btn btn-ghost" type="button" data-action="refresh">${icon("refresh")}刷新</button>
        </div>
      </div>
      ${renderSurface("改装工单列表", renderTable([
        { label: "工单号", key: "workOrderNo" },
        { label: "车辆ID", key: "machineId" },
        { label: "客户", key: "customerName" },
        { label: "销售单", key: "salesOrderNo" },
        { label: "状态", html: true, render: row => modificationStatusBadge(row.status) },
        { label: "替换明细", html: true, render: row => modificationLineSummary(row.lines) },
        { label: "创建时间", key: "createdAt", formatter: dateTime },
        { label: "操作", html: true, render: row => modificationOrderActions(row) }
      ], rows))}
    </div>
  `;
}

function renderRepairs() {
  const rows = filterRows(state.data.repairs, state.search.repairs, [
    "vehicleNumber", "customerName", "repairPerson", "status", "faultDescription"
  ]);

  return `
    <div class="page">
      ${renderToolbar("repairs", "搜索客户、车号、维修人、状态", "repair", "新增维修", "repair:write")}
      ${renderSurface("维修记录", renderTable([
        { label: "维修时间", key: "repairDate", formatter: dateTime },
        { label: "车号", key: "vehicleNumber" },
        { label: "客户", key: "customerName" },
        { label: "维修人", key: "repairPerson" },
        { label: "状态", html: true, render: row => statusBadge(row.status) },
        { label: "总费用", key: "totalFee", formatter: money },
        { label: "操作", html: true, render: row => rowActions("repair", row, ["edit", "delete"]) }
      ], rows))}
    </div>
  `;
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
        ${summaryCard("年度总收入", money(annual.totalIncome), `出库 ${money(annual.outboundRevenue)} / 维修 ${money(annual.repairIncome)}`)}
        ${summaryCard("入库成本", money(annual.inboundCost), `${annual.inboundQuantity || 0} 件/台入库`)}
        ${summaryCard("出库毛利", money(annual.grossProfit), `${annual.outboundQuantity || 0} 件/台出库`)}
        ${summaryCard("维修收入", money(annual.repairIncome), `${annual.repairOrders || 0} 单维修`)}
      </section>

      ${renderSurface("月度收支走势", renderFinanceBars(monthlyRows))}

      <section class="grid-two">
        ${renderSurface("月度财报", renderTable([
          { label: "月份", key: "period" },
          { label: "入库成本", key: "inboundCost", formatter: money },
          { label: "出库收入", key: "outboundRevenue", formatter: money },
          { label: "出库成本", key: "outboundCost", formatter: money },
          { label: "维修收入", key: "repairIncome", formatter: money },
          { label: "毛利", key: "grossProfit", formatter: money }
        ], monthlyRows))}
        ${renderSurface("年度对比", renderTable([
          { label: "年份", key: "period" },
          { label: "总收入", key: "totalIncome", formatter: money },
          { label: "入库成本", key: "inboundCost", formatter: money },
          { label: "出库毛利", key: "grossProfit", formatter: money },
          { label: "维修单", key: "repairOrders" }
        ], yearlyRows))}
      </section>

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
      <div class="toolbar logs-toolbar">
        ${searchBox("logs", "搜索日志类型、对象、操作人或备注")}
        <div class="toolbar-actions">
          <div class="logs-summary">
            <span>共 ${escapeHtml(state.data.operationLogs.length)} 条</span>
            <span>当前显示 ${escapeHtml(visibleRows.length)} / ${escapeHtml(rows.length)} 条</span>
          </div>
          <button class="btn btn-ghost" type="button" data-action="refresh">${icon("refresh")}刷新</button>
        </div>
      </div>
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
      <div class="toolbar">
        <div class="toolbar-actions">
          ${searchBox("configItems", "搜索配置项")}
          ${searchBox("configValues", "搜索配置值")}
        </div>
        <div class="toolbar-actions">
          ${hasPermission("config:write") ? `<button class="btn btn-primary" type="button" data-action="create" data-kind="configItem">${icon("plus")}新增配置项</button>` : ""}
          ${hasPermission("config:write") ? `<button class="btn" type="button" data-action="create" data-kind="configValue">${icon("plus")}新增配置值</button>` : ""}
          <button class="btn btn-ghost" type="button" data-action="refresh">${icon("refresh")}刷新</button>
        </div>
      </div>

      <section class="config-layout">
        ${renderSurface("配置项", `
          <div class="config-list">
            ${items.length ? items.map(renderConfigItemCard).join("") : emptyState("暂无配置项")}
          </div>
        `)}
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
      </section>
    </div>
  `;
}

function renderUsers() {
  const rows = filterRows(state.data.users, state.search.users, [
    "username", "roles"
  ]);

  return `
    <div class="page">
      <div class="toolbar">
        ${searchBox("users", "搜索用户名或角色")}
        <div class="toolbar-actions">
          ${hasPermission("user:write") ? `<button class="btn btn-primary" type="button" data-action="create" data-kind="user">${icon("plus")}新建用户</button>` : ""}
          <button class="btn btn-ghost" type="button" data-action="refresh">${icon("refresh")}刷新</button>
        </div>
      </div>
      ${renderSurface("用户列表", renderTable([
        { label: "用户名", key: "username" },
        { label: "角色", html: true, render: row => roleBadges(row.roles) },
        { label: "状态", html: true, render: row => row.enabled ? badge("启用", "teal") : badge("停用", "danger") },
        { label: "创建时间", key: "createdAt", formatter: dateTime },
        { label: "操作", html: true, render: row => userActions(row) }
      ], rows))}
    </div>
  `;
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
  return `
    <section class="surface">
      <div class="surface-head">
        <h2 class="surface-title">车辆详情 · ${escapeHtml(machine.vehicleProductNumber || "")}</h2>
        <div class="toolbar-actions">
          ${hasPermission("replace:write") ? `<button class="btn btn-primary" type="button" data-action="create-modification-order" data-machine-id="${escapeAttr(machine.id || "")}">${icon("swap")}新建改装工单</button>` : ""}
        </div>
      </div>
      <div class="surface-body split">
        <div class="detail-grid">
          ${detailItem("名称", machine.name)}
          ${detailItem("规格型号", machine.specificationModel)}
          ${detailItem("供应商", machine.supplier)}
          ${detailItem("仓库", machine.warehouseName)}
          ${detailItem("库存状态", stockStatusLabel(machine.stockStatus))}
          ${detailItem("发动机号", machine.engineNumber)}
          ${detailItem("车架号", machine.frameNumber)}
          ${detailItem("入库时间", dateTime(machine.inboundDate))}
        </div>
        <div class="grid-two">
          ${renderSurface("当前配置", renderTable([
            { label: "配置项", key: "itemName" },
            { label: "当前值", key: "selectedValue" },
            { label: "来源", key: "configSource" },
            { label: "操作", html: true, render: row => `
              <div class="action-row">
                ${hasPermission("replace:write") ? `<button class="btn btn-sm" type="button" data-action="create-modification-order" data-machine-id="${escapeAttr(machine.id)}" data-machine-config-id="${escapeAttr(row.id)}">${icon("swap")}生成工单</button>` : ""}
              </div>
            ` }
          ], configs))}
          ${renderSurface("改装工单", renderTable([
            { label: "工单号", key: "workOrderNo" },
            { label: "客户", key: "customerName" },
            { label: "状态", html: true, render: row => modificationStatusBadge(row.status) },
            { label: "替换明细", html: true, render: row => modificationLineSummary(row.lines) },
            { label: "操作", html: true, render: row => modificationOrderActions(row) }
          ], workOrders))}
          ${renderSurface("替换记录", renderTable([
            { label: "配置项", key: "itemName" },
            { label: "旧值", key: "oldValue" },
            { label: "新值", key: "newValue" },
            { label: "类型", key: "replaceType" },
            { label: "时间", key: "createdAt", formatter: dateTime }
          ], logs))}
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
  return `
    <section class="surface">
      <div class="surface-head">
        <h2 class="surface-title">车型详情 · ${escapeHtml(vehicleModelLabel(model))}</h2>
        <div class="toolbar-actions">
          ${hasPermission("vehicle:write") ? `<button class="btn btn-primary" type="button" data-action="model-inbound" data-model-key="${escapeAttr(detail.modelKey)}">${icon("plus")}此车型入库</button>` : ""}
        </div>
      </div>
      <div class="surface-body split">
        <div class="detail-grid">
          ${detailItem("车型名称", model.name)}
          ${detailItem("规格型号", model.specificationModel)}
          ${detailItem("车辆类型", model.machineType)}
          ${detailItem("供应商", model.supplier)}
          ${detailItem("仓库", model.warehouseName)}
          ${detailItem("库存台数", `${model.inventoryCount || 0} 台`)}
        </div>
        <div class="grid-two">
          ${renderSurface("库存车号", `
            <div class="vehicle-unit-grid">
              ${detail.vehicles.map(vehicle => renderVehicleUnitCard(vehicle, detail.modelKey, vehicle.id === selected.id)).join("")}
            </div>
          `)}
          ${renderSurface(`当前配置 · ${escapeHtml(selected.vehicleProductNumber || "请选择车号")}`, `
            <div class="detail-grid detail-grid-compact">
              ${detailItem("车号", selected.vehicleProductNumber)}
              ${detailItem("发动机号", selected.engineNumber)}
              ${detailItem("车架号", selected.frameNumber)}
              ${detailItem("保修卡号", selected.warrantyCardNumber)}
              ${detailItem("结算价", money(selected.settlementPrice))}
              ${detailItem("库存状态", stockStatusLabel(selected.stockStatus))}
            </div>
            <div class="toolbar-actions detail-action-row">
              ${hasPermission("replace:write") ? `<button class="btn btn-primary" type="button" data-action="create-modification-order" data-machine-id="${escapeAttr(selected.id || "")}">${icon("swap")}新建改装工单</button>` : ""}
            </div>
            ${renderTable([
              { label: "配置项", key: "itemName" },
              { label: "当前值", key: "selectedValue" },
              { label: "来源", key: "configSource" },
              { label: "操作", html: true, render: row => `
                <div class="action-row">
                  ${hasPermission("replace:write") ? `<button class="btn btn-sm" type="button" data-action="create-modification-order" data-machine-id="${escapeAttr(selected.id)}" data-machine-config-id="${escapeAttr(row.id)}">${icon("swap")}生成工单</button>` : ""}
                </div>
              ` }
            ], configs)}
          `)}
          ${renderSurface("改装工单", renderTable([
            { label: "工单号", key: "workOrderNo" },
            { label: "客户", key: "customerName" },
            { label: "状态", html: true, render: row => modificationStatusBadge(row.status) },
            { label: "替换明细", html: true, render: row => modificationLineSummary(row.lines) },
            { label: "操作", html: true, render: row => modificationOrderActions(row) }
          ], workOrders))}
          ${renderSurface("替换记录", renderTable([
            { label: "配置项", key: "itemName" },
            { label: "旧值", key: "oldValue" },
            { label: "新值", key: "newValue" },
            { label: "类型", key: "replaceType" },
            { label: "时间", key: "createdAt", formatter: dateTime }
          ], logs))}
        </div>
      </div>
    </section>
  `;
}

function renderVehicleUnitCard(vehicle, modelKey, active) {
  return `
    <button class="vehicle-unit-card${active ? " is-active" : ""}" type="button" data-action="select-model-vehicle" data-model-key="${escapeAttr(modelKey)}" data-machine-id="${escapeAttr(vehicle.id)}">
      <span class="vehicle-unit-title">${escapeHtml(vehicle.vehicleProductNumber || `ID ${vehicle.id}`)}</span>
      <span class="vehicle-unit-meta">${escapeHtml(stockStatusLabel(vehicle.stockStatus))} · ${escapeHtml(vehicle.engineNumber || "未填发动机号")}</span>
      <span class="vehicle-unit-meta">${escapeHtml(vehicle.frameNumber || "未填车架号")}</span>
    </button>
  `;
}

function renderToolbar(moduleKey, placeholder, kind, createLabel, createPermission) {
  return `
    <div class="toolbar">
      ${searchBox(moduleKey, placeholder)}
      <div class="toolbar-actions">
        ${!createPermission || hasPermission(createPermission) ? `<button class="btn btn-primary" type="button" data-action="create" data-kind="${kind}">${icon("plus")}${createLabel}</button>` : ""}
        <button class="btn btn-ghost" type="button" data-action="refresh">${icon("refresh")}刷新</button>
      </div>
    </div>
  `;
}

function searchBox(key, placeholder) {
  return `
    <label class="search-box">
      <span class="search-icon">${icons.search}</span>
      <input class="input" data-search-for="${escapeAttr(key)}" value="${escapeAttr(state.search[key] || "")}" placeholder="${escapeAttr(placeholder)}">
    </label>
  `;
}

function renderSurface(title, body) {
  return `
    <section class="surface">
      <div class="surface-head">
        <h2 class="surface-title">${title}</h2>
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

function renderFinanceBars(rows) {
  const values = rows.flatMap(row => [Number(row.totalIncome || 0), Number(row.inboundCost || 0)]);
  const max = Math.max(1, ...values);
  return `
    <div class="finance-bars">
      ${(rows || []).map(row => `
        <div class="finance-bar-row">
          <div class="finance-period">${escapeHtml(row.period?.slice(5) || row.period || "-")}</div>
          <div class="finance-bar-track">
            <span class="finance-bar income" style="width:${barWidth(row.totalIncome, max)}%"></span>
            <span class="finance-bar cost" style="width:${barWidth(row.inboundCost, max)}%"></span>
          </div>
          <div class="finance-bar-value">${escapeHtml(money(row.totalIncome))}</div>
        </div>
      `).join("")}
      <div class="finance-legend">
        <span><i class="legend-dot income"></i>收入</span>
        <span><i class="legend-dot cost"></i>入库成本</span>
      </div>
    </div>
  `;
}

function barWidth(value, max) {
  const numeric = Number(value || 0);
  if (!Number.isFinite(numeric) || numeric <= 0) return 0;
  return Math.max(3, Math.min(100, Math.round((numeric / max) * 100)));
}

function summaryCard(label, value, foot) {
  return `
    <article class="summary-card">
      <div class="summary-label">${escapeHtml(label)}</div>
      <div class="summary-value">${escapeHtml(value)}</div>
      <div class="summary-foot">${escapeHtml(foot)}</div>
    </article>
  `;
}

function renderTable(columns, rows) {
  if (!rows.length) return emptyState("暂无数据");
  return `
    <div class="table-wrap">
      <table>
        <thead>
          <tr>${columns.map(column => `<th>${escapeHtml(column.label)}</th>`).join("")}</tr>
        </thead>
        <tbody>
          ${rows.map(row => `
            <tr>
              ${columns.map(column => `<td>${renderCell(column, row)}</td>`).join("")}
            </tr>
          `).join("")}
        </tbody>
      </table>
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

function renderConfigItemCard(item) {
  const active = item.id === state.selectedConfigItemId ? " is-active" : "";
  return `
    <article class="config-item${active}">
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
    </div>
  `).join("");
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

function renderField(field, data) {
  const rawValue = data?.[field.name] ?? defaultValue(field);
  const value = toInputValue(rawValue, field);
  const span = field.span ? ` data-span="${field.span}"` : "";
  const required = field.required ? " required" : "";
  const coerce = field.coerce || field.type || "string";

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
    return `
      <label class="field"${span}>
        <span>${escapeHtml(field.label)}</span>
        <div class="combo${options.length ? "" : " is-empty"}" data-combo data-name="${escapeAttr(field.name)}" data-allow-custom="${field.allowCustom ? "true" : "false"}" data-required="${field.required ? "true" : "false"}">
          <input class="combo-input" data-combo-input type="text" value="${escapeAttr(displayValue)}" placeholder="${escapeAttr(placeholder)}" autocomplete="off"${required}>
          <input name="${escapeAttr(field.name)}" type="hidden" data-coerce="${escapeAttr(coerce)}" value="${escapeAttr(hiddenValue)}" data-selected-label="${escapeAttr(displayValue)}" data-selected-meta="${selected?.meta ? escapeAttr(JSON.stringify(selected.meta)) : ""}">
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
        <textarea name="${escapeAttr(field.name)}" data-coerce="${escapeAttr(coerce)}"${required}>${escapeHtml(value)}</textarea>
      </label>
    `;
  }

  return `
    <label class="field"${span}>
      <span>${escapeHtml(field.label)}</span>
      <input name="${escapeAttr(field.name)}" type="${escapeAttr(field.type || "text")}" data-coerce="${escapeAttr(coerce)}" value="${escapeAttr(value)}" placeholder="${escapeAttr(fieldPlaceholder(field, [], data))}"${field.step ? ` step="${escapeAttr(field.step)}"` : ""}${required}>
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

function setComboValue(combo, value, label, meta, shouldNotify) {
  const input = combo.querySelector("[data-combo-input]");
  const hidden = combo.querySelector("input[type='hidden']");
  const previous = hidden.value;
  input.value = label || "";
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

function closeAllCombos() {
  els.modalCard.querySelectorAll("[data-combo].is-open").forEach(combo => combo.classList.remove("is-open"));
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

function validateCombos(form) {
  const invalid = [...form.querySelectorAll("[data-combo][data-required='true']")]
    .find(combo => !combo.querySelector("input[type='hidden']").value.trim());
  if (!invalid) return true;
  const input = invalid.querySelector("[data-combo-input]");
  invalid.classList.add("is-open");
  input.focus();
  showToast("请从下拉列表中选择有效选项", "error");
  return false;
}

function resolveOptions(field) {
  if (typeof field.options === "function") return field.options();
  return field.options || [];
}

function fieldPlaceholder(field, options = [], data = {}) {
  const placeholder = data?.__placeholders?.[field.name];
  if (placeholder) return placeholder;
  if (field.placeholder) return typeof field.placeholder === "function" ? field.placeholder() : field.placeholder;
  if (field.placeholderValue !== undefined) {
    const placeholderValue = typeof field.placeholderValue === "function" ? field.placeholderValue() : field.placeholderValue;
    const option = findOptionByValue(options, placeholderValue);
    return `默认：${option?.label ?? placeholderValue}`;
  }
  return field.type === "select" ? "请选择或输入筛选" : "";
}

function serializeForm(kind, form) {
  const payload = {};
  for (const field of fields[kind]) {
    const input = form.elements[field.name];
    if (!input) continue;
    const coerce = input.dataset.coerce || "string";
    const raw = input.type === "checkbox" ? input.checked : String(input.value || "").trim();
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
  if (coerce === "datetime") return value.length === 16 ? `${value}:00` : value;
  if (coerce === "decimal") return value;
  return value;
}

function defaultEntity(kind) {
  const entity = {};
  for (const field of fields[kind] || []) {
    const value = defaultValue(field);
    if (value !== "") entity[field.name] = value;
  }
  if (kind === "configValue" && state.selectedConfigItemId) {
    entity.configItemId = state.selectedConfigItemId;
  }
  return entity;
}

function vehicleDefaultsForModel(group = {}) {
  return {
    name: group.name || "",
    specificationModel: group.specificationModel || "",
    machineType: group.machineType || "",
    configuration: "",
    supplier: group.supplier || "",
    warehouseName: group.warehouseName || "",
    stockStatus: "IN_STOCK",
    purchasePrice: group.purchasePrice || "",
    salePrice: group.salePrice || "",
    settlementPrice: "",
    inventoryCount: 1,
    __placeholders: {
      vehicleProductNumber: "请输入此辆整车唯一车号",
      engineNumber: "请输入发动机号",
      frameNumber: "请输入车架号",
      warrantyCardNumber: "请输入保修卡号",
      settlementPrice: "请输入此辆整车结算价"
    }
  };
}

function modificationOrderPlaceholders(machine = {}, configId = null) {
  if (!machine?.id) return {};
  const modelKey = vehicleModelKey(machine);
  const detailConfigs = state.vehicleDetail?.selectedDetail?.machine?.id === machine.id
    ? (state.vehicleDetail.selectedDetail.configs || [])
    : (state.vehicleDetail?.machine?.id === machine.id ? (state.vehicleDetail.configs || []) : []);
  const config = detailConfigs.find(item => String(item.id) === String(configId))
    || detailConfigs[0]
    || null;
  const placeholders = {
    vehicleModelKey: `预填：${vehicleModelLabel(machine)}`,
    machineId: `预填：${machine.vehicleProductNumber || machine.id}`,
    quantity: "默认：1",
    oldPartAction: "默认：拆下件入库"
  };
  if (config) {
    placeholders.machineConfigId = `预填：${machineConfigOptionLabel(config)}`;
  }
  return placeholders;
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
    form.elements[name].value = value || "";
  }
}

function defaultValue(field) {
  if (typeof field.defaultValue === "function") return field.defaultValue();
  if (field.defaultValue !== undefined) return field.defaultValue;
  if (field.type === "checkbox") return false;
  return "";
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
  const key = {
    vehicle: "vehicles",
    part: "parts",
    modificationOrder: "modificationOrders",
    repair: "repairs",
    configItem: "configItems",
    configValue: "configValues",
    user: "users"
  }[kind];
  return (state.data[key] || []).find(item => item.id === id) || {};
}

function modalTitle(kind, item) {
  const names = {
    vehicle: "车辆",
    part: "配件",
    repair: "维修记录",
    configItem: "配置项",
    configValue: "配置值",
    vehicleStock: item?.direction === "outbound" ? "整车出库" : "整车入库",
    partStock: item?.direction === "outbound" ? "配件出库" : "配件入库",
    partReplace: "配件替换",
    modificationOrder: "改装工单",
    user: "用户",
    userUsername: "修改用户名",
    userPassword: "修改密码",
    switchUser: "切换用户"
  };
  if (kind === "switchUser" || kind === "vehicleStock" || kind === "partStock" || kind === "partReplace" || kind === "modificationOrder" || kind === "userUsername" || kind === "userPassword") return names[kind];
  if (kind === "configValue" || kind === "user") return `新增${names[kind]}`;
  return item?.id ? `编辑${names[kind]}` : `新增${names[kind]}`;
}

function modalSubtitle(kind) {
  const subtitles = {
    vehicle: "填写车辆档案、价格和入库信息。",
    part: "维护配件编码、库存和价格。",
    repair: "记录维修过程、费用与处理状态。",
    configItem: "定义可维护的车辆配置项。",
    configValue: "为当前配置项添加可选值。",
    vehicleStock: "调整已有整车的库存数量。",
    partStock: "调整已有配件的库存数量。",
    partReplace: "选择车辆上的旧配件，并用同类型库存配件替换；拆下件会自动入库。",
    modificationOrder: "只填写这次客户要求替换的配置；完成工单时才会生成库存流水并更新车辆配置。",
    user: "由超级管理员创建管理员或普通用户。",
    userUsername: "仅超级管理员可修改管理员或普通用户的登录名。",
    userPassword: "仅超级管理员可重置管理员或普通用户的登录密码。",
    switchUser: "输入另一个账号后立即进入对应权限。"
  };
  return subtitles[kind] || "";
}

function configItemOptions() {
  return state.data.configItems.map(item => ({
    value: item.id,
    label: configItemLabel(item)
  }));
}

function vehicleCategoryOptions() {
  return uniqueOptions([
    "电动车",
    "手动车",
    "内燃车",
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

function vehicleModelKey(item) {
  return encodeURIComponent(JSON.stringify([
    String(item?.name || "").trim(),
    String(item?.specificationModel || "").trim()
  ]));
}

function decodeVehicleModelKey(key) {
  try {
    const [name, specificationModel] = JSON.parse(decodeURIComponent(key || ""));
    return { name, specificationModel };
  } catch (error) {
    return { name: "", specificationModel: "" };
  }
}

function vehicleModelLabel(model) {
  return `${model?.name || "-"} · ${model?.specificationModel || "-"}`;
}

function vehicleNumberLabel(item) {
  return `${item?.vehicleProductNumber || item?.id || "-"} · ${item?.name || "-"} / ${item?.specificationModel || "-"}`;
}

function vehicleModelGroups() {
  const groups = new Map();
  for (const vehicle of state.data.vehicles) {
    const modelKey = vehicleModelKey(vehicle);
    if (!groups.has(modelKey)) {
      groups.set(modelKey, {
        id: modelKey,
        modelKey,
        name: vehicle.name,
        specificationModel: vehicle.specificationModel,
        machineType: vehicle.machineType,
        supplier: vehicle.supplier,
        warehouseName: vehicle.warehouseName,
        purchasePrice: vehicle.purchasePrice,
        salePrice: vehicle.salePrice,
        settlementPrice: vehicle.settlementPrice,
        vehicles: [],
        vehicleNumbers: "",
        unitCount: 0,
        inventoryCount: 0
      });
    }
    const group = groups.get(modelKey);
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
    .filter(vehicle => vehicleModelKey(vehicle) === modelKey)
    .sort((a, b) => String(a.vehicleProductNumber || "").localeCompare(String(b.vehicleProductNumber || ""), "zh-CN"));
}

function vehicleModelOptions() {
  return vehicleModelGroups().map(group => ({
    value: group.modelKey,
    label: `${vehicleModelLabel(group)}（${group.inventoryCount} 台库存）`
  }));
}

function vehicleNumberOptions() {
  const modelKey = state.modal?.item?.vehicleModelKey;
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

function vehicleOptions() {
  return state.data.vehicles.map(item => ({
    value: item.id,
    label: vehicleNumberLabel(item)
  }));
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

function statusOptions() {
  return [
    { value: "PENDING", label: "待处理" },
    { value: "IN_PROGRESS", label: "处理中" },
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

function normalizeUser(data) {
  if (!data) return null;
  const roles = data.roles || [];
  return {
    username: data.username,
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
    USER: []
  };
  return [...new Set(roles.flatMap(role => permissionMap[role] || []))];
}

function canAccessTab(tab) {
  const permissionsByTab = {
    modifications: "replace:write",
    stats: "log:read",
    logs: "log:read",
    users: "user:read"
  };
  return !permissionsByTab[tab] || hasPermission(permissionsByTab[tab]);
}

function canWriteEntity(kind) {
  const permissionsByKind = {
    vehicle: "vehicle:write",
    part: "part:write",
    repair: "repair:write",
    configItem: "config:write",
    configValue: "config:write"
  };
  return !permissionsByKind[kind] || hasPermission(permissionsByKind[kind]);
}

function normalizeText(value) {
  return String(value || "").trim().toLowerCase();
}

function filterRows(rows, query, keys) {
  const keyword = String(query || "").trim().toLowerCase();
  if (!keyword) return rows;
  return rows.filter(row => keys.some(key => String(row[key] ?? "").toLowerCase().includes(keyword)));
}

function sortById(rows, desc = true) {
  return [...(rows || [])].sort((a, b) => desc ? Number(b.id || 0) - Number(a.id || 0) : Number(a.sortOrder || a.id || 0) - Number(b.sortOrder || b.id || 0));
}

function sortLogs(rows) {
  return [...(rows || [])].sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0));
}

function detailItem(label, value) {
  return `
    <div class="detail-item">
      <div class="label">${escapeHtml(label)}</div>
      <div class="value">${escapeHtml(display(value))}</div>
    </div>
  `;
}

function stockBadge(value, unit = "") {
  const amount = Number(value || 0);
  const type = amount <= 0 ? "danger" : amount < 5 ? "warn" : "teal";
  return badge(`${amount}${unit || ""}`, type);
}

function stockText(value) {
  return display(value);
}

function stockStatusLabel(status) {
  const labels = {
    PENDING_INBOUND: "待入库",
    IN_STOCK: "在库",
    PENDING_MODIFICATION: "待改装",
    MODIFYING: "改装中",
    PENDING_OUTBOUND: "待出库",
    OUTBOUND: "已出库",
    OUT_OF_STOCK: "已出库",
    RESERVED: "已预留",
    LOCKED: "已锁定"
  };
  return labels[status] || status || "未设置";
}

function stockStatusBadge(status) {
  const types = {
    PENDING_INBOUND: "primary",
    IN_STOCK: "teal",
    PENDING_MODIFICATION: "warn",
    MODIFYING: "warn",
    PENDING_OUTBOUND: "primary",
    OUTBOUND: "primary",
    OUT_OF_STOCK: "primary",
    RESERVED: "warn",
    LOCKED: "danger"
  };
  return badge(stockStatusLabel(status), types[status] || "primary");
}

function resourceTypeLabel(value) {
  const labels = {
    MACHINE: "整车",
    PART: "配件",
    REPAIR: "维修",
    USER: "用户"
  };
  return labels[value] || value;
}

function statusBadge(status) {
  const map = {
    PENDING: ["待处理", "warn"],
    IN_PROGRESS: ["处理中", "primary"],
    COMPLETED: ["已完成", "success"]
  };
  const [label, type] = map[status] || [status || "未设置", "primary"];
  return badge(label, type);
}

function modificationStatusBadge(status) {
  const map = {
    DRAFT: ["草稿", "primary"],
    WAITING_PARTS: ["待领料", "warn"],
    IN_PROGRESS: ["改装中", "primary"],
    COMPLETED: ["已完成", "success"],
    CANCELED: ["已取消", "danger"]
  };
  const [label, type] = map[status] || [status || "未设置", "primary"];
  return badge(label, type);
}

function badge(label, type = "primary") {
  return `<span class="badge ${escapeAttr(type)}">${escapeHtml(label)}</span>`;
}

function roleBadges(roles = []) {
  const roleLabels = {
    SUPER_ADMIN: "超级管理员",
    ADMIN: "管理员",
    USER: "普通用户"
  };
  return (roles || []).map(role => badge(roleLabels[role] || role, role === "ADMIN" ? "warn" : role === "SUPER_ADMIN" ? "teal" : "primary")).join(" ");
}

function logCategoryBadge(category) {
  const type = category === "配件替换" ? "teal" : category === "维修" ? "warn" : "primary";
  return badge(category || "-", type);
}

function operationActionBadge(action) {
  const labels = {
    CREATE: "新增",
    UPDATE: "更新",
    DELETE: "删除",
    LOCK: "锁定",
    UNLOCK: "解锁",
    CONFIG_UPDATE: "配置更新",
    REPLACE: "配置替换",
    USERNAME_UPDATE: "改用户名",
    PASSWORD_UPDATE: "改密码",
    PASSWORD_RESET: "重置密码",
    INBOUND: "入库",
    OUTBOUND: "出库",
    PART_REPLACE: "配件替换",
    PENDING: "待处理",
    IN_PROGRESS: "处理中",
    COMPLETED: "已完成"
  };
  const type = action === "OUTBOUND" || action === "DELETE"
    ? "warn"
    : action === "COMPLETED" || action === "INBOUND" || action === "CREATE"
      ? "teal"
      : "primary";
  return badge(labels[action] || action || "-", type);
}

function quantityChange(row) {
  if (row.quantity === null || row.quantity === undefined) return "-";
  const before = row.beforeQuantity ?? "-";
  const after = row.afterQuantity ?? "-";
  return `${escapeHtml(row.quantity)} <span class="helper-inline">(${escapeHtml(before)} -> ${escapeHtml(after)})</span>`;
}

function money(value) {
  if (value === null || value === undefined || value === "") return "";
  const numeric = Number(value);
  if (Number.isNaN(numeric)) return value;
  return new Intl.NumberFormat("zh-CN", {
    style: "currency",
    currency: "CNY",
    maximumFractionDigits: 2
  }).format(numeric);
}

function dateTime(value) {
  if (!value) return "";
  return String(value).replace("T", " ").slice(0, 16);
}

function toInputValue(value, field) {
  if (value === null || value === undefined) return "";
  if (field.type === "datetime-local") return String(value).slice(0, 16);
  if (field.type === "date") return String(value).slice(0, 10);
  return value;
}

function nowInputDateTime() {
  const date = new Date();
  const offset = date.getTimezoneOffset();
  return new Date(date.getTime() - offset * 60000).toISOString().slice(0, 16);
}

function display(value) {
  if (value === null || value === undefined || value === "") return "-";
  return String(value);
}

function icon(name) {
  return `<span class="btn-icon" aria-hidden="true">${icons[name] || ""}</span>`;
}

function emptyState(message) {
  return `<div class="helper">${escapeHtml(message)}</div>`;
}

function renderLoading() {
  return `<div class="surface"><div class="surface-body helper">正在加载数据...</div></div>`;
}

function showToast(message, type = "info") {
  const toast = document.createElement("div");
  toast.className = `toast ${type}`;
  toast.textContent = message;
  els.toastHost.appendChild(toast);
  window.setTimeout(() => toast.remove(), 3600);
}

function handleActionError(error) {
  if (error.message && error.message.includes("登录")) {
    logout("登录已过期，请重新登录");
    return;
  }
  showToast(error.message || "操作失败", "error");
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function escapeAttr(value) {
  return escapeHtml(value);
}
