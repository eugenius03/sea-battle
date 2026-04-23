package com.chnu.seabattle.security;

import com.chnu.seabattle.constants.ErrorConstants;
import com.chnu.seabattle.entity.MatchPlayer;
import com.chnu.seabattle.repository.MatchPlayerRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.UUID;


@Component
@RequiredArgsConstructor
public class StompMatchPlayerAuthInterceptor implements ChannelInterceptor {

    private static final String H_MATCH_ID = "X-Match-Id";
    private static final String H_MATCH_PLAYER_ID = "X-Match-Player-Id";

    private final MatchPlayerRepository matchPlayerRepository;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            Long matchId = parseLongHeader(accessor, H_MATCH_ID);
            UUID matchPlayerId = UUID.fromString(firstNativeHeader(accessor, H_MATCH_PLAYER_ID));

            if (matchId == null || matchPlayerId == null) {
                throw new IllegalArgumentException(ErrorConstants.MISSING_MATCH_HEADERS);
            }
            MatchPlayer mp = matchPlayerRepository
                    .findByMatchIdAndId(matchId, matchPlayerId)
                    .orElseThrow(() -> new IllegalArgumentException(ErrorConstants.INVALID_MATCH_HEADERS));

            Principal principal = new UsernamePasswordAuthenticationToken(
                    mp.getId().toString(), null, null
            );

            accessor.setUser(principal);

            accessor.getSessionAttributes().put("matchId", matchId);
            accessor.getSessionAttributes().put("matchPlayerId", mp.getId());
        }
        return message;
    }

    private static String firstNativeHeader(StompHeaderAccessor accessor, String name) {
        return accessor.getFirstNativeHeader(name);
    }

    private static Long parseLongHeader(StompHeaderAccessor accessor, String name) {
        String v = accessor.getFirstNativeHeader(name);
        if (v == null) return null;
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(String.format(ErrorConstants.INVALID_HEADER_VALUE, name));
        }
    }
}
