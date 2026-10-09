package com.chnu.seabattle.service;

import com.chnu.seabattle.constants.ErrorConstants;
import com.chnu.seabattle.dto.move.ShootingStatsProjection;
import com.chnu.seabattle.dto.user.PlayerCombatStats;
import com.chnu.seabattle.entity.Match;
import com.chnu.seabattle.entity.MatchStatus;
import com.chnu.seabattle.entity.User;
import com.chnu.seabattle.exception.ResourceNotFoundException;
import com.chnu.seabattle.exception.UnauthorizedException;
import com.chnu.seabattle.repository.UserRepository;
import com.chnu.seabattle.util.MatchUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService extends AbstractBaseService<User, UUID> {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MatchService matchService;
    private final MoveService moveService;

    @Override
    protected JpaRepository<User, UUID> getRepository() {
        return userRepository;
    }

    @Override
    protected void beforeCreate(User entity) {
        entity.setPasswordHash(passwordEncoder.encode(entity.getPasswordHash()));
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    public User getAuthenticatedUser() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            throw new UnauthorizedException(ErrorConstants.USER_NOT_AUTHENTICATED);
        }

        String username = authentication.getName();
        return this.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorConstants.AUTHENTICATED_USER_NOT_FOUND
                ));

    }

    public boolean existsById(UUID id) {
        return userRepository.existsById(id);
    }

    public UserDetails toUserDetails(User user) {
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPasswordHash())
                .authorities(user.isGuest() ? "ROLE_GUEST" : "ROLE_USER")
                .build();
    }

    @Cacheable(value = "player-stats", key = "#userId")
    public PlayerCombatStats getStats(UUID userId) {
        List<Match> matches = matchService.findAllByPlayersUserIdAndStatusOrderByCreatedAtDesc(userId, MatchStatus.FINISHED, null);
        ShootingStatsProjection shootingStats = moveService.getShootingStats(userId);
        int matchesCount = matches.size();
        int winsCount = (int) matches.stream().filter(match -> MatchUtils.isWinner(match, userId)).count();
        int winStreak = (int) matches.stream().takeWhile(match -> MatchUtils.isWinner(match, userId)).count();

        return PlayerCombatStats.builder()
                .matchesPlayed(matchesCount)
                .wins(winsCount)
                .losses(matchesCount - winsCount)
                .winRate(matchesCount == 0 ? 0.0 : Math.round(winsCount * 100.0 / matchesCount))
                .currentWinStreak(winStreak)
                .hits(shootingStats.getHits())
                .misses(shootingStats.getMisses())
                .shotsFired(shootingStats.getShotsFired())
                .accuracy(shootingStats.getShotsFired() == 0 ? 0.0 : Math.round(shootingStats.getHits() * 100.0 / shootingStats.getShotsFired()))
                .build();


    }

    @CacheEvict(value = "player-stats", key = "#userId")
    public void evictStats(UUID userId) {
        // empty, spring clears the cache on call
    }
}

