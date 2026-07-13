package com.example.forklift_erp.controller;

import com.example.forklift_erp.common.Result;
import com.example.forklift_erp.dto.MachineInboundPurchaseRequestDTO;
import com.example.forklift_erp.dto.MachineInboundPurchaseVO;
import com.example.forklift_erp.dto.VehicleOutboundWithCustomerRequestDTO;
import com.example.forklift_erp.dto.VehicleOutboundWithCustomerVO;
import com.example.forklift_erp.security.PermissionCodes;
import com.example.forklift_erp.service.BusinessWorkflowService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflows")
public class BusinessWorkflowController {
    @Autowired
    private BusinessWorkflowService service;

    @PostMapping("/machine-inbound-purchase")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(PermissionCodes.HAS_VEHICLE_WRITE + " and " + PermissionCodes.HAS_STOCK_ADJUST)
    public Result<MachineInboundPurchaseVO> createMachineInboundPurchase(
            @Valid @RequestBody MachineInboundPurchaseRequestDTO request
    ) {
        return Result.success("Machine and inbound order created", service.createMachineInboundPurchase(request));
    }

    @PostMapping("/vehicle-outbound-with-customer")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(PermissionCodes.HAS_VEHICLE_WRITE + " and " + PermissionCodes.HAS_STOCK_ADJUST)
    public Result<VehicleOutboundWithCustomerVO> createVehicleOutboundWithCustomer(
            @Valid @RequestBody VehicleOutboundWithCustomerRequestDTO request
    ) {
        return Result.success("Customer and vehicle outbound order created", service.createVehicleOutboundWithCustomer(request));
    }
}
