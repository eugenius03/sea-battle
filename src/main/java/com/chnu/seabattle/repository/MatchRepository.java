package com.chnu.seabattle.repository;

import com.chnu.seabattle.entity.Match;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {

    @EntityGraph(attributePaths = {"players"})
    @Query("SELECT m FROM Match m WHERE m.id = :id")
    Optional<Match> findByIdForGame(@Param("id") Long id);

    @EntityGraph(attributePaths = {"players"})
    Optional<Match> findByInviteToken(String inviteToken);

}
