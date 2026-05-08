# HMEDU Fee Collection Service

Hệ thống tự động thu học phí tích hợp Excel + VietQR + Zalo

## 🎯 Chức năng

- 📊 Đọc danh sách học sinh từ file Excel
- 💳 Tạo mã QR VietQR cho từng học sinh
- 📱 Gửi thông báo + QR qua Zalo
- 🗄️ Lưu trạng thái vào database (H2)
- ⏰ Chạy định kỳ theo cron

## 🚀 Hướng dẫn chạy

### Bước 1: Chuẩn bị

```bash
# 1. Tạo thư mục data
mkdir -p data

# 2. Copy file Excel vào
# Cột A: Tên HS, Cột D: SĐT, Cột R: Số tiền
# Cột G: Nội dung, Cột V: Tên TK, Cột W: Số TK, Cột X: Ngân hàng
cp "Theo dõi học phí tháng 4.xlsx" data/fee.xlsx
```

### Bước 2: Đảm bảo Zalo API đang chạy

```bash
# Kiểm tra Zalo API
curl http://10.10.33.99:10000/health

# Nếu chưa chạy, khởi động Zalo API trước:
cd /path/to/zalo-api-service
docker-compose up -d
```

### Bước 3: Build và chạy

```bash
# Cách 1: Dùng script
chmod +x run.sh
./run.sh

# Cách 2: Chạy thủ công
docker-compose build
docker-compose up -d
```

### Bước 4: Theo dõi logs

```bash
# Xem logs real-time
docker-compose logs -f fee-collection

# Xem logs file
tail -f logs/fee-collection.log
```

## ⚙️ Cấu hình

Chỉnh sửa `docker-compose.yml`:

```yaml
environment:
  - FEE_EXCEL_PATH=/app/data/fee.xlsx    # Đường dẫn file Excel
  - ZALO_API_URL=http://10.10.33.99:10000 # URL Zalo API
  - FEE_CRON=0 0 8 1 * ?                 # Cron schedule
```

## 📁 Cấu trúc thư mục

```
hmedu-fee-service/
├── Dockerfile              # Build Java app
├── docker-compose.yml      # Orchestration
├── run.sh                  # Script chạy
├── pom.xml                 # Maven config
├── src/                    # Source code
├── data/                   # File Excel + Database
│   └── fee.xlsx
└── logs/                   # Log files
    └── fee-collection.log
```

## 🔧 Lệnh hữu ích

```bash
# Dừng service
docker-compose down

# Restart
docker-compose restart

# Xem database
ls -la data/fee-collection-db.mv.db

# Build lại
docker-compose build --no-cache
```

## 📱 Nội dung tin nhắn Zalo

```
🎓 THU HỌC PHÍ THÁNG 04/2025

Kính gửi phụ huynh học sinh [TÊN],

• Học phí: [SỐ TIỀN] VNĐ
• Nội dung CK: [NỘI DUNG] MTC[MÃ]
• Mã tham chiếu: MTC[MÃ SỐ]

Quét mã QR để thanh toán.
```

## 🐛 Troubleshooting

### Lỗi "Cannot find file fee.xlsx"
- Kiểm tra file có trong thư mục `data/`
- Đảm bảo tên file đúng: `fee.xlsx`

### Lỗi kết nối Zalo API
- Kiểm tra Zalo API đang chạy: `curl http://10.10.33.99:10000/health`
- Đảm bảo mạng giữa các container thông nhau

### Lỗi gửi Zalo
- Kiểm tra đã login Zalo chưa: `curl http://10.10.33.99:10000/login/status`
- Nếu chưa login, mở browser: `http://10.10.33.99:10000/login/qr/web`
