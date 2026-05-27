export const endpoints = {
  vehicle: {
    list: "/api/inventory",
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
  logs: "/api/logs",
  statistics: "/api/statistics/finance"
};
