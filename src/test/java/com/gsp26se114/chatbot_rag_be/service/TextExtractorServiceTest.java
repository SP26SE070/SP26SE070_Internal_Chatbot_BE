package com.gsp26se114.chatbot_rag_be.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class TextExtractorServiceTest {

    private final TextExtractorService service = new TextExtractorService();

    @Test
    void extractTextFromXlsxIncludesEvaluatedFormulaValue() throws IOException {
        byte[] workbookBytes = workbookWithFormula();

        String extracted = service.extractText(
                workbookBytes,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "formula.xlsx"
        );

        assertThat(extracted).contains("Total", "42");
        assertThat(extracted).doesNotContain("SUM(A2:B2)");
    }

    private static byte[] workbookWithFormula() throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Formula");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Total");

            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(19);
            row.createCell(1).setCellValue(23);
            Cell formula = row.createCell(2);
            formula.setCellFormula("SUM(A2:B2)");

            workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}
