from pathlib import Path

path = Path(r"D:\erp\forklift-erp\src\main\resources\static\assets\app.js")
text = path.read_text(encoding="utf-8")


def replace_between(source: str, start: str, end: str, replacement: str) -> str:
    start_idx = source.find(start)
    if start_idx == -1:
        raise RuntimeError(f"start marker not found: {start!r}")
    end_idx = source.find(end, start_idx)
    if end_idx == -1:
        raise RuntimeError(f"end marker not found: {end!r}")
    return source[:start_idx] + replacement + source[end_idx:]


def replace_once(source: str, old: str, new: str) -> str:
    if old not in source:
        raise RuntimeError(f"pattern not found: {old[:80]!r}")
    return source.replace(old, new, 1)


text = replace_between(
    text,
    "  vehicle: [",
    "  part: [",
    """  vehicle: [
    { name: "vehicleProductNumber", label: "车号/产品编号", required: true },
    { name: "name", label: "名称", required: true },
    { name: "specificationModel", label: "规格型号", required: true },
    { name: "configuration", label: "配置", type: "textarea", span: 2 },
    { name: "machineType", label: "动力", type: "select", options: powerTypeOptions, allowCustom: true },
    { name: "supplier", label: "供应商" },
    { name: "warehouseName", label: "经销商/仓位" },
    { name: "applicationNumber", label: "样机申请单号" },
    { name: "materialNumber", label: "物料号" },
    { name: "stockStatus", label: "库存状态", type: "select", options: stockStatusOptions(), defaultValue: "IN_STOCK" },
    { name: "purchasePrice", label: "采购价", type: "number", coerce: "decimal", step: "0.01" },
    { name: "settlementPrice", label: "结算价", type: "number", coerce: "decimal", step: "0.01" },
    { name: "salePrice", label: "销售单价", type: "number", coerce: "decimal", step: "0.01" },
    { name: "engineNumber", label: "发动机号" },
    { name: "frameNumber", label: "车架号" },
    { name: "warrantyCardNumber", label: "保修卡号" },
    { name: "manufacturingDate", label: "制造日期", type: "date", coerce: "date" },
    { name: "inboundDate", label: "入库时间", type: "datetime-local", coerce: "datetime" },
    { name: "salesDate", label: "销售日期", type: "date", coerce: "date" },
    { name: "isSalesReported", label: "报销售状态" },
    { name: "salesReportDate", label: "报销售日期", type: "date", coerce: "date" },
    { name: "inventoryCount", label: "库存数", type: "number", coerce: "int", step: "1", defaultValue: 1 },
    { name: "destination1", label: "去向1", span: 2 },
    { name: "destination2", label: "去向2", span: 2 },
    { name: "destination3", label: "去向3", span: 2 },
    { name: "destination4", label: "去向4", span: 2 },
    { name: "destination5", label: "去向5", span: 2 },
    { name: "isInvoiceApplied", label: "发票申请状态" },
    { name: "remarks", label: "备注", type: "textarea", span: 2 }
  ],
  vehicleModel: [
    { name: "name", label: "车型", required: true },
    { name: "specificationModel", label: "规格型号", required: true },
    { name: "configuration", label: "默认配置", type: "textarea", span: 2 },
    { name: "machineType", label: "动力", type: "select", options: powerTypeOptions, allowCustom: true, required: true },
    { name: "supplier", label: "供应商" },
    { name: "warehouseName", label: "经销商/仓位" },
    { name: "purchasePrice", label: "采购价", type: "number", coerce: "decimal", step: "0.01" },
    { name: "salePrice", label: "销售单价", type: "number", coerce: "decimal", step: "0.01" }
  ],
  vehicleInbound: [
    { name: "vehicleProductNumber", label: "车号/产品编号", required: true },
    { name: "name", label: "名称", required: true },
    { name: "specificationModel", label: "规格型号", required: true },
    { name: "configuration", label: "配置", type: "textarea", span: 2 },
    { name: "machineType", label: "动力", type: "select", options: powerTypeOptions, allowCustom: true, required: true },
    { name: "supplier", label: "供应商" },
    { name: "warehouseName", label: "经销商/仓位" },
    { name: "applicationNumber", label: "样机申请单号" },
    { name: "materialNumber", label: "物料号" },
    { name: "stockStatus", label: "库存状态", type: "select", options: stockStatusOptions(), defaultValue: "IN_STOCK" },
    { name: "purchasePrice", label: "采购价", type: "number", coerce: "decimal", step: "0.01" },
    { name: "settlementPrice", label: "结算价", type: "number", coerce: "decimal", step: "0.01" },
    { name: "salePrice", label: "销售单价", type: "number", coerce: "decimal", step: "0.01" },
    { name: "engineNumber", label: "发动机号" },
    { name: "frameNumber", label: "车架号" },
    { name: "warrantyCardNumber", label: "保修卡号" },
    { name: "manufacturingDate", label: "制造日期", type: "date", coerce: "date" },
    { name: "inboundDate", label: "入库时间", type: "datetime-local", coerce: "datetime" },
    { name: "inventoryCount", label: "库存数", type: "number", coerce: "int", step: "1", defaultValue: 1 },
    { name: "remarks", label: "备注", type: "textarea", span: 2 }
  ],
""",
)

