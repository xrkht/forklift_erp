package com.example.forklift_erp.service;

import com.example.forklift_erp.dto.StatisticsDashboardVO;
import com.example.forklift_erp.entity.MachineInventory;
import com.example.forklift_erp.entity.PartInventory;
import com.example.forklift_erp.repository.MachineInventoryRepository;
import com.example.forklift_erp.repository.PartInventoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InventoryStatisticsBuilderTests {

    private final MachineInventoryRepository machineInventoryRepository = mock(MachineInventoryRepository.class);
    private final PartInventoryRepository partInventoryRepository = mock(PartInventoryRepository.class);
    private final InventoryStatisticsBuilder builder = new InventoryStatisticsBuilder(
            machineInventoryRepository,
            partInventoryRepository
    );

    @Test
    void stockValuesNormalizeNullNegativeAndLargeProjectionValues() {
        MachineInventoryRepository.StockValueProjection machineValue = mock(MachineInventoryRepository.StockValueProjection.class);
        when(machineValue.getItemCount()).thenReturn((long) Integer.MAX_VALUE + 5L);
        when(machineValue.getStockQuantity()).thenReturn(3L);
        when(machineValue.getCostValue()).thenReturn(new BigDecimal("-1.00"));
        when(machineValue.getSettlementValue()).thenReturn(new BigDecimal("1250.00"));

        PartInventoryRepository.StockValueProjection partValue = mock(PartInventoryRepository.StockValueProjection.class);
        when(partValue.getItemCount()).thenReturn(null);
        when(partValue.getStockQuantity()).thenReturn(null);
        when(partValue.getCostValue()).thenReturn(null);
        when(partValue.getSettlementValue()).thenReturn(new BigDecimal("-2.00"));

        when(machineInventoryRepository.stockValue()).thenReturn(machineValue);
        when(partInventoryRepository.stockValue()).thenReturn(partValue);

        List<StatisticsDashboardVO.StockValueRow> rows = builder.stockValues();

        StatisticsDashboardVO.StockValueRow machineRow = rows.getFirst();
        assertThat(machineRow.getResourceType()).isEqualTo("MACHINE");
        assertThat(machineRow.getItemCount()).isEqualTo(Integer.MAX_VALUE);
        assertThat(machineRow.getStockQuantity()).isEqualTo(3);
        assertThat(machineRow.getCostValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(machineRow.getSettlementValue()).isEqualByComparingTo("1250.00");

        StatisticsDashboardVO.StockValueRow partRow = rows.get(1);
        assertThat(partRow.getResourceType()).isEqualTo("PART");
        assertThat(partRow.getItemCount()).isZero();
        assertThat(partRow.getStockQuantity()).isZero();
        assertThat(partRow.getCostValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(partRow.getSettlementValue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void lowStocksMergeMachineAndPartRowsSortedByQuantity() {
        MachineInventory machine = new MachineInventory();
        machine.setVehicleProductNumber("M-001");
        machine.setName("Forklift");
        machine.setInventoryCount(0);

        PartInventory part = new PartInventory();
        part.setPartCode("P-001");
        part.setPartName("Filter");
        part.setQuantity(3);
        part.setUnit("pcs");

        when(machineInventoryRepository.findLowStock(eq(0), any(Pageable.class))).thenReturn(List.of(machine));
        when(partInventoryRepository.findLowStock(eq(5), any(Pageable.class))).thenReturn(List.of(part));

        List<StatisticsDashboardVO.LowStockRow> rows = builder.lowStocks();

        assertThat(rows).extracting(StatisticsDashboardVO.LowStockRow::getResourceType)
                .containsExactly("MACHINE", "PART");
        assertThat(rows.getFirst().getQuantity()).isZero();
        assertThat(rows.getFirst().getThreshold()).isZero();
        assertThat(rows.get(1).getQuantity()).isEqualTo(3);
        assertThat(rows.get(1).getThreshold()).isEqualTo(5);
    }
}
