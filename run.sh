#!/bin/bash

# Script chạy HMEDU Fee Collection Service

echo "========================================="
echo "HMEDU FEE COLLECTION SERVICE"
echo "========================================="

# Kiểm tra Docker
if ! command -v docker &> /dev/null; then
    echo "❌ Docker chưa được cài đặt!"
    echo "Vui lòng cài đặt Docker trước: https://docs.docker.com/get-docker/"
    exit 1
fi

# Kiểm tra file Excel
data_dir="./data"
excel_file="$data_dir/fee.xlsx"

if [ ! -f "$excel_file" ]; then
    echo "⚠️  Không tìm thấy file: $excel_file"
    echo "Vui lòng:"
    echo "1. Tạo thư mục: mkdir -p $data_dir"
    echo "2. Copy file Excel vào: cp 'Theo dõi học phí tháng 4.xlsx' $excel_file"
    exit 1
fi

echo "✅ Đã tìm thấy file Excel: $excel_file"

# Kiểm tra Zalo API
echo ""
echo "🔍 Kiểm tra Zalo API..."
if curl -s http://localhost:10000/health > /dev/null 2>&1; then
    echo "✅ Zalo API đang chạy tại localhost:10000"
    export ZALO_HOST=host.docker.internal
elif curl -s http://10.10.33.99:10000/health > /dev/null 2>&1; then
    echo "✅ Zalo API đang chạy tại 10.10.33.99:10000"
    export ZALO_HOST=10.10.33.99
else
    echo "⚠️  Không thể kết nối Zalo API"
    echo "Vui lòng đảm bảo Zalo API đang chạy tại 10.10.33.99:10000"
    echo ""
fi

# Build Docker image
echo ""
echo "🔧 Đang build Docker image..."
docker-compose build --no-cache

if [ $? -ne 0 ]; then
    echo "❌ Build thất bại!"
    exit 1
fi

echo "✅ Build thành công!"

# Chạy container
echo ""
echo "🚀 Đang khởi động Fee Collection Service..."
docker-compose up -d

if [ $? -eq 0 ]; then
    echo ""
    echo "========================================="
    echo "✅ KHỚi ĐỘNG THÀNH CÔNG!"
    echo "========================================="
    echo ""
    echo "Xem logs: docker-compose logs -f fee-collection"
    echo "Dừng:    docker-compose down"
    echo ""
    echo "⏰ Service sẽ tự động chạy theo lỌh cron (8h sáng ngày 1 hàng tháng)"
    echo "📋 File Excel: $excel_file"
    echo ""
else
    echo "❌ Khởi động thất bại!"
    exit 1
fi
