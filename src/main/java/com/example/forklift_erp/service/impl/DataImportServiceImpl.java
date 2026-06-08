package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.common.PageResult;
import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.dto.CustomerDTO;
import com.example.forklift_erp.dto.CustomerVO;
import com.example.forklift_erp.dto.DataImportErrorVO;
import com.example.forklift_erp.dto.DataImportJobVO;
import com.example.forklift_erp.dto.DataImportTemplateFile;
import com.example.forklift_erp.dto.DataImportValidationVO;
import com.example.forklift_erp.dto.MachineInventoryCreateDTO;
import com.example.forklift_erp.dto.MachineInventoryVO;
import com.example.forklift_erp.dto.OutboundOrderVO;
import com.example.forklift_erp.dto.PartInventoryCreateDTO;
import com.example.forklift_erp.dto.PartInventoryVO;
import com.example.forklift_erp.dto.VehicleOutboundOrderCreateDTO;
import com.example.forklift_erp.entity.DataImportJob;
import com.example.forklift_erp.entity.MachineInventory;
import com.example.forklift_erp.entity.OutboundOrder;
import com.example.forklift_erp.entity.PartInventory;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.repository.DataImportJobRepository;
import com.example.forklift_erp.service.CustomerService;
import com.example.forklift_erp.service.DataImportService;
import com.example.forklift_erp.service.MachineInventoryService;
import com.example.forklift_erp.service.OutboundOrderService;
import com.example.forklift_erp.service.PartInventoryService;
import com.example.forklift_erp.util.SecurityUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class DataImportServiceImpl implements DataImportService {
    private static final Logger log = LoggerFactory.getLogger(DataImportServiceImpl.class);
    private static final long MAX_IMPORT_FILE_SIZE = 20L * 1024 * 1024;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> VEHICLE_IMPORT_TYPES = Set.of("VEHICLE", "VEHICLE-WORKBOOK", "FULL", "FULL-WORKBOOK");
    private static final Set<String> PART_IMPORT_TYPES = Set.of("PART", "PARTS", "PARTS-PURCHASE", "PARTS-PURCHASE-WORKBOOK");

    @Value("${forklift-erp.import-storage-dir:${FORKLIFT_ERP_IMPORT_STORAGE_DIR:uploads/imports}}")
    private String importStorageDir;

    @Autowired
    private DataImportJobRepository jobRepository;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private MachineInventoryService machineInventoryService;

    @Autowired
    private OutboundOrderService outboundOrderService;

    @Autowired
    private PartInventoryService partInventoryService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResult<DataImportJobVO> findPage(String importType, String keyword, Integer page, Integer size) {
        int pageNumber = normalizePage(page);
        int pageSize = normalizeSize(size);
        Page<DataImportJob> result = jobRepository.search(trimToNull(importType), trimToNull(keyword),
                PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return PageResult.of(result.getContent().stream().map(DataImportJobVO::fromEntity).toList(),
                pageNumber, pageSize, result.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public DataImportJobVO findById(Long id) {
        return DataImportJobVO.fromEntity(findJobOrThrow(id));
    }

    @Override
    public DataImportTemplateFile template(String importType) {
        String normalizedType = normalizeImportType(importType);
        if (VEHICLE_IMPORT_TYPES.contains(normalizedType)) {
            return vehicleTemplate();
        }
        if (PART_IMPORT_TYPES.contains(normalizedType)) {
            return partTemplate();
        }
        throw new BusinessException(ResultCode.PARAM_ERROR, "Unsupported import type");
    }

    @Override
    @Transactional
    public DataImportValidationVO validate(String importType, MultipartFile file) {
        ImportProfile profile = profile(importType);
        DataImportJob job = createJob(profile, file);
        Path stagedFile = storeImportFile(file, job.getId(), profile.code());
        registerRollbackCleanup(stagedFile);
        job.setStagedFileName(stagedFile.getFileName().toString());

        ValidationResult validation = profile.validate(stagedFile);
        job.setTotalRows(validation.totalRows());
        job.setValidRows(validation.validRows());
        job.setErrorRows(validation.errors().size());
        job.setSummary(validation.summary());
        job.setErrorRowsJson(writeJson(validation.errors()));
        job.setValidationSnapshotJson(writeJson(validation.snapshot()));
        job.setStatus(validation.importable() ? "READY" : "VALIDATION_FAILED");
        jobRepository.save(job);
        return toValidationVO(job, validation.errors(), validation.importable());
    }

    @Override
    @Transactional
    public DataImportValidationVO confirm(Long jobId) {
        DataImportJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Import job not found"));
        if (!"READY".equals(job.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Import job is not ready");
        }

        ImportProfile profile = profile(job.getImportType());
        Path stagedFile = importStorageRoot().resolve(job.getStagedFileName()).normalize();
        if (!Files.isRegularFile(stagedFile)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Import file not found");
        }

        job.setStatus("IMPORTING");
        job.setStartedAt(LocalDateTime.now());
        jobRepository.save(job);

        try {
            ImportResult result = profile.importFile(stagedFile);
            job.setStatus("COMPLETED");
            job.setImportedRows(result.importedRows());
            job.setSkippedRows(result.skippedRows());
            job.setSummary(result.summary());
            job.setImportedBy(SecurityUtils.currentUsername());
            job.setFinishedAt(LocalDateTime.now());
            jobRepository.save(job);
            return toValidationVO(job, List.of(), true);
        } catch (RuntimeException ex) {
            job.setStatus("FAILED");
            job.setSummary(firstNonBlank(ex.getMessage(), "Import failed"));
            job.setImportedBy(SecurityUtils.currentUsername());
            job.setFinishedAt(LocalDateTime.now());
            jobRepository.save(job);
            throw ex;
        }
    }

    private DataImportJob createJob(ImportProfile profile, MultipartFile file) {
        ensureImportFile(file);
        DataImportJob job = new DataImportJob();
        job.setImportType(profile.code());
        job.setTemplateName(profile.templateName());
        job.setOriginalFileName(cleanOriginalName(file.getOriginalFilename(), profile.templateName() + ".xlsx"));
        job.setStatus("VALIDATING");
        job.setCreatedBy(SecurityUtils.currentUsername());
        return jobRepository.save(job);
    }

    private DataImportValidationVO toValidationVO(DataImportJob job, List<DataImportErrorVO> errors, boolean importable) {
        DataImportValidationVO vo = new DataImportValidationVO();
        vo.setJob(DataImportJobVO.fromEntity(job));
        vo.setErrors(errors);
        vo.setImportable(importable);
        return vo;
    }

    private DataImportTemplateFile vehicleTemplate() {
        try (Workbook workbook = new XSSFWorkbook()) {
            createSheet(workbook, "Inbound", List.of(
                    "ID", "Inbound Date", "Supplier", "Application No", "Name", "Material No", "Model",
                    "Configuration", "Vehicle No", "Engine No", "Frame No", "Warranty Card", "Manufacturing Date",
                    "Purchase Price", "Remarks", "Sales Reported", "Sales Report Date", "Invoice Applied",
                    "Inventory Count", "Destination 1", "Destination 2", "Destination 3", "Destination 4", "Destination 5",
                    "Warehouse", "Invoice Applied Flag", "Extra Remarks"
            ));
            createSheet(workbook, "Sales", List.of(
                    "ID", "Sales Date", "Name", "Brand", "Configuration", "Vehicle No", "Engine No", "Frame No",
                    "Warranty Card", "Settlement Price", "Sales Note", "Sale Price", "Payment Note", "Payment Settled",
                    "Customer Name", "Customer Address", "Contact Name", "Contact Phone", "Tax ID", "Invoice Status",
                    "Invoice Issued Date", "Sales Reported", "Sales Report Date", "Registration Status",
                    "Invoice Applied", "Invoice Applied Date", "Order Remark", "Extra Remark", "Contract Type"
            ));
            createSheet(workbook, "AutoReport", List.of("Auto report sheet", "unused"));
            createSheet(workbook, "OtherBrandSales", List.of(
                    "ID", "Sales Date", "Name", "Brand", "Model", "Configuration", "Vehicle No", "Engine No",
                    "Frame No", "Warranty Card", "Settlement Price", "Payment Note", "Payment Settled",
                    "Customer Name", "Customer Address", "Contact Name", "Contact Phone", "Tax ID", "Invoice Status",
                    "Invoice Issued Date", "Remark"
            ));
            createSheet(workbook, "OldInbound", List.of(
                    "ID", "Inbound Date", "Note", "Name", "Model", "Configuration", "Vehicle No", "Engine No",
                    "Sale Price", "Frame No", "Manufacturing Date", "Remark", "Warehouse Remark", "Inventory Count",
                    "Destination 1", "Destination 2", "Destination 3", "Destination 4", "Destination 5", "Extra Remark"
            ));
            createSheet(workbook, "OldSales", List.of(
                    "ID", "Sales Date", "Name", "Model", "Configuration", "Vehicle No", "Engine No", "Frame No",
                    "Warranty Card", "Sale Price", "Quantity", "Settlement Price", "Payment Note", "Customer Name",
                    "Customer Address", "Contact Name", "Contact Phone", "Tax ID", "Invoice Status",
                    "Invoice Issued Date", "Remark", "Brand"
            ));
            return new DataImportTemplateFile("vehicle-workbook-template.xlsx", toBytes(workbook));
        } catch (IOException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "Template generation failed");
        }
    }

    private DataImportTemplateFile partTemplate() {
        try (Workbook workbook = new XSSFWorkbook()) {
            createSheet(workbook, "Parts", List.of(
                    "Inbound Date", "Order No", "Document Type", "Document Name", "Part Name", "Specification",
                    "Unit", "Quantity", "Unit Price", "Warehouse", "Note", "Remark", "Source"
            ));
            return new DataImportTemplateFile("parts-purchase-template.xlsx", toBytes(workbook));
        } catch (IOException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "Template generation failed");
        }
    }

    private byte[] toBytes(Workbook workbook) throws IOException {
        try (workbook; var out = new java.io.ByteArrayOutputStream()) {
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void createSheet(Workbook workbook, String sheetName, List<String> headers) {
        Sheet sheet = workbook.createSheet(sheetName);
        var headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            headerRow.createCell(i).setCellValue(headers.get(i));
            sheet.setColumnWidth(i, 18 * 256);
        }
    }

    private ImportProfile profile(String importType) {
        String normalizedType = normalizeImportType(importType);
        if (VEHICLE_IMPORT_TYPES.contains(normalizedType)) {
            return new VehicleWorkbookProfile();
        }
        if (PART_IMPORT_TYPES.contains(normalizedType)) {
            return new PartsPurchaseProfile();
        }
        throw new BusinessException(ResultCode.PARAM_ERROR, "Unsupported import type");
    }

    private DataImportJob findJobOrThrow(Long jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Import job not found"));
    }

    private Path storeImportFile(MultipartFile file, Long jobId, String type) {
        ensureImportFile(file);
        Path root = importStorageRoot();
        String originalName = cleanOriginalName(file.getOriginalFilename(), "import.xlsx");
        String extension = StringUtils.getFilenameExtension(originalName);
        if (extension == null || !Set.of("xlsx", "xls").contains(extension.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Only Excel files are supported");
        }
        if (file.getSize() > MAX_IMPORT_FILE_SIZE) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Import file cannot exceed 20MB");
        }

        String storedFileName = "import-" + type.toLowerCase(Locale.ROOT) + "-" + jobId + "-" + UUID.randomUUID().toString().replace("-", "") + "." + extension.toLowerCase(Locale.ROOT);
        Path target = root.resolve(storedFileName).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Import path is invalid");
        }

        try {
            Files.createDirectories(root);
            Path tempFile = Files.createTempFile(root, storedFileName + "-", ".tmp");
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            moveIntoPlace(tempFile, target);
            return target;
        } catch (IOException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "Import file save failed");
        }
    }

    private void moveIntoPlace(Path tempFile, Path target) throws IOException {
        try {
            Files.move(tempFile, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tempFile, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path importStorageRoot() {
        return Paths.get(importStorageDir).toAbsolutePath().normalize();
    }

    private void registerRollbackCleanup(Path file) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    deleteQuietly(file);
                }
            }
        });
    }

    private void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Failed to delete import file: {}", file, e);
        }
    }

    private void ensureImportFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请选择导入文件");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String normalizeImportType(String importType) {
        String normalized = trimToNull(importType);
        return normalized == null ? "" : normalized.toUpperCase(Locale.ROOT).replace("_", "-");
    }

    private int normalizePage(Integer page) {
        return Math.max(0, page == null ? 0 : page);
    }

    private int normalizeSize(Integer size) {
        return Math.max(1, size == null ? 20 : size);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String cleanOriginalName(String originalFilename, String fallbackName) {
        String originalName = StringUtils.cleanPath(Objects.requireNonNullElse(originalFilename, fallbackName)).trim();
        if (originalName.isBlank() || originalName.contains("..") || originalName.contains("/") || originalName.contains("\\")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Import file name is invalid");
        }
        return originalName;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private interface ImportProfile {
        String code();

        String templateName();

        ValidationResult validate(Path file);

        ImportResult importFile(Path file);
    }

    private final class VehicleWorkbookProfile implements ImportProfile {
        @Override
        public String code() {
            return "vehicle-workbook";
        }

        @Override
        public String templateName() {
            return "vehicle-workbook";
        }

        @Override
        public ValidationResult validate(Path file) {
            WorkbookSnapshot snapshot = readVehicleWorkbook(file);
            List<DataImportErrorVO> errors = new ArrayList<>();
            validateVehicleRows(snapshot, errors);
            int totalRows = snapshot.totalRows();
            int validRows = Math.max(0, totalRows - errors.size());
            return new ValidationResult(totalRows, validRows, errors, errors.isEmpty(),
                    errors.isEmpty() ? "Workbook validated successfully" : "Workbook validation found " + errors.size() + " row issues",
                    Map.of("sheets", snapshot.sheetSizes()));
        }

        @Override
        public ImportResult importFile(Path file) {
            WorkbookSnapshot snapshot = readVehicleWorkbook(file);
            return importVehicleWorkbook(snapshot);
        }
    }

    private final class PartsPurchaseProfile implements ImportProfile {
        @Override
        public String code() {
            return "parts-purchase";
        }

        @Override
        public String templateName() {
            return "parts-purchase";
        }

        @Override
        public ValidationResult validate(Path file) {
            WorkbookSnapshot snapshot = readPartsWorkbook(file);
            List<DataImportErrorVO> errors = new ArrayList<>();
            validatePartRows(snapshot, errors);
            int totalRows = snapshot.sheetRows("Parts").size();
            int validRows = Math.max(0, totalRows - errors.size());
            return new ValidationResult(totalRows, validRows, errors, errors.isEmpty(),
                    errors.isEmpty() ? "Workbook validated successfully" : "Workbook validation found " + errors.size() + " row issues",
                    Map.of("sheet", "Parts", "rows", totalRows));
        }

        @Override
        public ImportResult importFile(Path file) {
            WorkbookSnapshot snapshot = readPartsWorkbook(file);
            return importPartsWorkbook(snapshot);
        }
    }

    private WorkbookSnapshot readVehicleWorkbook(Path file) {
        try (InputStream inputStream = Files.newInputStream(file);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            DataFormatter formatter = new DataFormatter(Locale.CHINA);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Map<String, List<WorkbookRow>> sheets = new LinkedHashMap<>();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                sheets.put(sheet.getSheetName(), readRows(sheet, formatter, evaluator));
            }
            return new WorkbookSnapshot(sheets);
        } catch (IOException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "Failed to read workbook");
        }
    }

    private WorkbookSnapshot readPartsWorkbook(Path file) {
        try (InputStream inputStream = Files.newInputStream(file);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            DataFormatter formatter = new DataFormatter(Locale.CHINA);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Map<String, List<WorkbookRow>> sheets = new LinkedHashMap<>();
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet != null) {
                sheets.put(sheet.getSheetName(), readRows(sheet, formatter, evaluator));
            }
            return new WorkbookSnapshot(sheets);
        } catch (IOException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "Failed to read workbook");
        }
    }

    private List<WorkbookRow> readRows(Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator) {
        List<WorkbookRow> rows = new ArrayList<>();
        if (sheet == null) {
            return rows;
        }
        int firstDataRow = Math.max(sheet.getFirstRowNum() + 1, 1);
        for (int rowIndex = firstDataRow; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            List<String> values = new ArrayList<>();
            boolean hasValue = false;
            for (int cellIndex = 0; cellIndex < Math.max(row.getLastCellNum(), 0); cellIndex++) {
                Cell cell = row.getCell(cellIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                String value = cell == null ? null : formatter.formatCellValue(cell, evaluator).trim();
                if (value != null && !value.isBlank()) {
                    hasValue = true;
                }
                values.add(trimToNull(value));
            }
            if (hasValue) {
                rows.add(new WorkbookRow(rowIndex + 1, values));
            }
        }
        return rows;
    }

    private void validateVehicleRows(WorkbookSnapshot snapshot, List<DataImportErrorVO> errors) {
        validateVehicleSheet(snapshot.sheetRows("Inbound"), "Inbound", 8, 14, 1, errors);
        validateVehicleSheet(snapshot.sheetRows("Sales"), "Sales", 5, 14, 1, errors);
        validateVehicleSheet(snapshot.sheetRows("OtherBrandSales"), "OtherBrandSales", 6, 13, 1, errors);
        validateVehicleSheet(snapshot.sheetRows("OldInbound"), "OldInbound", 6, -1, 1, errors);
        validateVehicleSheet(snapshot.sheetRows("OldSales"), "OldSales", 5, 13, 1, errors);
    }

    private void validateVehicleSheet(List<WorkbookRow> rows, String sheetName, int vehicleColumn, int customerColumn, int minimumColumns,
                                      List<DataImportErrorVO> errors) {
        Set<String> seenVehicles = new HashSet<>();
        for (WorkbookRow row : rows) {
            String vehicleNumber = text(row, vehicleColumn);
            if (vehicleNumber == null) {
                errors.add(new DataImportErrorVO(sheetName, row.rowNumber(), "vehicleNumber", "Vehicle number is required", null));
                continue;
            }
            if (!seenVehicles.add(vehicleNumber)) {
                errors.add(new DataImportErrorVO(sheetName, row.rowNumber(), "vehicleNumber", "Duplicate vehicle number in sheet", vehicleNumber));
            }
            if (customerColumn >= 0 && text(row, customerColumn) == null) {
                errors.add(new DataImportErrorVO(sheetName, row.rowNumber(), "customerName", "Customer name is required", null));
            }
            if (minimumColumns > 0 && row.values().size() < minimumColumns) {
                errors.add(new DataImportErrorVO(sheetName, row.rowNumber(), "columns", "Sheet row is incomplete", null));
            }
        }
    }

    private void validatePartRows(WorkbookSnapshot snapshot, List<DataImportErrorVO> errors) {
        for (WorkbookRow row : snapshot.sheetRows("Parts")) {
            if (text(row, 1) == null) {
                errors.add(new DataImportErrorVO("Parts", row.rowNumber(), "partCode", "Part code is required", null));
            }
            if (text(row, 4) == null) {
                errors.add(new DataImportErrorVO("Parts", row.rowNumber(), "partName", "Part name is required", null));
            }
            if (decimal(row, 7) == null) {
                errors.add(new DataImportErrorVO("Parts", row.rowNumber(), "quantity", "Quantity is required", null));
            }
        }
    }

    private ImportResult importVehicleWorkbook(WorkbookSnapshot snapshot) {
        Map<String, MachineInventory> machinesByNumber = machineInventoryService.findAll().stream()
                .filter(machine -> hasText(machine.getVehicleProductNumber()))
                .collect(Collectors.toMap(MachineInventory::getVehicleProductNumber, machine -> machine, (left, right) -> left, LinkedHashMap::new));
        Map<String, CustomerVO> customersByName = customerService.findAll().stream()
                .filter(customer -> hasText(customer.getCompanyName()))
                .collect(Collectors.toMap(CustomerVO::getCompanyName, customer -> customer, (left, right) -> left, LinkedHashMap::new));
        Set<String> orderVehicleNumbers = outboundOrderService.findAll().stream()
                .filter(order -> "MACHINE".equals(order.getResourceType()) && hasText(order.getResourceCode()))
                .map(OutboundOrderVO::getResourceCode)
                .collect(Collectors.toCollection(HashSet::new));

        Map<String, WorkbookRow> inboundByVehicle = indexByVehicle(snapshot.sheetRows("Inbound"), 8);
        Map<String, WorkbookRow> oldInboundByVehicle = indexByVehicle(snapshot.sheetRows("OldInbound"), 6);

        int importedCustomers = 0;
        int importedMachines = 0;
        int importedOrders = 0;
        int skippedRows = 0;

        for (WorkbookRow salesRow : snapshot.sheetRows("Sales")) {
            String vehicleNumber = cleanVehicleNumber(text(salesRow, 5));
            String customerName = text(salesRow, 14);
            if (!hasText(vehicleNumber) || !hasText(customerName)) {
                skippedRows++;
                continue;
            }
            WorkbookRow inboundRow = inboundByVehicle.get(vehicleNumber);
            MachineInventory machine = null;
            if (inboundRow != null) {
                machine = upsertMachineFromInboundRow(inboundRow, vehicleNumber, machinesByNumber);
            } else {
                machine = upsertMachineFromSalesRow(salesRow, vehicleNumber, machinesByNumber);
            }
            CustomerVO customer = upsertCustomer(customerName, salesRow, customersByName);
            if (!orderVehicleNumbers.contains(vehicleNumber) && machine.getInventoryCount() != null && machine.getInventoryCount() > 0) {
                VehicleOutboundOrderCreateDTO order = buildVehicleOutboundPayload(machine, customer, salesRow);
                outboundOrderService.createVehicleOutbound(order);
                orderVehicleNumbers.add(vehicleNumber);
                importedOrders++;
            }
            importedMachines++;
            if (customer.getId() != null) {
                importedCustomers++;
            }
        }

        for (WorkbookRow inboundRow : snapshot.sheetRows("Inbound")) {
            String vehicleNumber = cleanVehicleNumber(text(inboundRow, 8));
            if (!hasText(vehicleNumber) || machinesByNumber.containsKey(vehicleNumber)) {
                continue;
            }
            upsertMachineFromInboundRow(inboundRow, vehicleNumber, machinesByNumber);
            importedMachines++;
        }

        for (WorkbookRow otherBrandRow : snapshot.sheetRows("OtherBrandSales")) {
            String vehicleNumber = cleanVehicleNumber(text(otherBrandRow, 6));
            if (!hasText(vehicleNumber)) {
                vehicleNumber = generatedVehicleNumber("OTHER-SALE", otherBrandRow.rowNumber(), otherBrandRow);
            }
            if (machinesByNumber.containsKey(vehicleNumber)) {
                continue;
            }
            MachineInventory machine = upsertMachineFromOtherBrandRow(otherBrandRow, vehicleNumber, machinesByNumber);
            CustomerVO customer = upsertCustomer(firstNonBlank(text(otherBrandRow, 13), "Other-brand customer"), otherBrandRow, customersByName);
            if (!orderVehicleNumbers.contains(vehicleNumber)) {
                outboundOrderService.createVehicleOutbound(buildOtherBrandOutboundPayload(machine, customer, otherBrandRow));
                orderVehicleNumbers.add(vehicleNumber);
                importedOrders++;
            }
            importedMachines++;
            importedCustomers++;
        }

        for (WorkbookRow oldSalesRow : snapshot.sheetRows("OldSales")) {
            String vehicleNumber = cleanVehicleNumber(text(oldSalesRow, 5));
            if (!hasText(vehicleNumber)) {
                vehicleNumber = generatedVehicleNumber("OLD-SALE", oldSalesRow.rowNumber(), oldSalesRow);
            }
            if (machinesByNumber.containsKey(vehicleNumber)) {
                continue;
            }
            WorkbookRow oldInboundRow = oldInboundByVehicle.get(vehicleNumber);
            MachineInventory machine = oldInboundRow != null
                    ? upsertMachineFromOldInboundRow(oldInboundRow, vehicleNumber, machinesByNumber)
                    : upsertMachineFromOldSalesRow(oldSalesRow, vehicleNumber, machinesByNumber);
            CustomerVO customer = upsertCustomer(firstNonBlank(text(oldSalesRow, 13), "Used vehicle customer"), oldSalesRow, customersByName);
            if (!orderVehicleNumbers.contains(vehicleNumber)) {
                outboundOrderService.createVehicleOutbound(buildOldSalesOutboundPayload(machine, customer, oldSalesRow));
                orderVehicleNumbers.add(vehicleNumber);
                importedOrders++;
            }
            importedMachines++;
            importedCustomers++;
        }

        for (WorkbookRow oldInboundRow : snapshot.sheetRows("OldInbound")) {
            String vehicleNumber = cleanVehicleNumber(text(oldInboundRow, 6));
            if (!hasText(vehicleNumber) || machinesByNumber.containsKey(vehicleNumber)) {
                continue;
            }
            upsertMachineFromOldInboundRow(oldInboundRow, vehicleNumber, machinesByNumber);
            importedMachines++;
        }

        return new ImportResult(
                importedCustomers + importedMachines + importedOrders,
                skippedRows,
                "Imported customers=" + importedCustomers + ", machines=" + importedMachines + ", orders=" + importedOrders
        );
    }

    private ImportResult importPartsWorkbook(WorkbookSnapshot snapshot) {
        List<WorkbookRow> rows = snapshot.sheetRows("Parts");
        Map<String, List<WorkbookRow>> grouped = new LinkedHashMap<>();
        for (WorkbookRow row : rows) {
            String code = text(row, 1);
            if (!hasText(code)) {
                continue;
            }
            grouped.computeIfAbsent(code, key -> new ArrayList<>()).add(row);
        }
        int created = 0;
        int updated = 0;
        int reused = 0;
        for (Map.Entry<String, List<WorkbookRow>> entry : grouped.entrySet()) {
            String code = entry.getKey();
            List<WorkbookRow> group = entry.getValue();
            WorkbookRow latestRow = group.stream().max(Comparator.comparingInt(WorkbookRow::rowNumber)).orElse(group.get(0));
            PartInventoryCreateDTO dto = buildPartDto(group, latestRow);
            Optional<PartInventory> existing = partInventoryService.findByPartCode(code);
            if (existing.isEmpty()) {
                partInventoryService.create(dto);
                created++;
                continue;
            }
            PartInventory current = existing.get();
            dto.setVersion(current.getVersion());
            if (partChanged(current, dto)) {
                partInventoryService.update(current.getId(), dto);
                updated++;
            } else {
                reused++;
            }
        }
        return new ImportResult(created + updated + reused, 0,
                "Imported parts created=" + created + ", updated=" + updated + ", reused=" + reused);
    }

    private boolean partChanged(PartInventory current, PartInventoryCreateDTO dto) {
        return !Objects.equals(trimToNull(current.getPartBrand()), trimToNull(dto.getPartBrand()))
                || !Objects.equals(trimToNull(current.getPartName()), trimToNull(dto.getPartName()))
                || !Objects.equals(trimToNull(current.getSpecification()), trimToNull(dto.getSpecification()))
                || !Objects.equals(trimToNull(current.getPartCategory()), trimToNull(dto.getPartCategory()))
                || !Objects.equals(trimToNull(current.getApplicableModels()), trimToNull(dto.getApplicableModels()))
                || !Objects.equals(trimToNull(current.getSource()), trimToNull(dto.getSource()))
                || !Objects.equals(current.getQuantity(), dto.getQuantity())
                || !Objects.equals(trimToNull(current.getUnit()), trimToNull(dto.getUnit()))
                || !Objects.equals(current.getPurchasePrice(), dto.getPurchasePrice())
                || !Objects.equals(current.getSettlementPrice(), dto.getSettlementPrice())
                || !Objects.equals(trimToNull(current.getRemarks()), trimToNull(dto.getRemarks()));
    }

    private MachineInventory upsertMachineFromInboundRow(WorkbookRow row, String vehicleNumber, Map<String, MachineInventory> machinesByNumber) {
        MachineInventoryCreateDTO dto = buildMachineFromInboundRow(row, vehicleNumber);
        MachineInventory existing = machinesByNumber.get(vehicleNumber);
        if (existing == null) {
            MachineInventoryVO created = machineInventoryService.create(dto);
            MachineInventory machine = machineInventoryService.findById(created.getId()).orElseThrow();
            machinesByNumber.put(vehicleNumber, machine);
            return machine;
        }
        dto.setVersion(existing.getVersion());
        machineInventoryService.update(existing.getId(), dto);
        MachineInventory machine = machineInventoryService.findById(existing.getId()).orElseThrow();
        machinesByNumber.put(vehicleNumber, machine);
        return machine;
    }

    private MachineInventory upsertMachineFromSalesRow(WorkbookRow row, String vehicleNumber, Map<String, MachineInventory> machinesByNumber) {
        MachineInventoryCreateDTO dto = buildMachineFromSalesRow(row, vehicleNumber, "Workbook sales");
        MachineInventory existing = machinesByNumber.get(vehicleNumber);
        if (existing == null) {
            MachineInventoryVO created = machineInventoryService.create(dto);
            MachineInventory machine = machineInventoryService.findById(created.getId()).orElseThrow();
            machinesByNumber.put(vehicleNumber, machine);
            return machine;
        }
        machinesByNumber.put(vehicleNumber, existing);
        return existing;
    }

    private MachineInventory upsertMachineFromOtherBrandRow(WorkbookRow row, String vehicleNumber, Map<String, MachineInventory> machinesByNumber) {
        MachineInventoryCreateDTO dto = buildMachineFromOtherBrandRow(row, vehicleNumber);
        MachineInventory existing = machinesByNumber.get(vehicleNumber);
        if (existing == null) {
            MachineInventoryVO created = machineInventoryService.create(dto);
            MachineInventory machine = machineInventoryService.findById(created.getId()).orElseThrow();
            machinesByNumber.put(vehicleNumber, machine);
            return machine;
        }
        machinesByNumber.put(vehicleNumber, existing);
        return existing;
    }

    private MachineInventory upsertMachineFromOldInboundRow(WorkbookRow row, String vehicleNumber, Map<String, MachineInventory> machinesByNumber) {
        MachineInventoryCreateDTO dto = buildMachineFromOldInboundRow(row, vehicleNumber);
        MachineInventory existing = machinesByNumber.get(vehicleNumber);
        if (existing == null) {
            MachineInventoryVO created = machineInventoryService.create(dto);
            MachineInventory machine = machineInventoryService.findById(created.getId()).orElseThrow();
            machinesByNumber.put(vehicleNumber, machine);
            return machine;
        }
        machinesByNumber.put(vehicleNumber, existing);
        return existing;
    }

    private MachineInventory upsertMachineFromOldSalesRow(WorkbookRow row, String vehicleNumber, Map<String, MachineInventory> machinesByNumber) {
        MachineInventoryCreateDTO dto = buildMachineFromOldSalesRow(row, vehicleNumber);
        MachineInventory existing = machinesByNumber.get(vehicleNumber);
        if (existing == null) {
            MachineInventoryVO created = machineInventoryService.create(dto);
            MachineInventory machine = machineInventoryService.findById(created.getId()).orElseThrow();
            machinesByNumber.put(vehicleNumber, machine);
            return machine;
        }
        machinesByNumber.put(vehicleNumber, existing);
        return existing;
    }

    private CustomerVO upsertCustomer(String companyName, WorkbookRow row, Map<String, CustomerVO> customersByName) {
        String name = trimToNull(companyName);
        if (name == null) {
            name = "Workbook customer";
        }
        CustomerVO existing = customersByName.get(name);
        CustomerDTO dto = buildCustomerFromRow(name, row);
        if (existing == null) {
            CustomerVO created = customerService.create(dto);
            customersByName.put(name, created);
            return created;
        }
        dto.setVersion(existing.getVersion());
        if (customerChanged(existing, dto)) {
            CustomerVO updated = customerService.update(existing.getId(), dto);
            customersByName.put(name, updated);
            return updated;
        }
        return existing;
    }

    private CustomerDTO buildCustomerFromRow(String companyName, WorkbookRow row) {
        CustomerDTO dto = new CustomerDTO();
        dto.setCompanyName(companyName);
        dto.setAddress(firstNonBlank(text(row, 15), text(row, 16), text(row, 20), text(row, 26)));
        dto.setContactName(firstNonBlank(text(row, 16), text(row, 17)));
        dto.setContactPhone(firstNonBlank(text(row, 17), text(row, 18), text(row, 23)));
        dto.setTaxOrIdNumber(text(row, 18));
        dto.setRemarks(trimToLimit(joinNotes("Workbook import", text(row, 10), text(row, 20), text(row, 26), text(row, 27)), 255));
        return dto;
    }

    private boolean customerChanged(CustomerVO existing, CustomerDTO dto) {
        return !Objects.equals(trimToNull(existing.getAddress()), trimToNull(dto.getAddress()))
                || !Objects.equals(trimToNull(existing.getContactName()), trimToNull(dto.getContactName()))
                || !Objects.equals(trimToNull(existing.getContactPhone()), trimToNull(dto.getContactPhone()))
                || !Objects.equals(trimToNull(existing.getTaxOrIdNumber()), trimToNull(dto.getTaxOrIdNumber()))
                || !Objects.equals(trimToNull(existing.getRemarks()), trimToNull(dto.getRemarks()));
    }

    private VehicleOutboundOrderCreateDTO buildVehicleOutboundPayload(MachineInventory machine, CustomerVO customer, WorkbookRow row) {
        VehicleOutboundOrderCreateDTO dto = new VehicleOutboundOrderCreateDTO();
        dto.setMachineId(machine.getId());
        dto.setMachineVersion(machine.getVersion());
        dto.setCustomerId(customer.getId());
        dto.setSalesDate(date(row, 1));
        dto.setSettlementPrice(decimal(row, 9));
        dto.setSalePrice(decimal(row, 11));
        dto.setPaymentSettled(parseBool(text(row, 13)));
        dto.setPaymentRemark(text(row, 12));
        dto.setSalesReported(parseBool(text(row, 21)));
        dto.setSalesReportDate(date(row, 22));
        dto.setInvoiceApplied(parseBool(text(row, 24)));
        dto.setInvoiceApplicationDate(date(row, 20));
        dto.setInvoiceStatus(text(row, 19));
        dto.setInvoiceIssuedDate(date(row, 20));
        dto.setRegistrationStatus(text(row, 25));
        dto.setContractType(text(row, 28));
        dto.setOperator("import-workbook");
        dto.setOrderRemark(joinNotes(text(row, 10), text(row, 26), text(row, 27)));
        return dto;
    }

    private VehicleOutboundOrderCreateDTO buildOtherBrandOutboundPayload(MachineInventory machine, CustomerVO customer, WorkbookRow row) {
        VehicleOutboundOrderCreateDTO dto = new VehicleOutboundOrderCreateDTO();
        dto.setMachineId(machine.getId());
        dto.setMachineVersion(machine.getVersion());
        dto.setCustomerId(customer.getId());
        dto.setSalesDate(date(row, 1));
        dto.setSettlementPrice(decimal(row, 10));
        dto.setSalePrice(decimal(row, 10));
        dto.setPaymentSettled(parseBool(text(row, 12)));
        dto.setPaymentRemark(text(row, 11));
        dto.setInvoiceStatus(text(row, 18));
        dto.setInvoiceIssuedDate(date(row, 19));
        dto.setOperator("import-workbook");
        dto.setOrderRemark(joinNotes("Other brand sales", text(row, 20)));
        return dto;
    }

    private VehicleOutboundOrderCreateDTO buildOldSalesOutboundPayload(MachineInventory machine, CustomerVO customer, WorkbookRow row) {
        VehicleOutboundOrderCreateDTO dto = new VehicleOutboundOrderCreateDTO();
        dto.setMachineId(machine.getId());
        dto.setMachineVersion(machine.getVersion());
        dto.setCustomerId(customer.getId());
        dto.setSalesDate(date(row, 1));
        dto.setSettlementPrice(decimal(row, 11));
        dto.setSalePrice(decimal(row, 11));
        dto.setInvoiceStatus(text(row, 18));
        dto.setInvoiceIssuedDate(date(row, 19));
        dto.setOperator("import-workbook");
        dto.setOrderRemark(joinNotes("Used vehicle sales", text(row, 20)));
        return dto;
    }

    private MachineInventoryCreateDTO buildMachineFromInboundRow(WorkbookRow row, String vehicleNumber) {
        MachineInventoryCreateDTO dto = new MachineInventoryCreateDTO();
        dto.setVehicleProductNumber(vehicleNumber);
        dto.setName(firstNonBlank(text(row, 4), "Workbook vehicle"));
        dto.setSpecificationModel(firstNonBlank(text(row, 6), "Workbook model"));
        dto.setConfiguration(text(row, 7));
        dto.setMachineType(firstNonBlank(text(row, 6), text(row, 4), "Internal combustion forklift"));
        dto.setSupplier(text(row, 2));
        dto.setWarehouseName(firstNonBlank(text(row, 24), "Workbook warehouse"));
        dto.setApplicationNumber(text(row, 3));
        dto.setMaterialNumber(text(row, 5));
        dto.setEngineNumber(text(row, 9));
        dto.setFrameNumber(text(row, 10));
        dto.setWarrantyCardNumber(text(row, 11));
        dto.setManufacturingDate(date(row, 12));
        dto.setInboundDate(dateTime(row, 1));
        dto.setPurchasePrice(decimal(row, 13));
        dto.setSettlementPrice(decimal(row, 13));
        dto.setSalePrice(decimal(row, 13));
        dto.setInventoryCount(intValue(row, 18, 1));
        dto.setDestination1(text(row, 19));
        dto.setDestination2(text(row, 20));
        dto.setDestination3(text(row, 21));
        dto.setDestination4(text(row, 22));
        dto.setDestination5(text(row, 23));
        dto.setIsSalesReported(text(row, 15));
        dto.setSalesReportDate(date(row, 16));
        dto.setIsInvoiceApplied(text(row, 25));
        dto.setRemarks(joinNotes(text(row, 14), text(row, 26)));
        dto.setModelOnly(false);
        dto.setStockStatus("IN_STOCK");
        return normalizeMachinePayload(dto);
    }

    private MachineInventoryCreateDTO buildMachineFromSalesRow(WorkbookRow row, String vehicleNumber, String source) {
        MachineInventoryCreateDTO dto = new MachineInventoryCreateDTO();
        dto.setVehicleProductNumber(vehicleNumber);
        dto.setName(firstNonBlank(text(row, 2), "Workbook vehicle"));
        dto.setSpecificationModel(firstNonBlank(text(row, 3), text(row, 4), "Workbook model"));
        dto.setConfiguration(text(row, 4));
        dto.setMachineType(firstNonBlank(text(row, 2), text(row, 3), text(row, 4), "Internal combustion forklift"));
        dto.setSupplier(firstNonBlank(text(row, 27), source));
        dto.setWarehouseName(source);
        dto.setEngineNumber(text(row, 6));
        dto.setFrameNumber(text(row, 7));
        dto.setWarrantyCardNumber(text(row, 8));
        dto.setInboundDate(dateTime(row, 1));
        dto.setPurchasePrice(decimal(row, 9));
        dto.setSettlementPrice(decimal(row, 9));
        dto.setSalePrice(decimal(row, 11));
        dto.setInventoryCount(1);
        dto.setIsSalesReported(text(row, 21));
        dto.setSalesReportDate(date(row, 22));
        dto.setIsInvoiceApplied(text(row, 24));
        dto.setRemarks(joinNotes(source, text(row, 10), text(row, 26)));
        dto.setStockStatus("IN_STOCK");
        dto.setModelOnly(false);
        return normalizeMachinePayload(dto);
    }

    private MachineInventoryCreateDTO buildMachineFromOtherBrandRow(WorkbookRow row, String vehicleNumber) {
        MachineInventoryCreateDTO dto = new MachineInventoryCreateDTO();
        dto.setVehicleProductNumber(vehicleNumber);
        dto.setName(firstNonBlank(text(row, 2), "Other brand vehicle"));
        dto.setSpecificationModel(firstNonBlank(text(row, 4), "Other brand model"));
        dto.setConfiguration(text(row, 5));
        dto.setMachineType(firstNonBlank(text(row, 2), text(row, 4), "Other brand"));
        dto.setSupplier(firstNonBlank(text(row, 3), "Other brand"));
        dto.setWarehouseName("Other brand sales");
        dto.setEngineNumber(text(row, 7));
        dto.setFrameNumber(text(row, 8));
        dto.setWarrantyCardNumber(text(row, 9));
        dto.setInboundDate(dateTime(row, 1));
        dto.setSettlementPrice(decimal(row, 10));
        dto.setSalePrice(decimal(row, 10));
        dto.setInventoryCount(1);
        dto.setRemarks(joinNotes("Other brand sales", text(row, 20)));
        dto.setStockStatus("IN_STOCK");
        dto.setModelOnly(false);
        return normalizeMachinePayload(dto);
    }

    private MachineInventoryCreateDTO buildMachineFromOldInboundRow(WorkbookRow row, String vehicleNumber) {
        MachineInventoryCreateDTO dto = new MachineInventoryCreateDTO();
        dto.setVehicleProductNumber(vehicleNumber);
        dto.setName(firstNonBlank(text(row, 3), "Old vehicle"));
        dto.setSpecificationModel(firstNonBlank(text(row, 4), "Old vehicle model"));
        dto.setConfiguration(text(row, 5));
        dto.setMachineType(firstNonBlank(text(row, 3), text(row, 4), "Old vehicle"));
        dto.setSupplier("Old vehicle recovery");
        dto.setWarehouseName("Old vehicle stock");
        dto.setEngineNumber(text(row, 7));
        dto.setFrameNumber(text(row, 9));
        dto.setManufacturingDate(date(row, 10));
        dto.setInboundDate(dateTime(row, 1));
        dto.setSalePrice(decimal(row, 8));
        dto.setInventoryCount(Math.max(1, intValue(row, 13, 1)));
        dto.setDestination1(text(row, 14));
        dto.setDestination2(text(row, 15));
        dto.setDestination3(text(row, 16));
        dto.setDestination4(text(row, 17));
        dto.setDestination5(text(row, 18));
        dto.setRemarks(joinNotes(text(row, 2), text(row, 11), text(row, 12), text(row, 19)));
        dto.setStockStatus("IN_STOCK");
        dto.setModelOnly(false);
        return normalizeMachinePayload(dto);
    }

    private MachineInventoryCreateDTO buildMachineFromOldSalesRow(WorkbookRow row, String vehicleNumber) {
        MachineInventoryCreateDTO dto = new MachineInventoryCreateDTO();
        dto.setVehicleProductNumber(vehicleNumber);
        dto.setName(firstNonBlank(text(row, 2), "Used vehicle"));
        dto.setSpecificationModel(firstNonBlank(text(row, 3), "Used vehicle model"));
        dto.setConfiguration(text(row, 4));
        dto.setMachineType(firstNonBlank(text(row, 2), text(row, 3), "Used vehicle"));
        dto.setSupplier(firstNonBlank(text(row, 21), "Used vehicle"));
        dto.setWarehouseName("Used vehicle sales");
        dto.setEngineNumber(text(row, 6));
        dto.setFrameNumber(text(row, 7));
        dto.setWarrantyCardNumber(text(row, 8));
        dto.setInboundDate(dateTime(row, 1));
        dto.setSettlementPrice(decimal(row, 11));
        dto.setSalePrice(decimal(row, 11));
        dto.setInventoryCount(Math.max(1, intValue(row, 10, 1)));
        dto.setRemarks(joinNotes("Used vehicle sales", text(row, 20)));
        dto.setStockStatus("IN_STOCK");
        dto.setModelOnly(false);
        return normalizeMachinePayload(dto);
    }

    private MachineInventoryCreateDTO normalizeMachinePayload(MachineInventoryCreateDTO dto) {
        dto.setVehicleProductNumber(trimToNull(dto.getVehicleProductNumber()));
        dto.setName(firstNonBlank(dto.getName(), "Workbook vehicle"));
        dto.setSpecificationModel(firstNonBlank(dto.getSpecificationModel(), "Workbook model"));
        dto.setMachineType(trimToNull(dto.getMachineType()));
        dto.setConfiguration(trimToNull(dto.getConfiguration()));
        dto.setSupplier(trimToNull(dto.getSupplier()));
        dto.setWarehouseName(trimToNull(dto.getWarehouseName()));
        dto.setApplicationNumber(trimToNull(dto.getApplicationNumber()));
        dto.setMaterialNumber(trimToNull(dto.getMaterialNumber()));
        dto.setEngineNumber(trimToNull(dto.getEngineNumber()));
        dto.setFrameNumber(trimToNull(dto.getFrameNumber()));
        dto.setWarrantyCardNumber(trimToNull(dto.getWarrantyCardNumber()));
        dto.setPurchasePrice(scaleMoney(dto.getPurchasePrice()));
        dto.setSalePrice(scaleMoney(dto.getSalePrice()));
        dto.setSettlementPrice(scaleMoney(dto.getSettlementPrice()));
        dto.setRemarks(trimToNull(dto.getRemarks()));
        if (dto.getInventoryCount() == null) {
            dto.setInventoryCount(1);
        }
        if (dto.getStockStatus() == null) {
            dto.setStockStatus("IN_STOCK");
        }
        return dto;
    }

    private PartInventoryCreateDTO buildPartDto(List<WorkbookRow> group, WorkbookRow latestRow) {
        int totalQuantity = group.stream().mapToInt(row -> intValue(row, 7, 0)).sum();
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal weightedQuantity = BigDecimal.ZERO;
        for (WorkbookRow row : group) {
            BigDecimal price = decimal(row, 8);
            int quantity = intValue(row, 7, 0);
            if (price != null && quantity > 0) {
                totalAmount = totalAmount.add(price.multiply(BigDecimal.valueOf(quantity)));
                weightedQuantity = weightedQuantity.add(BigDecimal.valueOf(quantity));
            }
        }
        BigDecimal averagePrice = weightedQuantity.signum() > 0 ? totalAmount.divide(weightedQuantity, 2, RoundingMode.HALF_UP) : decimal(latestRow, 8);
        PartInventoryCreateDTO dto = new PartInventoryCreateDTO();
        dto.setPartCode(firstNonBlank(text(latestRow, 1), "PART"));
        dto.setPartBrand("Workbook import");
        dto.setPartName(firstNonBlank(text(latestRow, 4), dto.getPartCode()));
        dto.setSpecification(text(latestRow, 5));
        dto.setPartCategory(classifyPart(dto.getPartName(), dto.getSpecification(), text(latestRow, 2)));
        dto.setApplicableModels(text(latestRow, 5));
        dto.setSource("采购明细导入");
        dto.setQuantity(totalQuantity);
        dto.setUnit(text(latestRow, 6));
        dto.setPurchasePrice(averagePrice);
        dto.setSettlementPrice(averagePrice);
        dto.setRemarks(buildPartRemark(group));
        dto.setInboundDate(dateTime(latestRow, 0));
        return dto;
    }

    private String buildPartRemark(List<WorkbookRow> rows) {
        List<String> notes = new ArrayList<>();
        notes.add("来源：采购明细导入");
        for (WorkbookRow row : rows.stream().limit(5).toList()) {
            addIfText(notes, text(row, 1));
            addIfText(notes, text(row, 2));
            addIfText(notes, text(row, 10));
            addIfText(notes, text(row, 11));
            addIfText(notes, text(row, 12));
        }
        return trimToLimit(joinNotes(notes.toArray(String[]::new)), 255);
    }

    private String classifyPart(String name, String specification, String documentType) {
        String joined = String.join(" ", Arrays.asList(firstNonBlank(name, ""), firstNonBlank(specification, ""), firstNonBlank(documentType, "")));
        if (joined.contains("轮胎")) return "轮胎";
        if (joined.contains("电池")) return "电池";
        if (joined.contains("充电")) return "充电器";
        if (joined.contains("货叉")) return "货叉";
        if (joined.contains("门架")) return "门架";
        if (joined.contains("液压")) return "液压件";
        if (joined.contains("电控")) return "电控件";
        return "配件";
    }

    private Map<String, WorkbookRow> indexByVehicle(List<WorkbookRow> rows, int vehicleColumn) {
        Map<String, WorkbookRow> map = new LinkedHashMap<>();
        for (WorkbookRow row : rows) {
            String vehicleNumber = cleanVehicleNumber(text(row, vehicleColumn));
            if (hasText(vehicleNumber) && !map.containsKey(vehicleNumber)) {
                map.put(vehicleNumber, row);
            }
        }
        return map;
    }

    private String generatedVehicleNumber(String prefix, int rowNumber, WorkbookRow row) {
        String serial = text(row, 0);
        if (hasText(serial)) {
            String safeSerial = serial.replaceAll("[^A-Za-z0-9]", "");
            if (hasText(safeSerial)) {
                return prefix + "-" + safeSerial.substring(0, Math.min(20, safeSerial.length()));
            }
        }
        return prefix + "-ROW-" + rowNumber;
    }

    private String cleanVehicleNumber(String value) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        String normalized = text.replace("<", "").replace(">", "").trim();
        if (Set.of("/", "\\", "-", "--", "0", "none", "null", "n/a", "N/A").contains(normalized)) {
            return null;
        }
        return normalized;
    }

    private String joinNotes(String... values) {
        return Arrays.stream(values)
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .collect(Collectors.collectingAndThen(Collectors.toList(), list -> list.isEmpty() ? null : String.join(" / ", list)));
    }

    private void addIfText(List<String> list, String value) {
        if (hasText(value)) {
            list.add(value);
        }
    }

    private boolean parseBool(String value) {
        String text = trimToNull(value);
        if (text == null) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        return !(normalized.startsWith("否") || normalized.startsWith("无") || normalized.startsWith("没")
                || normalized.startsWith("不") || normalized.startsWith("0") || normalized.startsWith("/"));
    }

    private String text(WorkbookRow row, int index) {
        if (row == null || index < 0 || index >= row.values().size()) {
            return null;
        }
        return trimToNull(row.values().get(index));
    }

    private LocalDate date(WorkbookRow row, int index) {
        String value = text(row, index);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDate.parse(value, DateTimeFormatter.ofPattern("yyyy/M/d"));
            } catch (DateTimeParseException ignoredAgain) {
                try {
                    return LocalDate.parse(value, DateTimeFormatter.ofPattern("yyyy-M-d"));
                } catch (DateTimeParseException ignoredThird) {
                    return null;
                }
            }
        }
    }

    private LocalDateTime dateTime(WorkbookRow row, int index) {
        String value = text(row, index);
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            LocalDate parsedDate = date(row, index);
            return parsedDate == null ? null : parsedDate.atStartOfDay();
        }
    }

    private BigDecimal decimal(WorkbookRow row, int index) {
        String value = text(row, index);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", "").replace("元", "")).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer intValue(WorkbookRow row, int index, int fallback) {
        String value = text(row, index);
        if (value == null) {
            return fallback;
        }
        try {
            return new BigDecimal(value.replace(",", "")).intValue();
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private BigDecimal scaleMoney(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trimToLimit(String value, int maxLength) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private void registerAfterCommit(Runnable runnable) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runnable.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runnable.run();
            }
        });
    }

    private record ValidationResult(int totalRows, int validRows, List<DataImportErrorVO> errors, boolean importable,
                                    String summary, Map<String, Object> snapshot) {
    }

    private record ImportResult(int importedRows, int skippedRows, String summary) {
    }

    private record WorkbookSnapshot(Map<String, List<WorkbookRow>> sheets) {
        List<WorkbookRow> sheetRows(String name) {
            return sheets.getOrDefault(name, List.of());
        }

        int totalRows() {
            return sheets.values().stream().mapToInt(List::size).sum();
        }

        Map<String, Integer> sheetSizes() {
            Map<String, Integer> sizes = new LinkedHashMap<>();
            for (Map.Entry<String, List<WorkbookRow>> entry : sheets.entrySet()) {
                sizes.put(entry.getKey(), entry.getValue().size());
            }
            return sizes;
        }
    }

    private record WorkbookRow(int rowNumber, List<String> values) {
    }
}
