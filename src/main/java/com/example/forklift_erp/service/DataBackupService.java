package com.example.forklift_erp.service;

import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.util.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class DataBackupService {
    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[A-Za-z0-9_]+");
    private static final String FORMAT = "forklift-erp-json-backup-v1";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public byte[] createBackup() {
        BackupFile backup = new BackupFile();
        backup.setFormat(FORMAT);
        backup.setCreatedAt(LocalDateTime.now().toString());
        backup.setCreatedBy(SecurityUtils.currentUsername());
        for (String tableName : applicationTableNames()) {
            BackupTable table = new BackupTable();
            table.setName(tableName);
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

    @Transactional
    public Map<String, Long> restoreBackup(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Backup file is required");
        }
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
        Map<String, BackupTable> backupTables = new LinkedHashMap<>();
        for (BackupTable table : backup.getTables()) {
            validateIdentifier(table.getName());
            if (currentTables.contains(table.getName())) {
                backupTables.put(table.getName(), table);
            }
        }

        Map<String, Long> summary = new LinkedHashMap<>();
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=0");
        try {
            for (int i = currentTables.size() - 1; i >= 0; i--) {
                jdbcTemplate.update("delete from " + quote(currentTables.get(i)));
            }
            for (String tableName : currentTables) {
                BackupTable table = backupTables.get(tableName);
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

    private List<String> applicationTableNames() {
        return jdbcTemplate.queryForList("show tables", String.class).stream()
                .filter(table -> !"flyway_schema_history".equalsIgnoreCase(table))
                .sorted()
                .toList();
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

    @Data
    public static class BackupFile {
        private String format;
        private String createdAt;
        private String createdBy;
        private List<BackupTable> tables = new ArrayList<>();
    }

    @Data
    public static class BackupTable {
        private String name;
        private List<Map<String, Object>> rows = new ArrayList<>();
    }
}
