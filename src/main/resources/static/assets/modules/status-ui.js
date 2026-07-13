import { display, escapeAttr, escapeHtml } from "./display-utils.js";

export function stockBadge(value, unit = "") {
  const amount = Number(value || 0);
  const type = amount <= 0 ? "danger" : amount < 5 ? "warn" : "teal";
  return badge(`${amount}${unit || ""}`, type);
}

export function stockText(value) {
  return display(value);
}

export function stockStatusLabel(status) {
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

export function stockStatusBadge(status) {
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

export function rentalStatusBadge(status) {
  const map = {
    ACTIVE: ["租赁中", "warn"],
    RETURNED: ["已归还", "teal"]
  };
  const [label, type] = map[status] || [status || "未设置", "primary"];
  return badge(label, type);
}

export function purchaseStatusLabel(status) {
  const labels = {
    ORDERED: "已下单",
    PARTIAL: "部分到货",
    ARRIVED: "已到货",
    RECEIVED: "已收货",
    CANCELED: "已取消"
  };
  return labels[status] || status || "未设置";
}

export function purchaseStatusBadge(status) {
  const types = {
    ORDERED: "primary",
    PARTIAL: "warn",
    ARRIVED: "teal",
    RECEIVED: "teal",
    CANCELED: "danger"
  };
  return badge(purchaseStatusLabel(status), types[status] || "primary");
}

export function resourceTypeLabel(value) {
  const labels = {
    MACHINE: "整车",
    PART: "配件",
    REPAIR: "维修",
    USER: "用户"
  };
  return labels[value] || value;
}

export function statusBadge(status) {
  const map = {
    PENDING: ["待处理", "warn"],
    COMPLETED: ["已完成", "success"]
  };
  const [label, type] = map[status] || [status || "未设置", "primary"];
  return badge(label, type);
}

export function repairStatusText(status) {
  const map = {
    PENDING: "待处理",
    COMPLETED: "已完成"
  };
  return map[status] || status || "未设置";
}

export function modificationStatusBadge(status) {
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

export function badge(label, type = "primary") {
  return `<span class="badge ${escapeAttr(type)}">${escapeHtml(label)}</span>`;
}

export function yesNoBadge(value) {
  return Boolean(value) ? badge("是", "teal") : badge("否", "primary");
}

export function roleBadges(roles = []) {
  const roleLabels = {
    SUPER_ADMIN: "超级管理员",
    ADMIN: "管理员",
    USER: "普通用户"
  };
  return (roles || []).map(role => badge(roleLabels[role] || role, role === "ADMIN" ? "warn" : role === "SUPER_ADMIN" ? "teal" : "primary")).join(" ");
}

export function normalizeJobTag(value, roles = []) {
  const tag = String(value || "").trim().toUpperCase();
  if (["MANAGEMENT", "CLERK", "REPAIR"].includes(tag)) return tag;
  return roles?.some(role => role === "ADMIN" || role === "SUPER_ADMIN") ? "MANAGEMENT" : "CLERK";
}

export function jobTagLabel(tag) {
  const labels = {
    MANAGEMENT: "管理",
    CLERK: "文员",
    REPAIR: "维修"
  };
  return labels[normalizeJobTag(tag)] || "文员";
}

export function jobTagType(tag) {
  const types = {
    MANAGEMENT: "primary",
    CLERK: "teal",
    REPAIR: "warn"
  };
  return types[normalizeJobTag(tag)] || "primary";
}

export function jobTagBadge(tag) {
  return badge(jobTagLabel(tag), jobTagType(tag));
}

export function nextJobTag(tag) {
  const order = ["MANAGEMENT", "CLERK", "REPAIR"];
  const index = order.indexOf(normalizeJobTag(tag));
  return order[(index + 1) % order.length];
}

export function logCategoryBadge(category) {
  const type = category === "配件替换" ? "teal" : category === "维修" ? "warn" : "primary";
  return badge(category || "-", type);
}

export function operationActionBadge(action) {
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

export function quantityChange(row) {
  if (row.quantity === null || row.quantity === undefined) return "-";
  const before = row.beforeQuantity ?? "-";
  const after = row.afterQuantity ?? "-";
  return `${escapeHtml(row.quantity)} <span class="helper-inline">(${escapeHtml(before)} -> ${escapeHtml(after)})</span>`;
}
