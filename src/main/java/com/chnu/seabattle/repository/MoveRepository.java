package com.chnu.seabattle.repository;

import com.chnu.seabattle.entity.Move;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MoveRepository extends JpaRepository<Move, Long> {

    boolean existsByMatchIdAndShooterIdAndTargetXAndTargetY(Long matchId, UUID shooterId, Integer x, Integer y);

    List<Move> findByMatchIdAndShooterId(Long matchId, UUID shooterId);
}
