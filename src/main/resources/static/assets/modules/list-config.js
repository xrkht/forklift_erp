import { endpoints } from "./routes.js";

export const pagedTabs = {
  vehicles: {
    endpoint: endpoints.vehicle.models,
    dataKey: "vehicleModels",
    searchKey: "vehicles",
    permission: null,
    sortDesc: false,
    buildParams: state => ({ stock: state.filters.vehicles?.stock || "" })
  },
  parts: {
    endpoint: endpoints.part.list,
    dataKey: "parts",
    searchKey: "parts",
    permission: null,
    sortDesc: true,
    buildParams: state => ({ stock: state.filters.parts?.stock || "" })
  },
  modificationOrders: { endpoint: endpoints.modificationOrder.list, dataKey: "modificationOrders", searchKey: "modificationOrders", permission: "replace:write", sortDesc: true },
  outboundOrders: {
    endpoint: endpoints.outboundOrder.list,
    dataKey: "outboundOrders",
    searchKey: "outboundOrders",
    permission: "stock:adjust",
    sortDesc: true,
    buildParams: state => ({ stage: state.filters.outboundOrders?.stage || "" })
  },
  attachments: {
    endpoint: endpoints.attachments.list,
    dataKey: "attachments",
    searchKey: "attachments",
    permission: null,
    sortDesc: true,
    buildParams: state => {
      const filters = state.filters.attachments || {};
      return {
        resourceType: filters.resourceType || "",
        resourceId: filters.resourceId || "",
        category: filters.category || "",
        includeDeleted: filters.includeDeleted || ""
      };
    }
  },
  imports: {
    endpoint: endpoints.imports.list,
    dataKey: "importJobs",
    searchKey: "imports",
    permission: "stock:adjust",
    sortDesc: true,
    buildParams: state => {
      const filters = state.filters.imports || {};
      return {
        importType: filters.importType || ""
      };
    }
  },
  rentals: {
    endpoint: endpoints.rental.list,
    dataKey: "rentals",
    searchKey: "rentals",
    permission: "stock:adjust",
    sortDesc: true,
    buildParams: state => ({ status: state.filters.rentals?.status || "" })
  },
  customers: { endpoint: endpoints.customer.list, dataKey: "customers", searchKey: "customers", permission: null, sortDesc: false },
  suppliers: { endpoint: endpoints.supplier.list, dataKey: "suppliers", searchKey: "suppliers", permission: "stock:adjust", sortDesc: false },
  purchases: {
    endpoint: endpoints.purchaseOrder.list,
    dataKey: "purchaseOrders",
    searchKey: "purchases",
    permission: "stock:adjust",
    sortDesc: true,
    buildParams: state => {
      const filters = state.filters.purchases || {};
      return {
        resourceType: filters.resourceType || ""
      };
    }
  },
  stocktakes: { endpoint: endpoints.stocktaking.list, dataKey: "stocktakingRecords", searchKey: "stocktakes", permission: "stock:adjust", sortDesc: true },
  warehouses: { endpoint: endpoints.warehouse.list, dataKey: "warehouses", searchKey: "warehouses", permission: "stock:adjust", sortDesc: false },
  stockMovements: { endpoint: endpoints.stockMovement.list, dataKey: "stockMovements", searchKey: "stockMovements", permission: "log:read", sortDesc: true },
  repairs: { endpoint: endpoints.repair.list, dataKey: "repairs", searchKey: "repairs", permission: null, sortDesc: true },
  logs: { endpoint: endpoints.logs, dataKey: "operationLogs", searchKey: "logs", permission: "log:read", sortDesc: false },
  users: { endpoint: endpoints.user.list, dataKey: "users", searchKey: "users", permission: "user:read", sortDesc: true }
};

export const activeTabPageKeys = {
  modifications: "modificationOrders"
};

export const summaryTabs = new Set(["outboundOrders", "rentals", "suppliers", "purchases", "stocktakes", "warehouses"]);