text = replace_between(
    text,
    "  vehicleOutbound: [",
    "  partStock: [",
    """  vehicleOutbound: [
    { name: "machineId", label: "出库车号", type: "select", coerce: "int", required: true, options: vehicleOutboundOptions },
    { name: "customerId", label: "客户", type: "select", coerce: "int", required: true, options: customerOptions },
    { name: "salesDate", label: "销售日期", type: "date", coerce: "date", defaultValue: todayInputDate },
    { name: "settlementPrice", label: "结算价", type: "number", coerce: "decimal", step: "0.01", required: true },
    { name: "salePrice", label: "销售单价", type: "number", coerce: "decimal", step: "0.01" },
    { name: "paymentSettled", label: "车款结清", type: "checkbox", coerce: "boolean" },
    { name: "paymentRemark", label: "收款情况", type: "textarea", span: 2 },
    { name: "salesReported", label: "已报销售", type: "checkbox", coerce: "boolean" },
    { name: "salesReportDate", label: "报销售日期", type: "date", coerce: "date" },
    { name: "invoiceApplied", label: "申请发票", type: "checkbox", coerce: "boolean" },
    { name: "invoiceApplicationDate", label: "发票申请日期", type: "date", coerce: "date" },
    { name: "invoiceStatus", label: "开票情况" },
    { name: "invoiceIssuedDate", label: "开票日期", type: "date", coerce: "date" },
    { name: "registrationStatus", label: "上牌情况" },
    { name: "contractType", label: "合同" },
    { name: "operator", label: "经办人" },
    { name: "orderRemark", label: "订单备注", type: "textarea", span: 2 }
  ],
""",
)

text = replace_between(
    text,
    "  outboundOrder: [",
    "  partReplace: [",
    """  outboundOrder: [
    { name: "salesDate", label: "销售日期", type: "date", coerce: "date" },
    { name: "settlementPrice", label: "结算价", type: "number", coerce: "decimal", step: "0.01" },
    { name: "salePrice", label: "销售单价", type: "number", coerce: "decimal", step: "0.01" },
    { name: "paymentSettled", label: "车款结清", type: "checkbox", coerce: "boolean" },
    { name: "paymentRemark", label: "收款情况", type: "textarea", span: 2 },
    { name: "salesReported", label: "已报销售", type: "checkbox", coerce: "boolean" },
    { name: "invoiceApplied", label: "申请发票", type: "checkbox", coerce: "boolean" },
    { name: "salesReportDate", label: "报销售日期", type: "date", coerce: "date" },
    { name: "invoiceApplicationDate", label: "发票申请日期", type: "date", coerce: "date" },
    { name: "invoiceStatus", label: "开票情况" },
    { name: "invoiceIssuedDate", label: "开票日期", type: "date", coerce: "date" },
    { name: "registrationStatus", label: "上牌情况" },
    { name: "contractType", label: "合同" },
    { name: "orderRemark", label: "订单备注", type: "textarea", span: 2 },
    { name: "operator", label: "经办人" }
  ],
""",
)

