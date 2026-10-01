package com.hmedu.fee.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.xhtmlrenderer.simple.Graphics2DRenderer;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Service tạo ảnh thông báo học phí từ HTML template
 */
@Slf4j
@Service
public class FeeImageGeneratorService {

    private static final int WIDTH = 800;
    private static final float SCALE = 2.0f;

    /**
     * Tạo ảnh thông báo học phí và lưu vào thư mục data/images
     *
     * @return đường dẫn file ảnh đã lưu
     */
    public String generateFeeImage(Map<String, Object> data, String qrCodeUrl) throws Exception {
        String html = buildHtmlTemplate(data, qrCodeUrl);

        // Tạo thư mục images nếu chưa có
        Path imagesDir = Paths.get("/app/data/images");
        if (!Files.exists(imagesDir)) {
            Files.createDirectories(imagesDir);
        }

        String fileName = "fee_" + System.currentTimeMillis() + ".png";
        Path outputPath = imagesDir.resolve(fileName);

        renderHtmlToImage(html, outputPath.toFile());

        log.info("Generated fee image: {}", outputPath);
        return outputPath.toString();
    }

    /**
     * Build HTML template từ dữ liệu
     */
    private String buildHtmlTemplate(Map<String, Object> data, String qrCodeUrl) {
        String studentName = getString(data, "studentName", "Học sinh");
        String phone = getString(data, "phone", "");
        String className = getString(data, "className", "");
        String month = getString(data, "month", String.valueOf(LocalDateTime.now().getMonthValue()));
        String year = getString(data, "year", String.valueOf(LocalDateTime.now().getYear()));
        String dueDate = getString(data, "dueDate", "25/" + month + "/" + year);
        String accountNumber = getString(data, "accountNumber", "");
        String bank = getString(data, "bank", "");
        String accountName = getString(data, "accountName", "");
        String transactionId = getString(data, "transactionId", "");
        String totalAmount = formatCurrency(getBigDecimal(data, "amount", BigDecimal.ZERO));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> subjects = (List<Map<String, Object>>) data.get("subjects");
        String subjectsRows = buildSubjectsRows(subjects, totalAmount);

        String transferContent = studentName + " " + phone;
        if (StringUtils.hasText(transactionId)) {
            transferContent += " MTC" + transactionId;
        }

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8" />
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body {
                        font-family: "Times New Roman", Times, serif;
                        width: 800px;
                        padding: 30px;
                        background: white;
                        color: black;
                        font-size: 16px;
                    }
                    .header {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        margin-bottom: 20px;
                    }
                    .logo {
                        width: 90px;
                        height: 90px;
                        background: #f26522;
                        border-radius: 50%;
                        color: white;
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        justify-content: center;
                        font-weight: bold;
                        line-height: 1.2;
                    }
                    .logo-main { font-size: 22px; }
                    .logo-sub { font-size: 10px; }
                    .title-section { text-align: center; }
                    .title {
                        font-size: 28px;
                        font-weight: bold;
                        text-transform: uppercase;
                    }
                    .contact {
                        text-align: right;
                        font-size: 14px;
                    }
                    .info {
                        margin: 20px 0;
                        line-height: 1.8;
                    }
                    table {
                        width: 100%;
                        border-collapse: collapse;
                        margin: 15px 0;
                        font-size: 15px;
                    }
                    th, td {
                        border: 1px solid #333;
                        padding: 8px 10px;
                        text-align: left;
                    }
                    th {
                        background: #f5f5f5;
                        font-weight: bold;
                    }
                    .total-row {
                        font-weight: bold;
                        background: #f5f5f5;
                    }
                    .amount { text-align: right; }
                    .notice {
                        margin: 20px 0;
                        line-height: 1.8;
                    }
                    .highlight {
                        color: #d00;
                        font-weight: bold;
                    }
                    .payment-info {
                        margin: 15px 0;
                        line-height: 1.8;
                    }
                    .qr-section {
                        text-align: center;
                        margin-top: 25px;
                    }
                    .qr-section img {
                        width: 180px;
                        height: 180px;
                    }
                    .footer {
                        text-align: center;
                        margin-top: 25px;
                        font-weight: bold;
                    }
                    .note {
                        margin-top: 20px;
                        font-size: 14px;
                        text-align: justify;
                        line-height: 1.6;
                    }
                </style>
            </head>
            <body>
                <div class="header">
                    <div class="logo">
                        <div class="logo-main">HM EDU</div>
                        <div class="logo-sub">HỌC THẬT - THI THẬT</div>
                    </div>
                    <div class="title-section">
                        <div class="title">THÔNG BÁO HỌC PHÍ</div>
                    </div>
                    <div class="contact">
                        Số 22 Ngõ 1 Lê Văn Thiêm<br>0986.22.82.48
                    </div>
                </div>

                <div class="info">
                    <div>Kính gửi: Phụ huynh em <strong>%s/%s</strong> Lớp %s</div>
                    <div>HM EDU gửi tới Quý Phụ huynh Thông báo học phí tháng <strong>%s/%s</strong> của con như sau:</div>
                </div>

                <table>
                    <thead>
                        <tr>
                            <th>Môn</th>
                            <th>Mã lớp</th>
                            <th>Số ca học</th>
                            <th>Thành tiền</th>
                            <th>Ghi chú</th>
                        </tr>
                    </thead>
                    <tbody>
                        %s
                    </tbody>
                </table>

                <div class="notice">
                    Quý phụ huynh vui lòng hoàn thành học phí cho con trước ngày <span class="highlight">%s</span>
                </div>

                <div class="payment-info">
                    Hình thức nộp tiền: Quý phụ huynh có thể nộp tiền mặt Trực tiếp tại Trung tâm hoặc chuyển khoản theo thông tin sau:<br>
                    • Số tài khoản: %s mở tại ngân hàng %s<br>
                    • NgườI thụ hưởng: %s<br>
                    • Nội dung chuyển khoản: <span class="highlight">%s</span>
                </div>

                <div class="qr-section">
                    <img src="%s" alt="QR Code" />
                    <div>Quét mã QR để thanh toán</div>
                </div>

                <div class="note">
                    <strong>Ghi chú:</strong> Để đảm bảo chất lượng học tập. Trung tâm yêu cầu các con đi học đầy đủ, đúng giờ. Từ tháng 8 trở đi, trung tâm không hoàn học phí cho các buổi con nghỉ khi lớp học diễn ra. Khi con nghỉ, phụ huynh và học sinh cập nhật học liệu từ trung tâm và đọc các thông tin liên quan trong nhóm lớp.
                </div>

                <div class="footer">Trân trọng cảm ơn!</div>
            </body>
            </html>
            """.formatted(
                escapeHtml(studentName),
                escapeHtml(phone),
                escapeHtml(className),
                month, year,
                subjectsRows,
                dueDate,
                escapeHtml(accountNumber),
                escapeHtml(bank),
                escapeHtml(accountName),
                escapeHtml(transferContent),
                qrCodeUrl
        );
    }

    private String buildSubjectsRows(List<Map<String, Object>> subjects, String totalAmount) {
        if (subjects == null || subjects.isEmpty()) {
            return """
                <tr>
                    <td colspan="5" style="text-align: center;">Không có dữ liệu chi tiết</td>
                </tr>
                <tr class="total-row">
                    <td colspan="3"><strong>Tổng cộng</strong></td>
                    <td colspan="2" class="amount"><strong>%s</strong></td>
                </tr>
                """.formatted(totalAmount);
        }

        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> subject : subjects) {
            String name = getString(subject, "name", "");
            String classCode = getString(subject, "classCode", "");
            String sessions = getString(subject, "sessions", "");
            String amount = formatCurrency(getBigDecimal(subject, "amount", null));
            String note = getString(subject, "note", "-");

            sb.append("<tr>")
                    .append("<td>").append(escapeHtml(name)).append("</td>")
                    .append("<td>").append(escapeHtml(classCode)).append("</td>")
                    .append("<td>").append(escapeHtml(sessions)).append("</td>")
                    .append("<td class=\"amount\">").append(amount).append("</td>")
                    .append("<td>").append(escapeHtml(note)).append("</td>")
                    .append("</tr>");
        }

        sb.append("""
            <tr class="total-row">
                <td colspan="3"><strong>Tổng cộng</strong></td>
                <td colspan="2" class="amount"><strong>%s</strong></td>
            </tr>
            """.formatted(totalAmount));

        return sb.toString();
    }

    /**
     * Render HTML sang file PNG sử dụng Flying Saucer
     */
    private void renderHtmlToImage(String html, File outputFile) throws IOException {
        BufferedImage image = Graphics2DRenderer.renderToImage(html, WIDTH, -1);

        // Scale up để ảnh sắc nét hơn
        int scaledWidth = (int) (image.getWidth() * SCALE);
        int scaledHeight = (int) (image.getHeight() * SCALE);
        BufferedImage scaledImage = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = scaledImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.drawImage(image, 0, 0, scaledWidth, scaledHeight, null);
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(scaledImage, "png", baos);
        Files.write(outputFile.toPath(), baos.toByteArray());

        log.info("Rendered fee image: {}x{}", scaledWidth, scaledHeight);
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private BigDecimal getBigDecimal(Map<String, Object> map, String key, BigDecimal defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
        try {
            return new BigDecimal(value.toString());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "-";
        NumberFormat formatter = NumberFormat.getInstance(new Locale("vi", "VN"));
        return formatter.format(amount.longValue());
    }

    /**
     * Đọc file ảnh và encode sang base64 (không cần shared volume với Zalo API)
     */
    public String encodeImageToBase64(String imagePath) throws IOException {
        byte[] imageBytes = Files.readAllBytes(Path.of(imagePath));
        return Base64.getEncoder().encodeToString(imageBytes);
    }
}
