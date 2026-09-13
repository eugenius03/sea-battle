package com.chnu.seabattle.config;

import com.chnu.seabattle.repository.RevokedTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Log4j2
public class AppScheduler {

    private final RevokedTokenRepository revokedTokenRepository;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeExpiredRevokedTokens() {
        int deleted = revokedTokenRepository.deleteByExpiresAtBefore(Instant.now());
        log.info("Purged {} expired revoked tokens", deleted);
    }
}