text = replace_once(
    text,
    """    if (action === "load-more-logs") {
      state.visibleLogRows += LOG_PAGE_SIZE;
      renderCurrentTab();
      return;
    }
""",
    """    if (action === "load-more-logs") {
      state.visibleLogRows += LOG_PAGE_SIZE;
      renderCurrentTab();
      return;
    }
    if (action === "set-vehicle-view") {
      state.filters.vehicles = {
        ...state.filters.vehicles,
        view: control.dataset.view === "ledger" ? "ledger" : "model"
      };
      renderCurrentTab();
      return;
    }
""",
)

text = replace_once(
    text,
    """    if (action === "model-outbound") {
      const group = vehicleModelGroups().find(item => item.modelKey === control.dataset.modelKey);
      await openEntityModal("vehicleOutbound", vehicleOutboundDefaultsForModel(group));
      return;
    }
""",
    """    if (action === "model-outbound") {
      const group = vehicleModelGroups().find(item => item.modelKey === control.dataset.modelKey);
      await openEntityModal("vehicleOutbound", vehicleOutboundDefaultsForModel(group));
      return;
    }
    if (action === "vehicle-outbound-direct") {
      const machine = findEntity("vehicle", Number(control.dataset.machineId || id || 0));
      await openEntityModal("vehicleOutbound", vehicleOutboundDefaultsForMachine(machine));
      return;
    }
""",
)

text = replace_between(
    text,
    '    } else if (kind === "vehicleOutbound") {',
    '    } else if (kind === "vehicleStock") {',
    """    } else if (kind === "vehicleOutbound") {
      const machine = findEntity("vehicle", Number(payload.machineId || 0));
      await api(endpoints.outboundOrder.vehicle, {
        method: "POST",
        body: {
          machineId: payload.machineId,
          machineVersion: machine.version,
          customerId: payload.customerId,
          salesDate: payload.salesDate,
          settlementPrice: payload.settlementPrice,
          salePrice: payload.salePrice,
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
      showToast("整车出库订单已创建", "success");
""",
)

