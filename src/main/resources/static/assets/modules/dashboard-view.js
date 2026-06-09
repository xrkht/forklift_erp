export function createDashboardView(deps) {
  const {
    state,
    hasPermission,
    pageTotal,
    vehicleFlowRows,
    renderSurface,
    summaryCard,
    money,
    badge,
    emptyState,
    escapeAttr,
    escapeHtml,
    dateValue,
    stockText,
    icon,
    renderTable,
    renderYearOptions,
    renderFinanceTrend,
    compactTable,
    vehicleModelLabel,
    repairStatusText,
    rentalStatusBadge,
    resourceTypeLabel,
    isInvoiceUploadReady,
    isContractUploadReady,
    receivableOutstandingTotal,
    vehicleFlowMachineSummary,
    vehicleFlowStatusSummary,
    vehicleFlowFollowupSummary,
    vehicleFlowActions,
    toDataAttrName,
    repairStatusToggle
  } = deps;

  return {
    renderOverview,
    renderStatistics
  };

  function renderOverview() {
    const vehicles = state.data.vehicles.filter(item => !item.modelOnly);
    const vehicleRows = vehicleFlowRows();
    const parts = state.data.parts;
    const repairs = state.data.repairs;
    const orders = state.data.outboundOrders;
    const rentals = state.data.rentals || [];
    const todoCenter = state.data.todoCenter || {};
    const stats = state.data.statistics || null;
    const lowStockThreshold = Number(todoCenter.lowStockThreshold ?? 5);
    const inStockRows = vehicleRows.filter(row => !row.orderId && Number(row.inventoryCount || 0) > 0);
    const lowStockParts = parts.filter(item => Number(item.quantity || 0) <= lowStockThreshold);
    const missingInvoiceOrders = orders.filter(item => isInvoiceUploadReady(item) && !item.invoiceFileAvailable);
    const missingContractOrders = orders.filter(item => isContractUploadReady(item) && !item.contractFileAvailable);
    const pendingRepairs = todoCenter.pendingRepairCount ?? repairs.filter(item => item.status !== "COMPLETED").length;
    const lowParts = todoCenter.lowStockCount ?? lowStockParts.length;
    const longIdleVehicles = todoCenter.longIdleVehicleCount ?? 0;
    const unsettledOrders = todoCenter.pendingPaymentCount ?? orders.filter(item => !item.paymentSettled).length;
    const pendingSalesReports = todoCenter.pendingSalesReportCount ?? orders.filter(item => item.paymentSettled && !item.salesReported).length;
    const pendingInvoices = todoCenter.pendingInvoiceApplicationCount ?? orders.filter(item => item.paymentSettled && item.salesReported && !item.invoiceApplied).length;
    const missingInvoiceFiles = todoCenter.pendingInvoiceFileCount ?? missingInvoiceOrders.length;
    const missingContractFiles = todoCenter.pendingContractFileCount ?? missingContractOrders.length;
    const outstandingAmount = todoCenter.outstandingAmount ?? receivableOutstandingTotal(orders);
    const overduePaymentCount = todoCenter.overduePaymentCount ?? orders.filter(item => Number(item.overdueDays || 0) > 0).length;
    const rentalDueCount = todoCenter.rentalDueCount ?? rentals.filter(item => item.status === "ACTIVE" && daysUntil(item.endDate) !== null && daysUntil(item.endDate) <= 7).length;
    const queueRows = todoQueues(todoCenter, {
      orders,
      repairs,
      parts,
      rentals,
      vehicleRows,
      lowStockParts,
      missingInvoiceOrders,
      missingContractOrders,
      lowStockThreshold
    });
    const totalTodos = todoCenter.totalTodoCount ?? queueRows.reduce((sum, queue) => sum + Number(queue.count || 0), 0);
    const criticalTodos = todoCenter.criticalTodoCount ?? queueRows
      .filter(queue => queue.priority === "danger")
      .reduce((sum, queue) => sum + Number(queue.count || 0), 0);
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
          ${summaryCard("今日待办", totalTodos, `${criticalTodos} 项高优先级`, { action: "go-tab", data: { tab: "overview" } })}
          ${summaryCard("逾期收款", overduePaymentCount, money(todoCenter.overdueAmount || 0), { action: "go-tab", data: { tab: "outboundOrders", stage: "overdue" } })}
          ${summaryCard("租赁到期", rentalDueCount, `未来 ${todoCenter.rentalDueSoonDays ?? 7} 天`, { action: "go-tab", data: { tab: "rentals", status: "dueSoon" } })}
          ${summaryCard("库存异常", lowParts + longIdleVehicles, `低库存 ${lowParts} / 未动 ${longIdleVehicles}`, { action: "go-tab", data: { tab: "parts", stock: "low" } })}
        </section>

        ${stats ? renderFinanceKpis(stats, lowParts) : ""}

        ${renderSurface("经营异常队列", renderTodoQueues(queueRows), `
          <button class="btn btn-ghost" type="button" data-action="refresh">${icon("refresh")}刷新</button>
        `)}

        <section class="overview-workbench">
          ${renderSurface("优先处理", renderOverviewWorkItems(workItems))}
          <div class="overview-side">
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

        <section class="summary-grid">
          ${summaryCard("整车档案", vehicleTotal, `${inStockRows.length} 台在库待销售`, { action: "go-tab", data: { tab: "vehicles", stock: "inStock" } })}
          ${summaryCard("配件档案", partTotal, `${lowParts} 项低库存`, { action: "go-tab", data: { tab: "parts", stock: "low" } })}
          ${summaryCard("出库订单", orderTotal, `${unsettledOrders} 单待收款`, { action: "go-tab", data: { tab: "outboundOrders", stage: "payment" } })}
          ${summaryCard("销售闭环", pendingSalesReports + pendingInvoices, `${missingInvoiceFiles + missingContractFiles} 份附件待补`, { action: "go-tab", data: { tab: "outboundOrders" } })}
        </section>
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
          ${summaryCard("年度总收入", money(annual.totalIncome), `出库 ${money(annual.outboundRevenue)} / 维修 ${money(annual.repairIncome)} / 租赁 ${money(annual.rentalIncome)} / 改装收入 ${money(annual.modificationIncome)}`)}
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
            { label: "改装工单", key: "modificationOrders" }
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

  function todoQueues(todoCenter = {}, fallbackContext = {}) {
    if (Array.isArray(todoCenter.queues) && todoCenter.queues.length) {
      return todoCenter.queues
        .map(queue => ({
          ...queue,
          count: Number(queue.count || 0),
          amount: Number(queue.amount || 0),
          items: Array.isArray(queue.items) ? queue.items : [],
          targetFilter: queue.targetFilter || {}
        }))
        .sort((left, right) => priorityRank(left.priority) - priorityRank(right.priority) || Number(right.count || 0) - Number(left.count || 0));
    }
    return fallbackTodoQueues(fallbackContext);
  }

  function fallbackTodoQueues({
    orders = [],
    repairs = [],
    rentals = [],
    vehicleRows = [],
    lowStockParts = [],
    missingInvoiceOrders = [],
    missingContractOrders = []
  }) {
    const overdueOrders = orders.filter(item => Number(item.overdueDays || 0) > 0);
    const pendingRepairs = repairs.filter(item => item.status !== "COMPLETED");
    const dueRentals = rentals.filter(item => item.status === "ACTIVE" && daysUntil(item.endDate) !== null && daysUntil(item.endDate) <= 7);
    return [
      fallbackQueue("overduePayment", "逾期收款", "已过收款日期且仍有未收金额", "danger", overdueOrders.length, "outboundOrders", { stage: "overdue" }),
      fallbackQueue("invoiceFile", "待上传发票", "已具备发票上传条件但缺少文件", "primary", missingInvoiceOrders.length, "outboundOrders", { stage: "invoiceFile" }),
      fallbackQueue("contractFile", "待上传合同", "已标记合同但缺少合同文件", "teal", missingContractOrders.length, "outboundOrders", { stage: "contractFile" }),
      fallbackQueue("repairPending", "待维修", "未完成维修工单需要跟进", "warn", pendingRepairs.length, "repairs", { status: "pending" }),
      fallbackQueue("rentalDue", "租赁到期", "进行中租赁将在 7 天内到期或已逾期", "warn", dueRentals.length, "rentals", { status: "dueSoon" }),
      fallbackQueue("lowStock", "低库存", "配件库存小于等于预警阈值", "danger", lowStockParts.length, "parts", { stock: "low" }),
      fallbackQueue("longIdleVehicles", "长期未动库存", "在库整机超过 90 天未出库且未出租", "warn", 0, "vehicles", { stock: "inStock" }),
      fallbackQueue("pendingPayment", "待收款", "未结清的应收款", "warn", orders.filter(item => !item.paymentSettled).length, "outboundOrders", { stage: "payment" }),
      fallbackQueue("salesReport", "待报销售", "已收款但尚未完成销售报备", "primary", orders.filter(item => item.paymentSettled && !item.salesReported).length, "outboundOrders", { stage: "salesReport" })
    ];
  }

  function fallbackQueue(key, label, description, priority, count, targetTab, targetFilter) {
    return { key, label, description, priority, count, amount: 0, targetTab, targetFilter, items: [] };
  }

  function renderTodoQueues(queues) {
    if (!queues.length) return emptyState("暂无经营异常");
    return `
      <div class="todo-queue-grid">
        ${queues.map(queue => {
          const items = Array.isArray(queue.items) ? queue.items.slice(0, 4) : [];
          const hiddenCount = Math.max(0, Number(queue.count || 0) - items.length);
          return `
            <article class="todo-queue-card ${escapeAttr(queue.priority || "primary")}">
              <button class="todo-queue-head" type="button" ${queueActionAttrs(queue)}>
                <span class="todo-queue-label">${escapeHtml(queue.label || "-")}</span>
                <strong>${escapeHtml(queue.count ?? 0)}</strong>
                <span class="todo-queue-desc">${escapeHtml(queue.description || "-")}</span>
                ${Number(queue.amount || 0) > 0 ? `<span class="todo-queue-amount">${escapeHtml(money(queue.amount))}</span>` : ""}
              </button>
              <div class="todo-queue-list">
                ${items.length ? items.map(renderQueueItem).join("") : `<div class="todo-queue-empty">${Number(queue.count || 0) > 0 ? "进入列表查看全部事项" : "已清空"}</div>`}
                ${hiddenCount > 0 ? `<button class="todo-queue-more" type="button" ${queueActionAttrs(queue)}>还有 ${escapeHtml(hiddenCount)} 项</button>` : ""}
              </div>
            </article>
          `;
        }).join("")}
      </div>
    `;
  }

  function renderQueueItem(item) {
    const action = todoItemAction(item);
    const viewItem = overviewItem(
      item.label,
      item.title,
      todoItemDetail(item),
      item.priority || "primary",
      action.name,
      action.data,
      item.sortTime || item.dueDate || ""
    );
    return `
      <div class="todo-queue-item is-clickable" ${actionAttrs(viewItem)} tabindex="0" role="button">
        <div class="todo-queue-item-main">
          ${badge(item.label || "事项", item.priority || "primary")}
          <strong>${escapeHtml(item.title || "-")}</strong>
          <span>${escapeHtml(viewItem.detail || "-")}</span>
        </div>
        <button class="btn btn-sm" type="button" ${actionAttrs(viewItem)}>${icon(queueItemIcon(viewItem.action))}${escapeHtml(queueItemLabel(viewItem.action))}</button>
      </div>
    `;
  }

  function queueActionAttrs(queue = {}) {
    const attrs = [`data-action="go-tab"`, `data-tab="${escapeAttr(queue.targetTab || "overview")}"`];
    Object.entries(queue.targetFilter || {})
      .filter(([, value]) => value !== undefined && value !== null && value !== "")
      .forEach(([key, value]) => attrs.push(`data-${toDataAttrName(key)}="${escapeAttr(value)}"`));
    return attrs.join(" ");
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
    if (item.resourceType === "REPAIR") return { name: "edit", data: { kind: "repair", id: item.resourceId } };
    if (item.resourceType === "PART") return { name: "edit", data: { kind: "part", id: item.resourceId } };
    if (item.resourceType === "RENTAL") return { name: "edit", data: { kind: "rental", id: item.resourceId } };
    if (item.resourceType === "MACHINE") return { name: "detail-vehicle", data: { id: item.resourceId } };
    if (item.targetTab) return { name: "go-tab", data: { tab: item.targetTab, ...(item.targetFilter || {}) } };
    return { name: "go-tab", data: { tab: "overview" } };
  }

  function todoItemDetail(item = {}) {
    const fragments = [];
    if (item.detail) fragments.push(item.detail);
    if (Number(item.amount || 0) > 0) fragments.push(money(item.amount));
    if (Number(item.ageDays || 0) > 0) fragments.push(`在库 ${item.ageDays} 天`);
    if (item.dueDate) fragments.push(`到期 ${dateValue(item.dueDate)}`);
    if (Number(item.overdueDays || 0) > 0) fragments.push(`逾期 ${item.overdueDays} 天`);
    return fragments.join(" · ") || "-";
  }

  function overviewWorkItems(vehicleRows, orders, repairs, parts) {
    const items = [];
    orders.forEach(order => {
      const resource = `${order.resourceCode || "-"} · ${order.customerName || "-"}`;
      if (!order.paymentSettled) {
        items.push(overviewItem(Number(order.overdueDays || 0) > 0 ? "逾期收款" : "待收款", resource, order.paymentRemark || order.orderNo, Number(order.overdueDays || 0) > 0 ? "danger" : "warn", "edit", { kind: "outboundOrder", id: order.id }, order.createdAt));
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
      .forEach(repair => items.push(overviewItem("待维修", repair.customerName || repair.machineInfo || "-", repairStatusText(repair.status), "warn", "edit", { kind: "repair", id: repair.id }, repair.updatedAt)));
    parts
      .filter(part => Number(part.quantity || 0) <= 5)
      .forEach(part => items.push(overviewItem("低库存", part.partCode || "-", part.partName || "-", "danger", "edit", { kind: "part", id: part.id }, part.updatedAt)));
    vehicleRows
      .filter(row => !row.orderId && Number(row.inventoryCount || 0) > 0)
      .slice(0, 5)
      .forEach(row => items.push(overviewItem("在库待销售", row.vehicleProductNumber || `ID ${row.id}`, vehicleModelLabel(row), "teal", "vehicle-outbound-order", { machineId: row.id }, row.inboundDate)));
    return items.sort((left, right) => priorityRank(left.type) - priorityRank(right.type) || String(right.sortKey || "").localeCompare(String(left.sortKey || "")));
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
    return `<button class="btn btn-sm" type="button" ${actionAttrs(item)}>${icon(queueItemIcon(item.action))}${escapeHtml(queueItemLabel(item.action))}</button>`;
  }

  function queueItemIcon(action) {
    if (action?.startsWith("upload")) return "upload";
    if (action === "detail-vehicle") return "eye";
    if (action === "vehicle-outbound-order") return "minus";
    return "edit";
  }

  function queueItemLabel(action) {
    if (action === "upload-invoice") return "上传发票";
    if (action === "upload-contract") return "上传合同";
    if (action === "detail-vehicle") return "查看";
    if (action === "vehicle-outbound-order") return "登记销售";
    return "处理";
  }

  function actionAttrs(item = {}) {
    const attrs = [`data-action="${escapeAttr(item.action || "go-tab")}"`];
    Object.entries(item.data || {})
      .filter(([, value]) => value !== undefined && value !== null && value !== "")
      .forEach(([key, value]) => attrs.push(`data-${toDataAttrName(key)}="${escapeAttr(value)}"`));
    return attrs.join(" ");
  }

  function renderFinanceKpis(stats, lowParts) {
    const annual = stats.annualSummary || {};
    const stockRows = Array.isArray(stats.stockValues) ? stats.stockValues : [];
    const machineStock = stockRows.find(row => row.resourceType === "MACHINE") || {};
    const partStock = stockRows.find(row => row.resourceType === "PART") || {};
    const totalStockCost = sumMoney(stockRows.map(row => row.costValue));
    const totalStockRetail = sumMoney(stockRows.map(row => row.retailValue));
    return `
      <section class="summary-grid">
        ${summaryCard("年度总收入", money(annual.totalIncome), `出库 ${money(annual.outboundRevenue)} / 维修 ${money(annual.repairIncome)}`)}
        ${summaryCard("年度毛利", money(annual.grossProfit), `租赁 ${money(annual.rentalIncome)} / 改装 ${money(annual.modificationIncome)}`)}
        ${summaryCard("库存成本", money(totalStockCost), `估值 ${money(totalStockRetail)} / 配件低库存 ${lowParts} 项`)}
        ${summaryCard("库存结构", `${machineStock.itemCount || 0} / ${partStock.itemCount || 0}`, "整车 / 配件档案数")}
      </section>
    `;
  }

  function sumMoney(values) {
    return values.reduce((total, value) => total + Number(value || 0), 0);
  }

  function priorityRank(priority) {
    if (priority === "danger") return 0;
    if (priority === "warn") return 1;
    if (priority === "teal") return 2;
    return 3;
  }

  function daysUntil(value) {
    if (!value) return null;
    const [year, month, day] = String(value).split("-").map(part => Number(part));
    if (!year || !month || !day) return null;
    const end = new Date(year, month - 1, day);
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    return Math.floor((end.getTime() - today.getTime()) / 86400000);
  }
}
