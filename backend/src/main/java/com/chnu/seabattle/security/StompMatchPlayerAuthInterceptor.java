package com.chnu.seabattle.security;

import com.chnu.seabattle.constants.ErrorConstants;
import com.chnu.seabattle.entity.MatchPlayer;
import com.chnu.seabattle.exception.BadRequestException;
import com.chnu.seabattle.service.MatchPlayerService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class StompMatchPlayerAuthInterceptor implements ChannelInterceptor {

    private static final String H_INVITE_TOKEN = "X-Invite-Token";
    private static final String H_MATCH_PLAYER_ID = "X-Match-Player-Id";

    private static final String MATCH_TOPIC_PREFIX = "/topic/match/";
    private static final String MATCH_QUEUE_PREFIX = "/queue/match/";

    private final MatchPlayerService matchPlayerService;

    @Override
    public @Nullable Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        StompCommand cmd = accessor.getCommand();

        if (StompCommand.CONNECT.equals(cmd)) {

            handleConnect(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(cmd)) {

            handleSubscribe(accessor);

        }
        return message;
    }

    private void handleConnect(StompHeaderAccessor accessor) {
        if (accessor.getFirstNativeHeader("X-User-Id") != null) {
            return;
        }

        String inviteToken = accessor.getFirstNativeHeader(H_INVITE_TOKEN);
        String matchPlayerIdRaw = accessor.getFirstNativeHeader(H_MATCH_PLAYER_ID);

        if (inviteToken == null || matchPlayerIdRaw == null) {
            throw new BadRequestException(ErrorConstants.MISSING_MATCH_HEADERS);
        }

        UUID matchPlayerId = parseMatchPlayerId(matchPlayerIdRaw);

        MatchPlayer mp = matchPlayerService
                .findByMatchInviteTokenAndId(inviteToken, matchPlayerId)
                .orElseThrow(() -> new BadRequestException(ErrorConstants.INVALID_MATCH_HEADERS));

        accessor.setUser(new UsernamePasswordAuthenticationToken(
                mp.getId().toString(), null, new ArrayList<>()
        ));

        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs != null) {
            attrs.put("sessionType", SessionType.MATCH_PLAYER);
            attrs.put("inviteToken", inviteToken);
            attrs.put("matchPlayerId", mp.getId());
        }
    }

    private void handleSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !isMatchDestination(destination)) {
            return;
        }

        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs == null || attrs.get("sessionType") != SessionType.MATCH_PLAYER) {
            throw new BadRequestException("Only verified match players may subscribe to match destinations");
        }

        UUID sessionMatchPlayerId = (UUID) attrs.get("matchPlayerId");
        String sessionInviteToken = attrs.get("inviteToken").toString();

        if (!matchPlayerService.existsByMatchInviteTokenAndId(sessionInviteToken, sessionMatchPlayerId)) {
            throw new BadRequestException("You may only subscribe to your own match destination");
        }
    }

    private UUID parseMatchPlayerId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(ErrorConstants.INVALID_MATCH_HEADERS);
        }
    }

    private static boolean isMatchDestination(String dest) {
        return dest.startsWith(MATCH_TOPIC_PREFIX) || dest.startsWith(MATCH_QUEUE_PREFIX);
    }
}