text = replace_between(
    text,
    "function renderVehicles() {",
    "function renderParts() {",
    """function renderVehicles() {
  const view = state.filters.vehicles?.view === "ledger" ? "ledger" : "model";
  const modelRows = filterVehicleRows(filterRows(vehicleModelGroups(), state.search.vehicles, [
    "name", "specificationModel", "configuration", "machineType", "supplier", "warehouseName", "vehicleNumbers"
  ]));
  const ledgerRows = filterVehicleRows(filterRows(vehicleLedgerRows(), state.search.vehicles, [
    "vehicleProductNumber", "name", "specificationModel", "configuration", "supplier", "warehouseName",
    "applicationNumber", "materialNumber", "customerName", "destinationText", "paymentRemark", "invoiceStatus", "remarks"
  ]));

  return `
    <div class="page">
      <div class="toolbar">
        <div class="toolbar-main">
          ${searchBox("vehicles", view === "ledger" ? "搜索车号、型号、客户、去向、物料号或备注" : "搜索车型、型号、供应商或车号")}
          ${vehicleFilterControls()}
        </div>
        <div class="toolbar-actions">
          ${renderVehicleViewToggle(view)}
          ${hasPermission("vehicle:write") ? renderVehicleModelMenu() : ""}
          <button class="btn btn-ghost" type="button" data-action="refresh">${icon("refresh")}刷新</button>
        </div>
      </div>
      ${view === "ledger" ? renderVehicleLedgerPanel(ledgerRows) : renderVehicleModelPanel(modelRows)}
      ${renderVehicleDetail()}
    </div>
  `;
}

function renderVehicleViewToggle(view) {
  return `
    <div class="segmented-control" role="tablist" aria-label="车辆视图切换">
      <button class="segment-btn${view === "model" ? " is-active" : ""}" type="button" data-action="set-vehicle-view" data-view="model">车型视图</button>
      <button class="segment-btn${view === "ledger" ? " is-active" : ""}" type="button" data-action="set-vehicle-view" data-view="ledger">台账视图</button>
    </div>
  `;
}

function renderVehicleModelPanel(rows) {
  return renderSurface("车型库存列表", renderTable([
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
    selectableRow: row => ({
      action: "detail-vehicle",
      data: { modelKey: row.modelKey },
      active: state.vehicleDetail?.modelKey === row.modelKey,
      label: `查看车型 ${vehicleModelLabel(row)} 详情`
    })
  }));
}

function renderVehicleLedgerPanel(rows) {
  const inStockCount = rows.filter(row => Number(row.inventoryCount || 0) > 0).length;
  const outboundCount = rows.filter(row => row.orderId).length;
  const pendingReport = rows.filter(row => row.orderId && !row.salesReported).length;
  const pendingInvoice = rows.filter(row => row.orderId && !row.invoiceApplied).length;

  return `
    <section class="summary-grid">
      ${summaryCard("在库整机", inStockCount, `${rows.length - inStockCount} 台已出库或待跟进`)}
      ${summaryCard("已登记销售", outboundCount, `${Math.max(0, rows.length - outboundCount)} 台仅有入库档案`)}
      ${summaryCard("待报销售", pendingReport, `${rows.filter(row => row.orderId).length} 台已建出库订单`)}
      ${summaryCard("待申请发票", pendingInvoice, `${rows.filter(row => row.paymentSettled).length} 台车款已结清`)}
    </section>
    ${renderSurface("整机进出库台账", renderTable([
      { label: "入库时间", key: "inboundDate", formatter: dateTime },
      { label: "车号/产品编号", html: true, render: row => ledgerMachineSummary(row) },
      { label: "供应商", key: "supplier" },
      { label: "规格型号", key: "specificationModel" },
      { label: "台账状态", html: true, render: row => vehicleLedgerStatusSummary(row) },
      { label: "价格", html: true, render: row => vehicleLedgerPriceSummary(row) },
      { label: "销售跟进", html: true, render: row => vehicleLedgerFollowupSummary(row) },
      { label: "客户/去向", html: true, render: row => vehicleLedgerDestinationSummary(row) },
      { label: "备注", html: true, render: row => vehicleLedgerRemarkSummary(row) },
      { label: "操作", html: true, render: row => machineLedgerActions(row) }
    ], rows, {
      selectableRow: row => ({
        action: "detail-vehicle",
        data: { id: row.id },
        active: state.selectedVehicleId === row.id && !state.vehicleDetail?.modelKey,
        label: `查看车辆 ${row.vehicleProductNumber || row.id} 详情`
      })
    }))}
  `;
}

function vehicleLedgerRows() {
  return state.data.vehicles
    .filter(item => !item.modelOnly)
    .map(machine => {
      const order = latestVehicleOutboundOrder(machine.id);
      return {
        ...machine,
        orderId: order?.id || null,
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
        salePrice: order?.salePrice ?? machine.salePrice,
        settlementPrice: order?.settlementPrice ?? machine.settlementPrice,
        registrationStatus: order?.registrationStatus || "",
        contractType: order?.contractType || "",
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

function joinVehicleDestinations(machine = {}) {
  return [machine.destination1, machine.destination2, machine.destination3, machine.destination4, machine.destination5]
    .filter(value => value !== null && value !== undefined && String(value).trim() !== "")
    .join(" / ");
}

function machineLedgerActions(row) {
  return `
    <div class="action-row">
      <button class="btn btn-sm" type="button" data-action="detail-vehicle" data-id="${escapeAttr(row.id)}">${icon("eye")}详情</button>
      ${row.orderId && hasPermission("stock:adjust") ? `<button class="btn btn-sm" type="button" data-action="edit" data-kind="outboundOrder" data-id="${escapeAttr(row.orderId)}">${icon("edit")}销售跟进</button>` : ""}
      ${!row.orderId && Number(row.inventoryCount || 0) > 0 && hasPermission("stock:adjust") ? `<button class="btn btn-sm" type="button" data-action="vehicle-outbound-direct" data-machine-id="${escapeAttr(row.id)}">${icon("minus")}登记出库</button>` : ""}
      ${hasPermission("vehicle:write") ? `<button class="btn btn-sm" type="button" data-action="edit" data-kind="vehicle" data-id="${escapeAttr(row.id)}">${icon("edit")}编辑档案</button>` : ""}
    </div>
  `;
}

function ledgerMachineSummary(row) {
  return `
    <div class="cell-stack">
      <strong>${escapeHtml(row.vehicleProductNumber || `ID ${row.id}`)}</strong>
      <span class="helper-inline">${escapeHtml(row.name || "-")} · ${escapeHtml(row.specificationModel || "-")}</span>
      ${row.configuration ? `<span class="helper-inline">${escapeHtml(row.configuration)}</span>` : ""}
    </div>
  `;
}

function vehicleLedgerStatusSummary(row) {
  return `
    <div class="cell-stack">
      ${stockStatusBadge(row.stockStatus)}
      <span class="helper-inline">库存 ${escapeHtml(row.inventoryCount || 0)} 台</span>
    </div>
  `;
}

function vehicleLedgerPriceSummary(row) {
  return `
    <div class="cell-stack">
      <span>结算 ${escapeHtml(money(row.settlementPrice) || "-")}</span>
      <span class="helper-inline">销售 ${escapeHtml(money(row.salePrice) || "-")}</span>
    </div>
  `;
}

function vehicleLedgerFollowupSummary(row) {
  return `
    <div class="cell-stack">
      <span>${escapeHtml(dateValue(row.salesDate) || "未登记销售日期")}</span>
      <span class="helper-inline">车款 ${escapeHtml(yesNoText(row.paymentSettled))} · 报销售 ${escapeHtml(yesNoText(row.salesReported))} · 发票 ${escapeHtml(yesNoText(row.invoiceApplied))}</span>
      ${row.paymentRemark ? `<span class="helper-inline">${escapeHtml(row.paymentRemark)}</span>` : ""}
    </div>
  `;
}

function vehicleLedgerDestinationSummary(row) {
  const primary = row.customerName || row.destination1 || "";
  const extra = row.destinationText && row.destinationText !== primary ? row.destinationText : "";
  return `
    <div class="cell-stack">
      <span>${escapeHtml(primary || "未登记客户/去向")}</span>
      ${extra ? `<span class="helper-inline">${escapeHtml(extra)}</span>` : ""}
    </div>
  `;
}

function vehicleLedgerRemarkSummary(row) {
  const tags = [row.applicationNumber && `申请单 ${row.applicationNumber}`, row.materialNumber && `物料 ${row.materialNumber}`].filter(Boolean);
  return `
    <div class="cell-stack">
      ${tags.length ? `<span>${escapeHtml(tags.join(" · "))}</span>` : `<span class="muted">-</span>`}
      ${row.remarks ? `<span class="helper-inline">${escapeHtml(row.remarks)}</span>` : ""}
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

""",
)

