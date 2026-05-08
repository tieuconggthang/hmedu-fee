@echo off
chcp 65001 >nul
echo ==========================================
echo Building HMEDU Fee Collection Service
echo ==========================================

REM Check Maven
mvn -v >nul 2>&1
if errorlevel 1 (
    echo ERROR: Maven not found! Please install Maven.
    pause
    exit /b 1
)

REM Build
call mvn clean package -DskipTests

if errorlevel 1 (
    echo ERROR: Build failed!
    pause
    exit /b 1
)

echo ==========================================
echo Build successful!
echo JAR file: target\fee-collection-service-1.0.0.jar
echo ==========================================
echo.
echo To run:
echo   1. Copy Excel file to: data\Theo dõi học phí tháng 4.xlsx
echo   2. Run: run.bat
echo.
pause
