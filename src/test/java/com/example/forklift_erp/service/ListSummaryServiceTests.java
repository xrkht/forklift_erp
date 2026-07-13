package com.example.forklift_erp.service;

import com.example.forklift_erp.constant.RentalStatus;
import com.example.forklift_erp.dto.ListSummaryVO;
import com.example.forklift_erp.entity.PurchaseOrder;
import com.example.forklift_erp.entity.RentalRecord;
import com.example.forklift_erp.repository.OutboundOrderRepository;
import com.example.forklift_erp.repository.PurchaseOrderRepository;
import com.example.forklift_erp.repository.RentalRecordRepository;
import com.example.forklift_erp.repository.StocktakingRecordRepository;
import com.example.forklift_erp.repository.SupplierRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListSummaryServiceTests {

    private final OutboundOrderRepository outboundOrderRepository = mock(OutboundOrderRepository.class);
    private final RentalRecordRepository rentalRecordRepository = mock(RentalRecordRepository.class);
    private final SupplierRepository supplierRepository = mock(SupplierRepository.class);
    private final PurchaseOrderRepository purchaseOrderRepository = mock(PurchaseOrderRepository.class);
    private final StocktakingRecordRepository stocktakingRecordRepository = mock(StocktakingRecordRepository.class);
    private final RentalRevenueCalculator rentalRevenueCalculator = mock(RentalRevenueCalculator.class);
    private final ListSummaryService service = new ListSummaryService(
            outboundOrderRepository,
            rentalRecordRepository,
            supplierRepository,
            purchaseOrderRepository,
            stocktakingRecordRepository,
            rentalRevenueCalculator
    );

    @Test
    void rentalSummaryFiltersByKeywordAndAggregatesVisibleRows() {
        RentalRecord matched = rental("RENT-1", "Alpha customer", 1L, RentalStatus.RETURNED.code());
        RentalRecord ignored = rental("RENT-2", "Beta customer", 2L, RentalStatus.ACTIVE.code());
        when(rentalRecordRepository.searchForSummary(
                eq("alpha"), eq(""), eq(LocalDate.now()), eq(LocalDate.now().plusDays(7))))
                .thenReturn(List.of(matched));
        when(rentalRevenueCalculator.totalAmount(matched)).thenReturn(new BigDecimal("3100.00"));

        ListSummaryVO summary = service.summarize("rentals", "alpha");

        assertThat(summary.getType()).isEqualTo("rentals");
        assertThat(summary.getCards()).hasSize(4);
        assertThat(summary.getCards().getFirst().getValue()).isEqualTo(1);
        assertThat(summary.getCards().get(1).getValue()).isEqualTo(new BigDecimal("3100.00"));
        assertThat(summary.getCards().get(2).getValue()).isEqualTo(1L);
        assertThat(summary.getCards().get(3).getValue()).isEqualTo(0L);
        verify(rentalRecordRepository).searchForSummary(
                "alpha", "", LocalDate.now(), LocalDate.now().plusDays(7));
    }

    @Test
    void purchaseSummaryTrimsKeywordNormalizesResourceTypeAndCapsLargeCounts() {
        PurchaseOrderRepository.PurchaseSummaryProjection projection = mock(PurchaseOrderRepository.PurchaseSummaryProjection.class);
        when(projection.getTotalCount()).thenReturn((long) Integer.MAX_VALUE + 100L);
        when(projection.getTotalAmount()).thenReturn(new BigDecimal("-1.00"));
        when(projection.getFreightTotal()).thenReturn(new BigDecimal("25.00"));
        when(projection.getReceived()).thenReturn(2L);
        when(projection.getPending()).thenReturn(3L);
        when(purchaseOrderRepository.summarize(eq("bolt"), eq(PurchaseOrder.RESOURCE_PART))).thenReturn(projection);

        ListSummaryVO summary = service.summarize("purchases", " bolt ", "part");

        verify(purchaseOrderRepository).summarize("bolt", PurchaseOrder.RESOURCE_PART);
        assertThat(summary.getCards().getFirst().getValue()).isEqualTo(Integer.MAX_VALUE);
        assertThat(summary.getCards().get(1).getValue()).isEqualTo(BigDecimal.ZERO);
        assertThat(summary.getCards().get(2).getValue()).isEqualTo(2L);
    }

    private RentalRecord rental(String rentalNo, String customerName, Long machineId, String status) {
        RentalRecord rental = new RentalRecord();
        rental.setRentalNo(rentalNo);
        rental.setCustomerName(customerName);
        rental.setMachineId(machineId);
        rental.setStatus(status);
        return rental;
    }
}