text = replace_between(
    text,
    "function renderOutboundOrders() {",
    "function renderCustomers() {",
    """function renderOutboundOrders() {
  const rows = filterRows(state.data.outboundOrders, state.search.outboundOrders, [
    "orderNo", "resourceType", "resourceCode", "resourceName", "customerName", "operator", "orderRemark",
    "paymentRemark", "invoiceStatus", "registrationStatus", "contractType"
  ]);
  const unsettledOrders = rows.filter(item => !item.paymentSettled).length;
  const pendingReports = rows.filter(item => !item.salesReported).length;
  const pendingInvoices = rows.filter(item => !item.invoiceApplied).length;
  const settledOrders = rows.filter(item => item.paymentSettled).length;

  return `
    <div class="page">
      <section class="summary-grid">
        ${summaryCard("出库订单", rows.length, `${unsettledOrders} 单待收款`)}
        ${summaryCard("待报销售", pendingReports, `${rows.filter(item => item.resourceType === "MACHINE").length} 单整机订单`)}
        ${summaryCard("待申请发票", pendingInvoices, `${rows.filter(item => item.invoiceStatus).length} 单已写开票情况`)}
        ${summaryCard("已结清车款", settledOrders, `${rows.filter(item => item.salesReported).length} 单已报销售`)}
      </section>
      <div class="toolbar">
        ${searchBox("outboundOrders", "搜索订单号、客户、车号、收款情况、开票情况或备注")}
        <div class="toolbar-actions">
          <button class="btn btn-ghost" type="button" data-action="refresh">${icon("refresh")}刷新</button>
        </div>
      </div>
      ${renderSurface("出库订单列表", renderTable([
        { label: "订单号", key: "orderNo" },
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
      ], rows))}
    </div>
  `;
}

function orderPriceSummary(row) {
  return `
    <div class="cell-stack">
      <span>结算 ${escapeHtml(money(row.settlementPrice) || "-")}</span>
      <span class="helper-inline">销售 ${escapeHtml(money(row.salePrice) || "-")}</span>
    </div>
  `;
}

function orderPaymentSummary(row) {
  return `
    <div class="cell-stack">
      ${yesNoBadge(row.paymentSettled)}
      ${row.paymentRemark ? `<span class="helper-inline">${escapeHtml(row.paymentRemark)}</span>` : `<span class="helper-inline">未填写收款情况</span>`}
    </div>
  `;
}

function orderSalesReportSummary(row) {
  return `
    <div class="cell-stack">
      ${yesNoBadge(row.salesReported)}
      <span class="helper-inline">${escapeHtml(dateValue(row.salesReportDate) || "未登记报销售日期")}</span>
    </div>
  `;
}

function orderInvoiceSummary(row) {
  return `
    <div class="cell-stack">
      ${yesNoBadge(row.invoiceApplied)}
      ${row.invoiceStatus ? `<span class="helper-inline">${escapeHtml(row.invoiceStatus)}</span>` : ""}
      <span class="helper-inline">申请 ${escapeHtml(dateValue(row.invoiceApplicationDate) || "-")} · 开票 ${escapeHtml(dateValue(row.invoiceIssuedDate) || "-")}</span>
    </div>
  `;
}

function orderContractSummary(row) {
  return `
    <div class="cell-stack">
      <span>${escapeHtml(row.registrationStatus || "未登记")}</span>
      <span class="helper-inline">${escapeHtml(row.contractType || "未登记合同")}</span>
    </div>
  `;
}

""",
)

