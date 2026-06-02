package com.example.forklift_erp.controller;

import com.example.forklift_erp.common.Result;
import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.constant.RoleNames;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.service.BusinessDataResetService;
import com.example.forklift_erp.service.DataBackupService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminMaintenanceController {

    private static final String RESET_CONFIRMATION = "RESET-BUSINESS-DATA";
    private static final String RESTORE_CONFIRMATION = "RESTORE-DATA-BACKUP";
    private static final DateTimeFormatter BACKUP_FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    @Autowired
    private BusinessDataResetService businessDataResetService;

    @Autowired
    private DataBackupService dataBackupService;

    @Value("${forklift.admin.business-data-reset.enabled:false}")
    private boolean businessDataResetEnabled;

    @Value("${forklift.admin.data-restore.enabled:false}")
    private boolean dataRestoreEnabled;

    @Data
    public static class BusinessDataResetRequest {
        private String confirmation;
    }

    @PostMapping("/business-data/reset")
    @PreAuthorize("hasRole('" + RoleNames.SUPER_ADMIN + "')")
    public Result<Map<String, Long>> resetBusinessData(@RequestBody(required = false) BusinessDataResetRequest request) {
        if (!businessDataResetEnabled) {
            throw new BusinessException(ResultCode.FORBIDDEN, "Business data reset is disabled");
        }
        if (request == null || !RESET_CONFIRMATION.equals(request.getConfirmation())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Confirmation must be RESET-BUSINESS-DATA");
        }
        return Result.success("Business data reset completed", businessDataResetService.resetBusinessData());
    }

    @GetMapping("/data-backup")
    @PreAuthorize("hasRole('" + RoleNames.SUPER_ADMIN + "')")
    public ResponseEntity<byte[]> backupData() {
        byte[] payload = dataBackupService.createBackup();
        String filename = "forklift-erp-backup-" + BACKUP_FILE_TIME.format(LocalDateTime.now()) + ".json";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(payload);
    }

    @PostMapping(value = "/data-restore", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('" + RoleNames.SUPER_ADMIN + "')")
    public Result<Map<String, Long>> restoreData(@RequestParam("file") MultipartFile file,
                                                 @RequestParam(required = false) String confirmation) {
        if (!dataRestoreEnabled) {
            throw new BusinessException(ResultCode.FORBIDDEN, "Data restore is disabled");
        }
        if (!RESTORE_CONFIRMATION.equals(confirmation)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Confirmation must be RESTORE-DATA-BACKUP");
        }
        return Result.success("Data restore completed", dataBackupService.restoreBackup(file));
    }
}
