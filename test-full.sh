#!/bin/bash

# Script test toàn diện HMEDU Fee Collection
# 1. Copy Excel → 2. Chạy gửi tin → 3. Kiểm tra DB

echo "========================================="
echo "🧪 TEST TOÀN DIỆN - HMEDU FEE COLLECTION"
echo "========================================="

EXCEL_SOURCE="/home/thangtc/.hermes/cache/documents/doc_fb37d85e4bad_Theo dõi học phí tháng 4.xlsx"
DATA_DIR="./data"
EXCEL_DEST="$DATA_DIR/fee.xlsx"

# === BƯỚC 1: COPY FILE EXCEL ===
echo ""
echo "📋 BƯỚC 1: Copy file Excel"
echo "----------------------------------------"

mkdir -p "$DATA_DIR"

if [ -f "$EXCEL_SOURCE" ]; then
    cp "$EXCEL_SOURCE" "$EXCEL_DEST"
    echo "✅ Đã copy: $EXCEL_SOURCE → $EXCEL_DEST"
    ls -lh "$EXCEL_DEST"
else
    echo "❌ Không tìm thấy file Excel: $EXCEL_SOURCE"
    echo "Vui lòng đưa file Excel vào thư mục data/"
    exit 1
fi

# === BƯỚC 2: KIỂM TRA ZALO API ===
echo ""
echo "📋 BƯỚC 2: Kiểm tra Zalo API"
echo "----------------------------------------"

if curl -s http://localhost:10000/health > /dev/null 2>&1; then
    echo "✅ Zalo API đang chạy tại localhost:10000"
    ZALO_URL="http://localhost:10000"
elif curl -s http://10.10.33.99:10000/health > /dev/null 2>&1; then
    echo "✅ Zalo API đang chạy tại 10.10.33.99:10000"
    ZALO_URL="http://10.10.33.99:10000"
else
    echo "❌ Zalo API không khả dụng!"
    echo "Vui lòng chạy Zalo API trước:"
    echo "   cd /home/thangtc/zalo_api_service && docker-compose up -d"
    exit 1
fi

# === BƯỚC 3: BUILD ===
echo ""
echo "📋 BƯỚC 3: Build Java Application"
echo "----------------------------------------"

docker-compose build

if [ $? -ne 0 ]; then
    echo "❌ Build thất bại!"
    exit 1
fi
echo "✅ Build thành công!"

# === BƯỚC 4: CHẠY GỬI TIN (MANUAL) ===
echo ""
echo "📋 BƯỚC 4: Chạy gửi tin nhắn"
echo "----------------------------------------"
echo "📝 Đọc file Excel và gửi Zalo..."
echo ""

# Chạy container để test (với profile manual)
docker run --rm \
    -v "$(pwd)/data:/app/data" \
    -v "$(pwd)/logs:/app/logs" \
    -e FEE_EXCEL_PATH=/app/data/fee.xlsx \
    -e ZALO_API_URL=$ZALO_URL \
    -e SPRING_PROFILES_ACTIVE=manual \
    hmedu-fee-service_fee-collection:latest \
    2>&1 | tee logs/test-run.log

echo ""
echo "✅ Đã chạy xong!"

# === BƯỚC 5: KIỂM TRA LOGS ===
echo ""
echo "📋 BƯỚC 5: Kiểm tra logs"
echo "----------------------------------------"

if [ -f logs/fee-collection.log ]; then
    echo "📄 Nội dung logs:"
    echo "---"
    tail -50 logs/fee-collection.log
    echo "---"
else
    echo "⚠️ Chưa có file log"
fi

# === BƯỚC 6: KIỂM TRA DATABASE ===
echo ""
echo "📋 BƯỚC 6: Kiểm tra Database"
echo "----------------------------------------"

# Kiểm tra file DB
DB_FILE="data/fee-collection-db.mv.db"
if [ -f "$DB_FILE" ]; then
    echo "✅ Database file tồn tại: $DB_FILE"
    ls -lh "$DB_FILE"
    
    # Hiển thị thông tin (nếu có H2 tool)
    echo ""
    echo "📊 Thông tin bản ghi trong DB:"
    
    # Dùng Java để query H2
    docker run --rm \
        -v "$(pwd)/data:/data" \
        openjdk:17-jdk-slim \
        sh -c "
        cd /tmp
        curl -s -o h2.jar https://repo1.maven.org/maven2/com/h2database/h2/2.2.224/h2-2.2.224.jar
        java -cp h2.jar org.h2.tools.Shell -url 'jdbc:h2:/data/fee-collection-db' -user sa -password '' -sql 'SELECT COUNT(*) as total_records FROM fee_collection_records;'
        java -cp h2.jar org.h2.tools.Shell -url 'jdbc:h2:/data/fee-collection-db' -user sa -password '' -sql 'SELECT student_name, phone_number, amount, transaction_id, payment_status, zalo_message_sent FROM fee_collection_records LIMIT 5;'
        " 2>/dev/null || echo "⚠️ Không thể query DB (cần cài H2 tools)"
else
    echo "❌ Database chưa được tạo!"
fi

echo ""
echo "========================================="
echo "✅ TEST HOÀN TẤT!"
echo "========================================="
echo ""
echo "📁 Kết quả:"
echo "   - File Excel: $EXCEL_DEST"
echo "   - Logs: logs/fee-collection.log"
echo "   - Database: $DB_FILE"
echo ""
echo "🔍 Kiểm tra chi tiết:"
echo "   - Xem logs: tail -f logs/fee-collection.log"
echo "   - Xem DB: docker exec -it <container> sh"
echo ""
