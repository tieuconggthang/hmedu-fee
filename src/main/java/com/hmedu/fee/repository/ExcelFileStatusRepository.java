package com.hmedu.fee.repository;

import com.hmedu.fee.entity.ExcelFileStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExcelFileStatusRepository extends JpaRepository<ExcelFileStatus, Long> {
    
    Optional<ExcelFileStatus> findByFileName(String fileName);
    
    boolean existsByFileNameAndStatus(String fileName, ExcelFileStatus.FileStatus status);
}
