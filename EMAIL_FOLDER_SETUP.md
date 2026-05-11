# 📧 Hướng Dẫn Cấu Hình Folder Email

## Tóm Tắt

Module đọc email đã được sửa để:
- ✅ Dùng đúng entity `FeeCollectionRecord` (thay vì `PaymentRecord`)
- ✅ Đọc email từ folder được cấu hình trong `application.yml`
- ✅ Tự động đánh dấu đã đọc (SEEN) sau khi xử lý

---

## 📋 Cấu Hình Folder

### 1. Trong `application.yml`

```yaml
email:
  enabled: true
  username: tieucong.thang@gmail.com
  password: juww iiqq jaho pgbm  # App password (bỏ khoảng trắng khi nhập)
  folder: INBOX  # ⭐ Folder để đọc email
  check-interval: 300000  # 5 phút
```

### 2. Các Folder Có Thể Dùng

| Folder | Mô tả | Khi nào dùng |
|--------|--------|--------------|
| `INBOX` | Hộp thư đến mặc định | Email giao dịch vào trực tiếp |
| `Techcombank` | Filter tự tạo | Chỉ nhận email từ Techcombank |
| `[Gmail]/Important` | Gmail đánh dấu quan trọng | Lọc email quan trọng |

---

## 🔧 Cách Tạo Filter Trong Gmail

### Bước 1: Tạo Label Mới
1. Vào Gmail → Settings (bánh răng) → "See all settings"
2. Tab "Labels" → "Create new label"
3. Nhập tên: `Techcombank` → Create

### Bước 2: Tạo Filter
1. Trong hộp Search, nhập: `from:notify@techcombank.com.vn`
2. Click → "Create filter"
3. Chọn:
   - ☑️ "Apply the label" → "Techcombank"
   - ☑️ "Skip Inbox (Archive)" (tùy chọn)
4. Click "Create filter"

### Bước 3: Cập Nhật Config
```yaml
email:
  folder: Techcombank  # Thay vì INBOX
```

---

## 📚 Các Phương Thức Đọc Email

### 1. `readNewEmails()` - Đọc Email Chưa Đọc

```java
// Chỉ đọc email có flag UNSEEN
// Sau khi xử lý thành công → tự động đánh dấu SEEN
List<EmailMessage> emails = emailService.readNewEmails();
```

**Luồng hoạt động:**
```
1. Kết nối IMAP folder được cấu hình
2. Search email có flag UNSEEN (chưa đọc)
3. Parse từng email
4. Nếu thành công → message.setFlag(SEEN, true)
5. Nếu lỗi → không đánh dấu để thử lại sau
```

### 2. `readAllEmails(since, maxResults)` - Đọc Tất Cả

```java
// Đọc tất cả email (kể cã đã đọc) trong khoảng thờI gian
List<EmailMessage> emails = emailService.readAllEmails(
    LocalDateTime.now().minusDays(7),  // 7 ngày gần đây
    100  // Tối đa 100 email
);
```

**Lưu ý:** Method này dùng cho sync lần đầu, không đánh dấu SEEN.

---

## 🚀 Chạy Kiểm Tra

### 1. Kiểm Tra Tự Động (Mỗi 5 phút)
```bash
export EMAIL_ENABLED=true
export EMAIL_USERNAME=tieucong.thang@gmail.com
export EMAIL_PASSWORD="juwwiiqqjahopgbm"
export EMAIL_FOLDER=Techcombank  # hoặc INBOX

java -jar target/fee-collection-service.jar
```

### 2. Kiểm Tra Thủ Công (1 lần)
```bash
# Đọng bộ 7 ngày lịch sử
curl -X POST http://localhost:8080/api/email/sync/7
```

### 3. Xem Logs
```bash
tail -f logs/fee-collection.log | grep -E "Email|email|giao dịch|MTC"
```

**Log mẫu khi thành công:**
```
2025-05-10 18:00:00 - 🔄 Bắt đầu kiểm tra email từ folder 'Techcombank'...
2025-05-10 18:00:02 - 📨 Đã nhận 3 email mới từ folder 'Techcombank'
2025-05-10 18:00:03 - ✅ Đã parse giao dịch: TransactionInfo{bank='Techcombank', amount=2500000 VND...}
2025-05-10 18:00:04 - ✅ Đã cập nhật thanh toán thành công: MTC=2605100001, Amount=2500000
2025-05-10 18:00:05 - ✅ Hoàn thành kiểm tra email folder 'Techcombank': 3 giao dịch xử lý, 1 khớp với hệ thống
```

---

## ⚠️ Lưu Ý Quan Trọng

1. **Email chỉ đọc 1 lần**: Sau khi xử lý, email được đánh dấu SEEN
2. **Nếu lỗi**: Email không được đánh dấu SEEN để thử lại sau
3. **Folder phải tồn tại**: Nếu folder không tồn tại → log lỗi và liệt kê các folder có sẵn
4. **UNSEEN flag**: Gmail đôi khi delay vài giây khi đánh dấu đã đọc

---

## 📋 Cấu Trúc Đã Sửa

### File Đã Thay Đổi
| File | Thay Đổi |
|------|-----------|
| `EmailNotificationScheduler.java` | Dùng `FeeCollectionRecord` thay `PaymentRecord` |
| | Dùng `FeeCollectionRepository` thay `PaymentRecordRepository` |
| | Log folder name trong tất cả messages |

### File Không Thay Đổi (Hoạt Động Tốt)
| File | Chức Năng |
|------|-----------|
| `EmailService.java` | Đọc email + đánh dấu SEEN sau khi xử lý |
| `EmailConfig.java` | Cấu hình IMAP, đọc folder từ `application.yml` |
| `TransactionEmailParser.java` | Parse email Techcombank/VPBank tìm MTC code |

---

## 📞 Troubleshooting

### Lỗi: "Folder 'XXX' không tồn tại"
```
❌ Folder 'Techcombank' không tồn tại!
📂 Các folder có sẵn:
   - INBOX (type: MAILBOX)
   - [Gmail]/All Mail (type: MAILBOX)
   ...
```

**Giải pháp:** Tạo label trong Gmail trướcc, hoặc dùng folder có sẵn.

### Lỗi: Email đọc đi đọc lại
**Nguyên nhân:** Không được đánh dấu SEEN

**Kiểm tra:** Xem log có dòng "Đã xử lý email" không. Nếu không có → có lỗi parse.

### Lỗi: Không tìm thấy MTC code
**Kiểm tra:** Nội dung email có chứa `MTC2605100001` không? Pattern: `MTC\d{6,12}`

---

## ✅ Tóm Tắt Tính Năng

- 📧 Chỉ đọc từ folder được cấu hình
- ✅ Tự động đánh dấu đã đọc sau khi xử lý
- 🔄 Chạy định kỳ mỗi 5 phút
- 🐝 Hỗ trợ Techcombank, VPBank (và generic parser)
- 📊 Match MTC code với transaction trong DB
