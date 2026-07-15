package com.chnu.seabattle.service;

import com.chnu.seabattle.constants.ErrorConstants;
import com.chnu.seabattle.dto.match.MatchResponse;
import com.chnu.seabattle.entity.Match;
import com.chnu.seabattle.entity.MatchPlayer;
import com.chnu.seabattle.entity.MatchStatus;
import com.chnu.seabattle.exception.GameRuleViolationException;
import com.chnu.seabattle.exception.ResourceNotFoundException;
import com.chnu.seabattle.repository.MatchRepository;
import com.chnu.seabattle.util.MatchUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class MatchService extends AbstractBaseService<Match, Long> {

    private final MatchRepository matchRepository;
    private final MatchPlayerService matchPlayerService;
    private final WebSocketService webSocketService;

    @Override
    protected MatchRepository getRepository() {
        return matchRepository;
    }

    private String generateInviteToken() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
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
    public Match createMatch(UUID userId) {
        Match match = buildMatch();
        match = create(match);

        MatchPlayer matchPlayer = createMatchPlayer(match, userId);

        match.getPlayers().add(matchPlayer);
        log.info("Match created with inviteToken: {} by userId: {}", match.getInviteToken(), userId);
        return update(match);
    }

    @Transactional
    public Match createMatch(UUID player1Id, UUID player2Id) {
        Match match = buildMatch();

        match = create(match);

        MatchPlayer matchPlayer1 = createMatchPlayer(match, player1Id);
        MatchPlayer matchPlayer2 = createMatchPlayer(match, player2Id);

        match.getPlayers().addAll(List.of(matchPlayer1, matchPlayer2));
        match.setStatus(MatchStatus.PLANNING);
        return update(match);
    }

    public Optional<Match> findByInviteTokenForGame(String inviteToken) {
        return matchRepository.findByInviteTokenForGame(inviteToken);
    }

    @Transactional
    public Match joinMatch(UUID userId, String inviteToken) {
        Match match = matchRepository.findByInviteToken(inviteToken)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorConstants.MATCH_NOT_FOUND));

        return doJoinMatch(match, userId, inviteToken);
    }

    @Transactional(readOnly = true)
    public Match getByInviteToken(String inviteToken) {
        return matchRepository.findByInviteToken(inviteToken)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorConstants.MATCH_NOT_FOUND));
    }

    @Transactional
    public Optional<MatchResponse> processRematch(String inviteToken, UUID userId) {
        Match oldMatch = matchRepository.findByInviteToken(inviteToken)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorConstants.MATCH_NOT_FOUND));

        MatchUtils.requireStatuses(oldMatch, MatchStatus.FINISHED);

        if (oldMatch.getPlayers().size() != 2) {
            throw new GameRuleViolationException(ErrorConstants.ONLY_TWO_PLAYERS);
        }

        MatchPlayer requester = null;
        MatchPlayer opponent = null;

        for (MatchPlayer player : oldMatch.getPlayers()) {
            if (player.getUserId().equals(userId)) {
                requester = player;
            } else {
                opponent = player;
            }
        }

        if (requester == null) {
            throw new GameRuleViolationException(ErrorConstants.NOT_A_PLAYER_IN_MATCH);
        }

        if (opponent == null) {
            throw new GameRuleViolationException(ErrorConstants.PLAYER_NOT_FOUND);
        }

        requester.setWantsRematch(true);

        webSocketService.sendRematchRequested(inviteToken, requester.getId());

        if (!opponent.isWantsRematch()) {
            return Optional.empty();
        }

        Match newMatch = buildMatch();
        MatchPlayer p1 = createMatchPlayer(newMatch, requester.getUserId());
        newMatch.getPlayers().add(p1);
        update(newMatch);

        Match joinedMatch = doJoinMatch(newMatch, opponent.getUserId(), newMatch.getInviteToken());


        webSocketService.sendRematchAgreed(inviteToken, joinedMatch.getInviteToken());
        log.info("Players {} and {} agreed on rematch new match {} created",
                requester.getUserId(), opponent.getUserId(), newMatch.getInviteToken()
        );
        return Optional.of(new MatchResponse(
                joinedMatch.getInviteToken(),
                requester.getUserId()
        ));
    }

    private Match buildMatch() {
        Match match = new Match();
        match.setStatus(MatchStatus.WAITING);
        match.setInviteToken(generateInviteToken());
        return create(match);
    }

    private Match doJoinMatch(Match match, UUID userId, String inviteToken) {
        MatchUtils.requireStatuses(match, MatchStatus.WAITING);

        if (matchPlayerService.findByMatchInviteTokenAndUserId(inviteToken, userId).isPresent()) {
            throw new GameRuleViolationException(ErrorConstants.PLAYER_ALREADY_IN_MATCH);
        }

        if (match.getPlayers().size() == 2) {
            throw new GameRuleViolationException(ErrorConstants.MATCH_NOT_JOINABLE);
        }

        MatchPlayer matchPlayer = createMatchPlayer(match, userId);
        match.getPlayers().add(matchPlayer);

        if (match.getPlayers().size() == 2) {
            match.setStatus(MatchStatus.PLANNING);
            UUID opponentId = MatchUtils.getOpponentPlayerId(match, matchPlayer.getId());
            webSocketService.handleOpponentConnected(inviteToken, opponentId);
        }

        log.info("Player {} joined match {}", userId, inviteToken);
        return match;
    }
}