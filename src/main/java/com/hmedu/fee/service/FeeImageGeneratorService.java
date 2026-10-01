package com.hmedu.fee.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Service tạo ảnh thông báo học phí bằng Java 2D
 */
@Slf4j
@Service
public class FeeImageGeneratorService {

    private static final int WIDTH = 900;
    private static final int PADDING = 40;
    private static final Color ORANGE = new Color(242, 101, 34);
    private static final Color RED = new Color(204, 0, 0);
    private static final Color BLACK = Color.BLACK;
    private static final Color WHITE = Color.WHITE;
    private static final Color GRAY = new Color(245, 245, 245);
    private static final Color DARK_GRAY = new Color(80, 80, 80);

    /**
     * Tạo ảnh thông báo học phí và lưu vào thư mục data/images
     */
    public String generateFeeImage(Map<String, Object> data, String qrCodeUrl) throws Exception {
        BufferedImage image = drawFeeImage(data, qrCodeUrl);

        Path imagesDir = Paths.get("/app/data/images");
        if (!Files.exists(imagesDir)) {
            Files.createDirectories(imagesDir);
        }

        String fileName = "fee_" + System.currentTimeMillis() + ".png";
        Path outputPath = imagesDir.resolve(fileName);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        Files.write(outputPath, baos.toByteArray());

        log.info("Generated fee image: {} ({}x{})", outputPath, image.getWidth(), image.getHeight());
        return outputPath.toString();
    }

    /**
     * Đọc file ảnh và encode sang base64
     */
    public String encodeImageToBase64(String imagePath) throws IOException {
        byte[] imageBytes = Files.readAllBytes(Path.of(imagePath));
        return Base64.getEncoder().encodeToString(imageBytes);
    }

    private BufferedImage drawFeeImage(Map<String, Object> data, String qrCodeUrl) throws Exception {
        // Tính chiều cao trước bằng cách vẽ tạm
        BufferedImage tempImage = new BufferedImage(WIDTH, 2000, BufferedImage.TYPE_INT_ARGB);
        Graphics2D tempG = tempImage.createGraphics();
        tempG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        tempG.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        int y = drawContent(tempG, data, qrCodeUrl, 0, true);
        tempG.dispose();

        int height = y + PADDING;

        // Vẽ thật
        BufferedImage image = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        // Nền trắng
        g.setColor(WHITE);
        g.fillRect(0, 0, WIDTH, height);

        drawContent(g, data, qrCodeUrl, 0, false);
        g.dispose();

        return image;
    }

