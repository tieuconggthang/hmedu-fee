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
     * Đọc danh sách từ file Excel - Lấy sheet đầu tiên
     */
    public List<StudentFeeDto> readFeeDataFromFile(String filePath) {
        List<StudentFeeDto> students = new ArrayList<>();
        
        int skipRows = config.getExcel().getSkipRows();
        int colName = config.getExcel().getColumns().getOrDefault("student-name", 0);
        int colPhone = config.getExcel().getColumns().getOrDefault("phone", 3);
        int colAmount = config.getExcel().getColumns().getOrDefault("amount", 17);
        int colContent = config.getExcel().getColumns().getOrDefault("content", 6);
        int colAccountName = config.getExcel().getColumns().getOrDefault("account-name", 21);
        int colAccountNumber = config.getExcel().getColumns().getOrDefault("account-number", 22);
        int colBank = config.getExcel().getColumns().getOrDefault("bank", 23);
        
        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {
            
            // Lấy sheet đầu tiên (index 0)
            Sheet sheet = workbook.getSheetAt(0);
            String actualSheetName = sheet.getSheetName();
            
            log.info("Reading Excel file: {}, Sheet: {}, Total rows: {}", 
                filePath, actualSheetName, sheet.getPhysicalNumberOfRows());
            
            int validCount = 0;
            int skipCount = 0;
            
            for (int i = skipRows; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                // Kiểm tra tên học sinh
                String studentName = getCellValue(row.getCell(colName));
                if (studentName.trim().isEmpty()) {
                    skipCount++;
                    continue;
                }
                
                // Xử lý số điện thoại
                String phone = getCellValue(row.getCell(colPhone));
                phone = normalizePhoneNumber(phone);
                if (phone.isEmpty()) {
                    log.warn("Row {}: Skip '{}' - invalid phone", i, studentName);
                    skipCount++;
                    continue;
                }
                
                // Kiểm tra số tiền
                BigDecimal amount = getBigDecimalValue(row.getCell(colAmount));
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    log.warn("Row {}: Skip '{}' - invalid amount: {}", i, studentName, amount);
                    skipCount++;
                    continue;
                }
                
                // Tạo transactionId
                String transactionId = generateNumericTransactionId(i);
                
                StudentFeeDto student = StudentFeeDto.builder()
                    .rowIndex(i)
                    .studentName(studentName)
                    .phone(phone)
                    .amount(amount)
                    .content(getCellValue(row.getCell(colContent)))
                    .transactionId(transactionId)
                    .accountName(getCellValue(row.getCell(colAccountName)))
                    .accountNumber(getCellValue(row.getCell(colAccountNumber)))
                    .bank(getCellValue(row.getCell(colBank)))
                    .build();
                
                students.add(student);
                validCount++;
                log.debug("Read student: {}, transactionId: {}", studentName, transactionId);
            }
            
            log.info("File {}: {} valid students, {} skipped rows", 
                filePath, validCount, skipCount);
            
        } catch (Exception e) {
            log.error("Error reading Excel file: {}", filePath, e);
        }
        
        return students;
    }
    
    /**
     * Đọc danh sách từ file Excel mặc định
     */
    public List<StudentFeeDto> readFeeData() {
        return readFeeDataFromFile(config.getExcel().getFilePath());
    }
    
    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue().toString();
                }
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
    
    /**
     * Chuẩn hóa số điện thoại: 0xxxxx -> 84xxxxx
     */
    private String normalizePhoneNumber(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return "";
        }
        
        phone = phone.trim().replaceAll("[^\\d]", "");
        
        if (phone.startsWith("0")) {
            phone = "84" + phone.substring(1);
        }
        
        return phone;
    }
    
    /**
     * Tạo mã giao dịch: YYMMDD + rowIndex
     */
    public String generateNumericTransactionId(int rowIndex) {
        java.time.LocalDate now = java.time.LocalDate.now();
        String dateStr = String.format("%02d%02d%02d", 
            now.getYear() % 100, now.getMonthValue(), now.getDayOfMonth());
        return String.format("%s%04d", dateStr, rowIndex);
    }
}
