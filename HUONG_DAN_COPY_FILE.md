# 📁 HƯỚNG DẪN COPY FILE EXCEL

## 📅 MỖI THÁNG CHỈ CẦN LÀM 3 BƯỚC:

### Bước 1: Copy file Excel vào thư mục `data/`

```bash
cd /home/thangtc/hmedu-fee-service

# Copy file tháng mới vào
cp "/path/to/Theo dõi học phí tháng 5.xlsx" data/

# Hoặc copy nhiều file cùng lúc
cp "/path/to/Theo dõi học phí tháng 6.xlsx" data/
cp "/path/to/Theo dõi học phí tháng 7.xlsx" data/
```

### Bước 2: Kiểm tra file đã copy

```bash
ls -lh data/

# Kết quả ví dụ:
# -rw-r--r-- 1 user user 125K May  9 08:00 'Theo dõi học phí tháng 5.xlsx'
# -rw-r--r-- 1 user user 130K Jun  9 08:00 'Theo dõi học phí tháng 6.xlsx'
```

### Bước 3: Chạy app xử lý

```bash
# Khởi động app
docker-compose up -d

# Hoặc chạy ngay lập tức (không chờ cron)
docker-compose run --rm fee-collection

# Xem tiến trình
docker-compose logs -f fee-collection
```

---

## 📂 CẤU TRÚC THƯ MỤC

```
/home/thangtc/hmedu-fee-service/
├── data/                    ← ✓ COPY FILE EXCEL VÀO ĐÂY
│   ├── Theo dõi học phí tháng 5.xlsx
│   ├── Theo dõi học phí tháng 6.xlsx
│   └── fee-collection-db.mv.db  (DB tự tạo)
├── logs/                    ← Chứa file log
│   └── fee-collection.log
├── docker-compose.yml       ← Cấu hình Docker
└── Dockerfile               ← Build image
```

---

## ⚠️ LƯU Ý QUAN TRỌNG

### 1. Vị trí COPY FILE
- ĐÚnh dấu ✓: Copy vào `/home/thangtc/hmedu-fee-service/data/`
- Không cần đổi tên file, giữ nguyên tên gốc
- Có thể copy nhiều file cùng lúc

### 2. Định dạng file
- Chỉ nhận file `.xlsx` (Excel 2007+)
- Không nhận `.xls` (Excel 97-2003)

### 3. Cấu trúc Excel
App đọc các cột theo thứ tự:
- **Cột A**: Tên học sinh
- **Cột D**: Số điện thoại (người nhận)
- **Cột R**: Số tiền học phí
- **Cột G**: Nội dung chuyển khoản
- **Cột V**: Tên tài khoản
- **Cột W**: Số tài khoản
- **Cột X**: Ngân hàng

---

## 📊 KIỂM TRA KẾT QUẢ

### Xem file đã xử lý chưa:
```bash
# Vào container xem DB
docker exec -it hmedu-fee-service sh

# Query xem danh sách file
java -cp h2.jar org.h2.tools.Shell \
  -url jdbc:h2:/app/data/fee-collection-db \
  -user sa \
  -sql "SELECT file_name, status, processed_rows, total_rows FROM excel_file_status;"
```

### Xem logs:
```bash
tail -f logs/fee-collection.log
```

### Xem DB:
```bash
ls -lh data/fee-collection-db.mv.db
```

---

## 🔄 CHẠY LẠI FILE ĐÃ XỚT

Nếu muốn xử lý lại file đã xong:

```bash
# 1. Xóa trạng thái file trong DB
docker exec hmedu-fee-service sh -c "java -cp h2.jar org.h2.tools.Shell -url jdbc:h2:/app/data/fee-collection-db -user sa -sql 'DELETE FROM excel_file_status WHERE file_name='<Tên file>';'"

# 2. Chạy lại app
docker-compose restart fee-collection
```

---

## ✅ TÓM TẮT

| Thao tác | Lệnh |
|---------|------|
| **Copy file** | `cp file.xlsx data/` |
| **Xem file trong data** | `ls -lh data/` |
| **Chạy app** | `docker-compose up -d` |
| **Xem logs** | `docker-compose logs -f` |
| **Dừng app** | `docker-compose down` |

**Chỉ cần nhớ: COPY VÀO `data/` LÀ XONG!** 🎉
