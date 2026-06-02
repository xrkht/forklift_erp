export const tabs = {
  overview: { title: "总览", subtitle: "系统概览与业务入口" },
  vehicles: { title: "车辆库存", subtitle: "车型库存、整车档案与库存状态" },
  parts: { title: "配件库存", subtitle: "配件档案、库存数量与价格信息" },
  modifications: { title: "改装工单", subtitle: "客户出库前改装、配件替换与拆下件回库" },
  outboundOrders: { title: "订单列表", subtitle: "记录整车与配件出库、车款结清、报销售和发票申请" },
  rentals: { title: "租赁管理", subtitle: "记录租赁车辆、租赁去向、租赁价格和归还状态" },
  customers: { title: "客户列表", subtitle: "维护公司抬头、地址、联系人和税号/身份证号" },
  suppliers: { title: "采购供应商", subtitle: "维护供应商资料、联系人、税号与采购合作信息" },
  purchases: { title: "采购订单", subtitle: "统计配件采购入库、预计到货、金额与状态" },
  stocktakes: { title: "库存盘点", subtitle: "创建盘点草稿，确认后同步整车或配件库存数量" },
  warehouses: { title: "仓库管理", subtitle: "维护仓库档案，执行整车/配件调拨并查看库存分布" },
  repairs: { title: "维修记录", subtitle: "维修工单、费用与状态追踪" },
  stockMovements: { title: "库存流水", subtitle: "按仓库、资源和操作类型追踪库存变动明细" },
  logs: { title: "日志查看", subtitle: "配件替换、维修和出入库流水" },
  stats: { title: "统计财报", subtitle: "按月度和年度汇总库存收支、维修收入与库存价值" },
  configs: { title: "配置字典", subtitle: "车辆配置项与可选值维护" },
  users: { title: "用户管理", subtitle: "账号、角色与客户端登录切换" },
  maintenance: { title: "数据维护", subtitle: "导出数据库备份，并在确认后恢复业务数据" }
};

export const icons = {
  plus: '<svg viewBox="0 0 24 24" fill="none"><path d="M12 5v14M5 12h14" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>',
  minus: '<svg viewBox="0 0 24 24" fill="none"><path d="M5 12h14" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>',
  refresh: '<svg viewBox="0 0 24 24" fill="none"><path d="M20 12a8 8 0 1 1-2.34-5.66L20 8" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/><path d="M20 4v4h-4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  edit: '<svg viewBox="0 0 24 24" fill="none"><path d="M12 20h9" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4 12.5-12.5Z" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  trash: '<svg viewBox="0 0 24 24" fill="none"><path d="M4 7h16M10 11v6M14 11v6M6 7l1 14h10l1-14M9 7V4h6v3" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  eye: '<svg viewBox="0 0 24 24" fill="none"><path d="M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6S2 12 2 12Z" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/><circle cx="12" cy="12" r="3" stroke="currentColor" stroke-width="1.8"/></svg>',
  search: '<svg viewBox="0 0 24 24" fill="none"><circle cx="11" cy="11" r="7" stroke="currentColor" stroke-width="1.8"/><path d="m20 20-3.5-3.5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>',
  swap: '<svg viewBox="0 0 24 24" fill="none"><path d="M7 7h12l-3-3M17 17H5l3 3" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  upload: '<svg viewBox="0 0 24 24" fill="none"><path d="M12 16V4M7 9l5-5 5 5M5 20h14" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  download: '<svg viewBox="0 0 24 24" fill="none"><path d="M12 4v12M7 11l5 5 5-5M5 20h14" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  lock: '<svg viewBox="0 0 24 24" fill="none"><rect x="5" y="10" width="14" height="10" rx="2" stroke="currentColor" stroke-width="1.8"/><path d="M8 10V7a4 4 0 0 1 8 0v3" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>',
  unlock: '<svg viewBox="0 0 24 24" fill="none"><rect x="5" y="10" width="14" height="10" rx="2" stroke="currentColor" stroke-width="1.8"/><path d="M8 10V7a4 4 0 0 1 7.3-2.3" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>'
};
