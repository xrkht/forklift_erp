package com.example.forklift_erp.service;

import com.example.forklift_erp.dto.ModificationWorkOrderActionDTO;
import com.example.forklift_erp.dto.ModificationWorkOrderCreateDTO;
import com.example.forklift_erp.dto.ModificationWorkOrderVO;

import java.util.List;

public interface ModificationWorkOrderService {
    List<ModificationWorkOrderVO> findAll();

    List<ModificationWorkOrderVO> findByMachineId(Long machineId);

    ModificationWorkOrderVO findById(Long id);

    ModificationWorkOrderVO create(ModificationWorkOrderCreateDTO request);

    ModificationWorkOrderVO complete(Long id, ModificationWorkOrderActionDTO request);

    ModificationWorkOrderVO cancel(Long id, ModificationWorkOrderActionDTO request);
}
