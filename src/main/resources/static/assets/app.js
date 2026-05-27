import { clearSession, createApiClient, readStoredToken, readStoredUser, saveSession } from "./modules/session.js";
import { endpoints } from "./modules/routes.js";
import { assignPageState, createPageState, pageContent, pageUrl, resetPage } from "./modules/paging.js";

const LOG_PAGE_SIZE = 120;
const LIST_PAGE_SIZE = 20;

const state = {
  token: readStoredToken(),
  user: normalizeUser(readStoredUser()),
  activeTab: "overview",
  search: {
    vehicles: "",
    parts: "",
    modificationOrders: "",
    outboundOrders: "",
    rentals: "",
    customers: "",
    repairs: "",
    stats: "",
    logs: "",
    configItems: "",
    configValues: "",
    users: ""
  },
  filters: {
    vehicles: {
      power: "",
      supplier: "",
      stock: ""
    }
  },
  data: {
    vehicles: [],
    parts: [],
    modificationOrders: [],
    outboundOrders: [],
    rentals: [],
    customers: [],
    repairs: [],
    statistics: null,
    operationLogs: [],
    configItems: [],
    configValues: [],
    configValueMap: {},
    repairUsers: [],
    users: []
  },
  pages: {
    vehicles: createPageState(LIST_PAGE_SIZE),
    parts: createPageState(LIST_PAGE_SIZE),
    modificationOrders: createPageState(LIST_PAGE_SIZE),
    outboundOrders: createPageState(LIST_PAGE_SIZE),
    rentals: createPageState(LIST_PAGE_SIZE),
    customers: createPageState(LIST_PAGE_SIZE),
    repairs: createPageState(LIST_PAGE_SIZE),
    logs: createPageState(LOG_PAGE_SIZE),
    users: createPageState(LIST_PAGE_SIZE)
  },
  reference: {
    vehiclesLoaded: false,
    partsLoaded: false,
    customersLoaded: false,
    outboundOrdersLoaded: false,
    rentalsLoaded: false,
    repairUsersLoaded: false
  },
  selectedVehicleId: null,
  vehicleDetail: null,
  selectedConfigItemId: null,
  configItemScrollTop: 0,
  selectedStatsYear: new Date().getFullYear(),
  visibleLogRows: LOG_PAGE_SIZE,
  modal: null
};

const api = createApiClient(() => state.token);

const tabs = {
  overview: { title: "总览", subtitle: "系统概览与业务入口" },
  vehicles: { title: "车辆库存", subtitle: "车型库存、整车档案与库存状态" },
  parts: { title: "配件库存", subtitle: "配件档案、库存数量与价格信息" },
  modifications: { title: "改装工单", subtitle: "客户出库前改装、配件替换与拆下件回库" },
  outboundOrders: { title: "订单列表", subtitle: "记录整车与配件出库、车款结清、报销售和发票申请" },
  rentals: { title: "租赁管理", subtitle: "记录租赁车辆、租赁去向、租赁价格和归还状态" },
  customers: { title: "客户列表", subtitle: "维护公司抬头、地址、联系人和税号/身份证号" },
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
  swap: '<svg viewBox="0 0 24 24" fill="none"><path d="M7 7h12l-3-3M17 17H5l3 3" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  upload: '<svg viewBox="0 0 24 24" fill="none"><path d="M12 16V4M7 9l5-5 5 5M5 20h14" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  download: '<svg viewBox="0 0 24 24" fill="none"><path d="M12 4v12M7 11l5 5 5-5M5 20h14" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  lock: '<svg viewBox="0 0 24 24" fill="none"><rect x="5" y="10" width="14" height="10" rx="2" stroke="currentColor" stroke-width="1.8"/><path d="M8 10V7a4 4 0 0 1 8 0v3" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>',
  unlock: '<svg viewBox="0 0 24 24" fill="none"><rect x="5" y="10" width="14" height="10" rx="2" stroke="currentColor" stroke-width="1.8"/><path d="M8 10V7a4 4 0 0 1 7.3-2.3" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>'
};

