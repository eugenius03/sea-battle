package com.chnu.seabattle.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
public class MatchResponse {

    private String inviteToken;
    private Long matchId;
    private UUID playerId;

}
