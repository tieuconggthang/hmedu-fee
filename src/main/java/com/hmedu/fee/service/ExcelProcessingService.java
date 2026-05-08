package com.hmedu.fee.service;

import com.hmedu.fee.config.AppConfig;
import com.hmedu.fee.dto.StudentFeeDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelProcessingService {
    
    private final AppConfig config;
    
    /**
     * Đọc danh sách học phí từ file Excel
     */
    public List<StudentFeeDto> readFeeData() {
        List<StudentFeeDto> students = new ArrayList<>();
        
        String filePath = config.getExcel().getFilePath();
        String sheetName = config.getExcel().getSheetName();
        int skipRows = config.getExcel().getSkipRows();
        
        // Column indices (0-based)
        int colName = config.getExcel().getColumns().getOrDefault("student-name", 0);
        int colPhone = config.getExcel().getColumns().getOrDefault("phone", 1);
        int colAmount = config.getExcel().getColumns().getOrDefault("amount", 17); // R
        int colContent = config.getExcel().getColumns().getOrDefault("content", 6); // G
        int colAccountName = config.getExcel().getColumns().getOrDefault("account-name", 21); // V
        int colAccountNumber = config.getExcel().getColumns().getOrDefault("account-number", 22); // W
        int colBank = config.getExcel().getColumns().getOrDefault("bank", 23); // X
        
        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {
            
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                log.error("Sheet '{}' not found in file: {}", sheetName, filePath);
                return students;
            }
            
            log.info("Reading Excel file: {}, Sheet: {}, Total rows: {}", 
                filePath, sheetName, sheet.getPhysicalNumberOfRows());
            
            for (int i = skipRows; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                // Skip empty rows
                Cell nameCell = row.getCell(colName);
                if (nameCell == null || getCellValue(nameCell).trim().isEmpty()) {
                    continue;
                }
                
                StudentFeeDto student = StudentFeeDto.builder()
                    .rowIndex(i)
                    .studentName(getCellValue(row.getCell(colName)))
                    .phone(getCellValue(row.getCell(colPhone)))
                    .amount(getBigDecimalValue(row.getCell(colAmount)))
                    .content(getCellValue(row.getCell(colContent)))
                    .accountName(getCellValue(row.getCell(colAccountName)))
                    .accountNumber(getCellValue(row.getCell(colAccountNumber)))
                    .bank(getCellValue(row.getCell(colBank)))
                    .build();
                
                students.add(student);
                log.debug("Read student: {}", student.getStudentName());
            }
            
            log.info("Successfully read {} students from Excel", students.size());
            
        } catch (Exception e) {
            log.error("Error reading Excel file: {}", filePath, e);
        }
        
        return students;
    }
    
    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue().toString();
                }
                // Format số không có dấu phẩy
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val)) {
                    yield String.valueOf((long) val);
                }
                yield String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception e) {
                    yield String.valueOf(cell.getNumericCellValue());
                }
            }
            default -> "";
        };
    }
    
    private BigDecimal getBigDecimalValue(Cell cell) {
        if (cell == null) return BigDecimal.ZERO;
        
        try {
            return switch (cell.getCellType()) {
                case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue());
                case STRING -> new BigDecimal(cell.getStringCellValue().replaceAll("[^\\d.]", ""));
                default -> BigDecimal.ZERO;
            };
        } catch (Exception e) {
            log.warn("Could not parse amount from cell: {}", cell);
            return BigDecimal.ZERO;
        }
    }
}
