package com.example.forklift_erp.dto;

import lombok.Data;

@Data
public class ModificationWorkOrderActionDTO {
    private Long version;
    private String operator;
    private String remark;
}
