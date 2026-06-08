package com.example.forklift_erp.service;

import com.example.forklift_erp.dto.StatisticsDashboardVO;
import com.example.forklift_erp.dto.ListSummaryVO;
import com.example.forklift_erp.constant.ModificationWorkOrderStatuses;
import com.example.forklift_erp.constant.PartChangeActions;
import com.example.forklift_erp.constant.RentalStatuses;
import com.example.forklift_erp.entity.MachineInventory;
import com.example.forklift_erp.entity.ModificationWorkOrder;
import com.example.forklift_erp.entity.ModificationWorkOrderLine;
import com.example.forklift_erp.entity.OutboundOrder;
import com.example.forklift_erp.entity.PartInventory;
import com.example.forklift_erp.entity.PurchaseOrder;
import com.example.forklift_erp.entity.RepairRecord;
import com.example.forklift_erp.entity.RentalRecord;
import com.example.forklift_erp.entity.StocktakingRecord;
import com.example.forklift_erp.entity.StockOperationLog;
import com.example.forklift_erp.entity.Supplier;
import com.example.forklift_erp.repository.MachineInventoryRepository;
import com.example.forklift_erp.repository.ModificationWorkOrderLineRepository;
import com.example.forklift_erp.repository.ModificationWorkOrderRepository;
import com.example.forklift_erp.repository.OutboundOrderRepository;
import com.example.forklift_erp.repository.PartInventoryRepository;
import com.example.forklift_erp.repository.PurchaseOrderRepository;
import com.example.forklift_erp.repository.RepairRecordRepository;
import com.example.forklift_erp.repository.RentalRecordRepository;
import com.example.forklift_erp.repository.StocktakingRecordRepository;
import com.example.forklift_erp.repository.StockOperationLogRepository;
import com.example.forklift_erp.repository.SupplierRepository;
import com.example.forklift_erp.util.SearchKeywordSupport;
import com.example.forklift_erp.util.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class StatisticsService {
    private static final int LOW_PART_THRESHOLD = 5;
    private static final int LOW_MACHINE_THRESHOLD = 0;

    @Autowired
    private StockOperationLogRepository stockOperationLogRepository;

    @Autowired
    private MachineInventoryRepository machineInventoryRepository;

    @Autowired
    private PartInventoryRepository partInventoryRepository;

    @Autowired
    private RepairRecordRepository repairRecordRepository;

    @Autowired
    private RentalRecordRepository rentalRecordRepository;

    @Autowired
    private ModificationWorkOrderRepository modificationWorkOrderRepository;

    @Autowired
    private ModificationWorkOrderLineRepository modificationWorkOrderLineRepository;

    @Autowired
    private OutboundOrderRepository outboundOrderRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private StocktakingRecordRepository stocktakingRecordRepository;

    public StatisticsDashboardVO financeDashboard(Integer year) {
        int selectedYear = year == null ? LocalDate.now().getYear() : year;
        LocalDateTime startAt = LocalDate.of(selectedYear - 4, 1, 1).atStartOfDay();
        LocalDateTime endAt = LocalDate.of(selectedYear + 1, 1, 1).atStartOfDay();
        List<StockOperationLog> stockLogs = stockOperationLogRepository
                .findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(startAt, endAt);
        List<RepairRecord> repairs = repairRecordRepository.findByRepairDateBetween(startAt, endAt.minusNanos(1));
        List<RentalRecord> rentals = rentalRecordRepository.findInDateRange(
                startAt.toLocalDate(),
                endAt.toLocalDate().minusDays(1),
                startAt,
                endAt
        );
        List<ModificationWorkOrder> modificationOrders = modificationWorkOrderRepository.findCompletedInRange(
                ModificationWorkOrderStatuses.COMPLETED,
                startAt,
                endAt
        );
        List<Long> modificationOrderIds = modificationOrders.stream()
                .map(ModificationWorkOrder::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, List<ModificationWorkOrderLine>> modificationLinesByOrderId = modificationOrderIds.isEmpty()
                ? Map.of()
                : modificationWorkOrderLineRepository.findByWorkOrderIdIn(modificationOrderIds).stream()
                .collect(Collectors.groupingBy(ModificationWorkOrderLine::getWorkOrderId));
        Map<Long, MachineInventory> machines = loadStockMachines(stockLogs);
        Map<Long, PartInventory> parts = loadStockParts(stockLogs);

        StatisticsDashboardVO dashboard = new StatisticsDashboardVO();
        dashboard.setSelectedYear(selectedYear);
        dashboard.setGeneratedAt(LocalDateTime.now());
        dashboard.setMonthlyFinance(buildMonthlyRows(selectedYear, stockLogs, repairs, rentals, modificationOrders, modificationLinesByOrderId, machines, parts));
        dashboard.setYearlyFinance(buildYearlyRows(stockLogs, repairs, rentals, modificationOrders, modificationLinesByOrderId, machines, parts));
        dashboard.setAnnualSummary(sumRows(String.valueOf(selectedYear), dashboard.getMonthlyFinance()));
        dashboard.setResourceFlows(buildResourceFlows(selectedYear, stockLogs, machines, parts));
        dashboard.setTopOutbounds(buildTopOutbounds(selectedYear, stockLogs, machines, parts));
        dashboard.setTopRentals(buildTopRentals(selectedYear, rentals));
        dashboard.setStockValues(buildStockValues());
        dashboard.setLowStocks(buildLowStocks());
        return dashboard;
    }

    public ListSummaryVO listSummary(String type, String keyword) {
        String normalizedType = type == null ? "" : type.trim();
        return switch (normalizedType) {
            case "outboundOrders" -> outboundOrderSummary(keyword);
            case "rentals" -> rentalSummary(keyword);
            case "suppliers" -> supplierSummary(keyword);
            case "purchases" -> purchaseSummary(keyword);
            case "stocktakes" -> stocktakingSummary(keyword);
            default -> {
                ListSummaryVO empty = new ListSummaryVO();
                empty.setType(normalizedType);
                empty.setKeyword(keyword);
                yield empty;
            }
        };
    }

    private ListSummaryVO outboundOrderSummary(String keyword) {
        OutboundOrderRepository.OutboundOrderSummaryProjection data = outboundOrderRepository.summarize(
                SearchKeywordSupport.likePrefix(keyword),
                SearchKeywordSupport.fullTextBoolean(keyword),
                SecurityUtils.isAdminOrSuperAdmin()
        );
        long totalCount = number(data.getTotalCount());
        SummaryCount rows = new SummaryCount(totalCount);
        long unsettledOrders = number(data.getUnsettledOrders());
        long pendingReports = number(data.getPendingReports());
        long pendingInvoices = number(data.getPendingInvoices());
        long settledOrders = number(data.getSettledOrders());
        long uploadedInvoices = number(data.getUploadedInvoices());
        long uploadedContracts = number(data.getUploadedContracts());
        long overdueOrders = number(data.getOverdueOrders());
        long machineOrders = number(data.getMachineOrders());
        BigDecimal outstandingAmount = amount(data.getOutstandingAmount());
        return summary("outboundOrders", keyword)
                .addCard("出库订单", rows.size(), unsettledOrders + " 单待收款")
                .addMoneyCard("应收欠款", outstandingAmount, overdueOrders + " 单逾期")
                .addCard("待报销售", pendingReports, machineOrders + " 单整机订单")
                .addCard("待申请发票", pendingInvoices, uploadedInvoices + " 单已上传发票")
                .addCard("已结清车款", settledOrders, uploadedContracts + " 单已上传合同");
    }

    private ListSummaryVO rentalSummary(String keyword) {
        RentalRecordRepository.RentalSummaryProjection data = rentalRecordRepository.summarize(normalizeKeyword(keyword));
        long totalCount = number(data.getTotalCount());
        SummaryCount rows = new SummaryCount(totalCount);
        long activeRows = number(data.getActiveRows());
        long returnedRows = number(data.getReturnedRows());
        long vehicleCount = number(data.getVehicleCount());
        BigDecimal rentalIncome = amount(data.getRentalIncome());
        return summary("rentals", keyword)
                .addCard("租赁记录", rows.size(), activeRows + " 台租赁中")
                .addMoneyCard("月租收入", rentalIncome, returnedRows + " 单已归还")
                .addCard("租赁车辆", vehicleCount, "按具体车号记录")
                .addCard("待归还", activeRows, activeRows > 0 ? "请持续跟进租赁去向" : "暂无进行中租赁");
    }

    private ListSummaryVO supplierSummary(String keyword) {
        SupplierRepository.SupplierSummaryProjection data = supplierRepository.summarize(normalizeKeyword(keyword));
        long totalCount = number(data.getTotalCount());
        SummaryCount rows = new SummaryCount(totalCount);
        long typed = number(data.getTyped());
        long contacts = number(data.getContacts());
        long purchaseSupplierCount = purchaseOrderRepository.countDistinctSupplierNames();
        return summary("suppliers", keyword)
                .addCard("供应商总数", rows.size(), typed + " 家已标记类型")
                .addCard("采购订单供应商", purchaseSupplierCount, "已被采购订单引用")
                .addCard("联系人", contacts, "可直接联系");
    }

    private ListSummaryVO purchaseSummary(String keyword) {
        PurchaseOrderRepository.PurchaseSummaryProjection data = purchaseOrderRepository.summarize(normalizeKeyword(keyword));
        long totalCount = number(data.getTotalCount());
        SummaryCount rows = new SummaryCount(totalCount);
        BigDecimal totalAmount = amount(data.getTotalAmount());
        BigDecimal freightTotal = amount(data.getFreightTotal());
        long received = number(data.getReceived());
        long pending = number(data.getPending());
        return summary("purchases", keyword)
                .addCard("采购订单", rows.size(), pending + " 单待跟进")
                .addMoneyCard("采购金额", totalAmount, "当前筛选合计")
                .addCard("已收货", received, "运费 " + freightTotal);
    }

    private ListSummaryVO stocktakingSummary(String keyword) {
        StocktakingRecordRepository.StocktakingSummaryProjection data = stocktakingRecordRepository.summarize(normalizeKeyword(keyword));
        long totalCount = number(data.getTotalCount());
        SummaryCount rows = new SummaryCount(totalCount);
        long drafts = number(data.getDrafts());
        long completed = number(data.getCompleted());
        long differences = number(data.getDifferences());
        return summary("stocktakes", keyword)
                .addCard("盘点记录", rows.size(), drafts + " 条待入账")
                .addCard("已入账", completed, "库存已同步")
                .addCard("有差异", differences, "账实不一致");
    }

    private ListSummaryVO summary(String type, String keyword) {
        ListSummaryVO summary = new ListSummaryVO();
        summary.setType(type);
        summary.setKeyword(keyword);
        return summary;
    }

    private Map<Long, MachineInventory> loadStockMachines(List<StockOperationLog> stockLogs) {
        Set<Long> ids = stockLogs.stream()
                .filter(log -> "MACHINE".equals(log.getResourceType()))
                .map(StockOperationLog::getResourceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }
        return machineInventoryRepository.findAllById(ids).stream()
                .filter(machine -> !Boolean.TRUE.equals(machine.getModelOnly()))
                .collect(Collectors.toMap(MachineInventory::getId, machine -> machine, (left, right) -> left));
    }

    private Map<Long, PartInventory> loadStockParts(List<StockOperationLog> stockLogs) {
        Set<Long> ids = stockLogs.stream()
                .filter(log -> "PART".equals(log.getResourceType()))
                .map(StockOperationLog::getResourceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }
        return partInventoryRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(PartInventory::getId, part -> part, (left, right) -> left));
    }

    private void applyStockValue(
            StatisticsDashboardVO.StockValueRow row,
            Long itemCount,
            Long stockQuantity,
            BigDecimal costValue,
            BigDecimal retailValue
    ) {
        row.setItemCount(toInt(itemCount));
        row.setStockQuantity(toInt(stockQuantity));
        row.setCostValue(amount(costValue));
        row.setRetailValue(amount(retailValue));
    }

    private List<StatisticsDashboardVO.FinancialRow> buildMonthlyRows(
            int selectedYear,
            List<StockOperationLog> stockLogs,
            List<RepairRecord> repairs,
            List<RentalRecord> rentals,
            List<ModificationWorkOrder> modificationOrders,
            Map<Long, List<ModificationWorkOrderLine>> modificationLinesByOrderId,
            Map<Long, MachineInventory> machines,
            Map<Long, PartInventory> parts
    ) {
        Map<String, StatisticsDashboardVO.FinancialRow> rows = new LinkedHashMap<>();
        for (int month = 1; month <= 12; month++) {
            String period = "%d-%02d".formatted(selectedYear, month);
            StatisticsDashboardVO.FinancialRow row = new StatisticsDashboardVO.FinancialRow();
            row.setPeriod(period);
            rows.put(period, row);
        }
        for (StockOperationLog log : stockLogs) {
            if (log.getCreatedAt() == null || log.getCreatedAt().getYear() != selectedYear) {
                continue;
            }
            addStockToFinancial(rows.get(YearMonth.from(log.getCreatedAt()).toString()), log, machines, parts);
        }
        for (RepairRecord repair : repairs) {
            if (repair.getRepairDate() == null || repair.getRepairDate().getYear() != selectedYear) {
                continue;
            }
            addRepairToFinancial(rows.get(YearMonth.from(repair.getRepairDate()).toString()), repair);
        }
        for (RentalRecord rental : rentals) {
            LocalDate rentalDate = rentalDate(rental);
            if (rentalDate == null || rentalDate.getYear() != selectedYear) {
                continue;
            }
            addRentalToFinancial(rows.get(YearMonth.from(rentalDate).toString()), rental);
        }
        for (ModificationWorkOrder order : modificationOrders) {
            if (!ModificationWorkOrderStatuses.COMPLETED.equals(order.getStatus()) || order.getCompletedAt() == null || order.getCompletedAt().getYear() != selectedYear) {
                continue;
            }
            addModificationToFinancial(
                    rows.get(YearMonth.from(order.getCompletedAt()).toString()),
                    modificationLinesByOrderId.getOrDefault(order.getId(), List.of())
            );
        }
        rows.values().forEach(this::finishFinancialRow);
        return rows.values().stream().toList();
    }

    private List<StatisticsDashboardVO.FinancialRow> buildYearlyRows(
            List<StockOperationLog> stockLogs,
            List<RepairRecord> repairs,
            List<RentalRecord> rentals,
            List<ModificationWorkOrder> modificationOrders,
            Map<Long, List<ModificationWorkOrderLine>> modificationLinesByOrderId,
            Map<Long, MachineInventory> machines,
            Map<Long, PartInventory> parts
    ) {
        Map<String, StatisticsDashboardVO.FinancialRow> rows = new LinkedHashMap<>();
        for (StockOperationLog log : stockLogs) {
            if (log.getCreatedAt() == null) {
                continue;
            }
            StatisticsDashboardVO.FinancialRow row = rows.computeIfAbsent(
                    String.valueOf(log.getCreatedAt().getYear()),
                    this::newFinancialRow
            );
            addStockToFinancial(row, log, machines, parts);
        }
        for (RepairRecord repair : repairs) {
            if (repair.getRepairDate() == null) {
                continue;
            }
            StatisticsDashboardVO.FinancialRow row = rows.computeIfAbsent(
                    String.valueOf(repair.getRepairDate().getYear()),
                    this::newFinancialRow
            );
            addRepairToFinancial(row, repair);
        }
        for (RentalRecord rental : rentals) {
            LocalDate rentalDate = rentalDate(rental);
            if (rentalDate == null) {
                continue;
            }
            StatisticsDashboardVO.FinancialRow row = rows.computeIfAbsent(
                    String.valueOf(rentalDate.getYear()),
                    this::newFinancialRow
            );
            addRentalToFinancial(row, rental);
        }
        for (ModificationWorkOrder order : modificationOrders) {
            if (!ModificationWorkOrderStatuses.COMPLETED.equals(order.getStatus()) || order.getCompletedAt() == null) {
                continue;
            }
            StatisticsDashboardVO.FinancialRow row = rows.computeIfAbsent(
                    String.valueOf(order.getCompletedAt().getYear()),
                    this::newFinancialRow
            );
            addModificationToFinancial(row, modificationLinesByOrderId.getOrDefault(order.getId(), List.of()));
        }
        rows.values().forEach(this::finishFinancialRow);
        return rows.values().stream()
                .sorted(Comparator.comparing(StatisticsDashboardVO.FinancialRow::getPeriod).reversed())
                .toList();
    }

    private List<StatisticsDashboardVO.ResourceFlowRow> buildResourceFlows(
            int selectedYear,
            List<StockOperationLog> stockLogs,
            Map<Long, MachineInventory> machines,
            Map<Long, PartInventory> parts
    ) {
        Map<String, StatisticsDashboardVO.ResourceFlowRow> rows = new LinkedHashMap<>();
        for (String type : List.of("MACHINE", "PART")) {
            StatisticsDashboardVO.ResourceFlowRow row = new StatisticsDashboardVO.ResourceFlowRow();
            row.setResourceType(type);
            row.setLabel(resourceLabel(type));
            rows.put(type, row);
        }
        for (StockOperationLog log : stockLogs) {
            if (log.getCreatedAt() == null || log.getCreatedAt().getYear() != selectedYear) {
                continue;
            }
            StatisticsDashboardVO.ResourceFlowRow row = rows.computeIfAbsent(log.getResourceType(), type -> {
                StatisticsDashboardVO.ResourceFlowRow created = new StatisticsDashboardVO.ResourceFlowRow();
                created.setResourceType(type);
                created.setLabel(resourceLabel(type));
                return created;
            });
            Price price = priceFor(log, machines, parts);
            int quantity = quantity(log);
            if ("INBOUND".equals(log.getOperationType())) {
                row.setInboundQuantity(row.getInboundQuantity() + quantity);
                row.setInboundCost(row.getInboundCost().add(price.cost().multiply(BigDecimal.valueOf(quantity))));
            } else if ("OUTBOUND".equals(log.getOperationType())) {
                BigDecimal revenue = price.revenue().multiply(BigDecimal.valueOf(quantity));
                BigDecimal cost = price.cost().multiply(BigDecimal.valueOf(quantity));
                row.setOutboundQuantity(row.getOutboundQuantity() + quantity);
                row.setOutboundRevenue(row.getOutboundRevenue().add(revenue));
                row.setGrossProfit(row.getGrossProfit().add(revenue.subtract(cost)));
            }
        }
        return rows.values().stream().toList();
    }

    private List<StatisticsDashboardVO.TopOutboundRow> buildTopOutbounds(
            int selectedYear,
            List<StockOperationLog> stockLogs,
            Map<Long, MachineInventory> machines,
            Map<Long, PartInventory> parts
    ) {
        Map<String, StatisticsDashboardVO.TopOutboundRow> rows = new LinkedHashMap<>();
        for (StockOperationLog log : stockLogs) {
            if (!"OUTBOUND".equals(log.getOperationType())
                    || log.getCreatedAt() == null
                    || log.getCreatedAt().getYear() != selectedYear) {
                continue;
            }
            String key = log.getResourceType() + ":" + Objects.toString(log.getResourceId(), log.getResourceCode());
            StatisticsDashboardVO.TopOutboundRow row = rows.computeIfAbsent(key, ignored -> {
                StatisticsDashboardVO.TopOutboundRow created = new StatisticsDashboardVO.TopOutboundRow();
                created.setResourceType(log.getResourceType());
                created.setResourceCode(log.getResourceCode());
                created.setResourceName(log.getResourceName());
                return created;
            });
            Price price = priceFor(log, machines, parts);
            int quantity = quantity(log);
            BigDecimal revenue = price.revenue().multiply(BigDecimal.valueOf(quantity));
            BigDecimal cost = price.cost().multiply(BigDecimal.valueOf(quantity));
            row.setQuantity(row.getQuantity() + quantity);
            row.setRevenue(row.getRevenue().add(revenue));
            row.setCost(row.getCost().add(cost));
            row.setGrossProfit(row.getGrossProfit().add(revenue.subtract(cost)));
        }
        return rows.values().stream()
                .sorted(Comparator.comparing(StatisticsDashboardVO.TopOutboundRow::getRevenue).reversed())
                .limit(8)
                .toList();
    }

    private List<StatisticsDashboardVO.TopRentalRow> buildTopRentals(
            int selectedYear,
            List<RentalRecord> rentals
    ) {
        return rentals.stream()
                .filter(rental -> {
                    LocalDate rentalDate = rentalDate(rental);
                    return rentalDate != null && rentalDate.getYear() == selectedYear;
                })
                .sorted(Comparator.comparing(this::rentalAmount, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(8)
                .map(rental -> {
                    StatisticsDashboardVO.TopRentalRow row = new StatisticsDashboardVO.TopRentalRow();
                    row.setRentalNo(rental.getRentalNo());
                    row.setVehicleNumber(rental.getVehicleNumber());
                    row.setMachineName(rental.getMachineName());
                    row.setSpecificationModel(rental.getSpecificationModel());
                    row.setDestination(rental.getDestination());
                    row.setStatus(rental.getStatus());
                    row.setRentalPrice(rentalAmount(rental));
                    return row;
                })
                .toList();
    }

    private List<StatisticsDashboardVO.StockValueRow> buildStockValues() {
        StatisticsDashboardVO.StockValueRow machineRow = new StatisticsDashboardVO.StockValueRow();
        machineRow.setResourceType("MACHINE");
        machineRow.setLabel("Vehicle inventory");
        MachineInventoryRepository.StockValueProjection machineValue = machineInventoryRepository.stockValue();
        applyStockValue(machineRow, machineValue.getItemCount(), machineValue.getStockQuantity(),
                machineValue.getCostValue(), machineValue.getRetailValue());

        StatisticsDashboardVO.StockValueRow partRow = new StatisticsDashboardVO.StockValueRow();
        partRow.setResourceType("PART");
        partRow.setLabel("Part inventory");
        PartInventoryRepository.StockValueProjection partValue = partInventoryRepository.stockValue();
        applyStockValue(partRow, partValue.getItemCount(), partValue.getStockQuantity(),
                partValue.getCostValue(), partValue.getRetailValue());
        return List.of(machineRow, partRow);
    }

    private List<StatisticsDashboardVO.LowStockRow> buildLowStocks() {
        List<StatisticsDashboardVO.LowStockRow> rows = new java.util.ArrayList<>();
        for (MachineInventory machine : machineInventoryRepository.findLowStock(LOW_MACHINE_THRESHOLD, PageRequest.of(0, 10))) {
            StatisticsDashboardVO.LowStockRow row = new StatisticsDashboardVO.LowStockRow();
            row.setResourceType("MACHINE");
            row.setResourceCode(machine.getVehicleProductNumber());
            row.setResourceName(machine.getName());
            row.setQuantity(quantity(machine.getInventoryCount()));
            row.setUnit("\u53f0");
            row.setThreshold(LOW_MACHINE_THRESHOLD);
            rows.add(row);
        }
        for (PartInventory part : partInventoryRepository.findLowStock(LOW_PART_THRESHOLD, PageRequest.of(0, 10))) {
            StatisticsDashboardVO.LowStockRow row = new StatisticsDashboardVO.LowStockRow();
            row.setResourceType("PART");
            row.setResourceCode(part.getPartCode());
            row.setResourceName(part.getPartName());
            row.setQuantity(quantity(part.getQuantity()));
            row.setUnit(part.getUnit());
            row.setThreshold(LOW_PART_THRESHOLD);
            rows.add(row);
        }
        return rows.stream()
                .sorted(Comparator.comparing(StatisticsDashboardVO.LowStockRow::getQuantity))
                .limit(10)
                .toList();
    }

    private List<StatisticsDashboardVO.StockValueRow> buildStockValues(
            List<MachineInventory> machines,
            List<PartInventory> parts
    ) {
        StatisticsDashboardVO.StockValueRow machineRow = new StatisticsDashboardVO.StockValueRow();
        machineRow.setResourceType("MACHINE");
        machineRow.setLabel("整车库存");
        for (MachineInventory machine : machines) {
            int quantity = machine.getInventoryCount() == null ? 0 : machine.getInventoryCount();
            machineRow.setItemCount(machineRow.getItemCount() + 1);
            machineRow.setStockQuantity(machineRow.getStockQuantity() + quantity);
            machineRow.setCostValue(machineRow.getCostValue().add(price(machine.getPurchasePrice(), machine.getSettlementPrice()).multiply(BigDecimal.valueOf(quantity))));
            machineRow.setRetailValue(machineRow.getRetailValue().add(price(machine.getSalePrice(), machine.getSettlementPrice()).multiply(BigDecimal.valueOf(quantity))));
        }

        StatisticsDashboardVO.StockValueRow partRow = new StatisticsDashboardVO.StockValueRow();
        partRow.setResourceType("PART");
        partRow.setLabel("配件库存");
        for (PartInventory part : parts) {
            int quantity = part.getQuantity() == null ? 0 : part.getQuantity();
            partRow.setItemCount(partRow.getItemCount() + 1);
            partRow.setStockQuantity(partRow.getStockQuantity() + quantity);
            partRow.setCostValue(partRow.getCostValue().add(price(part.getPurchasePrice(), part.getSettlementPrice()).multiply(BigDecimal.valueOf(quantity))));
            partRow.setRetailValue(partRow.getRetailValue().add(price(part.getSalePrice(), part.getSettlementPrice()).multiply(BigDecimal.valueOf(quantity))));
        }
        return List.of(machineRow, partRow);
    }

    private List<StatisticsDashboardVO.LowStockRow> buildLowStocks(
            List<MachineInventory> machines,
            List<PartInventory> parts
    ) {
        List<StatisticsDashboardVO.LowStockRow> rows = new java.util.ArrayList<>();
        for (MachineInventory machine : machines) {
            int quantity = machine.getInventoryCount() == null ? 0 : machine.getInventoryCount();
            if (quantity <= LOW_MACHINE_THRESHOLD) {
                StatisticsDashboardVO.LowStockRow row = new StatisticsDashboardVO.LowStockRow();
                row.setResourceType("MACHINE");
                row.setResourceCode(machine.getVehicleProductNumber());
                row.setResourceName(machine.getName());
                row.setQuantity(quantity);
                row.setUnit("台");
                row.setThreshold(LOW_MACHINE_THRESHOLD);
                rows.add(row);
            }
        }
        for (PartInventory part : parts) {
            int quantity = part.getQuantity() == null ? 0 : part.getQuantity();
            if (quantity <= LOW_PART_THRESHOLD) {
                StatisticsDashboardVO.LowStockRow row = new StatisticsDashboardVO.LowStockRow();
                row.setResourceType("PART");
                row.setResourceCode(part.getPartCode());
                row.setResourceName(part.getPartName());
                row.setQuantity(quantity);
                row.setUnit(part.getUnit());
                row.setThreshold(LOW_PART_THRESHOLD);
                rows.add(row);
            }
        }
        return rows.stream()
                .sorted(Comparator.comparing(StatisticsDashboardVO.LowStockRow::getQuantity))
                .limit(10)
                .toList();
    }

    private StatisticsDashboardVO.FinancialRow sumRows(String period, List<StatisticsDashboardVO.FinancialRow> rows) {
        StatisticsDashboardVO.FinancialRow sum = newFinancialRow(period);
        for (StatisticsDashboardVO.FinancialRow row : rows) {
            sum.setInboundCost(sum.getInboundCost().add(row.getInboundCost()));
            sum.setOutboundRevenue(sum.getOutboundRevenue().add(row.getOutboundRevenue()));
            sum.setOutboundCost(sum.getOutboundCost().add(row.getOutboundCost()));
            sum.setGrossProfit(sum.getGrossProfit().add(row.getGrossProfit()));
            sum.setRepairIncome(sum.getRepairIncome().add(row.getRepairIncome()));
            sum.setRentalIncome(sum.getRentalIncome().add(row.getRentalIncome()));
            sum.setModificationIncome(sum.getModificationIncome().add(row.getModificationIncome()));
            sum.setModificationExpense(sum.getModificationExpense().add(row.getModificationExpense()));
            sum.setTotalIncome(sum.getTotalIncome().add(row.getTotalIncome()));
            sum.setInboundQuantity(sum.getInboundQuantity() + row.getInboundQuantity());
            sum.setOutboundQuantity(sum.getOutboundQuantity() + row.getOutboundQuantity());
            sum.setRepairOrders(sum.getRepairOrders() + row.getRepairOrders());
            sum.setRentalOrders(sum.getRentalOrders() + row.getRentalOrders());
            sum.setModificationOrders(sum.getModificationOrders() + row.getModificationOrders());
        }
        return sum;
    }

    private StatisticsDashboardVO.FinancialRow newFinancialRow(String period) {
        StatisticsDashboardVO.FinancialRow row = new StatisticsDashboardVO.FinancialRow();
        row.setPeriod(period);
        return row;
    }

    private void addStockToFinancial(
            StatisticsDashboardVO.FinancialRow row,
            StockOperationLog log,
            Map<Long, MachineInventory> machines,
            Map<Long, PartInventory> parts
    ) {
        if (row == null) {
            return;
        }
        Price price = priceFor(log, machines, parts);
        int quantity = quantity(log);
        if ("INBOUND".equals(log.getOperationType())) {
            row.setInboundQuantity(row.getInboundQuantity() + quantity);
            row.setInboundCost(row.getInboundCost().add(price.cost().multiply(BigDecimal.valueOf(quantity))));
        } else if ("OUTBOUND".equals(log.getOperationType())) {
            BigDecimal revenue = price.revenue().multiply(BigDecimal.valueOf(quantity));
            BigDecimal cost = price.cost().multiply(BigDecimal.valueOf(quantity));
            row.setOutboundQuantity(row.getOutboundQuantity() + quantity);
            row.setOutboundRevenue(row.getOutboundRevenue().add(revenue));
            row.setOutboundCost(row.getOutboundCost().add(cost));
            row.setGrossProfit(row.getGrossProfit().add(revenue.subtract(cost)));
        }
    }

    private void addRepairToFinancial(StatisticsDashboardVO.FinancialRow row, RepairRecord repair) {
        if (row == null || repair.getTotalFee() == null) {
            return;
        }
        row.setRepairIncome(row.getRepairIncome().add(repair.getTotalFee()));
        row.setRepairOrders(row.getRepairOrders() + 1);
    }

    private void addRentalToFinancial(StatisticsDashboardVO.FinancialRow row, RentalRecord rental) {
        if (row == null) {
            return;
        }
        row.setRentalIncome(row.getRentalIncome().add(rentalAmount(rental)));
        row.setRentalOrders(row.getRentalOrders() + 1);
    }

    private void addModificationToFinancial(
            StatisticsDashboardVO.FinancialRow row,
            List<ModificationWorkOrderLine> lines
    ) {
        if (row == null) {
            return;
        }
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        List<BigDecimal> signedAmounts = lines.stream()
                .filter(line -> PartChangeActions.DISCOUNT.equals(line.getOldPartAction()))
                .map(ModificationWorkOrderLine::getPriceDifference)
                .filter(Objects::nonNull)
                .toList();
        for (BigDecimal amount : signedAmounts) {
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                expense = expense.add(amount);
            } else if (amount.compareTo(BigDecimal.ZERO) < 0) {
                income = income.add(amount.abs());
            }
        }
        if (income.compareTo(BigDecimal.ZERO) == 0 && expense.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        row.setModificationIncome(row.getModificationIncome().add(income));
        row.setModificationExpense(row.getModificationExpense().add(expense));
        row.setGrossProfit(row.getGrossProfit().add(income).subtract(expense));
        row.setModificationOrders(row.getModificationOrders() + 1);
    }

    private BigDecimal rentalAmount(RentalRecord rental) {
        if (rental.getMonthlyRentalPrice() != null) {
            return rental.getMonthlyRentalPrice();
        }
        return rental.getRentalPrice() == null ? BigDecimal.ZERO : rental.getRentalPrice();
    }

    private void finishFinancialRow(StatisticsDashboardVO.FinancialRow row) {
        row.setTotalIncome(row.getOutboundRevenue()
                .add(row.getRepairIncome())
                .add(row.getRentalIncome())
                .add(row.getModificationIncome()));
    }

    private LocalDate rentalDate(RentalRecord rental) {
        if (rental.getStartDate() != null) {
            return rental.getStartDate();
        }
        return rental.getCreatedAt() == null ? null : rental.getCreatedAt().toLocalDate();
    }

    private Price priceFor(
            StockOperationLog log,
            Map<Long, MachineInventory> machines,
            Map<Long, PartInventory> parts
    ) {
        if ("MACHINE".equals(log.getResourceType())) {
            MachineInventory machine = machines.get(log.getResourceId());
            if (machine != null) {
                return new Price(
                        price(machine.getPurchasePrice(), machine.getSettlementPrice()),
                        price(machine.getSalePrice(), machine.getSettlementPrice())
                );
            }
        }
        if ("PART".equals(log.getResourceType())) {
            PartInventory part = parts.get(log.getResourceId());
            if (part != null) {
                return new Price(
                        price(part.getPurchasePrice(), part.getSettlementPrice()),
                        price(part.getSalePrice(), part.getSettlementPrice())
                );
            }
        }
        return new Price(BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private BigDecimal price(BigDecimal preferred, BigDecimal fallback) {
        if (preferred != null) {
            return preferred;
        }
        return fallback == null ? BigDecimal.ZERO : fallback;
    }

    private int quantity(StockOperationLog log) {
        return log.getQuantity() == null ? 0 : log.getQuantity();
    }

    private int quantity(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal receivableOutstanding(OutboundOrder order) {
        BigDecimal receivable = firstAmount(order.getReceivableAmount(), order.getSettlementPrice());
        BigDecimal received = amount(order.getReceivedAmount());
        return receivable.subtract(received).max(BigDecimal.ZERO);
    }

    private BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private long number(Long value) {
        return value == null ? 0L : value;
    }

    private int toInt(Long value) {
        if (value == null) {
            return 0;
        }
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : value.intValue();
    }

    private BigDecimal firstAmount(BigDecimal... values) {
        for (BigDecimal value : values) {
            if (value != null) {
                return amount(value);
            }
        }
        return BigDecimal.ZERO;
    }

    private int overdueDays(BigDecimal outstanding, LocalDate dueDate) {
        if (dueDate == null || outstanding == null || outstanding.signum() <= 0) {
            return 0;
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(dueDate, LocalDate.now());
        return days > 0 ? Math.toIntExact(days) : 0;
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String resourceLabel(String resourceType) {
        return "MACHINE".equals(resourceType) ? "整车" : "PART".equals(resourceType) ? "配件" : resourceType;
    }

    private record Price(BigDecimal cost, BigDecimal revenue) {
    }

    private record SummaryCount(long totalCount) {
        int size() {
            return toSafeInt(totalCount);
        }

        private static int toSafeInt(long value) {
            return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.toIntExact(value);
        }
    }
}
