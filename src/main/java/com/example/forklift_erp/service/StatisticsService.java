package com.example.forklift_erp.service;

import com.example.forklift_erp.dto.StatisticsDashboardVO;
import com.example.forklift_erp.entity.MachineInventory;
import com.example.forklift_erp.entity.OutboundOrder;
import com.example.forklift_erp.entity.PartInventory;
import com.example.forklift_erp.entity.RepairPartUsage;
import com.example.forklift_erp.entity.RepairRecord;
import com.example.forklift_erp.entity.RentalRecord;
import com.example.forklift_erp.entity.StockOperationLog;
import com.example.forklift_erp.repository.MachineInventoryRepository;
import com.example.forklift_erp.repository.OutboundOrderRepository;
import com.example.forklift_erp.repository.PartInventoryRepository;
import com.example.forklift_erp.repository.RepairPartUsageRepository;
import com.example.forklift_erp.repository.RepairRecordRepository;
import com.example.forklift_erp.repository.RentalRecordRepository;
import com.example.forklift_erp.repository.StockOperationLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StatisticsService {
    private static final int LOW_PART_THRESHOLD = 5;
    private static final int LOW_MACHINE_THRESHOLD = 0;
    private static final int MONEY_SCALE = 2;

    @Autowired
    private StockOperationLogRepository stockOperationLogRepository;

    @Autowired
    private MachineInventoryRepository machineInventoryRepository;

    @Autowired
    private PartInventoryRepository partInventoryRepository;

    @Autowired
    private RepairRecordRepository repairRecordRepository;

    @Autowired
    private RepairPartUsageRepository repairPartUsageRepository;

    @Autowired
    private RentalRecordRepository rentalRecordRepository;

    @Autowired
    private OutboundOrderRepository outboundOrderRepository;

    public StatisticsDashboardVO financeDashboard(Integer year) {
        int selectedYear = year == null ? LocalDate.now().getYear() : year;
        Map<Long, MachineInventory> machines = machineInventoryRepository.findAll().stream()
                .filter(machine -> !Boolean.TRUE.equals(machine.getModelOnly()))
                .collect(Collectors.toMap(MachineInventory::getId, Function.identity(), (a, b) -> a));
        Map<Long, PartInventory> parts = partInventoryRepository.findAll().stream()
                .collect(Collectors.toMap(PartInventory::getId, Function.identity(), (a, b) -> a));
        List<StockOperationLog> stockLogs = stockOperationLogRepository.findAllByOrderByCreatedAtDesc();
        List<RepairRecord> repairs = repairRecordRepository.findAll();
        Map<Long, BigDecimal> repairPartsCostByRepairId = repairPartCostByRepairId(repairs);
        List<RentalRecord> rentals = rentalRecordRepository.findAllByOrderByCreatedAtDesc();
        Map<Long, OutboundOrder> outboundOrdersByStockLogId = outboundOrderRepository.findAll().stream()
                .filter(order -> order.getStockOperationLogId() != null)
                .collect(Collectors.toMap(OutboundOrder::getStockOperationLogId, Function.identity(), (a, b) -> a));

        StatisticsDashboardVO dashboard = new StatisticsDashboardVO();
        dashboard.setSelectedYear(selectedYear);
        dashboard.setGeneratedAt(LocalDateTime.now());
        dashboard.setMonthlyFinance(buildMonthlyRows(selectedYear, stockLogs, repairs, rentals, machines, parts, outboundOrdersByStockLogId, repairPartsCostByRepairId));
        dashboard.setYearlyFinance(buildYearlyRows(stockLogs, repairs, rentals, machines, parts, outboundOrdersByStockLogId, repairPartsCostByRepairId));
        StatisticsDashboardVO.FinancialRow annualSummary = sumRows(String.valueOf(selectedYear), dashboard.getMonthlyFinance());
        annualSummary.setRentalOrders(countRentalsForPeriod(
                rentals,
                LocalDate.of(selectedYear, 1, 1),
                LocalDate.of(selectedYear, 12, 31)
        ));
        dashboard.setAnnualSummary(annualSummary);
        dashboard.setResourceFlows(buildResourceFlows(selectedYear, stockLogs, machines, parts, outboundOrdersByStockLogId));
        dashboard.setTopOutbounds(buildTopOutbounds(selectedYear, stockLogs, machines, parts, outboundOrdersByStockLogId));
        dashboard.setTopRentals(buildTopRentals(selectedYear, rentals));
        dashboard.setStockValues(buildStockValues(machines.values().stream().toList(), parts.values().stream().toList()));
        dashboard.setLowStocks(buildLowStocks(machines.values().stream().toList(), parts.values().stream().toList()));
        return dashboard;
    }

    private List<StatisticsDashboardVO.FinancialRow> buildMonthlyRows(
            int selectedYear,
            List<StockOperationLog> stockLogs,
            List<RepairRecord> repairs,
            List<RentalRecord> rentals,
            Map<Long, MachineInventory> machines,
            Map<Long, PartInventory> parts,
            Map<Long, OutboundOrder> outboundOrdersByStockLogId,
            Map<Long, BigDecimal> repairPartsCostByRepairId
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
            addStockToFinancial(rows.get(YearMonth.from(log.getCreatedAt()).toString()), log, machines, parts, outboundOrdersByStockLogId);
        }
        for (RepairRecord repair : repairs) {
            if (repair.getRepairDate() == null || repair.getRepairDate().getYear() != selectedYear) {
                continue;
            }
            addRepairToFinancial(rows.get(YearMonth.from(repair.getRepairDate()).toString()), repair, repairPartsCostByRepairId);
        }
        for (RentalRecord rental : rentals) {
            addRentalToMonthlyRows(rows, rental, selectedYear);
        }
        rows.values().forEach(this::finishFinancialRow);
        return rows.values().stream().toList();
    }

    private List<StatisticsDashboardVO.FinancialRow> buildYearlyRows(
            List<StockOperationLog> stockLogs,
            List<RepairRecord> repairs,
            List<RentalRecord> rentals,
            Map<Long, MachineInventory> machines,
            Map<Long, PartInventory> parts,
            Map<Long, OutboundOrder> outboundOrdersByStockLogId,
            Map<Long, BigDecimal> repairPartsCostByRepairId
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
            addStockToFinancial(row, log, machines, parts, outboundOrdersByStockLogId);
        }
        for (RepairRecord repair : repairs) {
            if (repair.getRepairDate() == null) {
                continue;
            }
            StatisticsDashboardVO.FinancialRow row = rows.computeIfAbsent(
                    String.valueOf(repair.getRepairDate().getYear()),
                    this::newFinancialRow
            );
            addRepairToFinancial(row, repair, repairPartsCostByRepairId);
        }
        for (RentalRecord rental : rentals) {
            addRentalToYearlyRows(rows, rental);
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
            Map<Long, PartInventory> parts,
            Map<Long, OutboundOrder> outboundOrdersByStockLogId
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
            Price price = priceFor(log, machines, parts, outboundOrdersByStockLogId);
            int quantity = movementQuantity(log);
            if (isInboundMovement(log)) {
                row.setInboundQuantity(row.getInboundQuantity() + quantity);
                row.setInboundCost(row.getInboundCost().add(price.cost().multiply(BigDecimal.valueOf(quantity))));
            } else if (isOutboundMovement(log)) {
                row.setOutboundQuantity(row.getOutboundQuantity() + quantity);
                if (isSalesOutbound(log, outboundOrdersByStockLogId)) {
                    BigDecimal revenue = price.revenue().multiply(BigDecimal.valueOf(quantity));
                    BigDecimal cost = price.cost().multiply(BigDecimal.valueOf(quantity));
                    row.setOutboundRevenue(row.getOutboundRevenue().add(revenue));
                    row.setGrossProfit(row.getGrossProfit().add(revenue.subtract(cost)));
                }
            }
        }
        return rows.values().stream().toList();
    }

    private List<StatisticsDashboardVO.TopOutboundRow> buildTopOutbounds(
            int selectedYear,
            List<StockOperationLog> stockLogs,
            Map<Long, MachineInventory> machines,
            Map<Long, PartInventory> parts,
            Map<Long, OutboundOrder> outboundOrdersByStockLogId
    ) {
        Map<String, StatisticsDashboardVO.TopOutboundRow> rows = new LinkedHashMap<>();
        for (StockOperationLog log : stockLogs) {
            if (!isSalesOutbound(log, outboundOrdersByStockLogId)
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
            Price price = priceFor(log, machines, parts, outboundOrdersByStockLogId);
            int quantity = movementQuantity(log);
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
        LocalDate yearStart = LocalDate.of(selectedYear, 1, 1);
        LocalDate yearEnd = LocalDate.of(selectedYear, 12, 31);
        return rentals.stream()
                .map(rental -> Map.entry(rental, rentalAmountForPeriod(rental, yearStart, yearEnd)))
                .filter(entry -> entry.getValue().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(Map.Entry<RentalRecord, BigDecimal>::getValue).reversed())
                .limit(8)
                .map(entry -> {
                    RentalRecord rental = entry.getKey();
                    StatisticsDashboardVO.TopRentalRow row = new StatisticsDashboardVO.TopRentalRow();
                    row.setRentalNo(rental.getRentalNo());
                    row.setVehicleNumber(rental.getVehicleNumber());
                    row.setMachineName(rental.getMachineName());
                    row.setSpecificationModel(rental.getSpecificationModel());
                    row.setDestination(rental.getDestination());
                    row.setStatus(rental.getStatus());
                    row.setRentalPrice(entry.getValue());
                    return row;
                })
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
            machineRow.setCostValue(machineRow.getCostValue().add(price(machine.getSettlementPrice(), machine.getPurchasePrice()).multiply(BigDecimal.valueOf(quantity))));
            machineRow.setSettlementValue(machineRow.getSettlementValue().add(price(machine.getSettlementPrice(), machine.getSalePrice()).multiply(BigDecimal.valueOf(quantity))));
        }

        StatisticsDashboardVO.StockValueRow partRow = new StatisticsDashboardVO.StockValueRow();
        partRow.setResourceType("PART");
        partRow.setLabel("配件库存");
        for (PartInventory part : parts) {
            int quantity = part.getQuantity() == null ? 0 : part.getQuantity();
            partRow.setItemCount(partRow.getItemCount() + 1);
            partRow.setStockQuantity(partRow.getStockQuantity() + quantity);
            partRow.setCostValue(partRow.getCostValue().add(price(part.getSettlementPrice(), part.getPurchasePrice()).multiply(BigDecimal.valueOf(quantity))));
            partRow.setSettlementValue(partRow.getSettlementValue().add(price(part.getSettlementPrice(), part.getSalePrice()).multiply(BigDecimal.valueOf(quantity))));
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
            sum.setRepairReceivable(sum.getRepairReceivable().add(row.getRepairReceivable()));
            sum.setRepairExpense(sum.getRepairExpense().add(row.getRepairExpense()));
            sum.setRepairPartsCost(sum.getRepairPartsCost().add(row.getRepairPartsCost()));
            sum.setRentalIncome(sum.getRentalIncome().add(row.getRentalIncome()));
            sum.setTotalIncome(sum.getTotalIncome().add(row.getTotalIncome()));
            sum.setTotalExpense(sum.getTotalExpense().add(row.getTotalExpense()));
            sum.setCashExpense(sum.getCashExpense().add(row.getCashExpense()));
            sum.setNetProfit(sum.getNetProfit().add(row.getNetProfit()));
            sum.setNetCashflow(sum.getNetCashflow().add(row.getNetCashflow()));
            sum.setInboundQuantity(sum.getInboundQuantity() + row.getInboundQuantity());
            sum.setOutboundQuantity(sum.getOutboundQuantity() + row.getOutboundQuantity());
            sum.setRepairOrders(sum.getRepairOrders() + row.getRepairOrders());
            sum.setRentalOrders(sum.getRentalOrders() + row.getRentalOrders());
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
            Map<Long, PartInventory> parts,
            Map<Long, OutboundOrder> outboundOrdersByStockLogId
    ) {
        if (row == null) {
            return;
        }
        Price price = priceFor(log, machines, parts, outboundOrdersByStockLogId);
        int quantity = movementQuantity(log);
        boolean repairStockMovement = isRepairStockMovement(log);
        if (isInboundMovement(log)) {
            row.setInboundQuantity(row.getInboundQuantity() + quantity);
            if (!repairStockMovement) {
                row.setInboundCost(row.getInboundCost().add(price.cost().multiply(BigDecimal.valueOf(quantity))));
            }
        } else if (isOutboundMovement(log)) {
            BigDecimal cost = price.cost().multiply(BigDecimal.valueOf(quantity));
            row.setOutboundQuantity(row.getOutboundQuantity() + quantity);
            if (!repairStockMovement) {
                row.setOutboundCost(row.getOutboundCost().add(cost));
            }
            if (isSalesOutbound(log, outboundOrdersByStockLogId)) {
                BigDecimal revenue = price.revenue().multiply(BigDecimal.valueOf(quantity));
                row.setOutboundRevenue(row.getOutboundRevenue().add(revenue));
                row.setGrossProfit(row.getGrossProfit().add(revenue.subtract(cost)));
            }
        }
    }

    private void addRepairToFinancial(
            StatisticsDashboardVO.FinancialRow row,
            RepairRecord repair,
            Map<Long, BigDecimal> repairPartsCostByRepairId
    ) {
        if (row == null || !"COMPLETED".equals(repair.getStatus())) {
            return;
        }
        BigDecimal repairFee = money(repair.getRepairFee());
        BigDecimal partsFee = money(repair.getPartsFee());
        BigDecimal repairExpense = money(repair.getRepairExpense());
        BigDecimal repairIncome = repairFee.add(partsFee);
        if (repairIncome.compareTo(BigDecimal.ZERO) == 0 && repair.getTotalFee() != null) {
            repairIncome = money(repair.getTotalFee()).subtract(repairExpense);
            if (repairIncome.compareTo(BigDecimal.ZERO) < 0) {
                repairIncome = BigDecimal.ZERO;
            }
        }
        BigDecimal repairReceivable = repair.getTotalFee() == null
                ? repairIncome.add(repairExpense)
                : money(repair.getTotalFee());
        BigDecimal repairPartsCost = repairPartsCostByRepairId.getOrDefault(repair.getId(), BigDecimal.ZERO);
        row.setRepairIncome(row.getRepairIncome().add(repairIncome));
        row.setRepairReceivable(row.getRepairReceivable().add(repairReceivable));
        row.setRepairExpense(row.getRepairExpense().add(repairExpense));
        row.setRepairPartsCost(row.getRepairPartsCost().add(repairPartsCost));
        row.setRepairOrders(row.getRepairOrders() + 1);
    }

    private void addRentalToFinancial(StatisticsDashboardVO.FinancialRow row, BigDecimal amount) {
        if (row == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        row.setRentalIncome(row.getRentalIncome().add(amount));
        row.setRentalOrders(row.getRentalOrders() + 1);
    }

    private BigDecimal rentalMonthlyPrice(RentalRecord rental) {
        if (rental.getMonthlyRentalPrice() != null) {
            return rental.getMonthlyRentalPrice();
        }
        return rental.getRentalPrice() == null ? BigDecimal.ZERO : rental.getRentalPrice();
    }

    private void addRentalToMonthlyRows(
            Map<String, StatisticsDashboardVO.FinancialRow> rows,
            RentalRecord rental,
            int selectedYear
    ) {
        LocalDate selectedStart = LocalDate.of(selectedYear, 1, 1);
        LocalDate selectedEnd = LocalDate.of(selectedYear, 12, 31);
        LocalDate start = maxDate(rentalStartDate(rental), selectedStart);
        LocalDate end = minDate(rentalEndDate(rental), selectedEnd);
        if (start == null || end == null || start.isAfter(end)) {
            return;
        }
        YearMonth month = YearMonth.from(start);
        YearMonth lastMonth = YearMonth.from(end);
        while (!month.isAfter(lastMonth)) {
            LocalDate monthStart = month.atDay(1);
            LocalDate monthEnd = month.atEndOfMonth();
            BigDecimal amount = rentalAmountForPeriod(rental, monthStart, monthEnd);
            addRentalToFinancial(rows.get(month.toString()), amount);
            month = month.plusMonths(1);
        }
    }

    private void addRentalToYearlyRows(Map<String, StatisticsDashboardVO.FinancialRow> rows, RentalRecord rental) {
        LocalDate start = rentalStartDate(rental);
        LocalDate end = rentalEndDate(rental);
        if (start == null || end == null || start.isAfter(end)) {
            return;
        }
        int year = start.getYear();
        int lastYear = end.getYear();
        while (year <= lastYear) {
            LocalDate yearStart = LocalDate.of(year, 1, 1);
            LocalDate yearEnd = LocalDate.of(year, 12, 31);
            BigDecimal amount = rentalAmountForPeriod(rental, yearStart, yearEnd);
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                StatisticsDashboardVO.FinancialRow row = rows.computeIfAbsent(String.valueOf(year), this::newFinancialRow);
                addRentalToFinancial(row, amount);
            }
            year++;
        }
    }

    private BigDecimal rentalAmountForPeriod(RentalRecord rental, LocalDate periodStart, LocalDate periodEnd) {
        LocalDate start = maxDate(rentalStartDate(rental), periodStart);
        LocalDate end = minDate(rentalEndDate(rental), periodEnd);
        if (start == null || end == null || start.isAfter(end)) {
            return BigDecimal.ZERO;
        }
        BigDecimal amount = BigDecimal.ZERO;
        YearMonth month = YearMonth.from(start);
        YearMonth lastMonth = YearMonth.from(end);
        BigDecimal monthlyPrice = rentalMonthlyPrice(rental);
        while (!month.isAfter(lastMonth)) {
            LocalDate monthStart = month.atDay(1);
            LocalDate monthEnd = month.atEndOfMonth();
            LocalDate overlapStart = maxDate(start, monthStart);
            LocalDate overlapEnd = minDate(end, monthEnd);
            if (overlapStart != null && overlapEnd != null && !overlapStart.isAfter(overlapEnd)) {
                long days = ChronoUnit.DAYS.between(overlapStart, overlapEnd.plusDays(1));
                BigDecimal monthAmount = monthlyPrice
                        .multiply(BigDecimal.valueOf(days))
                        .divide(BigDecimal.valueOf(month.lengthOfMonth()), MONEY_SCALE, RoundingMode.HALF_UP);
                amount = amount.add(monthAmount);
            }
            month = month.plusMonths(1);
        }
        return amount;
    }

    private int countRentalsForPeriod(List<RentalRecord> rentals, LocalDate periodStart, LocalDate periodEnd) {
        return (int) rentals.stream()
                .filter(rental -> rentalAmountForPeriod(rental, periodStart, periodEnd).compareTo(BigDecimal.ZERO) > 0)
                .count();
    }

    private LocalDate rentalStartDate(RentalRecord rental) {
        if (rental.getStartDate() != null) {
            return rental.getStartDate();
        }
        return rental.getCreatedAt() == null ? null : rental.getCreatedAt().toLocalDate();
    }

    private LocalDate rentalEndDate(RentalRecord rental) {
        LocalDate start = rentalStartDate(rental);
        if (start == null) {
            return null;
        }
        if (RentalRecord.STATUS_RETURNED.equals(rental.getStatus())) {
            return rental.getEndDate() == null ? start : rental.getEndDate();
        }
        if (RentalRecord.STATUS_ACTIVE.equals(rental.getStatus()) || rental.getStatus() == null || rental.getStatus().isBlank()) {
            LocalDate today = LocalDate.now();
            return rental.getEndDate() == null ? today : minDate(rental.getEndDate(), today);
        }
        return null;
    }

    private void finishFinancialRow(StatisticsDashboardVO.FinancialRow row) {
        row.setTotalIncome(row.getOutboundRevenue().add(row.getRepairIncome()).add(row.getRentalIncome()));
        row.setTotalExpense(row.getOutboundCost().add(row.getRepairExpense()).add(row.getRepairPartsCost()));
        row.setCashExpense(row.getInboundCost().add(row.getRepairExpense()));
        row.setNetProfit(row.getTotalIncome().subtract(row.getTotalExpense()));
        row.setNetCashflow(row.getTotalIncome().subtract(row.getCashExpense()));
    }

    private Price priceFor(
            StockOperationLog log,
            Map<Long, MachineInventory> machines,
            Map<Long, PartInventory> parts,
            Map<Long, OutboundOrder> outboundOrdersByStockLogId
    ) {
        BigDecimal unitCost = log.getUnitCost() != null
                ? log.getUnitCost()
                : currentCostFor(log, machines, parts);
        BigDecimal unitRevenue = log.getUnitRevenue();
        if (unitRevenue == null && isSalesOutbound(log, outboundOrdersByStockLogId)) {
            OutboundOrder order = outboundOrdersByStockLogId.get(log.getId());
            unitRevenue = price(order.getSalePrice(), order.getSettlementPrice());
        }
        return new Price(money(unitCost), money(unitRevenue));
    }

    private BigDecimal currentCostFor(
            StockOperationLog log,
            Map<Long, MachineInventory> machines,
            Map<Long, PartInventory> parts
    ) {
        if ("MACHINE".equals(log.getResourceType())) {
            MachineInventory machine = machines.get(log.getResourceId());
            if (machine != null) {
                return price(machine.getSettlementPrice(), machine.getPurchasePrice());
            }
        }
        if ("PART".equals(log.getResourceType())) {
            PartInventory part = parts.get(log.getResourceId());
            if (part != null) {
                return price(part.getSettlementPrice(), part.getPurchasePrice());
            }
        }
        return BigDecimal.ZERO;
    }

    private boolean isInboundMovement(StockOperationLog log) {
        String operationType = log.getOperationType();
        return "INBOUND".equals(operationType)
                || "INITIAL".equals(operationType)
                || ("ADJUST".equals(operationType) && quantityDelta(log) > 0);
    }

    private boolean isOutboundMovement(StockOperationLog log) {
        String operationType = log.getOperationType();
        return "OUTBOUND".equals(operationType)
                || ("ADJUST".equals(operationType) && quantityDelta(log) < 0);
    }

    private boolean isSalesOutbound(StockOperationLog log, Map<Long, OutboundOrder> outboundOrdersByStockLogId) {
        return "OUTBOUND".equals(log.getOperationType())
                && log.getId() != null
                && outboundOrdersByStockLogId.containsKey(log.getId());
    }

    private boolean isRepairStockMovement(StockOperationLog log) {
        return "REPAIR_RECORD".equals(log.getSourceType());
    }

    private int movementQuantity(StockOperationLog log) {
        if ("ADJUST".equals(log.getOperationType())) {
            int delta = quantityDelta(log);
            return delta == 0 ? quantity(log) : Math.abs(delta);
        }
        return quantity(log);
    }

    private int quantityDelta(StockOperationLog log) {
        if (log.getBeforeQuantity() != null && log.getAfterQuantity() != null) {
            return log.getAfterQuantity() - log.getBeforeQuantity();
        }
        int quantity = quantity(log);
        if ("INBOUND".equals(log.getOperationType()) || "INITIAL".equals(log.getOperationType())) {
            return quantity;
        }
        if ("OUTBOUND".equals(log.getOperationType())) {
            return -quantity;
        }
        return 0;
    }

    private BigDecimal price(BigDecimal preferred, BigDecimal fallback) {
        if (preferred != null) {
            return preferred;
        }
        return fallback == null ? BigDecimal.ZERO : fallback;
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private Map<Long, BigDecimal> repairPartCostByRepairId(List<RepairRecord> repairs) {
        List<Long> repairIds = repairs.stream()
                .map(RepairRecord::getId)
                .filter(Objects::nonNull)
                .toList();
        if (repairIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, BigDecimal> costs = new LinkedHashMap<>();
        for (RepairPartUsage usage : repairPartUsageRepository.findByRepairIdIn(repairIds)) {
            BigDecimal unitCost = money(usage.getUnitPrice());
            BigDecimal quantity = BigDecimal.valueOf(usage.getQuantity() == null ? 0 : usage.getQuantity());
            costs.merge(usage.getRepairId(), unitCost.multiply(quantity), BigDecimal::add);
        }
        return costs;
    }

    private LocalDate maxDate(LocalDate left, LocalDate right) {
        if (left == null || right == null) {
            return null;
        }
        return left.isAfter(right) ? left : right;
    }

    private LocalDate minDate(LocalDate left, LocalDate right) {
        if (left == null || right == null) {
            return null;
        }
        return left.isBefore(right) ? left : right;
    }

    private int quantity(StockOperationLog log) {
        return log.getQuantity() == null ? 0 : log.getQuantity();
    }

    private String resourceLabel(String resourceType) {
        return "MACHINE".equals(resourceType) ? "整车" : "PART".equals(resourceType) ? "配件" : resourceType;
    }

    private record Price(BigDecimal cost, BigDecimal revenue) {
    }
}
