// src/main/java/com/example/forklift_erp/service/MachineConfigService.java
package com.example.forklift_erp.service;

import com.example.forklift_erp.entity.MachineConfig;
import java.util.List;

public interface MachineConfigService {
    List<MachineConfig> findByMachineId(Long machineId);
    List<MachineConfig> saveAll(List<MachineConfig> configs);
    void deleteByMachineId(Long machineId);
}