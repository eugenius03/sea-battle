package com.chnu.seabattle.repository;

import com.chnu.seabattle.entity.Match;
import com.chnu.seabattle.entity.MatchStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {

    @EntityGraph(attributePaths = {"players"})
    @Query("SELECT m FROM Match m WHERE m.inviteToken = :inviteToken")
    Optional<Match> findByInviteTokenForGame(@Param("inviteToken") String inviteToken);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"players"})
    @Query("SELECT m FROM Match m WHERE m.inviteToken = :inviteToken")
    Optional<Match> findByInviteTokenForUpdate(@Param("inviteToken") String inviteToken);

    @EntityGraph(attributePaths = {"players"})
    Optional<Match> findByInviteToken(String inviteToken);

    @Query("SELECT m.inviteToken FROM Match m JOIN m.players p "
            + "WHERE m.status = :status AND p.id = m.currentPlayerTurnId AND p.userId = :userId")
    List<String> findInviteTokensByStatusAndTurnOfUser(@Param("status") MatchStatus status, @Param("userId") UUID userId);

    @EntityGraph(attributePaths = {"players"})
    List<Match> findAllByPlayersUserIdAndStatusOrderByCreatedAtDesc(UUID userId, MatchStatus status, Pageable pageable);
}
