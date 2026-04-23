package com.chnu.seabattle.service.impl;

import com.chnu.seabattle.constants.ErrorConstants;
import com.chnu.seabattle.entity.Match;
import com.chnu.seabattle.entity.MatchPlayer;
import com.chnu.seabattle.entity.MatchStatus;
import com.chnu.seabattle.exception.GameRuleViolationException;
import com.chnu.seabattle.exception.ResourceNotFoundException;
import com.chnu.seabattle.repository.MatchRepository;
import com.chnu.seabattle.service.AbstractBaseService;
import com.chnu.seabattle.service.MatchPlayerService;
import com.chnu.seabattle.service.MatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class MatchServiceImpl extends AbstractBaseService<Match, Long> implements MatchService {

    private final MatchRepository matchRepository;
    private final MatchPlayerService matchPlayerService;

    @Override
    protected MatchRepository getRepository() {
        return matchRepository;
    }

    private String generateInviteToken() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private MatchPlayer createMatchPlayer(Match match, UUID userId) {
        MatchPlayer matchPlayer = new MatchPlayer();
        matchPlayer.setUserId(userId);
        matchPlayer.setMatch(match);
        matchPlayer.setLastSeenAt(Instant.now());
        matchPlayer.setConnected(true);

        return matchPlayerService.create(matchPlayer);
    }

    @Transactional
    @Override
    public Match createMatch(UUID userId) {
        Match match = new Match();
        match.setStatus(MatchStatus.WAITING);
        match.setInviteToken(generateInviteToken());

        match = create(match);

        MatchPlayer matchPlayer = createMatchPlayer(match, userId);

        match.getPlayers().add(matchPlayer);
        return update(match);
    }

    @Override
    public Optional<Match> findByIdForGame(Long id) {
        return matchRepository.findByIdForGame(id);
    }

    @Override
    @Transactional
    public Match joinMatch(UUID userId, String inviteToken) {
        Match match = matchRepository.findByInviteToken(inviteToken)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorConstants.MATCH_NOT_FOUND));

        if (match.getStatus() != MatchStatus.WAITING) {
            throw new GameRuleViolationException(ErrorConstants.MATCH_NOT_JOINABLE);
        }

        if (match.getPlayers().getFirst().getUserId().equals(userId)) {
            throw new GameRuleViolationException(ErrorConstants.PLAYER_ALREADY_IN_MATCH);
        }

        if (match.getPlayers().size() == 2) {
            throw new GameRuleViolationException(ErrorConstants.MATCH_NOT_JOINABLE);
        }

        MatchPlayer matchPlayer = createMatchPlayer(match, userId);

        match.getPlayers().add(matchPlayer);
        match.setStatus(MatchStatus.PLANNING);

        return match;

    }

    @Override
    @Transactional(readOnly = true)
    public Match getMatchByInviteToken(String inviteToken) {
        return matchRepository.findByInviteToken(inviteToken)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorConstants.PLAYER_NOT_FOUND));
    }

}
