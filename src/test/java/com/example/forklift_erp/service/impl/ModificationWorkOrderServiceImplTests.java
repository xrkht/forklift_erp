package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.constant.ModificationWorkOrderStatus;
import com.example.forklift_erp.dto.ModificationWorkOrderActionDTO;
import com.example.forklift_erp.dto.ModificationWorkOrderCreateDTO;
import com.example.forklift_erp.entity.MachineConfig;
import com.example.forklift_erp.entity.MachineInventory;
import com.example.forklift_erp.entity.ModificationWorkOrder;
import com.example.forklift_erp.entity.ModificationWorkOrderLine;
import com.example.forklift_erp.entity.PartInventory;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.repository.MachineConfigRepository;
import com.example.forklift_erp.repository.MachineInventoryRepository;
import com.example.forklift_erp.repository.ModificationWorkOrderLineRepository;
import com.example.forklift_erp.repository.ModificationWorkOrderRepository;
import com.example.forklift_erp.repository.PartInventoryRepository;
import com.example.forklift_erp.service.CollaborationService;
import com.example.forklift_erp.service.ConfigReplaceService;
import com.example.forklift_erp.service.ResourceVisibilityPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModificationWorkOrderServiceImplTests {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createRejectsLockedMachine() {
        MachineInventoryRepository machineRepository = mock(MachineInventoryRepository.class);
        ModificationWorkOrderRepository workOrderRepository = mock(ModificationWorkOrderRepository.class);
        when(machineRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(machine(1L, true)));
        ModificationWorkOrderServiceImpl service = service(workOrderRepository, mock(ModificationWorkOrderLineRepository.class),
                machineRepository, mock(MachineConfigRepository.class), mock(PartInventoryRepository.class), mock(ConfigReplaceService.class));

        ModificationWorkOrderCreateDTO request = createRequest(1L, 10L, 2L);

        assertForbidden(() -> service.create(request), "Vehicle is locked and cannot create modification work order");
        verify(workOrderRepository, never()).save(any());
    }

    @Test
    void createRejectsLockedPart() {
        MachineInventoryRepository machineRepository = mock(MachineInventoryRepository.class);
        MachineConfigRepository configRepository = mock(MachineConfigRepository.class);
        PartInventoryRepository partRepository = mock(PartInventoryRepository.class);
        ModificationWorkOrderRepository workOrderRepository = mock(ModificationWorkOrderRepository.class);
        when(machineRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(machine(1L, false)));
        when(configRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(config(10L, 1L)));
        when(partRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(part(2L, true)));
        ModificationWorkOrderServiceImpl service = service(workOrderRepository, mock(ModificationWorkOrderLineRepository.class),
                machineRepository, configRepository, partRepository, mock(ConfigReplaceService.class));

        ModificationWorkOrderCreateDTO request = createRequest(1L, 10L, 2L);

        assertForbidden(() -> service.create(request), "Part is locked and cannot be used in modification work order");
        verify(workOrderRepository, never()).save(any());
    }

    @Test
    void completeRejectsMachineLockedAfterOrderWasCreated() {
        ModificationWorkOrderRepository workOrderRepository = mock(ModificationWorkOrderRepository.class);
        ModificationWorkOrderLineRepository lineRepository = mock(ModificationWorkOrderLineRepository.class);
        MachineInventoryRepository machineRepository = mock(MachineInventoryRepository.class);
        ModificationWorkOrder order = order(20L, 1L);
        when(workOrderRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(order));
        when(lineRepository.findByWorkOrderIdOrderByIdAsc(20L)).thenReturn(List.of(line(10L, 2L)));
        when(machineRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(machine(1L, true)));
        ModificationWorkOrderServiceImpl service = service(workOrderRepository, lineRepository, machineRepository,
                mock(MachineConfigRepository.class), mock(PartInventoryRepository.class), mock(ConfigReplaceService.class));

        assertForbidden(() -> service.complete(20L, action()), "Vehicle is locked and cannot complete modification work order");
        verify(machineRepository, never()).save(any());
    }

    @Test
    void completeRejectsPartLockedAfterOrderWasCreated() {
        ModificationWorkOrderRepository workOrderRepository = mock(ModificationWorkOrderRepository.class);
        ModificationWorkOrderLineRepository lineRepository = mock(ModificationWorkOrderLineRepository.class);
        MachineInventoryRepository machineRepository = mock(MachineInventoryRepository.class);
        MachineConfigRepository configRepository = mock(MachineConfigRepository.class);
        PartInventoryRepository partRepository = mock(PartInventoryRepository.class);
        ConfigReplaceService replaceService = mock(ConfigReplaceService.class);
        ModificationWorkOrder order = order(20L, 1L);
        when(workOrderRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(order));
        when(lineRepository.findByWorkOrderIdOrderByIdAsc(20L)).thenReturn(List.of(line(10L, 2L)));
        when(machineRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(machine(1L, false)));
        when(configRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(config(10L, 1L)));
        when(partRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(part(2L, true)));
        ModificationWorkOrderServiceImpl service = service(workOrderRepository, lineRepository, machineRepository,
                configRepository, partRepository, replaceService);

        assertForbidden(() -> service.complete(20L, action()), "Part is locked and cannot be used in modification work order");
        verify(replaceService, never()).performPartReplace(any());
    }

    @Test
    void cancelRejectsMachineLockedAfterOrderWasCreated() {
        ModificationWorkOrderRepository workOrderRepository = mock(ModificationWorkOrderRepository.class);
        MachineInventoryRepository machineRepository = mock(MachineInventoryRepository.class);
        ModificationWorkOrder order = order(20L, 1L);
        when(workOrderRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(order));
        when(machineRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(machine(1L, true)));
        ModificationWorkOrderServiceImpl service = service(workOrderRepository, mock(ModificationWorkOrderLineRepository.class),
                machineRepository, mock(MachineConfigRepository.class), mock(PartInventoryRepository.class), mock(ConfigReplaceService.class));

        assertForbidden(() -> service.cancel(20L, action()), "Vehicle is locked and cannot cancel modification work order");
        verify(workOrderRepository, never()).save(any());
        verify(machineRepository, never()).save(any());
    }

    private ModificationWorkOrderServiceImpl service(
            ModificationWorkOrderRepository workOrderRepository,
            ModificationWorkOrderLineRepository lineRepository,
            MachineInventoryRepository machineRepository,
            MachineConfigRepository configRepository,
            PartInventoryRepository partRepository,
            ConfigReplaceService replaceService
    ) {
        ModificationWorkOrderServiceImpl service = new ModificationWorkOrderServiceImpl();
        ReflectionTestUtils.setField(service, "workOrderRepository", workOrderRepository);
        ReflectionTestUtils.setField(service, "lineRepository", lineRepository);
        ReflectionTestUtils.setField(service, "machineRepository", machineRepository);
        ReflectionTestUtils.setField(service, "machineConfigRepository", configRepository);
        ReflectionTestUtils.setField(service, "partRepository", partRepository);
        ReflectionTestUtils.setField(service, "configReplaceService", replaceService);
        ReflectionTestUtils.setField(service, "collaborationService", mock(CollaborationService.class));
        ReflectionTestUtils.setField(service, "visibilityPolicy", new ResourceVisibilityPolicy());
        return service;
    }

    private ModificationWorkOrderCreateDTO createRequest(Long machineId, Long configId, Long partId) {
        ModificationWorkOrderCreateDTO.Line line = new ModificationWorkOrderCreateDTO.Line();
        line.setMachineConfigId(configId);
        line.setNewPartId(partId);
        ModificationWorkOrderCreateDTO request = new ModificationWorkOrderCreateDTO();
        request.setMachineId(machineId);
        request.setLines(List.of(line));
        return request;
    }

    private ModificationWorkOrderActionDTO action() {
        ModificationWorkOrderActionDTO request = new ModificationWorkOrderActionDTO();
        request.setVersion(0L);
        return request;
    }

    private ModificationWorkOrder order(Long id, Long machineId) {
        ModificationWorkOrder order = new ModificationWorkOrder();
        order.setId(id);
        order.setVersion(0L);
        order.setMachineId(machineId);
        order.setWorkOrderNo("MO-TEST");
        order.setStatus(ModificationWorkOrderStatus.WAITING_PARTS.code());
        return order;
    }

    private ModificationWorkOrderLine line(Long configId, Long partId) {
        ModificationWorkOrderLine line = new ModificationWorkOrderLine();
        line.setMachineConfigId(configId);
        line.setNewPartId(partId);
        line.setQuantity(1);
        return line;
    }

    private MachineConfig config(Long id, Long machineId) {
        MachineConfig config = new MachineConfig();
        config.setId(id);
        config.setMachineId(machineId);
        config.setConfigItemId(30L);
        config.setItemName("Tire");
        return config;
    }

    private MachineInventory machine(Long id, boolean locked) {
        MachineInventory machine = new MachineInventory();
        machine.setId(id);
        machine.setIsLocked(locked);
        machine.setInventoryCount(1);
        return machine;
    }

    private PartInventory part(Long id, boolean locked) {
        PartInventory part = new PartInventory();
        part.setId(id);
        part.setIsLocked(locked);
        part.setQuantity(1);
        part.setPartCategory("Tire");
        return part;
    }

    private void assertForbidden(Runnable action, String message) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode())
                        .isEqualTo(ResultCode.FORBIDDEN.getCode()))
                .hasMessage(message);
    }
}
