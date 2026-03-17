package com.chnu.seabattle.repository;

import com.chnu.seabattle.entity.MatchPlayer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MatchPlayerRepository extends JpaRepository<MatchPlayer, UUID> {

    @EntityGraph(attributePaths = {
            "ships"
    })
    Optional<MatchPlayer> findByMatchIdAndId(
            Long matchId, UUID MatchPlayerId
    );

    boolean existsByMatchIdAndUserId(Long matchId, UUID MatchPlayerId);

    Optional<MatchPlayer> findByMatchIdAndReconnectToken(
            Long matchId, String reconnectToken
    );

    @Query("""
            SELECT COUNT(mp) = 2
            FROM MatchPlayer mp
            WHERE mp.match.id = :matchId AND mp.isReady = true
            """)
    boolean areAllPlayersReady(@Param("matchId") Long matchId);
}