const fields = {
  vehicle: [
    { name: "vehicleProductNumber", label: "车号/产品编号", required: true, section: "入库信息" },
    { name: "inboundDate", label: "入库日期", type: "datetime-local", coerce: "datetime", defaultValue: nowInputDateTime, section: "入库信息" },
    { name: "supplier", label: "供应商", section: "入库信息" },
    { name: "warehouseName", label: "经销商名称/仓位", section: "入库信息" },
    { name: "applicationNumber", label: "样机申请单号", section: "入库信息" },
    { name: "materialNumber", label: "物料号", section: "入库信息" },
    { name: "name", label: "名称", required: true, section: "整机识别" },
    { name: "specificationModel", label: "规格型号", required: true, section: "整机识别" },
    { name: "configuration", label: "配置", type: "textarea", span: 2, section: "整机识别" },
    { name: "machineType", label: "动力", type: "select", options: powerTypeOptions, allowCustom: true, section: "整机识别" },
    { name: "engineNumber", label: "发动机号", section: "整机识别" },
    { name: "frameNumber", label: "车架号", section: "整机识别" },
    { name: "warrantyCardNumber", label: "保修卡号", section: "整机识别" },
    { name: "manufacturingDate", label: "制造日期", type: "date", coerce: "date", section: "整机识别" },
    { name: "stockStatus", label: "库存状态", type: "select", options: stockStatusOptions(), defaultValue: "IN_STOCK", section: "价格库存" },
    { name: "purchasePrice", label: "采购价", type: "number", coerce: "decimal", step: "0.01", section: "价格库存" },
    { name: "settlementPrice", label: "结算价", type: "number", coerce: "decimal", step: "0.01", section: "价格库存" },
    { name: "salePrice", label: "销售单价", type: "number", coerce: "decimal", step: "0.01", section: "价格库存" },
    { name: "inventoryCount", label: "库存数", type: "number", coerce: "int", step: "1", defaultValue: 1, section: "价格库存" },
    { name: "salesDate", label: "销售日期", type: "date", coerce: "date", section: "销售与去向" },
    { name: "isSalesReported", label: "是否报销售", section: "销售与去向" },
    { name: "salesReportDate", label: "报销售日期", type: "date", coerce: "date", section: "销售与去向" },
    { name: "isInvoiceApplied", label: "是否申请发票", section: "销售与去向" },
    { name: "destination1", label: "去向1", span: 2, section: "销售与去向" },
    { name: "destination2", label: "去向2", span: 2, section: "销售与去向" },
    { name: "destination3", label: "去向3", span: 2, section: "销售与去向" },
    { name: "destination4", label: "去向4", span: 2, section: "销售与去向" },
    { name: "destination5", label: "去向5", span: 2, section: "销售与去向" },
    { name: "remarks", label: "备注", type: "textarea", span: 2, section: "销售与去向" }
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
    { name: "vehicleProductNumber", label: "车号/产品编号", required: true, section: "入库信息" },
    { name: "inboundDate", label: "入库日期", type: "datetime-local", coerce: "datetime", defaultValue: nowInputDateTime, section: "入库信息" },
    { name: "supplier", label: "供应商", section: "入库信息" },
    { name: "warehouseName", label: "经销商名称/仓位", section: "入库信息" },
    { name: "applicationNumber", label: "样机申请单号", section: "入库信息" },
    { name: "materialNumber", label: "物料号", section: "入库信息" },
    { name: "name", label: "名称", required: true, section: "整机识别" },
    { name: "specificationModel", label: "规格型号", required: true, section: "整机识别" },
    { name: "configuration", label: "配置", type: "textarea", span: 2, section: "整机识别" },
    { name: "machineType", label: "动力", type: "select", options: powerTypeOptions, allowCustom: true, required: true, section: "整机识别" },
    { name: "engineNumber", label: "发动机号", section: "整机识别" },
    { name: "frameNumber", label: "车架号", section: "整机识别" },
    { name: "warrantyCardNumber", label: "保修卡号", section: "整机识别" },
    { name: "manufacturingDate", label: "制造日期", type: "date", coerce: "date", section: "整机识别" },
    { name: "stockStatus", label: "库存状态", type: "select", options: stockStatusOptions(), defaultValue: "IN_STOCK", section: "价格库存" },
    { name: "purchasePrice", label: "采购价", type: "number", coerce: "decimal", step: "0.01", section: "价格库存" },
    { name: "settlementPrice", label: "结算价", type: "number", coerce: "decimal", step: "0.01", section: "价格库存" },
    { name: "salePrice", label: "销售单价", type: "number", coerce: "decimal", step: "0.01", section: "价格库存" },
    { name: "inventoryCount", label: "库存数", type: "number", coerce: "int", step: "1", defaultValue: 1, section: "价格库存" },
    { name: "remarks", label: "备注", type: "textarea", span: 2, section: "销售与去向" }
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
    { name: "status", label: "状态", type: "toggle", options: statusOptions(), defaultValue: "PENDING" },
    { name: "machineId", label: "车号", type: "select", coerce: "int", required: true, options: vehicleOptions },
    { name: "customerId", label: "客户", type: "select", coerce: "int", required: true, options: customerOptions },
    { name: "faultDescription", label: "故障描述", type: "textarea", span: 2, required: true },
    { name: "repairContent", label: "维修内容", type: "textarea", span: 2 },
    { name: "repairPersonChoice", label: "维修人员", type: "select", required: true, options: repairPersonOptions },
    { name: "usedPartIds", label: "使用配件", type: "select", coerce: "intList", options: repairPartOptions },
    { name: "repairFee", label: "维修费", type: "number", coerce: "decimal", step: "0.01" },
    { name: "partsFee", label: "配件费", type: "number", coerce: "decimal", step: "0.01" },
    { name: "totalFee", label: "总费用", type: "number", coerce: "decimal", step: "0.01", readOnly: true },
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
  vehicleOutbound: [
    { name: "machineId", label: "车号/产品编号", type: "select", coerce: "int", required: true, options: vehicleOutboundOptions, section: "销售车辆" },
    { name: "salesDate", label: "销售日期", type: "date", coerce: "date", defaultValue: todayInputDate, section: "销售车辆" },
    { name: "settlementPrice", label: "结算价", type: "number", coerce: "decimal", step: "0.01", required: true, section: "价格收款" },
    { name: "salePrice", label: "销售单价", type: "number", coerce: "decimal", step: "0.01", section: "价格收款" },
    { name: "paymentSettled", label: "车款是否结清", type: "checkbox", coerce: "boolean", section: "价格收款" },
    { name: "paymentRemark", label: "收款情况", type: "textarea", span: 2, section: "价格收款" },
    { name: "customerMode", label: "客户录入", type: "select", required: true, options: customerEntryModeOptions, defaultValue: "existing", section: "客户资料" },
    { name: "customerId", label: "客户", type: "select", coerce: "int", required: true, options: customerOptions, section: "客户资料" },
    { name: "invoiceStatus", label: "开发票情况", section: "票据与报销售" },
    { name: "invoiceIssuedDate", label: "开票日期", type: "date", coerce: "date", section: "票据与报销售" },
    { name: "salesReported", label: "是否报销售", type: "checkbox", coerce: "boolean", section: "票据与报销售" },
    { name: "salesReportDate", label: "报销售日期", type: "date", coerce: "date", section: "票据与报销售" },
    { name: "invoiceApplied", label: "是否申请发票", type: "checkbox", coerce: "boolean", section: "票据与报销售" },
    { name: "invoiceApplicationDate", label: "发票申请日期", type: "date", coerce: "date", section: "票据与报销售" },
    { name: "registrationStatus", label: "是否上牌", section: "票据与报销售" },
    { name: "contractType", label: "合同", section: "票据与报销售" },
    { name: "operator", label: "经办人", section: "备注" },
    { name: "orderRemark", label: "备注", type: "textarea", span: 2, section: "备注" }
  ],
  vehicleOutboundQuickCustomer: [
    { name: "customerCompanyName", label: "客户", required: true, span: 2, section: "客户资料" },
    { name: "customerAddress", label: "地址", span: 2, section: "客户资料" },
    { name: "customerContactName", label: "联系人", section: "客户资料" },
    { name: "customerContactPhone", label: "电话", section: "客户资料" },
    { name: "customerTaxOrIdNumber", label: "税号/身份证号", span: 2, section: "客户资料" },
    { name: "customerRemarks", label: "客户备注", type: "textarea", span: 2, section: "客户资料" }
  ],
  partStock: [
    { name: "partCode", label: "配件", type: "select", required: true, options: partCodeOptions },
    { name: "quantity", label: "数量", type: "number", coerce: "int", step: "1", required: true, defaultValue: 1 },
    { name: "operator", label: "操作人" },
    { name: "remark", label: "备注", type: "textarea", span: 2 }
  ],
  partOutbound: [
    { name: "partCode", label: "配件", type: "select", required: true, options: partCodeOptions },
    { name: "quantity", label: "数量", type: "number", coerce: "int", step: "1", required: true, defaultValue: 1 },
    { name: "customerId", label: "客户", type: "select", coerce: "int", required: true, options: customerOptions },
    { name: "settlementPrice", label: "结算价", type: "number", coerce: "decimal", step: "0.01" },
    { name: "operator", label: "操作人" },
    { name: "orderRemark", label: "订单备注", type: "textarea", span: 2 }
  ],
  rental: [
    { name: "machineId", label: "租赁车辆", type: "select", coerce: "int", required: true, options: vehicleRentalOptions, section: "租赁车辆" },
    { name: "customerId", label: "租赁去向（客户）", type: "select", coerce: "int", required: true, options: customerOptions, section: "租赁信息" },
    { name: "destination", label: "具体去向/地址", span: 2, section: "租赁信息" },
    { name: "monthlyRentalPrice", label: "月租价格", type: "number", coerce: "decimal", step: "0.01", required: true, section: "租赁信息" },
    { name: "startDate", label: "开始日期", type: "date", coerce: "date", defaultValue: todayInputDate, section: "租赁信息" },
    { name: "endDate", label: "结束日期", type: "date", coerce: "date", section: "租赁信息" },
    { name: "status", label: "租赁状态", type: "toggle", options: rentalStatusOptions, defaultValue: "ACTIVE", section: "租赁信息" },
    { name: "operator", label: "经办人", section: "备注" },
    { name: "remark", label: "备注", type: "textarea", span: 2, section: "备注" }
  ],
  customer: [
    { name: "companyName", label: "公司名称", required: true },
    { name: "address", label: "地址", span: 2 },
    { name: "contactName", label: "联系人姓名" },
    { name: "contactPhone", label: "联系人电话" },
    { name: "taxOrIdNumber", label: "税号/身份证号", span: 2 },
    { name: "remarks", label: "备注", type: "textarea", span: 2 }
  ],
  outboundOrder: [
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
  invoiceUpload: [
    { name: "invoiceFile", label: "发票文件", type: "file", required: true, span: 2, accept: ".pdf,.ofd,.jpg,.jpeg,.png,.webp,application/pdf,image/jpeg,image/png,image/webp" }
  ],
  contractUpload: [
    { name: "contractFile", label: "合同文件", type: "file", required: true, span: 2, accept: ".pdf,.ofd,.doc,.docx,.jpg,.jpeg,.png,.webp,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,image/jpeg,image/png,image/webp" }
  ],
  partReplace: [
    { name: "machineId", label: "整车", type: "select", coerce: "int", required: true, options: vehicleOptions },
    { name: "machineConfigId", label: "原车配件", type: "select", coerce: "int", required: true, options: machineConfigOptions },
    { name: "newPartId", label: "同类型库存配件", type: "select", coerce: "int", required: true, options: compatiblePartOptions },
    { name: "operator", label: "操作人" },
    { name: "remark", label: "备注", type: "textarea", span: 2 }
  ],
  vehiclePartInstall: [
    { name: "machineId", type: "hidden", coerce: "int" },
    { name: "configItemId", label: "配件分类", type: "select", coerce: "int", required: true, options: installPartCategoryOptions },
    { name: "newPartId", label: "仓库配件", type: "select", coerce: "int", required: true, options: installPartOptions },
    { name: "quantity", label: "数量", type: "number", coerce: "int", step: "1", required: true, defaultValue: 1 }
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
    { name: "role", label: "角色", type: "select", options: userRoleOptions, required: true, defaultValue: "USER" },
    { name: "jobTag", label: "职务", type: "select", options: jobTagOptions, required: true, defaultValue: "CLERK" }
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

const pagedTabs = {
  vehicles: { endpoint: endpoints.vehicle.list, dataKey: "vehicles", searchKey: "vehicles", permission: null, sortDesc: true },
  parts: { endpoint: endpoints.part.list, dataKey: "parts", searchKey: "parts", permission: null, sortDesc: true },
  modificationOrders: { endpoint: endpoints.modificationOrder.list, dataKey: "modificationOrders", searchKey: "modificationOrders", permission: "replace:write", sortDesc: true },
  outboundOrders: { endpoint: endpoints.outboundOrder.list, dataKey: "outboundOrders", searchKey: "outboundOrders", permission: "stock:adjust", sortDesc: true },
  rentals: { endpoint: endpoints.rental.list, dataKey: "rentals", searchKey: "rentals", permission: "stock:adjust", sortDesc: true },
  customers: { endpoint: endpoints.customer.list, dataKey: "customers", searchKey: "customers", permission: null, sortDesc: false },
  repairs: { endpoint: endpoints.repair.list, dataKey: "repairs", searchKey: "repairs", permission: null, sortDesc: true },
  logs: { endpoint: endpoints.logs, dataKey: "operationLogs", searchKey: "logs", permission: "log:read", sortDesc: false },
  users: { endpoint: endpoints.user.list, dataKey: "users", searchKey: "users", permission: "user:read", sortDesc: true }
};

const activeTabPageKeys = {
  modifications: "modificationOrders"
};

let els;
let searchReloadTimer = null;

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
  els.content.addEventListener("keydown", handleContentKeydown);
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
  clearSession();
  closeModal();
  showLogin();
  if (message) showToast(message, "info");
}

async function loadAllData(options = {}) {
  const activeModelKey = state.vehicleDetail?.modelKey;
  await ensureConfigData();
  await loadCurrentTab({ force: options.force !== false });
  if (state.selectedVehicleId) {
    if (activeModelKey) {
      await loadVehicleModelDetail(activeModelKey, state.selectedVehicleId, false);
    } else {
      await loadVehicleDetail(state.selectedVehicleId, false);
    }
  }
}

async function loadCurrentTab(options = {}) {
  const tab = state.activeTab;
  if (tab === "overview") {
    await loadOverviewData(options);
    return;
  }
  if (tab === "stats") {
    if (hasPermission("log:read")) {
      await loadStatistics(state.selectedStatsYear);
    } else {
      state.data.statistics = null;
    }
    return;
  }
  if (tab === "configs") {
    await ensureConfigData(true);
    return;
  }
  if (tab === "vehicles") {
    await loadVehicleModelData(options);
    return;
  }
  const pagedTab = activePagedTab();
  if (pagedTab) {
    await loadPagedTab(pagedTab, options);
  }
}

async function ensureConfigData(force = false) {
  if (!force && state.data.configItems.length) return;
  state.data.configItems = sortById(await api(endpoints.configItem.list), false);
  await loadConfigValueCache(state.data.configItems);
  if (!state.selectedConfigItemId && state.data.configItems.length) {
    state.selectedConfigItemId = state.data.configItems[0].id;
  }
  if (state.selectedConfigItemId) {
    state.data.configValues = sortById(state.data.configValueMap[state.selectedConfigItemId] || await fetchConfigValues(state.selectedConfigItemId), false);
  }
}

async function loadOverviewData() {
  await Promise.all([
    loadPagedTab("vehicles", { force: true, silent: true, withVehicleReferences: false }),
    loadPagedTab("parts", { force: true, silent: true }),
    loadPagedTab("repairs", { force: true, silent: true }),
    loadPagedTab("outboundOrders", { force: true, silent: true }),
    loadPagedTab("rentals", { force: true, silent: true })
  ]);
  await loadVehicleFlowReferences({ force: true });
}

async function loadVehicleModelData(options = {}) {
  if (options.force === false && state.reference.vehiclesLoaded) return;
  state.data.vehicles = sortById(await api(endpoints.vehicle.list));
  state.reference.vehiclesLoaded = true;
}

async function loadPagedTab(tab, options = {}) {
  const config = pagedTabs[tab];
  if (!config) return;
  if (config.permission && !hasPermission(config.permission)) {
    state.data[config.dataKey] = [];
    assignPageState(state.pages[tab], { content: [], page: 0, size: state.pages[tab].size, totalElements: 0, totalPages: 0, first: true, last: true });
    return;
  }

  const pageState = state.pages[tab];
  pageState.loading = true;
  const payload = await api(pageUrl(config.endpoint, pageState, state.search[config.searchKey] || ""));
  const rows = pageContent(payload);
  state.data[config.dataKey] = config.dataKey === "operationLogs"
    ? sortLogs(rows)
    : sortById(rows, config.sortDesc !== false);
  assignPageState(pageState, payload);

  if (tab === "vehicles" && options.withVehicleReferences !== false) {
    await loadVehicleFlowReferences({ force: true });
  }
  if (tab === "repairs" && hasPermission("stock:adjust") && !state.reference.rentalsLoaded) {
    state.data.rentals = sortById(await api(endpoints.rental.list));
    state.reference.rentalsLoaded = true;
  }
}

async function loadVehicleFlowReferences(options = {}) {
  if (!hasPermission("stock:adjust")) {
    state.data.outboundOrders = [];
    state.data.rentals = [];
    state.reference.outboundOrdersLoaded = true;
    state.reference.rentalsLoaded = true;
    return;
  }
  if (options.force === false && state.reference.outboundOrdersLoaded && state.reference.rentalsLoaded) {
    return;
  }
  const [orders, rentals] = await Promise.all([
    api(endpoints.outboundOrder.list),
    api(endpoints.rental.list)
  ]);
  state.data.outboundOrders = sortById(orders);
  state.data.rentals = sortById(rentals);
  state.reference.outboundOrdersLoaded = true;
  state.reference.rentalsLoaded = true;
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

async function loadConfigValues(itemId, options = {}) {
  state.selectedConfigItemId = Number(itemId);
  const values = sortById(await fetchConfigValues(itemId), false);
  state.data.configValueMap[state.selectedConfigItemId] = values;
  state.data.configValues = values;
  renderCurrentTab();
  if (options.restoreConfigScroll) restoreConfigItemScroll();
}

function restoreConfigItemScroll() {
  requestAnimationFrame(() => {
    const list = els.content.querySelector(".config-items-pane .config-list");
    if (list) list.scrollTop = state.configItemScrollTop || 0;
  });
}

async function loadVehicleDetail(id, shouldRender = true) {
  state.selectedVehicleId = Number(id);
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
  const vehicles = vehiclesForModelKey(modelKey);
  const model = vehicleModelGroups().find(group => group.modelKey === modelKey) || { ...decodeVehicleModelKey(modelKey), modelKey, vehicles };
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
  const button = event.target.closest("[data-tab]");
  if (!button) return;
  state.activeTab = button.dataset.tab;
  els.content.innerHTML = renderLoading();
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
    if (action === "export-excel") {
      await downloadExcel(control.dataset.exportType);
      return;
    }
    if (action === "page-list") {
      const tab = control.dataset.pageTab;
      if (!pagedTabs[tab]) return;
      state.pages[tab].page = Math.max(0, Number(control.dataset.page || 0));
      if (tab === "vehicles") {
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
      const group = vehicleModelGroups().find(item => item.modelKey === control.dataset.modelKey);
      await openEntityModal("vehicleInbound", vehicleInboundDefaultsForModel(group));
      return;
    }
    if (action === "model-outbound") {
      const group = vehicleModelGroups().find(item => item.modelKey === control.dataset.modelKey);
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
  const selectable = event.target.closest("[data-select-action]");
  if (!selectable || !els.content.contains(selectable)) return;
  event.preventDefault();
  selectable.click();
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
    if (tab === "vehicles") {
      renderCurrentTab();
      const nextInput = els.content.querySelector(`[data-search-for="${input.dataset.searchFor}"]`);
      if (nextInput) {
        nextInput.focus();
        nextInput.setSelectionRange(input.value.length, input.value.length);
      }
      return;
    }
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
      await loadPagedTab(tab, { force: true });
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

function handleOverlayClick(event) {
  if (event.target === els.modalOverlay) closeModal();
}

function handleModalClick(event) {
  const configAction = event.target.closest("[data-config-action]");
  if (configAction) {
    event.preventDefault();
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
    openCombo(comboInput.closest("[data-combo]"), comboInput.value.trim());
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
}

function handleModalFocusIn(event) {
  const input = event.target.closest("[data-combo-input]");
  if (!input) return;
  openCombo(input.closest("[data-combo]"), input.value.trim());
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
    state.modal.item[event.target.name] = event.target.value;
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
  }

  if (kind === "rental" && event.target.name === "customerId") {
    syncRentalCustomerDestination(form, Number(event.target.value || 0));
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

  if (kind === "partStock" && state.modal?.item?.direction === "outbound" && event.target.name === "partCode") {
    syncPartOutboundDefaults(event.target.value);
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
  const activeModelKey = state.vehicleDetail?.modelKey || item.__modelKey || "";

  if (kind === "invoiceUpload" || kind === "contractUpload") {
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
      showToast("车型已新增", "success");
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
      showToast("此车型入库成功", "success");
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
      showToast(customerResult.created ? "已新建客户并创建整车出库订单" : "整车出库订单已创建", "success");
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
        showToast("租赁记录已更新", "success");
      } else {
        await api(endpoints.rental.create, { method: "POST", body });
        showToast("租赁记录已创建", "success");
      }
    } else if (kind === "vehicleStock") {
      attachStockVersion(payload, "vehicle");
      await api(`/api/inventory/${payload.machineId}/${item.direction}`, {
        method: "PUT",
        body: { quantity: payload.quantity, operator: payload.operator, remark: payload.remark, version: payload.version }
      });
      showToast(item.direction === "inbound" ? "整车入库成功" : "整车出库成功", "success");
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
            operator: payload.operator,
            orderRemark: payload.orderRemark
          }
        });
        showToast("配件出库订单已创建", "success");
      } else {
        await api(`/api/parts/${item.direction}`, {
          method: "PUT",
          body: { partCode: payload.partCode, quantity: payload.quantity, operator: payload.operator, remark: payload.remark, version: payload.version }
        });
        showToast("配件入库成功", "success");
      }
    } else if (kind === "partReplace") {
      enrichPartReplacePayload(payload);
      await api(endpoints.partReplace.create, { method: "POST", body: payload });
      showToast("配件替换成功，旧件已自动入库", "success");
    } else if (kind === "vehiclePartInstall") {
      enrichVehiclePartInstallPayload(payload);
      await api(endpoints.partReplace.install, { method: "POST", body: payload });
      showToast("配件装车成功", "success");
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
    } else if (kind === "outboundOrder") {
      await api(endpoints.outboundOrder.update(item.id), { method: "PUT", body: payload });
      showToast("订单状态已更新", "success");
    } else if (item.id && endpoints[kind].update) {
      await api(endpoints[kind].update(item.id), { method: "PUT", body: payload });
      showToast("保存成功", "success");
    } else {
      await api(endpoints[kind].create, { method: "POST", body: payload });
      showToast("新增成功", "success");
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
  const message = kind === "user" ? "确认删除这个用户？" : "确认删除这条数据？";
  if (!confirm(message)) return;
  await api(withVersion(endpoints[kind].delete(id), item), { method: "DELETE" });
  showToast("删除成功", "success");
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
  if (!confirm("确认完成这张改装工单？完成后会扣减新配件库存、旧件入库，并更新车辆配置。")) return;
  const activeModelKey = state.vehicleDetail?.modelKey;
  const targetMachineId = order.machineId || state.selectedVehicleId;
  await api(endpoints.modificationOrder.complete(id), {
    method: "PUT",
    body: { version: order.version }
  });
  showToast("改装工单已完成", "success");
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

async function cancelModificationOrder(id) {
  const order = findModificationOrder(id);
  if (!order.id) {
    showToast("工单数据已刷新，请再点一次取消", "info");
    await loadAllData();
    renderCurrentTab();
    return;
  }
  if (!confirm("确认取消这张改装工单？")) return;
  const activeModelKey = state.vehicleDetail?.modelKey;
  const targetMachineId = order.machineId || state.selectedVehicleId;
  await api(endpoints.modificationOrder.cancel(id), {
    method: "PUT",
    body: { version: order.version }
  });
  showToast("改装工单已取消", "success");
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
        quantity: payload.quantity || 1,
        oldPartAction: payload.oldPartAction || "STOCK_IN",
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
  await api(endpoints.outboundOrder.uploadInvoice(order.id), {
    method: "POST",
    body: formData
  });
  showToast("发票已上传", "success");
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
  await api(endpoints.outboundOrder.uploadContract(order.id), {
    method: "POST",
    body: formData
  });
  showToast("合同已上传", "success");
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

async function toggleUserJobTag(id) {
  const user = findEntity("user", id);
  if (!user?.id || !canUpdateUserJobTag(user)) return;
  const nextTag = nextJobTag(user.jobTag);
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
  if ((kind === "configValue" || kind === "vehiclePartInstall") && !state.data.configItems.length) {
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
  const needsVehicles = ["vehicleStock", "vehicleOutbound", "partReplace", "vehiclePartInstall", "modificationOrder", "vehicleInbound", "rental", "repair"].includes(kind);
  const needsParts = ["part", "partStock", "partReplace", "vehiclePartInstall", "modificationOrder", "repair"].includes(kind);
  const needsCustomers = ["vehicleOutbound", "partStock", "partOutbound", "customer", "rental", "repair"].includes(kind);
  const needsRentals = ["vehicleOutbound", "rental", "repair"].includes(kind);
  const needsRepairUsers = kind === "repair";
  const requests = [];
  if (needsVehicles && !state.reference.vehiclesLoaded) {
    requests.push(api(endpoints.vehicle.list).then(rows => {
      state.data.vehicles = sortById(rows);
      state.reference.vehiclesLoaded = true;
    }));
  }
  if (needsParts && !state.reference.partsLoaded) {
    requests.push(api(endpoints.part.list).then(rows => {
      state.data.parts = sortById(rows);
      state.reference.partsLoaded = true;
    }));
  }
  if (needsCustomers && !state.reference.customersLoaded && hasAnyPermission("stock:adjust", "vehicle:write", "repair:write")) {
    requests.push(api(endpoints.customer.list).then(rows => {
      state.data.customers = sortById(rows, false);
      state.reference.customersLoaded = true;
    }));
  }
  if (needsRentals && !state.reference.rentalsLoaded && hasPermission("stock:adjust")) {
    requests.push(api(endpoints.rental.list).then(rows => {
      state.data.rentals = sortById(rows);
      state.reference.rentalsLoaded = true;
    }));
  }
  if (needsRepairUsers && hasPermission("repair:write")) {
    requests.push(api(endpoints.user.repairers).then(rows => {
      state.data.repairUsers = sortById(rows);
      state.reference.repairUsersLoaded = true;
    }));
  }
  await Promise.all(requests);
}

function markReferenceDataStale(kinds = []) {
  if (!kinds.length || kinds.includes("vehicle")) state.reference.vehiclesLoaded = false;
  if (!kinds.length || kinds.includes("part")) state.reference.partsLoaded = false;
  if (!kinds.length || kinds.includes("customer")) state.reference.customersLoaded = false;
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
    outboundOrder: "outboundOrders"
  };
  const tab = mapping[kind];
  if (tab && state.pages[tab]) {
    resetPage(state.pages[tab]);
  }
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

function scrollToVehicleDetail() {
  requestAnimationFrame(() => {
    const target = document.getElementById("vehicleDetailSurface");
    if (target) target.scrollIntoView({ block: "start", behavior: "smooth" });
  });
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
  const vehicles = state.data.vehicles.filter(item => !item.modelOnly);
  const vehicleRows = vehicleFlowRows();
  const parts = state.data.parts;
  const repairs = state.data.repairs;
  const orders = state.data.outboundOrders;
  const inStockRows = vehicleRows.filter(row => !row.orderId && Number(row.inventoryCount || 0) > 0);
  const lowStockParts = parts.filter(item => Number(item.quantity || 0) <= 0);
  const missingInvoiceOrders = orders.filter(item => isInvoiceUploadReady(item) && !item.invoiceFileAvailable);
  const missingContractOrders = orders.filter(item => isContractUploadReady(item) && !item.contractFileAvailable);
  const pendingRepairs = repairs.filter(item => item.status !== "COMPLETED").length;
  const lowParts = lowStockParts.length;
  const unsettledOrders = orders.filter(item => !item.paymentSettled).length;
  const pendingSalesReports = orders.filter(item => item.paymentSettled && !item.salesReported).length;
  const pendingInvoices = orders.filter(item => item.paymentSettled && item.salesReported && !item.invoiceApplied).length;
  const missingInvoiceFiles = missingInvoiceOrders.length;
  const missingContractFiles = missingContractOrders.length;
  const vehicleTotal = pageTotal("vehicles", vehicles.length);
  const partTotal = pageTotal("parts", parts.length);
  const orderTotal = pageTotal("outboundOrders", orders.length);
  const workItems = overviewWorkItems(vehicleRows, orders, repairs, parts).slice(0, 12);
  const recentVehicleRows = vehicleRows
    .filter(row => row.orderId || Number(row.inventoryCount || 0) > 0)
    .slice(0, 8);

  return `
    <div class="page overview-page">
      <section class="summary-grid">
        ${summaryCard("整车档案", vehicleTotal, `${inStockRows.length} 台在库待销售`)}
        ${summaryCard("出库订单", orderTotal, `${unsettledOrders} 单待收款`)}
        ${summaryCard("销售闭环", pendingSalesReports + pendingInvoices, `${missingInvoiceFiles} 单待发票文件`)}
        ${summaryCard("合同文件", missingContractFiles, `${orders.filter(isContractUploadReady).length} 单标记有合同`)}
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
        <div class="overview-attachment-row">
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
        <div class="overview-task-row">
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
  const attrs = Object.entries(item.data || {})
    .filter(([, value]) => value !== undefined && value !== null && value !== "")
    .map(([key, value]) => `data-${toDataAttrName(key)}="${escapeAttr(value)}"`)
    .join(" ");
  const label = item.action === "upload-invoice" ? "上传发票"
    : item.action === "upload-contract" ? "上传合同"
      : item.action === "toggle-repair-status" ? "完成"
      : item.action === "vehicle-outbound-direct" ? "登记销售"
        : "处理";
  return `<button class="btn btn-sm" type="button" data-action="${escapeAttr(item.action)}" ${attrs}>${icon(item.action?.startsWith("upload") ? "upload" : item.action === "toggle-repair-status" ? "swap" : "edit")}${label}</button>`;
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
  const modelBaseRows = filterVehicleRows(filterRows(vehicleModelGroups(), state.search.vehicles, [
    "name", "specificationModel", "configuration", "machineType", "supplier", "warehouseName", "vehicleNumbers"
  ]));
  const modelRows = paginateClientRows("vehicles", modelBaseRows);

  return `
    <div class="page">
      <div class="toolbar">
        <div class="toolbar-main">
          ${searchBox("vehicles", "搜索车型、型号、供应商或车号")}
          ${vehicleFilterControls()}
        </div>
        <div class="toolbar-actions">
          ${hasPermission("vehicle:write") ? renderVehicleModelMenu() : ""}
          ${exportButton("vehicles")}
          <button class="btn btn-ghost" type="button" data-action="refresh">${icon("refresh")}刷新</button>
        </div>
      </div>
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
      <div class="toolbar">
        ${searchBox("parts", "搜索编码、名称、品牌、分类")}
        <div class="toolbar-actions">
          ${hasPermission("part:write") ? `<button class="btn btn-primary" type="button" data-action="create" data-kind="part">${icon("plus")}新增配件</button>` : ""}
          ${hasPermission("stock:adjust") ? `<button class="btn" type="button" data-action="part-stock" data-direction="inbound">${icon("plus")}配件入库</button>` : ""}
          ${hasPermission("stock:adjust") ? `<button class="btn" type="button" data-action="part-stock" data-direction="outbound">${icon("minus")}配件出库</button>` : ""}
          ${exportButton("parts")}
          <button class="btn btn-ghost" type="button" data-action="refresh">${icon("refresh")}刷新</button>
        </div>
      </div>
      ${renderExportableSurface("配件列表", "parts", renderTable([
        { label: "编码", key: "partCode" },
        { label: "名称", key: "partName" },
        { label: "品牌", key: "partBrand" },
        { label: "分类", key: "partCategory" },
        { label: "数量", html: true, render: row => stockBadge(row.quantity, row.unit) },
        { label: "销售价", key: "salePrice", formatter: money },
        { label: "来源", key: "source" },
        { label: "操作", html: true, render: row => rowActions("part", row, ["stockIn", "stockOut", "edit", "delete"]) }
      ], rows))}
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
      <div class="toolbar">
        ${searchBox("modificationOrders", "搜索工单号、客户、销售单或状态")}
        <div class="toolbar-actions">
          ${hasPermission("replace:write") ? `<button class="btn btn-primary" type="button" data-action="create" data-kind="modificationOrder">${icon("plus")}新建改装工单</button>` : ""}
          <button class="btn btn-ghost" type="button" data-action="refresh">${icon("refresh")}刷新</button>
        </div>
      </div>
      ${renderSurface("改装工单列表", renderTable([
        { label: "工单号", key: "workOrderNo" },
        { label: "车辆", html: true, render: row => modificationOrderMachineSummary(row) },
        { label: "客户", key: "customerName" },
        { label: "销售单", key: "salesOrderNo" },
        { label: "状态", html: true, render: row => modificationStatusBadge(row.status) },
        { label: "替换明细", html: true, render: row => modificationLineSummary(row.lines) },
        { label: "创建时间", key: "createdAt", formatter: dateTime },
        { label: "操作", html: true, render: row => modificationOrderActions(row) }
      ], rows))}
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
  const unsettledOrders = rows.filter(item => !item.paymentSettled).length;
  const pendingReports = rows.filter(item => !item.salesReported).length;
  const pendingInvoices = rows.filter(item => !item.invoiceApplied).length;
  const settledOrders = rows.filter(item => item.paymentSettled).length;
  const uploadedInvoices = rows.filter(item => item.invoiceFileAvailable).length;
  const uploadedContracts = rows.filter(item => item.contractFileAvailable).length;

  return `
    <div class="page">
      <section class="summary-grid">
        ${summaryCard("出库订单", rows.length, `${unsettledOrders} 单待收款`)}
        ${summaryCard("待报销售", pendingReports, `${rows.filter(item => item.resourceType === "MACHINE").length} 单整机订单`)}
        ${summaryCard("待申请发票", pendingInvoices, `${uploadedInvoices} 单已上传发票`)}
        ${summaryCard("已结清车款", settledOrders, `${uploadedContracts} 单已上传合同`)}
      </section>
      <div class="toolbar">
        ${searchBox("outboundOrders", "搜索订单号、客户、车号、收款情况、开票情况或备注")}
        <div class="toolbar-actions">
          ${exportButton("outboundOrders")}
          <button class="btn btn-ghost" type="button" data-action="refresh">${icon("refresh")}刷新</button>
        </div>
      </div>
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
      ], rows))}
      ${renderPagination("outboundOrders")}
    </div>
  `;
}

function renderRentals() {
  const rows = filterRows(state.data.rentals, state.search.rentals, [
    "rentalNo", "vehicleNumber", "machineName", "specificationModel", "customerName", "customerAddress", "destination", "status", "operator", "remark"
  ]);
  const activeRows = rows.filter(item => item.status === "ACTIVE");
  const returnedRows = rows.filter(item => item.status === "RETURNED");
  const rentalIncome = rows.reduce((sum, item) => sum + Number(rentalMonthlyPrice(item) || 0), 0);

  return `
    <div class="page">
      <section class="summary-grid">
        ${summaryCard("租赁记录", rows.length, `${activeRows.length} 台租赁中`)}
        ${summaryCard("月租收入", money(rentalIncome), `${returnedRows.length} 单已归还`)}
        ${summaryCard("租赁车辆", new Set(rows.map(item => item.machineId).filter(Boolean)).size, "按具体车号记录")}
        ${summaryCard("待归还", activeRows.length, activeRows.length ? "请持续跟进租赁去向" : "暂无进行中租赁")}
      </section>
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
      ], rows))}
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
  return `
    <div class="cell-stack">
      ${orderStatusToggle(row, "paymentSettled", Boolean(row.paymentSettled), "已结清", "未结清")}
      ${row.paymentRemark ? `<span class="helper-inline">${escapeHtml(row.paymentRemark)}</span>` : `<span class="helper-inline">未填写收款情况</span>`}
    </div>
  `;
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
      ], rows))}
      ${renderPagination("customers")}
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
      ${renderExportableSurface("维修记录", "repairs", renderTable([
        { label: "维修时间", key: "repairDate", formatter: dateTime },
        { label: "车号", html: true, render: row => repairVehicleSummary(row) },
        { label: "客户", html: true, render: row => repairCustomerSummary(row) },
        { label: "维修人", html: true, render: row => repairPersonSummary(row) },
        { label: "状态", html: true, render: row => repairStatusToggle(row) },
        { label: "总费用", key: "totalFee", formatter: money },
        { label: "操作", html: true, render: row => rowActions("repair", row, ["edit", "delete"]) }
      ], rows))}
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
        ${summaryCard("年度总收入", money(annual.totalIncome), `出库 ${money(annual.outboundRevenue)} / 维修 ${money(annual.repairIncome)} / 租赁 ${money(annual.rentalIncome)}`)}
        ${summaryCard("入库成本", money(annual.inboundCost), `${annual.inboundQuantity || 0} 件/台入库`)}
        ${summaryCard("出库毛利", money(annual.grossProfit), `${annual.outboundQuantity || 0} 件/台出库`)}
        ${summaryCard("租赁收入", money(annual.rentalIncome), `${annual.rentalOrders || 0} 单租赁`)}
      </section>

      ${renderSurface("月度收支走势", renderFinanceBars(monthlyRows))}

      <section class="grid-two">
        ${renderSurface("月度财报", renderTable([
          { label: "月份", key: "period" },
          { label: "入库成本", key: "inboundCost", formatter: money },
          { label: "出库收入", key: "outboundRevenue", formatter: money },
          { label: "出库成本", key: "outboundCost", formatter: money },
          { label: "维修收入", key: "repairIncome", formatter: money },
          { label: "租赁收入", key: "rentalIncome", formatter: money },
          { label: "毛利", key: "grossProfit", formatter: money }
        ], monthlyRows))}
        ${renderSurface("年度对比", renderTable([
          { label: "年份", key: "period" },
          { label: "总收入", key: "totalIncome", formatter: money },
          { label: "入库成本", key: "inboundCost", formatter: money },
          { label: "出库毛利", key: "grossProfit", formatter: money },
          { label: "维修单", key: "repairOrders" },
          { label: "租赁单", key: "rentalOrders" }
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
      <div class="toolbar">
        ${searchBox("users", "搜索用户名、角色或职务")}
        <div class="toolbar-actions">
          ${hasPermission("user:write") ? `<button class="btn btn-primary" type="button" data-action="create" data-kind="user">${icon("plus")}新建用户</button>` : ""}
          <button class="btn btn-ghost" type="button" data-action="refresh">${icon("refresh")}刷新</button>
        </div>
      </div>
      ${renderSurface("用户列表", renderTable([
        { label: "用户名", key: "username" },
        { label: "角色", html: true, render: row => roleBadges(row.roles) },
        { label: "职务", html: true, render: row => jobTagControl(row) },
        { label: "状态", html: true, render: row => userEnabledControl(row) },
        { label: "创建时间", key: "createdAt", formatter: dateTime },
        { label: "操作", html: true, render: row => userActions(row) }
      ], rows))}
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
    ? `<div class="toolbar-actions detail-action-row">
      <button class="btn btn-sm" type="button" data-action="edit" data-kind="vehicle" data-id="${escapeAttr(machine.id)}">${icon("edit")}编辑档案</button>
    </div>`
    : "";
  return renderSurface("入库档案", `
    ${actions}
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
  `);
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
    <div class="toolbar-actions detail-action-row">${actions}</div>
    ${body}
  `);
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
  return `
    <section class="surface" id="vehicleDetailSurface">
      <div class="surface-head">
        <h2 class="surface-title">车辆详情 · ${escapeHtml(machine.vehicleProductNumber || "")}</h2>
        <div class="toolbar-actions">
          ${order?.id && hasPermission("stock:adjust") ? `<button class="btn" type="button" data-action="edit" data-kind="outboundOrder" data-id="${escapeAttr(order.id)}">${icon("edit")}销售跟进</button>` : ""}
          ${!order?.id && Number(machine.inventoryCount || 0) > 0 && hasPermission("stock:adjust") ? `<button class="btn" type="button" data-action="vehicle-outbound-direct" data-machine-id="${escapeAttr(machine.id || "")}">${icon("minus")}登记出库</button>` : ""}
          ${hasPermission("replace:write") ? `<button class="btn" type="button" data-action="create-vehicle-part" data-machine-id="${escapeAttr(machine.id || "")}">${icon("plus")}新增配件</button>` : ""}
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
          ${renderVehiclePartsSurface(machine, vehicleParts)}
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
  const vehicleParts = detail.selectedDetail?.parts || [];
  const manualSelected = isManualForklift(selected.machineType || model.machineType);
  const order = latestVehicleOutboundOrder(selected.id);
  return `
    <section class="surface" id="vehicleDetailSurface">
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
              ${selected.id && hasPermission("replace:write") ? `<button class="btn" type="button" data-action="create-vehicle-part" data-machine-id="${escapeAttr(selected.id || "")}">${icon("plus")}新增配件</button>` : ""}
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
          ${renderVehiclePartsSurface(selected, vehicleParts)}
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
  const manual = isManualForklift(vehicle.machineType);
  return `
    <button class="vehicle-unit-card${active ? " is-active" : ""}" type="button" data-action="select-model-vehicle" data-model-key="${escapeAttr(modelKey)}" data-machine-id="${escapeAttr(vehicle.id)}">
      <span class="vehicle-unit-title">${escapeHtml(vehicle.vehicleProductNumber || `ID ${vehicle.id}`)}</span>
      <span class="vehicle-unit-meta">${escapeHtml(stockStatusLabel(vehicle.stockStatus))}${manual ? " · 系统车号" : ` · ${escapeHtml(vehicle.engineNumber || "未填发动机号")}`}</span>
      ${manual ? "" : `<span class="vehicle-unit-meta">${escapeHtml(vehicle.frameNumber || "未填车架号")}</span>`}
    </button>
  `;
}

function renderToolbar(moduleKey, placeholder, kind, createLabel, createPermission) {
  return `
    <div class="toolbar">
      ${searchBox(moduleKey, placeholder)}
      <div class="toolbar-actions">
        ${!createPermission || hasPermission(createPermission) ? `<button class="btn btn-primary" type="button" data-action="create" data-kind="${kind}">${icon("plus")}${createLabel}</button>` : ""}
        ${exportButton(moduleKey)}
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

function vehicleFilterControls() {
  const filters = state.filters.vehicles;
  return `
    <div class="filter-row">
      ${filterSelect("vehicles", "power", filters.power, "全部动力", powerTypeOptions())}
      ${filterSelect("vehicles", "supplier", filters.supplier, "全部供应商", supplierFilterOptions())}
      ${filterSelect("vehicles", "stock", filters.stock, "全部库存", stockFilterOptions())}
    </div>
  `;
}

function filterSelect(key, name, value, placeholder, options) {
  return `
    <select class="filter-select" data-filter-for="${escapeAttr(key)}" data-filter-name="${escapeAttr(name)}">
      <option value="">${escapeHtml(placeholder)}</option>
      ${options.map(option => `<option value="${escapeAttr(option.value)}"${String(option.value) === String(value) ? " selected" : ""}>${escapeHtml(option.label)}</option>`).join("")}
    </select>
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

function renderTable(columns, rows, options = {}) {
  if (!rows.length) return emptyState("暂无数据");
  return `
    <div class="table-wrap">
      <table>
        <thead>
          <tr>${columns.map(column => `<th>${escapeHtml(column.label)}</th>`).join("")}</tr>
        </thead>
        <tbody>
          ${rows.map(row => {
            const selectable = options.selectableRow ? options.selectableRow(row) : null;
            const rowClass = [
              selectable ? "table-row-selectable" : "",
              selectable?.active ? "table-row-active" : ""
            ].filter(Boolean).join(" ");
            return `
              <tr${rowClass ? ` class="${rowClass}"` : ""}${renderSelectableAttrs(selectable)}>
                ${columns.map(column => `<td>${renderCell(column, row)}</td>`).join("")}
              </tr>
            `;
          }).join("")}
        </tbody>
      </table>
    </div>
  `;
}

function renderExportableSurface(title, exportType, body) {
  return `
    <section class="surface">
      <div class="surface-head">
        <h2 class="surface-title">${title}</h2>
        <div class="toolbar-actions">${exportButton(exportType)}</div>
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
    </div>
  `).join("");
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
    return `
      <label class="field"${span}>
        <span>${escapeHtml(field.label)}</span>
        <div class="combo${options.length ? "" : " is-empty"}" data-combo data-name="${escapeAttr(field.name)}" data-allow-custom="${field.allowCustom ? "true" : "false"}" data-required="${field.required ? "true" : "false"}">
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
  const key = {
    vehicle: "vehicles",
    part: "parts",
    modificationOrder: "modificationOrders",
    outboundOrder: "outboundOrders",
    rental: "rentals",
    customer: "customers",
    repair: "repairs",
    configItem: "configItems",
    configValue: "configValues",
    user: "users"
  }[kind];
  return (state.data[key] || []).find(item => item.id === id) || {};
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
  if (kind === "switchUser" || kind === "vehicleStock" || kind === "vehicleInbound" || kind === "vehicleOutbound" || kind === "rental" || kind === "partStock" || kind === "partReplace" || kind === "vehiclePartInstall" || kind === "modificationOrder" || kind === "userUsername" || kind === "userPassword" || kind === "invoiceUpload" || kind === "contractUpload") {
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
  return (state.data.configValueMap[configItemId] || [])
    .map(value => ({
      value: value.id,
      label: value.valueLabel || "-"
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
    rental: "stock:adjust",
    configItem: "config:write",
    configValue: "config:write",
    customer: "vehicle:write"
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

function rentalStatusBadge(status) {
  const map = {
    ACTIVE: ["租赁中", "warn"],
    RETURNED: ["已归还", "teal"]
  };
  const [label, type] = map[status] || [status || "未设置", "primary"];
  return badge(label, type);
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
    COMPLETED: ["已完成", "success"]
  };
  const [label, type] = map[status] || [status || "未设置", "primary"];
  return badge(label, type);
}

function repairStatusText(status) {
  const map = {
    PENDING: "待处理",
    COMPLETED: "已完成"
  };
  return map[status] || status || "未设置";
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

function yesNoBadge(value) {
  return Boolean(value) ? badge("是", "teal") : badge("否", "primary");
}

function roleBadges(roles = []) {
  const roleLabels = {
    SUPER_ADMIN: "超级管理员",
    ADMIN: "管理员",
    USER: "普通用户"
  };
  return (roles || []).map(role => badge(roleLabels[role] || role, role === "ADMIN" ? "warn" : role === "SUPER_ADMIN" ? "teal" : "primary")).join(" ");
}

function normalizeJobTag(value, roles = []) {
  const tag = String(value || "").trim().toUpperCase();
  if (["MANAGEMENT", "CLERK", "REPAIR"].includes(tag)) return tag;
  return roles?.some(role => role === "ADMIN" || role === "SUPER_ADMIN") ? "MANAGEMENT" : "CLERK";
}

function jobTagLabel(tag) {
  const labels = {
    MANAGEMENT: "管理",
    CLERK: "文员",
    REPAIR: "维修"
  };
  return labels[normalizeJobTag(tag)] || "文员";
}

function jobTagType(tag) {
  const types = {
    MANAGEMENT: "primary",
    CLERK: "teal",
    REPAIR: "warn"
  };
  return types[normalizeJobTag(tag)] || "primary";
}

function jobTagBadge(tag) {
  return badge(jobTagLabel(tag), jobTagType(tag));
}

function nextJobTag(tag) {
  const order = ["MANAGEMENT", "CLERK", "REPAIR"];
  const index = order.indexOf(normalizeJobTag(tag));
  return order[(index + 1) % order.length];
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

function dateValue(value) {
  if (!value) return "";
  return String(value).slice(0, 10);
}

function dateTime(value) {
  if (!value) return "";
  return String(value).replace("T", " ").slice(0, 16);
}

function fileSize(value) {
  const size = Number(value || 0);
  if (!Number.isFinite(size) || size <= 0) return "-";
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}

function toInputValue(value, field) {
  if (value === null || value === undefined) return "";
  if (field.type === "datetime-local") return String(value).slice(0, 16);
  if (field.type === "date") return String(value).slice(0, 10);
  return value;
}

function todayInputDate() {
  const date = new Date();
  const offset = date.getTimezoneOffset();
  return new Date(date.getTime() - offset * 60000).toISOString().slice(0, 10);
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

function handleActionError(error) {
  if (isAuthExpiredError(error)) {
    logout("登录已过期，请重新登录");
    return;
  }
  showToast(error.message || "操作失败", "error");
}

function isAuthExpiredError(error) {
  return Boolean(error?.authExpired);
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