    private int drawContent(Graphics2D g, Map<String, Object> data, String qrCodeUrl, int startY, boolean measureOnly) throws Exception {
        int x = PADDING;
        int y = startY + PADDING;
        int contentWidth = WIDTH - 2 * PADDING;

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
        BigDecimal totalAmount = getBigDecimal(data, "amount", BigDecimal.ZERO);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> subjects = (List<Map<String, Object>>) data.get("subjects");

        // Header
        y = drawHeader(g, x, y, contentWidth, measureOnly);
        y += 25;

        // Thông tin phụ huynh
        g.setColor(BLACK);
        g.setFont(new Font("Arial", Font.PLAIN, 16));
        y = drawText(g, "Kính gửi: Phụ huynh em " + studentName + "/" + phone + " Lớp " + className, x, y, contentWidth, measureOnly);
        y += 5;
        y = drawText(g, "HM EDU gửi tới Quý Phụ huynh Thông báo học phí tháng " + month + "/" + year + " của con như sau:", x, y, contentWidth, measureOnly);
        y += 20;

        // Bảng môn học
        y = drawSubjectsTable(g, x, y, contentWidth, subjects, totalAmount, measureOnly);
        y += 20;

        // Hạn nộp
        g.setFont(new Font("Arial", Font.PLAIN, 16));
        y = drawText(g, "Quý phụ huynh vui lòng hoàn thành học phí cho con trước ngày ", x, y, contentWidth, measureOnly);
        // Vẽ phần dueDate màu đỏ
        Font dueFont = new Font("Arial", Font.BOLD, 16);
        g.setFont(dueFont);
        g.setColor(RED);
        y = drawText(g, dueDate, x, y, contentWidth, measureOnly);
        g.setColor(BLACK);
        y += 20;

        // Thông tin thanh toán
        g.setFont(new Font("Arial", Font.PLAIN, 15));
        y = drawText(g, "Hình thức nộp tiền: Quý phụ huynh có thể nộp tiền mặt Trực tiếp tại Trung tâm hoặc chuyển khoản theo thông tin sau:", x, y, contentWidth, measureOnly);
        y += 5;
        y = drawText(g, "• Số tài khoản: " + accountNumber + " mở tại ngân hàng " + bank, x + 10, y, contentWidth - 10, measureOnly);
        y = drawText(g, "• NgườI thụ hưởng: " + accountName, x + 10, y, contentWidth - 10, measureOnly);

        String transferContent = studentName + " " + phone;
        if (StringUtils.hasText(transactionId)) {
            transferContent += " MTC" + transactionId;
        }
        y = drawText(g, "• Nội dung chuyển khoản: " + transferContent, x + 10, y, contentWidth - 10, measureOnly);
        y += 25;

        // QR Code
        BufferedImage qrImage = downloadImage(qrCodeUrl);
        if (qrImage != null) {
            int qrSize = 180;
            int qrX = (WIDTH - qrSize) / 2;
            if (!measureOnly) {
                g.drawImage(qrImage, qrX, y, qrSize, qrSize, null);
            }
            y += qrSize + 5;
            g.setFont(new Font("Arial", Font.PLAIN, 14));
            g.setColor(DARK_GRAY);
            y = drawCenteredText(g, "Quét mã QR để thanh toán", y, measureOnly);
            g.setColor(BLACK);
        }
        y += 20;

        // Ghi chú
        g.setFont(new Font("Arial", Font.PLAIN, 13));
        y = drawText(g, "Ghi chú: Để đảm bảo chất lượng học tập. Trung tâm yêu cầu các con đi học đầy đủ, đúng giờ. Từ tháng 8 trở đi, trung tâm không hoàn học phí cho các buổi con nghỉ khi lớp học diễn ra. Khi con nghỉ, phụ huynh và học sinh cập nhật học liệu từ trung tâm và đọc các thông tin liên quan trong nhóm lớp.", x, y, contentWidth, measureOnly);
        y += 25;

        // Footer
        g.setFont(new Font("Arial", Font.BOLD, 16));
        y = drawCenteredText(g, "Trân trọng cảm ơn!", y, measureOnly);

        return y;
    }

    private int drawHeader(Graphics2D g, int x, int y, int contentWidth, boolean measureOnly) {
        int logoSize = 80;
        int logoY = y;

        if (!measureOnly) {
            // Vẽ logo hình tròn
            g.setColor(ORANGE);
            g.fillOval(x, logoY, logoSize, logoSize);
            g.setColor(WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 20));
            FontMetrics fm = g.getFontMetrics();
            String line1 = "HM";
            int line1X = x + (logoSize - fm.stringWidth(line1)) / 2;
            g.drawString(line1, line1X, logoY + 28);
            String line2 = "EDU";
            g.setFont(new Font("Arial", Font.BOLD, 16));
            fm = g.getFontMetrics();
            int line2X = x + (logoSize - fm.stringWidth(line2)) / 2;
            g.drawString(line2, line2X, logoY + 48);
            g.setFont(new Font("Arial", Font.PLAIN, 8));
            fm = g.getFontMetrics();
            String line3 = "HỌC THẬT - THI THẬT";
            int line3X = x + (logoSize - fm.stringWidth(line3)) / 2;
            g.drawString(line3, line3X, logoY + 64);

            // Tiêu đề
            g.setColor(BLACK);
            g.setFont(new Font("Arial", Font.BOLD, 28));
            String title = "THÔNG BÁO HỌC PHÍ";
            FontMetrics titleFm = g.getFontMetrics();
            int titleX = (WIDTH - titleFm.stringWidth(title)) / 2;
            g.drawString(title, titleX, y + 45);

            // Contact
            g.setFont(new Font("Arial", Font.PLAIN, 13));
            g.setColor(DARK_GRAY);
            g.drawString("Số 22 Ngõ 1 Lê Văn Thiêm", WIDTH - PADDING - 160, y + 25);
            g.drawString("0986.22.82.48", WIDTH - PADDING - 160, y + 45);
        }

