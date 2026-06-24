package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.entity.MachineInventory;
import com.example.forklift_erp.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MachineInventoryServiceImplTests {

    @Test
    void saveRejectsNegativeInventoryCountBeforePersistence() {
        MachineInventory machine = new MachineInventory();
        machine.setVehicleProductNumber("CPD-NEGATIVE-001");
        machine.setName("Forklift");
        machine.setSpecificationModel("CPD30");
        machine.setInventoryCount(-1);

        MachineInventoryServiceImpl service = new MachineInventoryServiceImpl();

        assertThatThrownBy(() -> service.save(machine))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode())
                        .isEqualTo(ResultCode.PARAM_ERROR.getCode()))
                .hasMessage("Inventory count cannot be negative");
    }
}
