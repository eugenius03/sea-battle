package com.chnu.seabattle.util;

import com.chnu.seabattle.constants.ErrorConstants;
import com.chnu.seabattle.entity.Match;
import com.chnu.seabattle.entity.MatchPlayer;
import com.chnu.seabattle.entity.MatchStatus;
import com.chnu.seabattle.exception.GameRuleViolationException;
import com.chnu.seabattle.exception.ResourceNotFoundException;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class MatchUtils {

    private MatchUtils() {

    }

    public static void requireStatuses(Match match, MatchStatus... expected) {
        boolean isValid = Arrays.stream(expected)
                .anyMatch(status -> status.equals(match.getStatus()));

        if (!isValid) {
            throw new GameRuleViolationException(
                    String.format(ErrorConstants.INVALID_MATCH_STATE, match.getStatus())
            );
        }
    }

    public static UUID getOpponentPlayerId(Match match, UUID playerId) {
        List<MatchPlayer> players = match.getPlayers();
        if (players.size() != 2) {
            throw new GameRuleViolationException(ErrorConstants.ONLY_TWO_PLAYERS);
        }

        return players.stream()
                .filter(p -> !p.getId().equals(playerId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(ErrorConstants.PLAYER_NOT_FOUND))
                .getId();
    }
}
