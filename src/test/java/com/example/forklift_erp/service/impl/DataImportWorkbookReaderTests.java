package com.example.forklift_erp.service.impl;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DataImportWorkbookReaderTests {

    private final DataImportWorkbookReader reader = new DataImportWorkbookReader();

    @Test
    void vehicleWorkbookReadsAllSheetsAndSkipsBlankRows() throws Exception {
        Path workbookFile = workbookFile("vehicle");
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet inbound = workbook.createSheet("Inbound");
            inbound.createRow(0).createCell(0).setCellValue("header");
            Row dataRow = inbound.createRow(1);
            dataRow.createCell(0).setCellValue("  value  ");
            inbound.createRow(2);

            Sheet sales = workbook.createSheet("Sales");
            sales.createRow(0).createCell(0).setCellValue("header");
            sales.createRow(1).createCell(1).setCellValue("customer");

            try (var output = Files.newOutputStream(workbookFile)) {
                workbook.write(output);
            }
        }

        WorkbookSnapshot snapshot = reader.readVehicleWorkbook(workbookFile);

        assertThat(snapshot.sheetSizes()).containsEntry("Inbound", 1).containsEntry("Sales", 1);
        assertThat(snapshot.totalRows()).isEqualTo(2);
        assertThat(snapshot.sheetRows("Inbound").getFirst())
                .isEqualTo(new WorkbookRow(2, List.of("value")));
    }

    @Test
    void partsWorkbookReadsOnlyTheFirstSheet() throws Exception {
        Path workbookFile = workbookFile("parts");
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet parts = workbook.createSheet("Parts");
            parts.createRow(0).createCell(0).setCellValue("header");
            parts.createRow(1).createCell(0).setCellValue("part");

            Sheet ignored = workbook.createSheet("Ignored");
            ignored.createRow(0).createCell(0).setCellValue("header");
            ignored.createRow(1).createCell(0).setCellValue("ignored");

            try (var output = Files.newOutputStream(workbookFile)) {
                workbook.write(output);
            }
        }

        WorkbookSnapshot snapshot = reader.readPartsWorkbook(workbookFile);

        assertThat(snapshot.sheetSizes()).containsOnlyKeys("Parts");
        assertThat(snapshot.sheetRows("Parts").getFirst())
                .isEqualTo(new WorkbookRow(2, List.of("part")));
    }

    private Path workbookFile(String prefix) throws Exception {
        Path directory = Path.of("target", "data-import-workbook-reader-tests");
        Files.createDirectories(directory);
        return directory.resolve(prefix + "-" + UUID.randomUUID() + ".xlsx");
    }
}