text = replace_between(
    text,
    "function renderVehicleDetail() {",
    "function renderVehicleModelDetail() {",
    """function renderDetailGrid(items) {
  return `
    <div class="detail-grid detail-grid-compact">
      ${items.map(item => detailItem(item.label, item.value)).join("")}
    </div>
  `;
}

function renderVehicleArchiveSurface(machine = {}) {
  return renderSurface("入库档案", renderDetailGrid([
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
  ]));
}

function renderVehicleSalesSurface(machine = {}, order = null) {
  return renderSurface("销售跟进", `
    ${renderDetailGrid([
      { label: "销售日期", value: dateValue(order?.salesDate || machine.salesDate) },
      { label: "车款结清", value: yesNoText(order?.paymentSettled) },
      { label: "收款情况", value: order?.paymentRemark },
      { label: "报销售状态", value: machine.isSalesReported || yesNoText(order?.salesReported) },
      { label: "报销售日期", value: dateValue(order?.salesReportDate || machine.salesReportDate) },
      { label: "发票申请", value: machine.isInvoiceApplied || yesNoText(order?.invoiceApplied) },
      { label: "发票申请日期", value: dateValue(order?.invoiceApplicationDate) },
      { label: "开票情况", value: order?.invoiceStatus },
      { label: "开票日期", value: dateValue(order?.invoiceIssuedDate) },
      { label: "上牌情况", value: order?.registrationStatus },
      { label: "合同", value: order?.contractType },
      { label: "订单备注", value: order?.orderRemark }
    ])}
    ${order?.id && hasPermission("stock:adjust") ? `<div class="toolbar-actions detail-action-row"><button class="btn" type="button" data-action="edit" data-kind="outboundOrder" data-id="${escapeAttr(order.id)}">${icon("edit")}编辑销售跟进</button></div>` : ""}
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

function renderVehicleDetail() {
  if (!state.vehicleDetail) return "";
  if (state.vehicleDetail.modelKey) {
    return renderVehicleModelDetail();
  }
  const machine = state.vehicleDetail.machine || {};
  const configs = state.vehicleDetail.configs || [];
  const logs = state.vehicleDetail.logs || [];
  const workOrders = state.vehicleDetail.workOrders || [];
  const order = latestVehicleOutboundOrder(machine.id);
  return `
    <section class="surface">
      <div class="surface-head">
        <h2 class="surface-title">车辆详情 · ${escapeHtml(machine.vehicleProductNumber || "")}</h2>
        <div class="toolbar-actions">
          ${order?.id && hasPermission("stock:adjust") ? `<button class="btn" type="button" data-action="edit" data-kind="outboundOrder" data-id="${escapeAttr(order.id)}">${icon("edit")}销售跟进</button>` : ""}
          ${!order?.id && Number(machine.inventoryCount || 0) > 0 && hasPermission("stock:adjust") ? `<button class="btn" type="button" data-action="vehicle-outbound-direct" data-machine-id="${escapeAttr(machine.id || "")}">${icon("minus")}登记出库</button>` : ""}
          ${hasPermission("replace:write") ? `<button class="btn btn-primary" type="button" data-action="create-modification-order" data-machine-id="${escapeAttr(machine.id || "")}">${icon("swap")}新建改装工单</button>` : ""}
        </div>
      </div>
      <div class="surface-body split">
        <div class="grid-two">
          ${renderVehicleArchiveSurface(machine)}
          ${renderVehicleSalesSurface(machine, order)}
          ${renderVehicleDestinationSurface(machine)}
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
        </div>
        <div class="grid-two">
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

""",
)

