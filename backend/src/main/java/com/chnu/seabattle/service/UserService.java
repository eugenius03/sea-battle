package com.chnu.seabattle.service;

import com.chnu.seabattle.constants.ErrorConstants;
import com.chnu.seabattle.dto.move.ShootingStatsProjection;
import com.chnu.seabattle.dto.user.PlayerCombatStats;
import com.chnu.seabattle.entity.Match;
import com.chnu.seabattle.entity.MatchStatus;
import com.chnu.seabattle.entity.User;
import com.chnu.seabattle.enums.EmailTokenPurpose;
import com.chnu.seabattle.enums.UserType;
import com.chnu.seabattle.exception.BadRequestException;
import com.chnu.seabattle.exception.RateLimitException;
import com.chnu.seabattle.exception.ResourceNotFoundException;
import com.chnu.seabattle.exception.UnauthorizedException;
import com.chnu.seabattle.repository.UserRepository;
import com.chnu.seabattle.repository.redis.EmailTokenRedisRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService extends AbstractBaseService<User, UUID> {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MatchService matchService;
    private final MoveService moveService;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final EmailTokenRedisRepository emailTokenRedisRepository;
    private final RefreshTokenService refreshTokenService;

    private static final Duration VERIFY_EMAIL_TTL = Duration.ofHours(24);
    private static final Duration RESET_EMAIL_TTL = Duration.ofHours(1);


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
                .authorities(user.getUserType() == UserType.GUEST ? "ROLE_GUEST" : "ROLE_USER")
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
                .losses((long) matchesCount - winsCount)
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

    private static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    @Transactional
    public void requestEmailVerification(User user, String email) {
        if (user.getUserType() != UserType.REGISTERED || user.isEmailVerified()) {
            throw new BadRequestException(ErrorConstants.ACTION_NOT_ALLOWED);
        }

        emailService.checkRateLimit(user.getId(), EmailTokenPurpose.VERIFY_EMAIL);
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isPresent() && !userOptional.get().getId().equals(user.getId())) {
            throw new BadRequestException(ErrorConstants.EMAIL_ALREADY_EXISTS);
        }
        if (!Objects.equals(user.getEmail(), email)) {
            user.setEmail(email);
        }
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        emailTokenRedisRepository.issue(EmailTokenPurpose.VERIFY_EMAIL, user.getId(), hashToken(token), VERIFY_EMAIL_TTL);
        emailService.sendVerificationEmail(user.getId(), email, user.getUsername(), token);
    }

    public void requestResetPassword(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || user.getUserType() != UserType.REGISTERED || !user.isEmailVerified()) {
            return;
        }
        if (user.getEmail() == null || user.getEmail().isEmpty()) {
            return;
        }

        try {
            emailService.checkRateLimit(user.getId(), EmailTokenPurpose.RESET_PASSWORD);
        } catch (RateLimitException e) {
            return;
        }
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        emailTokenRedisRepository.issue(EmailTokenPurpose.RESET_PASSWORD, user.getId(), hashToken(token), RESET_EMAIL_TTL);
        emailService.sendResetEmail(user.getId(), user.getEmail(), user.getUsername(), token);

    }

    @Transactional
    public void verifyEmail(String token) {
        User user = consumeToken(EmailTokenPurpose.VERIFY_EMAIL, token);
        if (user.getEmail() == null) {
            throw new BadRequestException(ErrorConstants.INVALID_TOKEN);
        }
        user.setEmailVerified(true);
    }

    @Transactional
    public void resetPassword(String token, String password) {
        User user = consumeToken(EmailTokenPurpose.RESET_PASSWORD, token);
        if (user.getUserType() != UserType.REGISTERED) {
            throw new BadRequestException(ErrorConstants.INVALID_TOKEN);
        }
        user.setPasswordHash(passwordEncoder.encode(password));
        refreshTokenService.revokeAllForUser(user.getUsername());
    }

    public boolean isBot(UUID userId) {
        return findById(userId).map(u -> u.getUserType() == UserType.BOT).orElse(false);
    }

    private User consumeToken(EmailTokenPurpose purpose, String token) {
        UUID userId = emailTokenRedisRepository.consume(purpose, hashToken(token))
                .orElseThrow(() -> new BadRequestException(ErrorConstants.INVALID_TOKEN));
        return userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException(ErrorConstants.INVALID_TOKEN));
    }
}
