package com.hmedu.fee.runner;

import com.hmedu.fee.scheduler.EmailNotificationScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * CommandLineRunner để chạy kiểm tra email ngay khi khởi động
 * Chỉ chạy nếu có flag --check-email
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class EmailCheckRunner implements CommandLineRunner {

    private final EmailNotificationScheduler emailScheduler;

    @Override
    public void run(String... args) throws Exception {
        // Kiểm tra có flag --check-email không
        boolean checkEmail = false;
        boolean syncHistorical = false;
        int daysBack = 7;

        for (int i = 0; i < args.length; i++) {
            if ("--check-email".equals(args[i])) {
                checkEmail = true;
            }
            if ("--sync-email".equals(args[i])) {
                syncHistorical = true;
                // Có thể có tham số ngày: --sync-email 30
                if (i + 1 < args.length && args[i + 1].matches("\\d+")) {
                    daysBack = Integer.parseInt(args[i + 1]);
                }
            }
        }

        if (syncHistorical) {
            log.info("🔄 Đồng bộ email lịch sử {} ngày...", daysBack);
            emailScheduler.syncHistoricalEmails(daysBack);
            log.info("✅ Đồng bổ hoàn tất");
        }

        if (checkEmail) {
            log.info("🔍 Chạy kiểm tra email thủ công...");
            emailScheduler.manualCheck();
            log.info("✅ Kiểm tra hoàn tất");
        }
    }
}
