package com.chnu.seabattle.service.serviceImpl;

import com.chnu.seabattle.entity.Match;
import com.chnu.seabattle.entity.MatchPlayer;
import com.chnu.seabattle.entity.MatchStatus;
import com.chnu.seabattle.exception.GameRuleViolationException;
import com.chnu.seabattle.exception.ResourceNotFoundException;
import com.chnu.seabattle.repository.MatchPlayerRepository;
import com.chnu.seabattle.repository.MatchRepository;
import com.chnu.seabattle.service.MatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class MatchServiceImpl implements MatchService {

    private final MatchRepository matchRepository;
    private final MatchPlayerRepository matchPlayerRepository;

    private MatchPlayer createMatchPlayer(Long matchId, UUID playerId) {
        if (matchPlayerRepository.findByMatchIdAndId(matchId, playerId).isPresent()) {
            throw new GameRuleViolationException("Player is already in the match");
        }
        MatchPlayer matchPlayer = new MatchPlayer();
        matchPlayer.setUserId(playerId);
        return matchPlayer;
    }

    private String generateInviteToken() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    @Transactional
    @Override
    public Match createMatch(UUID playerId) {
        Match match = new Match();
        match.setStatus(MatchStatus.WAITING);
        match.setInviteToken(generateInviteToken());

        match = matchRepository.save(match);

        MatchPlayer matchPlayer = createMatchPlayer(match.getId(), playerId);
        matchPlayer.setMatch(match);
        matchPlayer.setLastSeenAt(Instant.now());
        matchPlayer.setReconnectToken(generateInviteToken());
        matchPlayer.setConnected(true);

        match.getPlayers().add(matchPlayer);
        matchRepository.save(match);

        return match;
    }

    @Override
    @Transactional
    public Match joinMatch(UUID playerId, String inviteToken) {
        Match match = matchRepository.findByInviteToken(inviteToken)
                .orElseThrow(() -> new ResourceAccessException("Invalid invite token"));

        if (match.getStatus() != MatchStatus.WAITING) {
            throw new GameRuleViolationException("Match is not joinable");
        }

        if (match.getPlayers().size() >= 2) {
            throw new GameRuleViolationException("Match is already full");
        }

        MatchPlayer matchPlayer = createMatchPlayer(match.getId(), playerId);
        matchPlayer.setReconnectToken(generateInviteToken());
        matchPlayer.setMatch(match);
        matchPlayer.setLastSeenAt(Instant.now());
        matchPlayer.setConnected(true);

        match.getPlayers().add(matchPlayer);

        match.setStatus(MatchStatus.PLANNING);
        matchRepository.save(match);

        return match;

    }

    @Override
    @Transactional(readOnly = true)
    public Match getMatchById(Long matchId) {
        return matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Match not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public Match getMatchByInviteToken(String inviteToken) {
        return matchRepository.findByInviteToken(inviteToken)
                .orElseThrow(() -> new ResourceNotFoundException("Match not found"));
    }
}
