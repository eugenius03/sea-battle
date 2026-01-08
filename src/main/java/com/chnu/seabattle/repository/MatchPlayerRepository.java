package com.chnu.seabattle.repository;

import com.chnu.seabattle.entity.MatchPlayer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MatchPlayerRepository extends JpaRepository<MatchPlayer, Long> {
    Optional<MatchPlayer> findByMatchIdAndUserIdOrMatchIdAndGuestId(
            Long matchId1, UUID userId,
            Long matchId2, UUID guestId
    );

}
