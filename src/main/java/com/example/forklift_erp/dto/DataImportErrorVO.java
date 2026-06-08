package com.example.forklift_erp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataImportErrorVO {
    private String sheetName;
    private Integer rowNumber;
    private String fieldName;
    private String message;
    private String value;
}
