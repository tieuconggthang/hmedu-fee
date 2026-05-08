import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.File;
import java.io.FileInputStream;
import java.net.URLEncoder;

public class TestReadExcel {
    public static void main(String[] args) throws Exception {
        String filePath = "/home/thangtc/.hermes/cache/documents/doc_fb37d85e4bad_Theo dõi học phí tháng 4.xlsx";
        
        FileInputStream fis = new FileInputStream(new File(filePath));
        Workbook workbook = new XSSFWorkbook(fis);
        Sheet sheet = workbook.getSheetAt(0);
        
        // Lấy dòng đầu tiên có dữ liệu (dòng 2, index 1)
        Row row = sheet.getRow(1);
        
        // Đọc các cột: B(1), G(6), R(17), V(21), W(22), X(23)
        String tenHs = getCellValue(row.getCell(1));           // Cột B
        String noiDung = getCellValue(row.getCell(6));         // Cột G
        String soTienStr = getCellValue(row.getCell(17));      // Cột R
        String tenTk = getCellValue(row.getCell(21));          // Cột V
        String soTk = getCellValue(row.getCell(22));           // Cột W
        String nganHang = getCellValue(row.getCell(23));       // Cột X
        
        // Xử lý số tiền
        long soTien = 0;
        try {
            soTienStr = soTienStr.replaceAll("[,\\.]", "");
            soTien = Long.parseLong(soTienStr);
        } catch (Exception e) {
            soTien = (long) Double.parseDouble(soTienStr);
        }
        
        // Xử lý số TK
        soTk = soTk.replaceAll("[\\.\\s]", "");
        
        // Map ngân hàng
        String bankCode = "TCB";
        if (nganHang.toLowerCase().contains("vietcombank")) bankCode = "VCB";
        else if (nganHang.toLowerCase().contains("techcombank")) bankCode = "TCB";
        else if (nganHang.toLowerCase().contains("mb")) bankCode = "MB";
        else if (nganHang.toLowerCase().contains("bidv")) bankCode = "BIDV";
        else if (nganHang.toLowerCase().contains("agribank")) bankCode = "VBA";
        
        // Tạo VietQR URL
        String noiDungEncoded = URLEncoder.encode(noiDung, "UTF-8");
        String vietqrUrl = String.format("https://img.vietqr.io/image/%s-%s-qr_only.png?amount=%d&addInfo=%s",
            bankCode, soTk, soTien, noiDungEncoded);
        
        // Tạo caption
        String caption = String.format(
            "🎓 THU HỌC PHÍ THÁNG 4/2025\n\n" +
            "Kính gửi phụ huynh học sinh %s,\n\n" +
            "• Học phí: %,d VNĐ\n" +
            "• Nội dung CK: %s\n" +
            "• TK: %s %s\n" +
            "• Chủ TK: %s\n\n" +
            "Quét mã QR để thanh toán.\nXin cảm ơn!\n\n" +
            "HMEDU 🏫",
            tenHs, soTien, noiDung, nganHang, soTk, tenTk
        );
        
        System.out.println("📋 DỮ LIỆU HỌC SINH ĐẦU TIÊN:");
        System.out.println("  Tên HS: " + tenHs);
        System.out.println("  Số tiền: " + String.format("%,d", soTien) + " VNĐ");
        System.out.println("  Nội dung: " + noiDung);
        System.out.println("  Tên TK: " + tenTk);
        System.out.println("  Số TK: " + soTk);
        System.out.println("  Ngân hàng: " + nganHang + " (" + bankCode + ")");
        System.out.println("\n🔗 VietQR URL:\n" + vietqrUrl);
        System.out.println("\n📱 CAPTION:\n" + caption);
        
        workbook.close();
        fis.close();
    }
    
    private static String getCellValue(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                }
                return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "";
        }
    }
}
