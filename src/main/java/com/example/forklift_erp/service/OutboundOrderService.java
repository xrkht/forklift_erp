package com.example.forklift_erp.service;

import com.example.forklift_erp.dto.OutboundOrderUpdateDTO;
import com.example.forklift_erp.dto.OutboundOrderVO;
import com.example.forklift_erp.dto.PartOutboundOrderCreateDTO;
import com.example.forklift_erp.dto.VehicleOutboundOrderCreateDTO;

import java.util.List;

public interface OutboundOrderService {
    List<OutboundOrderVO> findAll();

    OutboundOrderVO findById(Long id);

    OutboundOrderVO createVehicleOutbound(VehicleOutboundOrderCreateDTO request);

    OutboundOrderVO createPartOutbound(PartOutboundOrderCreateDTO request);

    OutboundOrderVO update(Long id, OutboundOrderUpdateDTO request);
}
