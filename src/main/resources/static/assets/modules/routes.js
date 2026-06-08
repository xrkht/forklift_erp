export const endpoints = {
  vehicle: {
    list: "/api/inventory",
    models: "/api/inventory/models",
    modelVehicles: "/api/inventory/model-vehicles",
    create: "/api/inventory",
    update: id => `/api/inventory/${id}`,
    delete: id => `/api/inventory/${id}`
  },
  part: {
    list: "/api/parts",
    create: "/api/parts",
    update: id => `/api/parts/${id}`,
    delete: id => `/api/parts/${id}`
  },
  repair: {
    list: "/api/repairs",
    create: "/api/repairs",
    update: id => `/api/repairs/${id}`,
    updateStatus: id => `/api/repairs/${id}/status`,
    delete: id => `/api/repairs/${id}`
  },
  configItem: {
    list: "/api/config/items",
    create: "/api/config/items",
    update: id => `/api/config/items/${id}`,
    delete: id => `/api/config/items/${id}`
  },
  configValue: {
    listByItems: "/api/config/values",
    create: "/api/config/values",
    delete: id => `/api/config/values/${id}`
  },
  partReplace: {
    create: "/api/replace/part",
    install: "/api/replace/install-part"
  },
  modificationOrder: {
    list: "/api/modification-work-orders",
    create: "/api/modification-work-orders",
    complete: id => `/api/modification-work-orders/${id}/complete`,
    cancel: id => `/api/modification-work-orders/${id}/cancel`
  },
  outboundOrder: {
    list: "/api/outbound-orders",
    vehicle: "/api/outbound-orders/vehicle",
    part: "/api/outbound-orders/part",
    update: id => `/api/outbound-orders/${id}`,
    lock: id => `/api/outbound-orders/${id}/lock`,
    uploadInvoice: id => `/api/outbound-orders/${id}/invoice`,
    downloadInvoice: id => `/api/outbound-orders/${id}/invoice`,
    uploadContract: id => `/api/outbound-orders/${id}/contract`,
    downloadContract: id => `/api/outbound-orders/${id}/contract`
  },
  attachments: {
    list: "/api/attachments",
    byResource: "/api/attachments/resource",
    upload: "/api/attachments",
    download: id => `/api/attachments/${id}/download`,
    preview: id => `/api/attachments/${id}/preview`,
    delete: id => `/api/attachments/${id}`
  },
  rental: {
    list: "/api/rentals",
    create: "/api/rentals",
    update: id => `/api/rentals/${id}`,
    delete: id => `/api/rentals/${id}`
  },
  customer: {
    list: "/api/customers",
    create: "/api/customers",
    update: id => `/api/customers/${id}`,
    delete: id => `/api/customers/${id}`
  },
  supplier: {
    list: "/api/suppliers",
    create: "/api/suppliers",
    update: id => `/api/suppliers/${id}`,
    delete: id => `/api/suppliers/${id}`
  },
  purchaseOrder: {
    list: "/api/purchase-orders",
    create: "/api/purchase-orders",
    update: id => `/api/purchase-orders/${id}`,
    received: id => `/api/purchase-orders/${id}/received`,
    freight: id => `/api/purchase-orders/${id}/freight`,
    delete: id => `/api/purchase-orders/${id}`
  },
  stocktaking: {
    list: "/api/stocktaking-records",
    create: "/api/stocktaking-records",
    update: id => `/api/stocktaking-records/${id}`,
    complete: id => `/api/stocktaking-records/${id}/complete`,
    delete: id => `/api/stocktaking-records/${id}`
  },
  warehouse: {
    list: "/api/warehouses",
    create: "/api/warehouses",
    update: id => `/api/warehouses/${id}`,
    delete: id => `/api/warehouses/${id}`,
    transfer: "/api/warehouses/transfer"
  },
  stockMovement: {
    list: "/api/stock-movements"
  },
  admin: {
    backup: "/api/admin/data-backup",
    restore: "/api/admin/data-restore"
  },
  user: {
    list: "/api/auth/users",
    repairers: "/api/auth/repair-users",
    create: "/api/auth/register",
    updateJobTag: id => `/api/auth/users/${id}/job-tag`,
    updateEnabled: id => `/api/auth/users/${id}/enabled`,
    updateUsername: id => `/api/auth/users/${id}/username`,
    updatePassword: id => `/api/auth/users/${id}/password`,
    delete: id => `/api/auth/users/${id}`
  },
  export: {
    vehicles: "/api/export/vehicles",
    parts: "/api/export/parts",
    customers: "/api/export/customers",
    outboundOrders: "/api/export/outbound-orders",
    rentals: "/api/export/rentals",
    repairs: "/api/export/repairs",
    suppliers: "/api/export/suppliers",
    purchases: "/api/export/purchase-orders",
    stocktakes: "/api/export/stocktaking-records"
  },
  imports: {
    list: "/api/imports",
    detail: id => `/api/imports/${id}`,
    template: type => `/api/imports/templates/${type}`,
    validate: type => `/api/imports/${type}/validate`,
    confirm: id => `/api/imports/${id}/confirm`
  },
  logs: "/api/logs",
  todos: "/api/todos",
  statistics: "/api/statistics/finance",
  listSummary: "/api/statistics/list-summary"
};
