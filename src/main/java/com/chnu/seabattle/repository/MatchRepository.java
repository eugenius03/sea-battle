package com.chnu.seabattle.repository;

import com.chnu.seabattle.entity.Match;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {

    @EntityGraph(attributePaths = {
            "players",
            "players.ships",
            "moves"
    })
    Optional<Match> findById(Long id);

    Optional<Match> findByPlayers_UserId(UUID userId);

    Optional<Match> findByInviteToken(String inviteToken);

}
