@echo off
chcp 65001 >nul
echo ==========================================
echo HMEDU Fee Collection Service
echo ==========================================

REM Create directories
if not exist "data" mkdir data
if not exist "logs" mkdir logs

REM Check Excel file exists
if not exist "data\Theo dõi học phí tháng 4.xlsx" (
    echo WARNING: Excel file not found in data folder!
    echo Please copy your Excel file to: data\Theo dõi học phí tháng 4.xlsx
    pause
    exit /b 1
)

REM Run the application
echo Starting Fee Collection Service...
java -jar target\fee-collection-service-1.0.0.jar

pause
