package com.example.forklift_erp.service;

import com.example.forklift_erp.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataBackupServiceTests {

    private static final String FORMAT = "forklift-erp-json-backup-v1";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void dryRunReportsRowsWithoutMutatingDatabase() throws Exception {
        JdbcTemplate jdbcTemplate = jdbcTemplateWithUsersTable();
        DataBackupService service = new DataBackupService(jdbcTemplate, objectMapper);
        DataBackupService.BackupFile backup = backupWithUsers(
                List.of("id", "username"),
                row("id", 1, "username", "admin")
        );

        DataBackupService.BackupValidationResult result = service.dryRunRestore(multipartBackup(backup));

        assertThat(result.isDryRun()).isTrue();
        assertThat(result.getFormat()).isEqualTo(FORMAT);
        assertThat(result.getBackupSchemaVersion()).isEqualTo("35");
        assertThat(result.getCurrentSchemaVersion()).isEqualTo("35");
        assertThat(result.getTotalRows()).isEqualTo(1);
        assertThat(result.getTableRows()).containsEntry("users", 1L);
        verify(jdbcTemplate, never()).execute(anyString());
    }

    @Test
    void dryRunRejectsUnknownColumns() throws Exception {
        DataBackupService service = new DataBackupService(jdbcTemplateWithUsersTable(), objectMapper);
        DataBackupService.BackupFile backup = backupWithUsers(
                List.of("id", "username"),
                row("id", 1, "username", "admin", "unknown_column", "value")
        );

        assertThatThrownBy(() -> service.dryRunRestore(multipartBackup(backup)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Backup contains unknown column: unknown_column");
    }

    @Test
    void dryRunRejectsRowsOutsideDeclaredColumns() throws Exception {
        DataBackupService service = new DataBackupService(jdbcTemplateWithUsersTable(), objectMapper);
        DataBackupService.BackupFile backup = backupWithUsers(
                List.of("id"),
                row("id", 1, "username", "admin")
        );

        assertThatThrownBy(() -> service.dryRunRestore(multipartBackup(backup)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Backup row contains undeclared column: username");
    }

    @Test
    void dryRunRejectsDuplicateTables() throws Exception {
        DataBackupService service = new DataBackupService(jdbcTemplateWithUsersTable(), objectMapper);
        DataBackupService.BackupFile backup = new DataBackupService.BackupFile();
        backup.setFormat(FORMAT);
        backup.setSchemaVersion("35");
        backup.setTables(List.of(
                backupTable("users", List.of("id"), row("id", 1)),
                backupTable("users", List.of("id"), row("id", 2))
        ));

        assertThatThrownBy(() -> service.dryRunRestore(multipartBackup(backup)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Backup contains duplicate table: users");
    }

    private JdbcTemplate jdbcTemplateWithUsersTable() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList("show tables", String.class)).thenReturn(List.of("users"));
        when(jdbcTemplate.queryForList("show columns from `users`")).thenReturn(columns("id", "username"));
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class))).thenReturn("35");
        return jdbcTemplate;
    }

    private DataBackupService.BackupFile backupWithUsers(List<String> columns, Map<String, Object> row) {
        DataBackupService.BackupFile backup = new DataBackupService.BackupFile();
        backup.setFormat(FORMAT);
        backup.setSchemaVersion("35");
        backup.setTables(List.of(backupTable("users", columns, row)));
        return backup;
    }

    private DataBackupService.BackupTable backupTable(String tableName, List<String> columns, Map<String, Object> row) {
        DataBackupService.BackupTable table = new DataBackupService.BackupTable();
        table.setName(tableName);
        table.setColumns(columns);
        table.setRows(List.of(row));
        return table;
    }

    private MockMultipartFile multipartBackup(DataBackupService.BackupFile backup) throws Exception {
        return new MockMultipartFile(
                "file",
                "backup.json",
                "application/json",
                objectMapper.writeValueAsBytes(backup)
        );
    }

    private List<Map<String, Object>> columns(String... names) {
        List<Map<String, Object>> columns = new ArrayList<>();
        for (String name : names) {
            columns.add(Map.of("Field", name));
        }
        return columns;
    }

    private Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            row.put(String.valueOf(values[i]), values[i + 1]);
        }
        return row;
    }
}
