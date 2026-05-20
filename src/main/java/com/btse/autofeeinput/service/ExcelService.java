package com.btse.autofeeinput.service;

import com.btse.autofeeinput.model.SheetData;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

/**
 * Reads csv / xls / xlsx into a SheetData. Writes processed data back to xlsx.
 */
public class ExcelService {

    public SheetData read(File file) throws IOException {
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".csv")) {
            return readCsv(file);
        } else if (name.endsWith(".xls") || name.endsWith(".xlsx")) {
            return readExcel(file);
        }
        throw new IOException("Unsupported file type: " + file.getName());
    }

    private SheetData readCsv(File file) throws IOException {
        SheetData data = new SheetData();
        Charset cs = detectCharset(file);
        try (Reader reader = new InputStreamReader(new FileInputStream(file), cs);
             CSVReader csv = new CSVReader(reader)) {
            List<String[]> all = csv.readAll();
            if (all.isEmpty()) return data;
            String[] header = all.get(0);
            for (String h : header) data.getHeaders().add(h == null ? "" : h);
            for (int i = 1; i < all.size(); i++) {
                String[] row = all.get(i);
                ObservableList<String> obs = FXCollections.observableArrayList();
                for (int c = 0; c < data.getHeaders().size(); c++) {
                    obs.add(c < row.length && row[c] != null ? row[c] : "");
                }
                data.getRows().add(obs);
            }
            return data;
        } catch (CsvException e) {
            throw new IOException("CSV parse failed: " + e.getMessage(), e);
        }
    }

    private Charset detectCharset(File file) {
        try (InputStream in = new FileInputStream(file)) {
            byte[] bom = new byte[3];
            int read = in.read(bom);
            if (read == 3 && (bom[0] & 0xFF) == 0xEF && (bom[1] & 0xFF) == 0xBB && (bom[2] & 0xFF) == 0xBF) {
                return StandardCharsets.UTF_8;
            }
        } catch (IOException ignored) { }
        return StandardCharsets.UTF_8;
    }

    private SheetData readExcel(File file) throws IOException {
        SheetData data = new SheetData();
        try (InputStream in = new FileInputStream(file);
             Workbook wb = file.getName().toLowerCase(Locale.ROOT).endsWith(".xls")
                     ? new HSSFWorkbook(in)
                     : new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) return data;
            DataFormatter fmt = new DataFormatter();
            int last = sheet.getLastRowNum();
            Row header = sheet.getRow(sheet.getFirstRowNum());
            int colCount = 0;
            if (header != null) {
                colCount = header.getLastCellNum();
                for (int c = 0; c < colCount; c++) {
                    Cell cell = header.getCell(c, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                    data.getHeaders().add(fmt.formatCellValue(cell).trim());
                }
            }
            for (int r = sheet.getFirstRowNum() + 1; r <= last; r++) {
                Row row = sheet.getRow(r);
                ObservableList<String> obs = FXCollections.observableArrayList();
                for (int c = 0; c < colCount; c++) {
                    if (row == null) {
                        obs.add("");
                    } else {
                        Cell cell = row.getCell(c, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        obs.add(fmt.formatCellValue(cell));
                    }
                }
                data.getRows().add(obs);
            }
            return data;
        }
    }

    /** Write sheet data as xlsx to the given path. */
    public void write(Path out, SheetData data) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("data");
            Row headerRow = sheet.createRow(0);
            for (int c = 0; c < data.getHeaders().size(); c++) {
                headerRow.createCell(c).setCellValue(data.getHeaders().get(c));
            }
            for (int r = 0; r < data.getRows().size(); r++) {
                Row row = sheet.createRow(r + 1);
                ObservableList<String> rowData = data.getRows().get(r);
                for (int c = 0; c < rowData.size(); c++) {
                    row.createCell(c).setCellValue(rowData.get(c));
                }
            }
            for (int c = 0; c < data.getHeaders().size(); c++) {
                sheet.autoSizeColumn(c);
            }
            try (OutputStream os = Files.newOutputStream(out)) {
                wb.write(os);
            }
        }
    }
}
