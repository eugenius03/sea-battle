package com.chnu.seabattle.util;

import com.chnu.seabattle.constants.ErrorConstants;
import com.chnu.seabattle.entity.Match;
import com.chnu.seabattle.entity.MatchStatus;
import com.chnu.seabattle.exception.GameRuleViolationException;

import java.util.Arrays;

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
}
