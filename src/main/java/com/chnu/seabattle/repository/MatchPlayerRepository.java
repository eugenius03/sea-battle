package com.chnu.seabattle.repository;

import com.chnu.seabattle.entity.MatchPlayer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MatchPlayerRepository extends JpaRepository<MatchPlayer, UUID> {

    @EntityGraph(attributePaths = {
            "ships"
    })
    Optional<MatchPlayer> findByMatchInviteTokenAndId(
            String inviteToken, UUID matchPlayerId
    );

    Optional<MatchPlayer> findByMatchIdAndUserId(Long matchId, UUID userId);

    Optional<MatchPlayer> findByMatchInviteTokenAndUserId(String inviteToken, UUID userId);

    boolean existsByMatchIdAndUserId(Long matchId, UUID matchPlayerId);

}
