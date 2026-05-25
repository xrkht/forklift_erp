package com.example.forklift_erp.service;

import com.example.forklift_erp.dto.StatisticsDashboardVO;
import com.example.forklift_erp.entity.MachineInventory;
import com.example.forklift_erp.entity.PartInventory;
import com.example.forklift_erp.entity.RepairRecord;
import com.example.forklift_erp.entity.StockOperationLog;
import com.example.forklift_erp.repository.MachineInventoryRepository;
import com.example.forklift_erp.repository.PartInventoryRepository;
import com.example.forklift_erp.repository.RepairRecordRepository;
import com.example.forklift_erp.repository.StockOperationLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
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

    @Autowired
    private StockOperationLogRepository stockOperationLogRepository;

    @Autowired
    private MachineInventoryRepository machineInventoryRepository;

    @Autowired
    private PartInventoryRepository partInventoryRepository;

    @Autowired
    private RepairRecordRepository repairRecordRepository;

    public StatisticsDashboardVO financeDashboard(Integer year) {
        int selectedYear = year == null ? LocalDate.now().getYear() : year;
        Map<Long, MachineInventory> machines = machineInventoryRepository.findAll().stream()
                .filter(machine -> !Boolean.TRUE.equals(machine.getModelOnly()))
                .collect(Collectors.toMap(MachineInventory::getId, Function.identity(), (a, b) -> a));
        Map<Long, PartInventory> parts = partInventoryRepository.findAll().stream()
                .collect(Collectors.toMap(PartInventory::getId, Function.identity(), (a, b) -> a));
        List<StockOperationLog> stockLogs = stockOperationLogRepository.findAllByOrderByCreatedAtDesc();
        List<RepairRecord> repairs = repairRecordRepository.findAll();

        StatisticsDashboardVO dashboard = new StatisticsDashboardVO();
        dashboard.setSelectedYear(selectedYear);
        dashboard.setGeneratedAt(LocalDateTime.now());
        dashboard.setMonthlyFinance(buildMonthlyRows(selectedYear, stockLogs, repairs, machines, parts));
        dashboard.setYearlyFinance(buildYearlyRows(stockLogs, repairs, machines, parts));
        dashboard.setAnnualSummary(sumRows(String.valueOf(selectedYear), dashboard.getMonthlyFinance()));
        dashboard.setResourceFlows(buildResourceFlows(selectedYear, stockLogs, machines, parts));
        dashboard.setTopOutbounds(buildTopOutbounds(selectedYear, stockLogs, machines, parts));
        dashboard.setStockValues(buildStockValues(machines.values().stream().toList(), parts.values().stream().toList()));
        dashboard.setLowStocks(buildLowStocks(machines.values().stream().toList(), parts.values().stream().toList()));
        return dashboard;
    }

    private List<StatisticsDashboardVO.FinancialRow> buildMonthlyRows(
            int selectedYear,
            List<StockOperationLog> stockLogs,
            List<RepairRecord> repairs,
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
        rows.values().forEach(this::finishFinancialRow);
        return rows.values().stream().toList();
    }

    private List<StatisticsDashboardVO.FinancialRow> buildYearlyRows(
            List<StockOperationLog> stockLogs,
            List<RepairRecord> repairs,
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
            sum.setTotalIncome(sum.getTotalIncome().add(row.getTotalIncome()));
            sum.setInboundQuantity(sum.getInboundQuantity() + row.getInboundQuantity());
            sum.setOutboundQuantity(sum.getOutboundQuantity() + row.getOutboundQuantity());
            sum.setRepairOrders(sum.getRepairOrders() + row.getRepairOrders());
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

    private void finishFinancialRow(StatisticsDashboardVO.FinancialRow row) {
        row.setTotalIncome(row.getOutboundRevenue().add(row.getRepairIncome()));
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

    private String resourceLabel(String resourceType) {
        return "MACHINE".equals(resourceType) ? "整车" : "PART".equals(resourceType) ? "配件" : resourceType;
    }

    private record Price(BigDecimal cost, BigDecimal revenue) {
    }
}
