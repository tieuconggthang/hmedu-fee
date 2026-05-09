# HMEDU Fee Collection - Deploy Guide

## Yêu cầu

- Docker & Docker Compose đã cài đặt
- Zalo API đang chạy tại: `10.10.33.99:10000`
- File Excel học phí theo đúng cấu trúc cột

## Cấu trúc File Excel

| Cột | Nội dung | Ví dụ |
|------|----------|--------|
| A (0) | Tên học sinh | Nguyễn Văn A |
| D (3) | SĐT người nhận | 0396935585 |
| R (17) | Số tiền | 800000 |
| G (6) | Nội dung CK | Hoc phi thang 4 |
| V (21) | Tên chủ TK | Nguyen Van A |
| W (22) | Số tài khoản | 1234567890 |
| X (23) | Ngân hàng | Techcombank |

## Các bước Deploy

### 1. Copy file Excel vào thư mục data

```bash
cd /home/thangtc/hmedu-fee-service
cp "/path/to/Theo dõi học phí tháng 5.xlsx" data/fee.xlsx
```

### 2. Build Docker image

```bash
docker build -t hmedu-fee:latest .
```

### 3. Chạy với docker-compose

```bash
# Chế độ scheduled (tự động chạy ngày 1 hàng tháng)
docker-compose up -d

# Hoặc chạy ngay lập tức (manual mode)
docker-compose run --rm fee-collection
```

### 4. Kiểm tra logs

```bash
# Xem logs real-time
docker logs -f hmedu-fee-service

# Hoặc xem file logs
tail -f logs/fee-collection.log
```

### 5. Dừng service

```bash
docker-compose down
```

## Chế độ Manual (Test)

Chỉnh sửa `docker-compose.yml`:

```yaml
environment:
  # Thay đổi cron để test
  - FEE_CRON=0 */5 * * * ?  # Chạy mỗi 5 phút
```

Hoặc chạy trực tiếp:

```bash
docker run --rm \\
  -v $(pwd)/data:/app/data \\
  -v $(pwd)/logs:/app/logs \\
  -e SPRING_PROFILES_ACTIVE=manual \\
  -e ZALO_API_URL=http://10.10.33.99:10000 \\
  hmedu-fee:latest
```

## Kiểm tra kết quả

- H2 Database: `data/fee-collection-db.mv.db`
- Logs: `logs/fee-collection.log`
- Zalo message sẽ gửi đến SĐT trong file Excel

## Troubleshooting

### Lỗi: "Cannot read Excel file"
- Kiểm tra file tồn tại: `ls -la data/fee.xlsx`
- Kiểm tra quyền: `chmod 644 data/fee.xlsx`

### Lỗi: "Zalo API connection refused"
- Kiểm tra Zalo API đang chạy: `curl http://10.10.33.99:10000/friends`
- Nếu chưa chạy, khởi động Zalo API trước

### Lỗi: "No recipients found"
- Kiểm tra cột D có số điện thoại
- Kiểm tra cột R có số tiền > 0
