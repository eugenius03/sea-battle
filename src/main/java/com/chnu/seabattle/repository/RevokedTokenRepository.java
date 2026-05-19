package com.chnu.seabattle.repository;

import com.chnu.seabattle.entity.RevokedToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, UUID> {
    int deleteByExpiresAtBefore(Instant threshold);
}