package com.hmedu.fee.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Lưu trạng thái xử lý của file Excel
 */
@Entity
@Table(name = "excel_file_status")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcelFileStatus {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "file_name", nullable = false, unique = true)
    private String fileName;
    
    @Column(name = "file_path")
    private String filePath;
    
    @Column(name = "total_rows")
    private Integer totalRows;
    
    @Column(name = "processed_rows")
    private Integer processedRows;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private FileStatus status;
    
    @Column(name = "processed_at")
    private LocalDateTime processedAt;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
    public enum FileStatus {
        PENDING,      // Chờ xử lý
        PROCESSING,   // Đang xử lý
        COMPLETED,    // Đã xử lý xong
        FAILED        // Lỗi
    }
}
