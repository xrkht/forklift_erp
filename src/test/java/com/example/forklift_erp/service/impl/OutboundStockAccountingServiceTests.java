package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.entity.MachineInventory;
import com.example.forklift_erp.entity.OutboundOrder;
import com.example.forklift_erp.entity.StockOperationLog;
import com.example.forklift_erp.util.InventoryQuantities;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboundStockAccountingServiceTests {

    @Test
    void recordMachineOutboundBuildsStockCommandFromOrderSnapshot() {
        StockOperationRecorder recorder = mock(StockOperationRecorder.class);
        StockOperationLog savedLog = new StockOperationLog();
        savedLog.setId(21L);
        when(recorder.record(any())).thenReturn(savedLog);
        OutboundStockAccountingService service = new OutboundStockAccountingService(recorder);

        MachineInventory machine = new MachineInventory();
        machine.setId(7L);
        machine.setVehicleProductNumber("V-700");
        machine.setName("Forklift");
        machine.setWarehouseId(3L);
        OutboundOrder order = new OutboundOrder();
        order.setId(9L);
        order.setReceivableAmount(new BigDecimal("1500.00"));

        StockOperationLog result = service.recordMachineOutbound(
                machine,
                order,
                new InventoryQuantities.QuantityChange(2, 1, 1),
                new BigDecimal("1000.00"),
                "tester",
                "sale"
        );

        ArgumentCaptor<StockOperationRecorder.Command> captor =
                ArgumentCaptor.forClass(StockOperationRecorder.Command.class);
        verify(recorder).record(captor.capture());
        StockOperationRecorder.Command command = captor.getValue();
        assertThat(result).isSameAs(savedLog);
        assertThat(command.resourceType()).isEqualTo(OutboundOrder.RESOURCE_MACHINE);
        assertThat(command.resourceId()).isEqualTo(7L);
        assertThat(command.quantity()).isEqualTo(1);
        assertThat(command.beforeQuantity()).isEqualTo(2);
        assertThat(command.afterQuantity()).isEqualTo(1);
        assertThat(command.unitCost()).isEqualByComparingTo("1000.00");
        assertThat(command.unitRevenue()).isEqualByComparingTo("1500.00");
        assertThat(command.movementSourceType()).isEqualTo("OUTBOUND_ORDER");
        assertThat(command.movementSourceId()).isEqualTo(9L);
    }
}
