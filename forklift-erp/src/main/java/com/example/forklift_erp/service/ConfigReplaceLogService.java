package com.example.forklift_erp.service;

import com.example.forklift_erp.entity.ConfigReplaceLog;
import java.util.List;

public interface ConfigReplaceLogService {
    List<ConfigReplaceLog> findByMachineId(Long machineId);
    ConfigReplaceLog save(ConfigReplaceLog log);
}