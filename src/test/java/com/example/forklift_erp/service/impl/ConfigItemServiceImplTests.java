package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.entity.ConfigItem;
import com.example.forklift_erp.repository.ConfigItemRepository;
import com.example.forklift_erp.repository.ConfigValueRepository;
import com.example.forklift_erp.repository.MachineConfigRepository;
import com.example.forklift_erp.repository.VehicleConfigValueRepository;
import com.example.forklift_erp.service.CollaborationService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigItemServiceImplTests {

    @Test
    void deleteByIdValidatesExpectedVersionInsideServiceTransaction() {
        ConfigItemRepository configItemRepository = mock(ConfigItemRepository.class);
        ConfigValueRepository configValueRepository = mock(ConfigValueRepository.class);
        CollaborationService collaborationService = mock(CollaborationService.class);
        MachineConfigRepository machineConfigRepository = mock(MachineConfigRepository.class);
        VehicleConfigValueRepository vehicleConfigValueRepository = mock(VehicleConfigValueRepository.class);
        ConfigItem item = new ConfigItem();
        item.setId(10L);
        item.setVersion(4L);
        when(configItemRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(item));
        when(machineConfigRepository.findByConfigItemId(10L)).thenReturn(List.of());
        when(vehicleConfigValueRepository.existsByConfigItemId(10L)).thenReturn(false);
        ConfigItemServiceImpl service = new ConfigItemServiceImpl(
                configItemRepository,
                configValueRepository,
                collaborationService,
                machineConfigRepository,
                vehicleConfigValueRepository
        );

        service.deleteById(10L, 4L);

        verify(collaborationService).validateWrite(item, 4L);
        verify(configValueRepository).deleteByConfigItemId(10L);
        verify(configItemRepository).deleteById(10L);
    }
}
