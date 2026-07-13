package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.constant.MachineStockStatus;
import com.example.forklift_erp.dto.RentalRecordUpdateDTO;
import com.example.forklift_erp.entity.MachineInventory;
import com.example.forklift_erp.entity.RentalRecord;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.repository.MachineInventoryRepository;
import com.example.forklift_erp.repository.RentalRecordRepository;
import com.example.forklift_erp.service.CollaborationService;
import com.example.forklift_erp.service.OperationAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RentalRecordServiceImplTests {

    private RentalRecordRepository rentalRecordRepository;
    private MachineInventoryRepository machineRepository;
    private CollaborationService collaborationService;
    private OperationAuditService operationAuditService;
    private RentalRecordServiceImpl service;

    @BeforeEach
    void setUp() {
        rentalRecordRepository = mock(RentalRecordRepository.class);
        machineRepository = mock(MachineInventoryRepository.class);
        collaborationService = mock(CollaborationService.class);
        operationAuditService = mock(OperationAuditService.class);
        service = new RentalRecordServiceImpl();
        ReflectionTestUtils.setField(service, "rentalRecordRepository", rentalRecordRepository);
        ReflectionTestUtils.setField(service, "machineRepository", machineRepository);
        ReflectionTestUtils.setField(service, "collaborationService", collaborationService);
        ReflectionTestUtils.setField(service, "operationAuditService", operationAuditService);
    }

    @Test
    void deleteRejectsActiveRental() {
        RentalRecord record = rental(12L, 3L, RentalRecord.STATUS_ACTIVE);
        when(rentalRecordRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.delete(12L, 3L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("进行中的租赁记录不能删除，请先办理归还")
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo(ResultCode.CONFLICT.getCode());

        verify(collaborationService).validateWrite(record, 3L);
        verify(rentalRecordRepository, never()).delete(any(RentalRecord.class));
        verifyNoInteractions(operationAuditService);
    }

    @Test
    void deleteRemovesReturnedRental() {
        RentalRecord record = rental(13L, 4L, RentalRecord.STATUS_RETURNED);
        when(rentalRecordRepository.findByIdForUpdate(13L)).thenReturn(Optional.of(record));

        service.delete(13L, 4L);

        verify(collaborationService).validateWrite(record, 4L);
        verify(rentalRecordRepository).delete(record);
        verify(operationAuditService).record(
                "租赁管理", "DELETE", "RENTAL_RECORD", 13L,
                "RT-013", "CPD-013", "删除车辆租赁记录",
                "legacy-operator", "returned", "RENTAL_RECORD", 13L
        );
    }

    @Test
    void updateRejectsReactivatingRentalForLockedMachine() {
        RentalRecord record = rental(14L, 5L, RentalRecord.STATUS_RETURNED);
        record.setMachineId(50L);
        MachineInventory machine = machine(50L, true);
        when(rentalRecordRepository.findByIdForUpdate(14L)).thenReturn(Optional.of(record));
        when(machineRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(machine));

        RentalRecordUpdateDTO request = updateRequest(5L);
        assertThatThrownBy(() -> service.update(14L, request))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo(ResultCode.VEHICLE_NOT_FOUND.getCode());

        verify(rentalRecordRepository, never()).saveAndFlush(any(RentalRecord.class));
        verifyNoInteractions(operationAuditService);
    }

    @Test
    void updateRejectsReactivatingRentalWhenAnotherRentalIsActive() {
        RentalRecord record = rental(15L, 6L, RentalRecord.STATUS_RETURNED);
        record.setMachineId(51L);
        MachineInventory machine = machine(51L, false);
        when(rentalRecordRepository.findByIdForUpdate(15L)).thenReturn(Optional.of(record));
        when(machineRepository.findByIdForUpdate(51L)).thenReturn(Optional.of(machine));
        when(rentalRecordRepository.existsByMachineIdAndStatus(51L, RentalRecord.STATUS_ACTIVE)).thenReturn(true);

        RentalRecordUpdateDTO request = updateRequest(6L);
        assertThatThrownBy(() -> service.update(15L, request))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo(ResultCode.CONFLICT.getCode());

        verify(rentalRecordRepository, never()).saveAndFlush(any(RentalRecord.class));
        verifyNoInteractions(operationAuditService);
    }

    private RentalRecordUpdateDTO updateRequest(Long version) {
        RentalRecordUpdateDTO request = new RentalRecordUpdateDTO();
        request.setVersion(version);
        request.setStatus(RentalRecord.STATUS_ACTIVE);
        return request;
    }

    private MachineInventory machine(Long id, boolean locked) {
        MachineInventory machine = new MachineInventory();
        machine.setId(id);
        machine.setIsLocked(locked);
        machine.setModelOnly(false);
        machine.setInventoryCount(1);
        machine.setStockStatus(MachineStockStatus.IN_STOCK.code());
        return machine;
    }

    private RentalRecord rental(Long id, Long version, String status) {
        RentalRecord record = new RentalRecord();
        record.setId(id);
        record.setVersion(version);
        record.setStatus(status);
        record.setRentalNo("RT-0" + id);
        record.setVehicleNumber("CPD-0" + id);
        record.setOperator("legacy-operator");
        record.setRemark("returned");
        return record;
    }
}
