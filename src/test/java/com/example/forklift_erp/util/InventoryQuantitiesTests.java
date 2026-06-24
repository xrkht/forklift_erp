package com.example.forklift_erp.util;

import com.example.forklift_erp.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryQuantitiesTests {

    @Test
    void inboundReturnsBeforeDeltaAndAfterQuantities() {
        InventoryQuantities.QuantityChange change = InventoryQuantities.inbound(3, 2, "Quantity must be greater than 0");

        assertThat(change.beforeQuantity()).isEqualTo(3);
        assertThat(change.quantity()).isEqualTo(2);
        assertThat(change.afterQuantity()).isEqualTo(5);
    }

    @Test
    void outboundRejectsInsufficientStock() {
        assertThatThrownBy(() -> InventoryQuantities.outbound(
                1,
                2,
                "Quantity must be greater than 0",
                "Insufficient stock: "
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Insufficient stock: 1");
    }

    @Test
    void adjustToRejectsNegativeTarget() {
        assertThatThrownBy(() -> InventoryQuantities.adjustTo(1, -1, "Inventory count cannot be negative"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Inventory count cannot be negative");
    }
}
