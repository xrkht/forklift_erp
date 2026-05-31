package com.example.forklift_erp.controller;

import com.example.forklift_erp.dto.ExcelExportFile;
import com.example.forklift_erp.service.ExcelExportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/export")
public class ExcelExportController {
    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    @Autowired
    private ExcelExportService excelExportService;

    @GetMapping("/{type}")
    public ResponseEntity<byte[]> export(@PathVariable String type, Authentication authentication) {
        ExcelExportFile file = excelExportService.export(type, authentication);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(file.content());
    }
}
