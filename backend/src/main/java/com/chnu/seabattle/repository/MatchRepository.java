package com.chnu.seabattle.repository;

import com.chnu.seabattle.entity.Match;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

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
}
