package com.example.forklift_erp.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DataImportValidationVO {
    private DataImportJobVO job;
    private boolean importable;
    private List<DataImportErrorVO> errors = new ArrayList<>();
}
