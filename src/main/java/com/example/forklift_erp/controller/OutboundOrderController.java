package com.example.forklift_erp.controller;

import com.example.forklift_erp.common.Result;
import com.example.forklift_erp.dto.OutboundInvoiceDownload;
import com.example.forklift_erp.dto.OutboundOrderUpdateDTO;
import com.example.forklift_erp.dto.OutboundOrderVO;
import com.example.forklift_erp.dto.PartOutboundOrderCreateDTO;
import com.example.forklift_erp.dto.VehicleOutboundOrderCreateDTO;
import com.example.forklift_erp.service.OutboundOrderService;
import com.example.forklift_erp.util.ListPageSupport;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/outbound-orders")
public class OutboundOrderController {

    @Autowired
    private OutboundOrderService service;

    @GetMapping
    @PreAuthorize("@permissionService.hasPermission(authentication, 'stock:adjust')")
    public Result<?> getAll(@RequestParam(defaultValue = "false") boolean paged,
                            @RequestParam(required = false) String keyword,
                            @RequestParam(required = false) Integer page,
                            @RequestParam(required = false) Integer size) {
        List<OutboundOrderVO> list = service.findAll();
        if (paged) {
            List<OutboundOrderVO> filtered = ListPageSupport.filter(list, keyword, row -> ListPageSupport.text(
                    row.getOrderNo(),
                    row.getResourceType(),
                    row.getResourceCode(),
                    row.getResourceName(),
                    row.getSpecificationModel(),
                    row.getCustomerName(),
                    row.getCustomerAddress(),
                    row.getContactName(),
                    row.getContactPhone(),
                    row.getOperator(),
                    row.getPaymentRemark(),
                    row.getInvoiceStatus(),
                    row.getInvoiceOriginalName(),
                    row.getRegistrationStatus(),
                    row.getContractType(),
                    row.getOrderRemark()
            ));
            return Result.success(ListPageSupport.page(filtered, page, size));
        }
        return Result.success(list);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'stock:adjust')")
    public Result<OutboundOrderVO> getById(@PathVariable Long id) {
        return Result.success(service.findById(id));
    }

    @PostMapping("/vehicle")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionService.hasPermission(authentication, 'stock:adjust')")
    public Result<OutboundOrderVO> createVehicleOutbound(@Valid @RequestBody VehicleOutboundOrderCreateDTO request) {
        return Result.success("整车出库订单创建成功", service.createVehicleOutbound(request));
    }

    @PostMapping("/part")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionService.hasPermission(authentication, 'stock:adjust')")
    public Result<OutboundOrderVO> createPartOutbound(@Valid @RequestBody PartOutboundOrderCreateDTO request) {
        return Result.success("配件出库订单创建成功", service.createPartOutbound(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'stock:adjust')")
    public Result<OutboundOrderVO> update(@PathVariable Long id, @RequestBody OutboundOrderUpdateDTO request) {
        return Result.success("出库订单更新成功", service.update(id, request == null ? new OutboundOrderUpdateDTO() : request));
    }

    @PutMapping("/{id}/lock")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public Result<OutboundOrderVO> setLocked(
            @PathVariable Long id,
            @RequestParam boolean locked,
            @RequestParam(required = false) Long version
    ) {
        return Result.success(locked ? "订单已锁定" : "订单已解锁", service.setLocked(id, locked, version));
    }

    @PostMapping(value = "/{id}/invoice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@permissionService.hasPermission(authentication, 'stock:adjust')")
    public Result<OutboundOrderVO> uploadInvoice(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return Result.success("发票上传成功", service.uploadInvoice(id, file));
    }

    @GetMapping("/{id}/invoice")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'stock:adjust')")
    public ResponseEntity<Resource> downloadInvoice(@PathVariable Long id) {
        OutboundInvoiceDownload invoice = service.downloadInvoice(id);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (invoice.contentType() != null && !invoice.contentType().isBlank()) {
            mediaType = MediaType.parseMediaType(invoice.contentType());
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(invoice.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(invoice.originalName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(invoice.resource());
    }
}
