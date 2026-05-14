package com.chnu.seabattle.service;

import java.util.UUID;

public interface MatchmakingService {
    void joinQueue(UUID userId);

    void leaveQueue(UUID userId);

}
