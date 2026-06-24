package com.example.forklift_erp.service;

import com.example.forklift_erp.dto.StatisticsDashboardVO;
import com.example.forklift_erp.entity.MachineInventory;
import com.example.forklift_erp.entity.PartInventory;
import com.example.forklift_erp.repository.MachineInventoryRepository;
import com.example.forklift_erp.repository.PartInventoryRepository;
import com.example.forklift_erp.util.MoneyValues;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
public class InventoryStatisticsBuilder {
    private static final int LOW_PART_THRESHOLD = 5;
    private static final int LOW_MACHINE_THRESHOLD = 0;

    private final MachineInventoryRepository machineInventoryRepository;
    private final PartInventoryRepository partInventoryRepository;

    public InventoryStatisticsBuilder(
            MachineInventoryRepository machineInventoryRepository,
            PartInventoryRepository partInventoryRepository
    ) {
        this.machineInventoryRepository = machineInventoryRepository;
        this.partInventoryRepository = partInventoryRepository;
    }

    List<StatisticsDashboardVO.StockValueRow> stockValues() {
        StatisticsDashboardVO.StockValueRow machineRow = new StatisticsDashboardVO.StockValueRow();
        machineRow.setResourceType("MACHINE");
        machineRow.setLabel("Vehicle inventory");
        MachineInventoryRepository.StockValueProjection machineValue = machineInventoryRepository.stockValue();
        applyStockValue(machineRow, machineValue.getItemCount(), machineValue.getStockQuantity(),
                machineValue.getCostValue(), machineValue.getSettlementValue());

        StatisticsDashboardVO.StockValueRow partRow = new StatisticsDashboardVO.StockValueRow();
        partRow.setResourceType("PART");
        partRow.setLabel("Part inventory");
        PartInventoryRepository.StockValueProjection partValue = partInventoryRepository.stockValue();
        applyStockValue(partRow, partValue.getItemCount(), partValue.getStockQuantity(),
                partValue.getCostValue(), partValue.getSettlementValue());
        return List.of(machineRow, partRow);
    }

    List<StatisticsDashboardVO.LowStockRow> lowStocks() {
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

    private void applyStockValue(
            StatisticsDashboardVO.StockValueRow row,
            Long itemCount,
            Long stockQuantity,
            BigDecimal costValue,
            BigDecimal settlementValue
    ) {
        row.setItemCount(toInt(itemCount));
        row.setStockQuantity(toInt(stockQuantity));
        row.setCostValue(amount(costValue));
        row.setSettlementValue(amount(settlementValue));
        row.setRetailValueForCompatibility(amount(settlementValue));
    }

    private int quantity(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal amount(BigDecimal value) {
        return MoneyValues.zeroIfNullOrNegative(value);
    }

    private int toInt(Long value) {
        if (value == null) {
            return 0;
        }
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : value.intValue();
    }
}
