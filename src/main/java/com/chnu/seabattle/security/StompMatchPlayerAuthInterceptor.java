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

    private static final String H_INVITE_TOKEN = "X-Invite-Token";
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
            String inviteToken = firstNativeHeader(accessor, H_INVITE_TOKEN);
            String matchPlayerIdRaw = firstNativeHeader(accessor, H_MATCH_PLAYER_ID);
            if (inviteToken == null || matchPlayerIdRaw == null) {
                throw new IllegalArgumentException(ErrorConstants.MISSING_MATCH_HEADERS);
            }
            UUID matchPlayerId = UUID.fromString(matchPlayerIdRaw);
            MatchPlayer mp = matchPlayerRepository
                    .findByMatchInviteTokenAndId(inviteToken, matchPlayerId)
                    .orElseThrow(() -> new IllegalArgumentException(ErrorConstants.INVALID_MATCH_HEADERS));

            Principal principal = new UsernamePasswordAuthenticationToken(
                    mp.getId().toString(), null, null
            );

            accessor.setUser(principal);

            accessor.getSessionAttributes().put("inviteToken", inviteToken);
            accessor.getSessionAttributes().put("matchPlayerId", mp.getId());
        }
        return message;
    }

    private static String firstNativeHeader(StompHeaderAccessor accessor, String name) {
        return accessor.getFirstNativeHeader(name);
    }
}