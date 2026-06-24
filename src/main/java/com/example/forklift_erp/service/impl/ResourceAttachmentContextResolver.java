package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.repository.CustomerRepository;
import com.example.forklift_erp.repository.MachineInventoryRepository;
import com.example.forklift_erp.repository.OutboundOrderRepository;
import com.example.forklift_erp.repository.PartInventoryRepository;
import com.example.forklift_erp.repository.RepairRecordRepository;
import org.springframework.stereotype.Component;

@Component
class ResourceAttachmentContextResolver {
    private final MachineInventoryRepository machineInventoryRepository;
    private final RepairRecordRepository repairRecordRepository;
    private final OutboundOrderRepository outboundOrderRepository;
    private final PartInventoryRepository partInventoryRepository;
    private final CustomerRepository customerRepository;

    ResourceAttachmentContextResolver(
            MachineInventoryRepository machineInventoryRepository,
            RepairRecordRepository repairRecordRepository,
            OutboundOrderRepository outboundOrderRepository,
            PartInventoryRepository partInventoryRepository,
            CustomerRepository customerRepository
    ) {
        this.machineInventoryRepository = machineInventoryRepository;
        this.repairRecordRepository = repairRecordRepository;
        this.outboundOrderRepository = outboundOrderRepository;
        this.partInventoryRepository = partInventoryRepository;
        this.customerRepository = customerRepository;
    }

    ResourceContext resolve(String resourceType, Long resourceId) {
        if (resourceId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请选择业务对象");
        }
        return switch (resourceType) {
            case "MACHINE" -> machineInventoryRepository.findById(resourceId)
                    .map(machine -> new ResourceContext(
                            "MACHINE",
                            machine.getId(),
                            machine.getVehicleProductNumber(),
                            firstNonBlank(machine.getName(), machine.getSpecificationModel(), "Vehicle"),
                            machine.getIsLocked()
                    ))
                    .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Vehicle not found"));
            case "REPAIR" -> repairRecordRepository.findById(resourceId)
                    .map(repair -> new ResourceContext(
                            "REPAIR",
                            repair.getId(),
                            repair.getVehicleNumber(),
                            firstNonBlank(repair.getCustomerName(), repair.getFaultDescription(), "Repair"),
                            repair.getIsLocked()
                    ))
                    .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Repair record not found"));
            case "OUTBOUND_ORDER" -> outboundOrderRepository.findById(resourceId)
                    .map(order -> new ResourceContext(
                            "OUTBOUND_ORDER",
                            order.getId(),
                            order.getOrderNo(),
                            firstNonBlank(order.getCustomerName(), order.getResourceName(), "Outbound order"),
                            order.getIsLocked()
                    ))
                    .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Outbound order not found"));
            case "PART" -> partInventoryRepository.findById(resourceId)
                    .map(part -> new ResourceContext(
                            "PART",
                            part.getId(),
                            part.getPartCode(),
                            firstNonBlank(part.getPartName(), "Part"),
                            part.getIsLocked()
                    ))
                    .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Part not found"));
            case "CUSTOMER" -> customerRepository.findById(resourceId)
                    .map(customer -> new ResourceContext(
                            "CUSTOMER",
                            customer.getId(),
                            customer.getCompanyName(),
                            firstNonBlank(customer.getContactName(), customer.getCompanyName(), "Customer")
                    ))
                    .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Customer not found"));
            default -> throw new BusinessException(ResultCode.PARAM_ERROR, "Unsupported attachment resource type");
        };
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
