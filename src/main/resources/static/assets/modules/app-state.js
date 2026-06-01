import { createPageState } from "./paging.js";

export const LOG_PAGE_SIZE = 120;
export const LIST_PAGE_SIZE = 20;

export function createInitialState({ token = "", user = null } = {}) {
  return {
    token,
    user,
    activeTab: "overview",
    search: {
      vehicles: "",
      parts: "",
      modificationOrders: "",
      outboundOrders: "",
      rentals: "",
      customers: "",
      suppliers: "",
      purchases: "",
      stocktakes: "",
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
    sorts: {},
    data: {
      vehicles: [],
      parts: [],
      modificationOrders: [],
      outboundOrders: [],
      todoCenter: null,
      rentals: [],
      customers: [],
      suppliers: [],
      purchaseOrders: [],
      stocktakingRecords: [],
      repairs: [],
      statistics: null,
      operationLogs: [],
      summaries: {},
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
      suppliers: createPageState(LIST_PAGE_SIZE),
      purchases: createPageState(LIST_PAGE_SIZE),
      stocktakes: createPageState(LIST_PAGE_SIZE),
      repairs: createPageState(LIST_PAGE_SIZE),
      logs: createPageState(LOG_PAGE_SIZE),
      users: createPageState(LIST_PAGE_SIZE)
    },
    reference: {
      vehiclesLoaded: false,
      partsLoaded: false,
      customersLoaded: false,
      suppliersLoaded: false,
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
    detailDrawer: null,
    batchSelections: {},
    modal: null
  };
}
