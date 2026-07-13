package com.example.forklift_erp.service;

import com.example.forklift_erp.common.PageResult;
import com.example.forklift_erp.dto.OutboundOrderUpdateDTO;
import com.example.forklift_erp.dto.OutboundOrderVO;
import com.example.forklift_erp.dto.OutboundInvoiceDownload;
import com.example.forklift_erp.dto.PartOutboundOrderCreateDTO;
import com.example.forklift_erp.dto.VehicleOutboundOrderCreateDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface OutboundOrderService {
    List<OutboundOrderVO> findAll();

    PageResult<OutboundOrderVO> findPage(String keyword, String stage, Integer page, Integer size);

    default PageResult<OutboundOrderVO> findPage(String keyword, Integer page, Integer size) {
        return findPage(keyword, null, page, size);
    }

    OutboundOrderVO findById(Long id);

    OutboundOrderVO createVehicleOutbound(VehicleOutboundOrderCreateDTO request);

    OutboundOrderVO createPartOutbound(PartOutboundOrderCreateDTO request);

    OutboundOrderVO update(Long id, OutboundOrderUpdateDTO request);

    OutboundOrderVO setLocked(Long id, boolean locked, Long version);

    OutboundOrderVO uploadInvoice(Long id, MultipartFile file, Long version);

    OutboundInvoiceDownload downloadInvoice(Long id);

    OutboundOrderVO uploadContract(Long id, MultipartFile file, Long version);

    OutboundInvoiceDownload downloadContract(Long id);
}
