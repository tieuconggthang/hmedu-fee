# 📋 CHEAT SHEET - NHỚ 3 LỆNH NÀY

## 🎯 MỖI THÁNG LÀM 3 BƯỚC:

```bash
# 1. VÀO THƯ MỤC
 cd /home/thangtc/hmedu-fee-service

# 2. COPY FILE EXCEL (QUAN TRỌNG NHẤT)
 cp "/path/to/Theo dõi học phí tháng X.xlsx" data/

# 3. CHẠY APP
 docker-compose up -d
```

---

## 📁 VỊ TRÍ COPY FILE

```
/home/thangtc/hmedu-fee-service/data/
         ↑
         └── COPY FILE EXCEL VÀO ĐÂY
```

**Lệnh kiểm tra:**
```bash
ls -lh /home/thangtc/hmedu-fee-service/data/
```

---

## 🔍 XEM KẾT QUẢ

```bash
# Xem logs
docker-compose logs -f

# Xem file đã xử lý
docker exec hmedu-fee-service sh -c "echo 'SELECT file_name, status FROM excel_file_status;' | java -cp h2.jar org.h2.tools.Shell -url jdbc:h2:/app/data/fee-collection-db -user sa"
```

---

## 🛑 DỪNG APP

```bash
docker-compose down
```

---

## 💡 MẸO NHỚ

> **"Copy vào data, rồi up -d"**
> 
> 1. `cp file.xlsx data/`
> 2. `docker-compose up -d`
> 3. Xong!

✅ App tự động:
- Quét tất cả file `.xlsx` trong `data/`
- Xử lý file mới
- Bỏ qua file đã xong
- Gửi Zalo + Lưu DB
