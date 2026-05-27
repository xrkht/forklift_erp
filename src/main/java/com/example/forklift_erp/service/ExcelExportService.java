package com.example.forklift_erp.service;

import com.example.forklift_erp.dto.ExcelExportFile;
import org.springframework.security.core.Authentication;

public interface ExcelExportService {
    ExcelExportFile export(String type, Authentication authentication);
}
