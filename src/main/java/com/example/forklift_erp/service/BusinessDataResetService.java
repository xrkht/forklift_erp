package com.example.forklift_erp.service;

import com.example.forklift_erp.repository.ConfigReplaceLogRepository;
import com.example.forklift_erp.repository.CustomerRepository;
import com.example.forklift_erp.repository.MachineConfigRepository;
import com.example.forklift_erp.repository.MachineInventoryRepository;
import com.example.forklift_erp.repository.ModificationWorkOrderLineRepository;
import com.example.forklift_erp.repository.ModificationWorkOrderRepository;
import com.example.forklift_erp.repository.OperationAuditLogRepository;
import com.example.forklift_erp.repository.OutboundOrderRepository;
import com.example.forklift_erp.repository.PartInventoryRepository;
import com.example.forklift_erp.repository.RepairRecordRepository;
import com.example.forklift_erp.repository.RentalRecordRepository;
import com.example.forklift_erp.repository.StockBalanceRepository;
import com.example.forklift_erp.repository.StockMovementLineRepository;
import com.example.forklift_erp.repository.StockMovementRepository;
import com.example.forklift_erp.repository.StockOperationLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class BusinessDataResetService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ModificationWorkOrderLineRepository modificationWorkOrderLineRepository;

    @Autowired
    private ModificationWorkOrderRepository modificationWorkOrderRepository;

    @Autowired
    private ConfigReplaceLogRepository configReplaceLogRepository;

    @Autowired
    private StockOperationLogRepository stockOperationLogRepository;

    @Autowired
    private OutboundOrderRepository outboundOrderRepository;

    @Autowired
    private RentalRecordRepository rentalRecordRepository;

    @Autowired
    private MachineConfigRepository machineConfigRepository;

    @Autowired
    private StockMovementLineRepository stockMovementLineRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private StockBalanceRepository stockBalanceRepository;

    @Autowired
    private RepairRecordRepository repairRecordRepository;

    @Autowired
    private PartInventoryRepository partInventoryRepository;

    @Autowired
    private MachineInventoryRepository machineInventoryRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OperationAuditLogRepository operationAuditLogRepository;

    @Transactional
    public Map<String, Long> resetBusinessData() {
        Map<String, Long> summary = new LinkedHashMap<>();

        summary.put("repairPartUsage", deleteNativeTable("repair_part_usage"));
        summary.put("modificationWorkOrderLines", deleteAll(modificationWorkOrderLineRepository));
        summary.put("modificationWorkOrders", deleteAll(modificationWorkOrderRepository));
        summary.put("configReplaceLogs", deleteAll(configReplaceLogRepository));
        summary.put("stockOperationLogs", deleteAll(stockOperationLogRepository));
        summary.put("outboundOrders", deleteAll(outboundOrderRepository));
        summary.put("rentalRecords", deleteAll(rentalRecordRepository));
        summary.put("machineConfigs", deleteAll(machineConfigRepository));
        summary.put("stockMovementLines", deleteAll(stockMovementLineRepository));
        summary.put("stockMovements", deleteAll(stockMovementRepository));
        summary.put("stockBalances", deleteAll(stockBalanceRepository));
        summary.put("repairRecords", deleteAll(repairRecordRepository));
        summary.put("partInventories", deleteAll(partInventoryRepository));
        summary.put("machineInventories", deleteAll(machineInventoryRepository));
        summary.put("customers", deleteAll(customerRepository));
        summary.put("operationAuditLogs", deleteAll(operationAuditLogRepository));

        return summary;
    }

    private long deleteNativeTable(String tableName) {
        Long count = jdbcTemplate.queryForObject("select count(*) from " + tableName, Long.class);
        jdbcTemplate.update("delete from " + tableName);
        return count == null ? 0L : count;
    }

    private long deleteAll(JpaRepository<?, ?> repository) {
        long count = repository.count();
        if (count > 0) {
            repository.deleteAllInBatch();
        }
        return count;
    }
}