        return y + logoSize + 10;
    }

    private int drawSubjectsTable(Graphics2D g, int x, int y, int width, List<Map<String, Object>> subjects, BigDecimal totalAmount, boolean measureOnly) {
        String[] headers = {"Môn", "Mã lớp", "Số ca học", "Thành tiền", "Ghi chú"};
        int[] colWidths = {width / 4, width / 6, width / 6, width / 5, width / 5};

        int rowHeight = 32;
        int currentY = y;

        // Header
        if (!measureOnly) {
            g.setColor(GRAY);
            g.fillRect(x, currentY, width, rowHeight);
            g.setColor(BLACK);
            g.drawRect(x, currentY, width, rowHeight);
        }
        g.setFont(new Font("Arial", Font.BOLD, 14));
        int cellX = x;
        for (int i = 0; i < headers.length; i++) {
            if (!measureOnly) {
                g.setColor(BLACK);
                g.drawLine(cellX + colWidths[i], currentY, cellX + colWidths[i], currentY + rowHeight);
                FontMetrics fm = g.getFontMetrics();
                g.drawString(headers[i], cellX + 8, currentY + 22);
            }
            cellX += colWidths[i];
        }
        currentY += rowHeight;

        // Data rows
        g.setFont(new Font("Arial", Font.PLAIN, 14));
        if (subjects != null) {
            for (Map<String, Object> subject : subjects) {
                String name = getString(subject, "name", "");
                String classCode = getString(subject, "classCode", "");
                String sessions = getString(subject, "sessions", "");
                BigDecimal amount = getBigDecimal(subject, "amount", null);
                String note = getString(subject, "note", "-");

                if (!measureOnly) {
                    g.setColor(BLACK);
                    g.drawRect(x, currentY, width, rowHeight);
                }

                String[] values = {name, classCode, sessions, formatCurrency(amount), note};
                cellX = x;
                for (int i = 0; i < values.length; i++) {
                    if (!measureOnly) {
                        g.setColor(BLACK);
                        g.drawLine(cellX + colWidths[i], currentY, cellX + colWidths[i], currentY + rowHeight);
                        FontMetrics fm = g.getFontMetrics();
                        if (i == 3) {
                            g.drawString(values[i], cellX + colWidths[i] - 8 - fm.stringWidth(values[i]), currentY + 22);
                        } else {
                            g.drawString(values[i], cellX + 8, currentY + 22);
                        }
                    }
                    cellX += colWidths[i];
                }
                currentY += rowHeight;
            }
        }

        // Total row
        if (!measureOnly) {
            g.setColor(GRAY);
            g.fillRect(x, currentY, width, rowHeight);
            g.setColor(BLACK);
            g.drawRect(x, currentY, width, rowHeight);
            g.setFont(new Font("Arial", Font.BOLD, 14));
            FontMetrics fm = g.getFontMetrics();
            g.drawString("Tổng cộng", x + 8, currentY + 22);
            String totalStr = formatCurrency(totalAmount);
            g.drawString(totalStr, x + colWidths[0] + colWidths[1] + colWidths[2] + colWidths[3] - 8 - fm.stringWidth(totalStr), currentY + 22);
        }

        return currentY + rowHeight;
    }

    private int drawText(Graphics2D g, String text, int x, int y, int maxWidth, boolean measureOnly) {
        FontMetrics fm = g.getFontMetrics();
        int lineHeight = fm.getHeight() + 2;
        int currentY = y;

        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();

        for (String word : words) {
            String testLine = line.length() > 0 ? line + " " + word : word;
            if (fm.stringWidth(testLine) > maxWidth && line.length() > 0) {
                if (!measureOnly) {
                    g.drawString(line.toString(), x, currentY + fm.getAscent());
                }
                currentY += lineHeight;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(testLine);
            }
        }
        if (line.length() > 0) {
            if (!measureOnly) {
                g.drawString(line.toString(), x, currentY + fm.getAscent());
            }
            currentY += lineHeight;
        }

        return currentY;
    }

    private int drawCenteredText(Graphics2D g, String text, int y, boolean measureOnly) {
        FontMetrics fm = g.getFontMetrics();
        int x = (WIDTH - fm.stringWidth(text)) / 2;
        if (!measureOnly) {
            g.drawString(text, x, y + fm.getAscent());
        }
        return y + fm.getHeight();
    }

    private BufferedImage downloadImage(String imageUrl) {
        try {
            URL url = new URL(imageUrl);
            return ImageIO.read(url);
        } catch (Exception e) {
            log.error("Error downloading QR image: {}", e.getMessage());
            return null;
        }
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
}
