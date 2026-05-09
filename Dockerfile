# Build stage
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app

# Copy pom.xml và tải dependencies trước (tận dụng cache)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code và build
COPY src ./src
RUN mvn clean package -DskipTests -B

# Run stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Cài đặt các gói cần thiết
RUN apk add --no-cache curl

# Copy jar từ build stage
COPY --from=build /app/target/fee-collection-service-*.jar app.jar

# Tạo thư mục data và logs
RUN mkdir -p /app/data /app/logs

# Environment variables
ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENV FEE_EXCEL_PATH=/app/data/fee.xlsx
ENV ZALO_API_URL=http://10.10.33.99:10000

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Chạy ứng dụng
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
