#!/bin/bash

# Script build Docker và chuẩn bị môi trường

echo "========================================="
echo "🚀 BUILD & RUN - HMEDU FEE COLLECTION"
echo "========================================="

# 1. Tạo thư mục cần thiết
echo ""
echo "📁 Bước 1: Tạo thư mục"
echo "----------------------------------------"
mkdir -p data logs
echo "✁  Đã tạo:"
echo "   - data/  (✓) Đây là nơi COPY FILE EXCEL vào"
echo "   - logs/  (✓) Lưu logs"

# 2. Build Docker image
echo ""
echo "🔧 Bước 2: Build Docker Image"
echo "----------------------------------------"
docker-compose build --no-cache

if [ $? -ne 0 ]; then
    echo "❌ Build thất bại!"
    exit 1
fi
echo "✅ Build thành công!"

# 3. Kiểm tra Zalo API
echo ""
echo "🔍 Bước 3: Kiểm tra Zalo API"
echo "----------------------------------------"
if curl -s http://localhost:10000/health > /dev/null 2>&1; then
    echo "✅ Zalo API đang chạy tại localhost:10000"
elif curl -s http://10.10.33.99:10000/health > /dev/null 2>&1; then
    echo "✅ Zalo API đang chạy tại 10.10.33.99:10000"
else
    echo "⚠️  CẢNH BÁO: Zalo API chưa chạy!"
    echo "   Chạy Zalo API trước khi khởi động app:"
    echo "   cd /home/thangtc/zalo_api_service && docker-compose up -d"
fi

# 4. Hiển thị hướng dẫn
echo ""
echo "========================================="
echo "✅ BUILD HOÀN TẤT!"
echo "========================================="
echo ""
echo "📁 THƯ MỤC QUAN TRỌNG:"
echo "----------------------------------------"
echo "📄 data/     ← COPY FILE EXCEL VÀO ĐÂY"
echo "📄 logs/     ← Chứa file log"
echo ""
echo "📝 CÁCH COPY FILE EXCEL ĐỂ XỬ LÝ:"
echo "----------------------------------------"
echo "1. Copy file vào thư mục data/:"
echo "   cp '/path/to/Theo dõi học phí tháng 5.xlsx' data/"
echo ""
echo "2. Kiểm tra file đã copy:"
echo "   ls -lh data/"
echo ""
echo "3. Chạy app để xử lý:"
echo "   docker-compose up -d"
echo ""
echo "4. Xem tiến trình:"
echo "   docker-compose logs -f fee-collection"
echo ""
echo "📋 XEM DANH SÁCH FILE ĐÃ XỬ LÝ:"
echo "   docker exec hmedu-fee-service sh -c 'java -cp h2.jar org.h2.tools.Shell -url jdbc:h2:/app/data/fee-collection-db -user sa -sql \"SELECT file_name, status, processed_rows, total_rows FROM excel_file_status;\"'"
echo ""