text = replace_between(
    text,
    "function renderVehicleModelDetail() {",
    "function renderVehicleUnitCard(vehicle, modelKey, active) {",
    """function renderVehicleModelDetail() {
  const detail = state.vehicleDetail;
  const model = detail.model || {};
  const selected = detail.selectedDetail?.machine || {};
  const configs = detail.selectedDetail?.configs || [];
  const logs = detail.selectedDetail?.logs || [];
  const workOrders = detail.selectedDetail?.workOrders || [];
  const manualSelected = isManualForklift(selected.machineType || model.machineType);
  const order = latestVehicleOutboundOrder(selected.id);
  return `
    <section class="surface">
      <div class="surface-head">
        <h2 class="surface-title">车型详情 · ${escapeHtml(vehicleModelLabel(model))}</h2>
        <div class="toolbar-actions">
          ${hasPermission("vehicle:write") ? `<button class="btn btn-primary" type="button" data-action="model-inbound" data-model-key="${escapeAttr(detail.modelKey)}">${icon("plus")}此车型入库</button>` : ""}
          ${hasPermission("stock:adjust") ? `<button class="btn" type="button" data-action="model-outbound" data-model-key="${escapeAttr(detail.modelKey)}">${icon("minus")}整车出库</button>` : ""}
        </div>
      </div>
      <div class="surface-body split">
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
        <div class="grid-two">
          ${renderSurface("库存车号", `
            <div class="vehicle-unit-grid">
              ${detail.vehicles.length ? detail.vehicles.map(vehicle => renderVehicleUnitCard(vehicle, detail.modelKey, vehicle.id === selected.id)).join("") : emptyState("此车型还没有库存车")}
            </div>
          `)}
          ${renderVehicleArchiveSurface(selected)}
          ${renderVehicleSalesSurface(selected, order)}
          ${renderSurface(`当前配置 · ${escapeHtml(selected.vehicleProductNumber || "请选择车号")}`, `
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
              ${selected.id && hasPermission("replace:write") ? `<button class="btn btn-primary" type="button" data-action="create-modification-order" data-machine-id="${escapeAttr(selected.id || "")}">${icon("swap")}新建改装工单</button>` : ""}
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

""",
)

