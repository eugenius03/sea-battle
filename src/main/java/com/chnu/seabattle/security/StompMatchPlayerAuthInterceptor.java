package com.chnu.seabattle.security;

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


@Component
@RequiredArgsConstructor
public class StompMatchPlayerAuthInterceptor implements ChannelInterceptor {

    private static final String H_MATCH_ID = "X-Match-Id";
    private static final String H_RECONNECT_TOKEN = "X-Reconnect-Token";

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
            String reconnectToken = firstNativeHeader(accessor, H_RECONNECT_TOKEN);

            if (matchId == null || reconnectToken == null || reconnectToken.isBlank()) {
                throw new IllegalArgumentException("Missing X-Match-Id / X-Reconnect-Token");
            }
            MatchPlayer mp = matchPlayerRepository
                    .findByMatchIdAndReconnectToken(matchId, reconnectToken)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid X-Match-Id / X-Reconnect-Token"));

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
            throw new IllegalArgumentException(String.format("Invalid %s", name));
        }
    }
}
