# HMEDU Fee Collection Service

Ứng dụng Java tự động thu học phí - Tích hợp VietQR + Zalo API

## Tính năng

- 📊 **Đọc Excel** - Danh sách học sinh và học phí
- 💳 **Tạo VietQR** - Mã QR chuyển tiền tự động  
- 💬 **Gửi Zalo** - Thông báo + QR đến phụ huynh
- 🗄️ **Lưu Database** - Theo dõi trạng thái thanh toán
- ⏰ **Chạy định kỳ** - Tự động chạy theo lịch cấu hình

## Yêu cầu

- Java 17+
- Maven 3.8+
- File Excel danh sách học sinh
- Zalo API Service đang chạy (Docker)

## Cài đặt & Chạy

### 1. Build

```bash
build.bat
```

Hoặc:

```bash
mvn clean package -DskipTests
```

### 2. Chuẩn bị dữ liệu

```
data/
  └── Theo dõi học phí tháng 4.xlsx   <-- Copy file Excel vào đây
```

**Cấu trúc Excel:**
- Cột A (0): Tên học sinh
- Cột B (1): Số điện thoại (Zalo)
- Cột R (17): Số tiền học phí
- Cột G (6): Nội dung chuyển tiền
- Cột V (21): Tên tài khoản
- Cột W (22): Số tài khoản
- Cột X (23): Ngân hàng

### 3. Chạy ứng dụng

```bash
run.bat
```

Hoặc:

```bash
java -jar target/fee-collection-service-1.0.0.jar
```

## Cấu hình

### application.yml

```yaml
hmedu:
  fee-collection:
    excel:
      file-path: ./data/Theo dõi học phí tháng 4.xlsx
      columns:
        student-name: 0
        phone: 1
        amount: 17      # Cột R
        content: 6      # Cột G
        account-name: 21   # Cột V
        account-number: 22 # Cột W
        bank: 23           # Cột X
    
    vietqr:
      enabled: true
      default-bank-id: 970407  # Techcombank
    
    zalo:
      enabled: true
      api-url: http://10.10.33.99:10000
    
    scheduler:
      cron: "0 0 8 1 * ?"  # 8h sáng ngày 1 hàng tháng
      # Test: "0 * * * * ?"  # Mỗi phút (test)
```

### Lịch chạy (Cron)

- `0 0 8 1 * ?` - 8h sáng ngày 1 hàng tháng
- `0 0 9 * * MON` - 9h sáng mỗi thứ 2
- `0 */6 * * * ?` - Mỗi 6 giờ

## Database

H2 database file: `data/fee-collection-db.mv.db`

**Bảng fee_collection_records:**
- id: Mã giao dịch
- student_name: Tên học sinh
- phone_number: SĐT
- amount: Số tiền
- content: Nội dung CK
- bank_name: Ngân hàng
- qr_code_url: Link QR
- zalo_message_sent: Đã gửi Zalo?
- payment_status: PENDING/PAID/NOTIFIED
- month_year: Tháng/Năm

## Log

File log: `logs/fee-collection.log`

## Kiểm tra

1. **Zalo API running?**
   ```bash
   curl http://10.10.33.99:10000/login/status
   ```

2. **Excel đúng định dạng?**
   - Kiểm tra cột theo cấu hình
   - Số tiền là số (không có dấu phẩy)

3. **Xem log chạy:**
   ```bash
   tail -f logs/fee-collection.log
   ```

## Troubleshooting

| Lỗi | Nguyên nhân | Cách fix |
|-----|-------------|----------|
| Không tìm thấy Excel | Sai đường dẫn | Kiểm tra `hmedu.fee-collection.excel.file-path` |
| Không gửi được Zalo | Zalo API chưa login | Vào `http://IP:10000/login/qr/web` để login |
| QR sai | Sai mã ngân hàng | Kiểm tra `bank` trong Excel |

## Liên kết

- Zalo API Service: https://github.com/tieuconggthang/zalo-auto
- VietQR: https://vietqr.io