text = replace_between(
    text,
    "function vehicleInboundDefaultsForModel(group = {}) {",
    "function modificationOrderPlaceholders(machine = {}, configId = null) {",
    """function vehicleInboundDefaultsForModel(group = {}) {
  const entity = {
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
    __modelKey: modelKey || vehicleModelKey(machine),
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
  const settlement = machine.settlementPrice || machine.salePrice || "";
  const salePrice = machine.salePrice || machine.settlementPrice || "";
  setPrefill(state.modal.item, "settlementPrice", settlement, settlement ? `预填：${money(settlement)}` : undefined);
  setPrefill(state.modal.item, "salePrice", salePrice, salePrice ? `预填：${money(salePrice)}` : undefined);
}

function syncPartOutboundDefaults(partCode) {
  const part = state.data.parts.find(item => String(item.partCode) === String(partCode)) || {};
  if (part.version !== undefined) {
    state.modal.item.version = part.version;
  }
  clearPrefill(state.modal.item, "settlementPrice");
  const price = part.settlementPrice || part.salePrice || "";
  setPrefill(state.modal.item, "settlementPrice", price, price ? `预填：${money(price)}` : undefined);
}

""",
)

text = replace_between(
    text,
    "function getFields(kind, item = state.modal?.item || {}) {",
    "function fieldOrPrefillValue(item, name) {",
    """function getFields(kind, item = state.modal?.item || {}) {
  if (kind === "vehicleInbound" && isManualForklift(fieldOrPrefillValue(item, "machineType"))) {
    const hiddenNames = new Set(["vehicleProductNumber", "engineNumber", "frameNumber", "warrantyCardNumber", "machineType"]);
    return [
      ...fields.vehicleInbound.filter(field => !hiddenNames.has(field.name)),
      { name: "machineType", type: "hidden" }
    ];
  }
  if (kind === "partStock" && item.direction === "outbound") {
    return fields.partOutbound;
  }
  return fields[kind] || [];
}

""",
)

text = replace_once(
    text,
    """function dateTime(value) {
  if (!value) return "";
  return String(value).replace("T", " ").slice(0, 16);
}
""",
    """function dateValue(value) {
  if (!value) return "";
  return String(value).slice(0, 10);
}

function dateTime(value) {
  if (!value) return "";
  return String(value).replace("T", " ").slice(0, 16);
}
""",
)

text = replace_once(
    text,
    """function nowInputDateTime() {
  const date = new Date();
  const offset = date.getTimezoneOffset();
  return new Date(date.getTime() - offset * 60000).toISOString().slice(0, 16);
}
""",
    """function todayInputDate() {
  const date = new Date();
  const offset = date.getTimezoneOffset();
  return new Date(date.getTime() - offset * 60000).toISOString().slice(0, 10);
}

function nowInputDateTime() {
  const date = new Date();
  const offset = date.getTimezoneOffset();
  return new Date(date.getTime() - offset * 60000).toISOString().slice(0, 16);
}
""",
)

path.write_text(text, encoding="utf-8")
print("updated app.js")
