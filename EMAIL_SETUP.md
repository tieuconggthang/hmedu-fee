# 📧 Hướng Dẫn Cấu Hình Email (Gmail IMAP)

Tính năng này cho phép app tự động đọc email thông báo chuyển khoản từ Gmail và cập nhật trạng thái thanh toán trong database.

## 📝 Mục Lục

1. [Tạo Gmail App Password](#1-tạo-gmail-app-password)
2. [Cấu Hình Application](#2-cấu-hình-application)
3. [Chọn Folder/Đọc Đúng Nguồn](#3-chọn-folderđọc-đúng-nguồn)
4. [Chạy Kiểm Tra Email](#4-chạy-kiểm-tra-email)
5. [Xem Logs](#5-xem-logs)
6. [Troubleshooting](#6-troubleshooting)

---

## 1. Tạo Gmail App Password

**KHÔNG DÙNG** mật khẩu Gmail thường! Phải tạo App Password riêng:

### Bước 1: Bật 2-Factor Authentication
1. Vào https://myaccount.google.com/
2. Security → 2-Step Verification → Bật

### Bước 2: Tạo App Password
1. Security → App passwords
2. Chọn app: **Mail**
3. Chọn device: **Other (Custom name)** → Nhập "HMEDU Fee Service"
4. Click **Generate**
5. Copy mã 16 ký tự (VD: `abcd efgh ijkl mnop`)

> **Lưu ý**: Mã này chỉ hiển thị 1 lần, copy ngay!

---

## 2. Cấu Hình Application

### Cách 1: Environment Variables (Khuyến nghị)

```bash
# Windows Command Prompt
set EMAIL_ENABLED=true
set EMAIL_USERNAME=your-email@gmail.com
set EMAIL_PASSWORD=abcd efgh ijkl mnop
set EMAIL_FOLDER=INBOX
set EMAIL_CHECK_INTERVAL=300000

# Windows PowerShell
$env:EMAIL_ENABLED="true"
$env:EMAIL_USERNAME="your-email@gmail.com"
$env:EMAIL_PASSWORD="abcd efgh ijkl mnop"
$env:EMAIL_FOLDER="INBOX"

# Linux/Mac
export EMAIL_ENABLED=true
export EMAIL_USERNAME=your-email@gmail.com
export EMAIL_PASSWORD="abcd efgh ijkl mnop"
export EMAIL_FOLDER=INBOX
```

### Cách 2: Docker Compose

```yaml
services:
  fee-service:
    environment:
      - EMAIL_ENABLED=true
      - EMAIL_USERNAME=your-email@gmail.com
      - EMAIL_PASSWORD=abcd efgh ijkl mnop
      - EMAIL_FOLDER=INBOX
      - EMAIL_CHECK_INTERVAL=300000
```

### Cách 3: Trực tiếp trong application.yml

```yaml
email:
  enabled: true
  username: your-email@gmail.com
  password: "abcd efgh ijkl mnop"
  folder: INBOX
  check-interval: 300000  # 5 phút
```

---

## 3. Chọn Folder/Đọc Đúng Nguồn

### Các Folder Thường Dùng

| Nguồn Email | Folder Name | Mô tả |
|-------------|-------------|-------|
| **Techcombank** | `INBOX` | Email chuyển khoản vào INBOX |
| **Có filter** | `Techcombank` | Filter tự tạo trong Gmail |
| **Nhiều ngân hàng** | `Thông báo giao dịch` | Folder tổng hợp |

### Cách Tạo Filter Gmail

1. Vào Gmail → Search: `from:notify@techcombank.com.vn`
2. Click ⚙️ → "Create filter"
3. ✓ "Apply the label" → "New label" → "Techcombank"
4. ✓ "Skip Inbox (Archive)"
5. Click "Create filter"

Sau đó set `EMAIL_FOLDER=Techcombank`

---

## 4. Chạy Kiểm Tra Email

### Chạy Định Kỳ (Mặc định)

App tự động check email mỗi 5 phút:

```bash
java -jar fee-collection-service.jar
```

### Chạy Thủ Công (1 lần)

```bash
# Kiểm tra email ngay lập tức
java -jar fee-collection-service.jar --check-email

# Đồng bộ 7 ngày lịch sử
java -jar fee-collection-service.jar --sync-email

# Đồng bộ 30 ngày lịch sử
java -jar fee-collection-service.jar --sync-email 30
```

### Docker

```bash
# Kiểm tra thủ công
docker exec fee-service java -jar app.jar --check-email

# Đồng bộ 30 ngày
docker exec fee-service java -jar app.jar --sync-email 30
```

---

## 5. Xem Logs

### Khi có email mới

```
🔄 Bắt đầu kiểm tra email...
📧 Tìm thấy 3 email chưa đọc trong folder 'INBOX'
✅ Đã parse giao dịch: TransactionInfo{bank='Techcombank', amount=2500000 VND, ...}
✅ Đã cập nhật thanh toán thành công: MTC=2605090001, Amount=2500000, From=NGUYEN VAN A
✅ Hoàn thành kiểm tra email: 3 giao dịch xử lý, 2 khớp với hệ thống
```

### Khi không tìm thấy MTC code

```
⚠️ Không tìm thấy MTC code trong giao dịch
⚠️ Không tìm thấy payment record với MTC code: null
```

### Kiểm tra file log

```bash
# Xem log real-time
tail -f logs/fee-collection.log

# Tìm log email
grep "Email\|email\|giao dịch" logs/fee-collection.log
```

---

## 6. Troubleshooting

### ❌ "Invalid credentials"

**Nguyên nhân**: Dùng mật khẩu Gmail thay vì App Password  
**Giải pháp**: Tạo App Password theo hướng dẫn bên trên

### ❌ "Folder 'XXX' không tồn tại"

**Kiểm tra các folder có sẵn**:
```bash
# Bật debug trong application.yml
email:
  debug: true
```

Log sẽ hiển thị:
```
📂 Các folder có sẵn:
   - INBOX (type: MAILBOX)
   - Techcombank (type: MAILBOX)
   - [Gmail]/Trash (type: MAILBOX)
```

### ❌ Không nhận được email giao dịch

**Kiểm tra**:
1. Email có được gửi vào folder đúng không?
2. Subject có chứa từ khóa như "chuyển khoản", "giao dịch" không?
3. Trong email có chứa MTC code không?

**Debug**:
```bash
# Chạy với log chi tiết
java -jar fee-collection-service.jar --check-email --debug
```

### ❌ "Less secure app access"

**Giải pháp**:
1. Dùng App Password thay vì mật khẩu thường
2. Hoặc bật IMAP trong Gmail Settings:
   - Settings → Forwarding and POP/IMAP → IMAP Access: **Enable**

---

## 🎯 Tóm Tắt Quy Trình

```
1. Tạo Gmail App Password
   ↓
2. Set environment variables
   EMAIL_ENABLED=true
   EMAIL_USERNAME=your@gmail.com
   EMAIL_PASSWORD=xxxx xxxx xxxx xxxx
   ↓
3. Chạy app
   java -jar fee-collection-service.jar
   ↓
4. App tự động check email mỗi 5 phút
   ↓
5. Parse email giao dịch → Tìm MTC code
   ↓
6. Update database: status = PAID
```

---

## 📚 Thông Tin Kỹ Thuật

| Thuộc tính | Giá trị mặc định | Mô tả |
|-----------|------------------|-------|
| `email.enabled` | `false` | Bật/tắt tính năng |
| `email.host` | `imap.gmail.com` | IMAP server |
| `email.port` | `993` | IMAP SSL port |
| `email.folder` | `INBOX` | Folder đọc email |
| `email.check-interval` | `300000` | ms (5 phút) |

### Các Ngân Hàng Hỗ Trợ

- ✅ Techcombank (full support)
- ✅ VPBank (basic)
- ✅ Vietcombank (basic)
- ⚠️ Các ngân hàng khác (generic parser)

---

**Cần hỗ trợ thêm?** Kiểm tra logs và cấu hình, sau đó liên hệ dev team! 🚀
