package com.example.forklift_erp.service;

import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.util.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class DataBackupService {
    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[A-Za-z0-9_]+");
    private static final String FORMAT = "forklift-erp-json-backup-v1";
    private static final long MAX_BACKUP_FILE_SIZE = 50L * 1024 * 1024;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public DataBackupService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public byte[] createBackup() {
        BackupFile backup = new BackupFile();
        backup.setFormat(FORMAT);
        backup.setSchemaVersion(currentSchemaVersion());
        backup.setCreatedAt(LocalDateTime.now().toString());
        backup.setCreatedBy(SecurityUtils.currentUsername());
        for (String tableName : applicationTableNames()) {
            BackupTable table = new BackupTable();
            table.setName(tableName);
            table.setColumns(applicationColumnNames(tableName));
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("select * from " + quote(tableName));
            table.setRows(rows.stream().map(this::normalizeRow).toList());
            backup.getTables().add(table);
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(backup);
        } catch (IOException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "Failed to create backup");
        }
    }

    @Transactional(readOnly = true)
    public BackupValidationResult dryRunRestore(MultipartFile file) {
        RestorePlan plan = prepareRestore(file);
        BackupValidationResult result = new BackupValidationResult();
        result.setDryRun(true);
        result.setFormat(plan.backup().getFormat());
        result.setBackupSchemaVersion(plan.backup().getSchemaVersion());
        result.setCurrentSchemaVersion(plan.currentSchemaVersion());
        for (String tableName : plan.currentTables()) {
            BackupTable table = plan.backupTables().get(tableName);
            long rows = table == null || table.getRows() == null ? 0L : table.getRows().size();
            result.getTableRows().put(tableName, rows);
            result.setTotalRows(result.getTotalRows() + rows);
        }
        return result;
    }

    @Transactional
    public Map<String, Long> restoreBackup(MultipartFile file) {
        RestorePlan plan = prepareRestore(file);

        Map<String, Long> summary = new LinkedHashMap<>();
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=0");
        try {
            for (int i = plan.currentTables().size() - 1; i >= 0; i--) {
                jdbcTemplate.update("delete from " + quote(plan.currentTables().get(i)));
            }
            for (String tableName : plan.currentTables()) {
                BackupTable table = plan.backupTables().get(tableName);
                long inserted = 0;
                if (table != null && table.getRows() != null) {
                    for (Map<String, Object> row : table.getRows()) {
                        insertRow(tableName, row);
                        inserted++;
                    }
                }
                summary.put(tableName, inserted);
            }
        } finally {
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=1");
        }
        return summary;
    }

    private RestorePlan prepareRestore(MultipartFile file) {
        ensureBackupFile(file);
        BackupFile backup;
        try {
            backup = objectMapper.readValue(file.getBytes(), BackupFile.class);
        } catch (IOException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Invalid backup file");
        }
        if (backup == null || !FORMAT.equals(backup.getFormat()) || backup.getTables() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Unsupported backup format");
        }

        List<String> currentTables = applicationTableNames();
        Map<String, Set<String>> currentColumns = applicationColumnsByTable(currentTables);
        String currentSchemaVersion = currentSchemaVersion();
        validateBackupBeforeRestore(backup, currentTables, currentColumns, currentSchemaVersion);

        Map<String, BackupTable> backupTables = new LinkedHashMap<>();
        for (BackupTable table : backup.getTables()) {
            backupTables.put(table.getName(), table);
        }
        return new RestorePlan(backup, currentTables, backupTables, currentSchemaVersion);
    }

    private List<String> applicationTableNames() {
        return jdbcTemplate.queryForList("show tables", String.class).stream()
                .filter(table -> !"flyway_schema_history".equalsIgnoreCase(table))
                .sorted()
                .toList();
    }

    private Map<String, Set<String>> applicationColumnsByTable(List<String> tableNames) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (String tableName : tableNames) {
            result.put(tableName, new HashSet<>(applicationColumnNames(tableName)));
        }
        return result;
    }

    private List<String> applicationColumnNames(String tableName) {
        return jdbcTemplate.queryForList("show columns from " + quote(tableName)).stream()
                .map(row -> String.valueOf(row.get("Field")))
                .toList();
    }

    private String currentSchemaVersion() {
        try {
            return jdbcTemplate.queryForObject("""
                    select version
                    from flyway_schema_history
                    where success = 1
                    order by installed_rank desc
                    limit 1
                    """, String.class);
        } catch (RuntimeException ex) {
            return "unknown";
        }
    }

    private void validateBackupBeforeRestore(
            BackupFile backup,
            List<String> currentTables,
            Map<String, Set<String>> currentColumns,
            String currentSchemaVersion
    ) {
        if (backup.getSchemaVersion() != null
                && !backup.getSchemaVersion().isBlank()
                && !"unknown".equals(currentSchemaVersion)
                && !currentSchemaVersion.equals(backup.getSchemaVersion())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Backup schema version does not match current database");
        }

        Set<String> allowedTables = new HashSet<>(currentTables);
        Set<String> seenTables = new HashSet<>();
        for (BackupTable table : backup.getTables()) {
            validateIdentifier(table.getName());
            if (!allowedTables.contains(table.getName())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "Backup contains unknown table: " + table.getName());
            }
            if (!seenTables.add(table.getName())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "Backup contains duplicate table: " + table.getName());
            }
            validateBackupColumns(table, currentColumns.getOrDefault(table.getName(), Set.of()));
        }
    }

    private void validateBackupColumns(BackupTable table, Set<String> allowedColumns) {
        Set<String> declaredColumns = null;
        if (table.getColumns() != null && !table.getColumns().isEmpty()) {
            declaredColumns = new HashSet<>();
            for (String column : table.getColumns()) {
                validateColumnAllowed(column, allowedColumns);
                if (!declaredColumns.add(column)) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "Backup contains duplicate column: " + column);
                }
            }
        }
        if (table.getRows() == null) {
            return;
        }
        for (Map<String, Object> row : table.getRows()) {
            if (row == null) {
                continue;
            }
            for (String column : row.keySet()) {
                validateColumnAllowed(column, allowedColumns);
                if (declaredColumns != null && !declaredColumns.contains(column)) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "Backup row contains undeclared column: " + column);
                }
            }
        }
    }

    private void validateColumnAllowed(String column, Set<String> allowedColumns) {
        validateIdentifier(column);
        if (!allowedColumns.contains(column)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Backup contains unknown column: " + column);
        }
    }

    private Map<String, Object> normalizeRow(Map<String, Object> row) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        row.forEach((key, value) -> normalized.put(key, normalizeValue(value)));
        return normalized;
    }

    private Object normalizeValue(Object value) {
        if (value == null
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof BigDecimal) {
            return value;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toString();
        }
        if (value instanceof Date date) {
            return date.toLocalDate().toString();
        }
        if (value instanceof Time time) {
            return time.toLocalTime().toString();
        }
        if (value instanceof byte[] bytes) {
            if (bytes.length == 1) {
                return Byte.toUnsignedInt(bytes[0]);
            }
            return Base64.getEncoder().encodeToString(bytes);
        }
        return String.valueOf(value);
    }

    private void insertRow(String tableName, Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return;
        }
        row.keySet().forEach(this::validateIdentifier);
        List<String> columns = new ArrayList<>(row.keySet());
        String columnSql = columns.stream().map(this::quote).reduce((a, b) -> a + ", " + b).orElse("");
        String placeholders = columns.stream().map(column -> "?").reduce((a, b) -> a + ", " + b).orElse("");
        Object[] values = columns.stream().map(row::get).toArray();
        jdbcTemplate.update("insert into " + quote(tableName) + " (" + columnSql + ") values (" + placeholders + ")", values);
    }

    private String quote(String identifier) {
        validateIdentifier(identifier);
        return "`" + identifier + "`";
    }

    private void validateIdentifier(String identifier) {
        if (identifier == null || !SQL_IDENTIFIER.matcher(identifier).matches()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Invalid backup identifier");
        }
    }

    private void ensureBackupFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Backup file is required");
        }
        if (file.getSize() > MAX_BACKUP_FILE_SIZE) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Backup file cannot exceed 50MB");
        }
        String filename = file.getOriginalFilename();
        if (filename != null) {
            String normalized = filename.trim().replace('\\', '/');
            if (normalized.contains("../") || normalized.contains("/") || !normalized.toLowerCase(java.util.Locale.ROOT).endsWith(".json")) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "Backup file name must be a JSON file");
            }
        }
    }

    @Data
    public static class BackupFile {
        private String format;
        private String schemaVersion;
        private String createdAt;
        private String createdBy;
        private List<BackupTable> tables = new ArrayList<>();
    }

    @Data
    public static class BackupTable {
        private String name;
        private List<String> columns = new ArrayList<>();
        private List<Map<String, Object>> rows = new ArrayList<>();
    }

    @Data
    public static class BackupValidationResult {
        private boolean dryRun;
        private String format;
        private String backupSchemaVersion;
        private String currentSchemaVersion;
        private long totalRows;
        private Map<String, Long> tableRows = new LinkedHashMap<>();
    }

    private record RestorePlan(
            BackupFile backup,
            List<String> currentTables,
            Map<String, BackupTable> backupTables,
            String currentSchemaVersion
    ) {
    }
}
