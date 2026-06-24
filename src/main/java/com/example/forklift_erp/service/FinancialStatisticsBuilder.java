package com.example.forklift_erp.service;

import com.example.forklift_erp.constant.ModificationWorkOrderStatus;
import com.example.forklift_erp.constant.PartChangeAction;
import com.example.forklift_erp.constant.RepairStatus;
import com.example.forklift_erp.dto.StatisticsDashboardVO;
import com.example.forklift_erp.entity.ModificationWorkOrder;
import com.example.forklift_erp.entity.ModificationWorkOrderLine;
import com.example.forklift_erp.entity.RentalRecord;
import com.example.forklift_erp.entity.RepairRecord;
import com.example.forklift_erp.entity.StockOperationLog;
import com.example.forklift_erp.util.MoneyValues;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class FinancialStatisticsBuilder {

    private final RentalRevenueCalculator rentalRevenueCalculator;

    public FinancialStatisticsBuilder(RentalRevenueCalculator rentalRevenueCalculator) {
        this.rentalRevenueCalculator = rentalRevenueCalculator;
    }

    List<StatisticsDashboardVO.FinancialRow> buildMonthlyRows(
            int selectedYear,
            List<StockOperationLog> stockLogs,
            List<RepairRecord> repairs,
            List<RentalRecord> rentals,
            List<ModificationWorkOrder> modificationOrders,
            Map<Long, List<ModificationWorkOrderLine>> modificationLinesByOrderId
    ) {
        Map<String, StatisticsDashboardVO.FinancialRow> rows = new LinkedHashMap<>();
        for (int month = 1; month <= 12; month++) {
            String period = "%d-%02d".formatted(selectedYear, month);
            StatisticsDashboardVO.FinancialRow row = newFinancialRow(period);
            rows.put(period, row);
        }
        for (StockOperationLog log : stockLogs) {
            if (log.getCreatedAt() == null || log.getCreatedAt().getYear() != selectedYear) {
                continue;
            }
            addStockToFinancial(rows.get(YearMonth.from(log.getCreatedAt()).toString()), log);
        }
        for (RepairRecord repair : repairs) {
            if (repair.getRepairDate() == null || repair.getRepairDate().getYear() != selectedYear) {
                continue;
            }
            addRepairToFinancial(rows.get(YearMonth.from(repair.getRepairDate()).toString()), repair);
        }
        for (RentalRecord rental : rentals) {
            addRentalToMonthlyRows(rows, selectedYear, rental);
        }
        for (ModificationWorkOrder order : modificationOrders) {
            if (!ModificationWorkOrderStatus.COMPLETED.code().equals(order.getStatus())
                    || order.getCompletedAt() == null
                    || order.getCompletedAt().getYear() != selectedYear) {
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

    List<StatisticsDashboardVO.FinancialRow> buildYearlyRows(
            List<StockOperationLog> stockLogs,
            List<RepairRecord> repairs,
            List<RentalRecord> rentals,
            List<ModificationWorkOrder> modificationOrders,
            Map<Long, List<ModificationWorkOrderLine>> modificationLinesByOrderId,
            LocalDate rangeStart,
            LocalDate rangeEnd
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
            addStockToFinancial(row, log);
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
            addRentalToYearlyRows(rows, rental, rangeStart, rangeEnd);
        }
        for (ModificationWorkOrder order : modificationOrders) {
            if (!ModificationWorkOrderStatus.COMPLETED.code().equals(order.getStatus()) || order.getCompletedAt() == null) {
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

    StatisticsDashboardVO.FinancialRow annualRow(int selectedYear, List<StatisticsDashboardVO.FinancialRow> rows) {
        String period = String.valueOf(selectedYear);
        return rows.stream()
                .filter(row -> period.equals(row.getPeriod()))
                .findFirst()
                .orElseGet(() -> newFinancialRow(period));
    }

    private StatisticsDashboardVO.FinancialRow newFinancialRow(String period) {
        StatisticsDashboardVO.FinancialRow row = new StatisticsDashboardVO.FinancialRow();
        row.setPeriod(period);
        return row;
    }

    private void addStockToFinancial(StatisticsDashboardVO.FinancialRow row, StockOperationLog log) {
        if (row == null) {
            return;
        }
        Price price = priceFor(log);
        int inboundQuantity = inboundQuantity(log);
        if (inboundQuantity > 0) {
            row.setInboundQuantity(row.getInboundQuantity() + inboundQuantity);
            row.setInboundCost(row.getInboundCost().add(price.cost().multiply(BigDecimal.valueOf(inboundQuantity))));
        }
        int outboundQuantity = outboundQuantity(log);
        if (outboundQuantity > 0) {
            BigDecimal revenue = price.revenue().multiply(BigDecimal.valueOf(outboundQuantity));
            BigDecimal cost = price.cost().multiply(BigDecimal.valueOf(outboundQuantity));
            row.setOutboundQuantity(row.getOutboundQuantity() + outboundQuantity);
            row.setOutboundRevenue(row.getOutboundRevenue().add(revenue));
            row.setOutboundCost(row.getOutboundCost().add(cost));
            if (revenue.signum() > 0) {
                row.setGrossProfit(row.getGrossProfit().add(revenue.subtract(cost)));
            }
        }
    }

    private void addRepairToFinancial(StatisticsDashboardVO.FinancialRow row, RepairRecord repair) {
        if (row == null || !RepairStatus.COMPLETED.code().equals(repair.getStatus())) {
            return;
        }
        BigDecimal income = repairIncome(repair);
        BigDecimal expense = repairExpense(repair);
        BigDecimal partsCost = repairPartsCost(repair);
        row.setRepairReceivable(row.getRepairReceivable().add(repairReceivable(repair, income, expense)));
        row.setRepairIncome(row.getRepairIncome().add(income));
        row.setRepairExpense(row.getRepairExpense().add(expense));
        row.setRepairPartsCost(row.getRepairPartsCost().add(partsCost));
        row.setRepairOrders(row.getRepairOrders() + 1);
    }

    private void addRentalToFinancial(StatisticsDashboardVO.FinancialRow row, BigDecimal amount) {
        if (row == null || amount == null || amount.signum() <= 0) {
            return;
        }
        row.setRentalIncome(row.getRentalIncome().add(amount));
        row.setGrossProfit(row.getGrossProfit().add(amount));
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
                .filter(line -> PartChangeAction.DISCOUNT.code().equals(line.getOldPartAction()))
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

    private void addRentalToMonthlyRows(
            Map<String, StatisticsDashboardVO.FinancialRow> rows,
            int selectedYear,
            RentalRecord rental
    ) {
        LocalDate yearStart = LocalDate.of(selectedYear, 1, 1);
        LocalDate yearEnd = LocalDate.of(selectedYear, 12, 31);
        rentalRevenueCalculator.monthlyAmounts(rental, yearStart, yearEnd).forEach((period, amount) ->
                addRentalToFinancial(rows.get(period.toString()), amount));
    }

    private void addRentalToYearlyRows(
            Map<String, StatisticsDashboardVO.FinancialRow> rows,
            RentalRecord rental,
            LocalDate rangeStart,
            LocalDate rangeEnd
    ) {
        Map<Integer, BigDecimal> yearlyAmounts = new LinkedHashMap<>();
        rentalRevenueCalculator.monthlyAmounts(rental, rangeStart, rangeEnd).forEach((period, amount) ->
                yearlyAmounts.merge(period.getYear(), amount, BigDecimal::add));
        yearlyAmounts.forEach((year, amount) -> {
            StatisticsDashboardVO.FinancialRow row = rows.computeIfAbsent(String.valueOf(year), this::newFinancialRow);
            addRentalToFinancial(row, amount);
        });
    }

    private BigDecimal repairIncome(RepairRecord repair) {
        BigDecimal income = amount(repair.getRepairFee()).add(amount(repair.getPartsFee()));
        if (income.signum() == 0 && repair.getTotalFee() != null) {
            return amount(repair.getTotalFee()).subtract(repairExpense(repair)).max(BigDecimal.ZERO);
        }
        return income;
    }

    private BigDecimal repairReceivable(RepairRecord repair, BigDecimal income, BigDecimal expense) {
        if (repair.getTotalFee() != null) {
            return amount(repair.getTotalFee());
        }
        return amount(income).add(amount(expense));
    }

    private BigDecimal repairExpense(RepairRecord repair) {
        return Boolean.TRUE.equals(repair.getRepairExternal()) ? amount(repair.getRepairExpense()) : BigDecimal.ZERO;
    }

    private BigDecimal repairPartsCost(RepairRecord repair) {
        return amount(repair.getPartsCost());
    }

    private void finishFinancialRow(StatisticsDashboardVO.FinancialRow row) {
        BigDecimal totalIncome = row.getOutboundRevenue()
                .add(row.getRepairIncome())
                .add(row.getRentalIncome())
                .add(row.getModificationIncome());
        BigDecimal operatingExpense = row.getOutboundCost()
                .add(row.getRepairExpense())
                .add(row.getRepairPartsCost())
                .add(row.getModificationExpense());
        BigDecimal totalExpense = row.getInboundCost().add(operatingExpense);
        BigDecimal netProfit = totalIncome.subtract(operatingExpense);
        row.setTotalIncome(totalIncome);
        row.setTotalExpense(totalExpense);
        row.setGrossProfit(netProfit);
        row.setNetProfit(netProfit);
        row.setNetCashflow(totalIncome.subtract(totalExpense));
    }

    private Price priceFor(StockOperationLog log) {
        return new Price(amount(log.getUnitCost()), amount(log.getUnitRevenue()));
    }

    private int quantity(StockOperationLog log) {
        return log.getQuantity() == null ? 0 : log.getQuantity();
    }

    private int inboundQuantity(StockOperationLog log) {
        String operationType = log.getOperationType();
        if ("INBOUND".equals(operationType) || "INITIAL".equals(operationType)) {
            return quantity(log);
        }
        if ("ADJUST".equals(operationType)) {
            return Math.max(quantityDelta(log), 0);
        }
        return 0;
    }

    private int outboundQuantity(StockOperationLog log) {
        String operationType = log.getOperationType();
        if ("OUTBOUND".equals(operationType)) {
            return quantity(log);
        }
        if ("ADJUST".equals(operationType)) {
            return Math.max(-quantityDelta(log), 0);
        }
        return 0;
    }

    private int quantityDelta(StockOperationLog log) {
        if (log.getBeforeQuantity() != null && log.getAfterQuantity() != null) {
            return log.getAfterQuantity() - log.getBeforeQuantity();
        }
        return quantity(log);
    }

    private BigDecimal amount(BigDecimal value) {
        return MoneyValues.zeroIfNullOrNegative(value);
    }

    private record Price(BigDecimal cost, BigDecimal revenue) {
    }
}
