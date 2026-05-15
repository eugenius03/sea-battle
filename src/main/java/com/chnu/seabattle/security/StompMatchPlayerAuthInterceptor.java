package com.chnu.seabattle.security;

import com.chnu.seabattle.constants.ErrorConstants;
import com.chnu.seabattle.entity.MatchPlayer;
import com.chnu.seabattle.exception.BadRequestException;
import com.chnu.seabattle.service.MatchPlayerService;
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
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        StompCommand cmd = accessor.getCommand();

        if (StompCommand.CONNECT.equals(cmd)) {

            String userId = accessor.getFirstNativeHeader("X-User-Id");
            if (userId != null) {
                return message;
            }

            String inviteToken = accessor.getFirstNativeHeader(H_INVITE_TOKEN);
            String matchPlayerIdRaw = accessor.getFirstNativeHeader(H_MATCH_PLAYER_ID);

            if (inviteToken == null || matchPlayerIdRaw == null) {
                throw new BadRequestException(ErrorConstants.MISSING_MATCH_HEADERS);
            }

            UUID matchPlayerId;
            try {
                matchPlayerId = UUID.fromString(matchPlayerIdRaw);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException(ErrorConstants.INVALID_MATCH_HEADERS);
            }

            MatchPlayer mp = matchPlayerService
                    .findByMatchInviteTokenAndId(inviteToken, matchPlayerId)
                    .orElseThrow(() -> new BadRequestException(ErrorConstants.INVALID_MATCH_HEADERS));

            Principal principal = new UsernamePasswordAuthenticationToken(
                    mp.getId().toString(), null, null
            );
            accessor.setUser(principal);

            Map<String, Object> attrs = accessor.getSessionAttributes();
            if (attrs != null) {
                attrs.put("sessionType", SessionType.MATCH_PLAYER);
                attrs.put("inviteToken", inviteToken);
                attrs.put("matchPlayerId", mp.getId());
            }
        } else if (StompCommand.SUBSCRIBE.equals(cmd)) {

            String destination = accessor.getDestination();
            if (destination == null || !isMatchDestination(destination)) {
                return message;
            }

            Map<String, Object> attrs = accessor.getSessionAttributes();
            if (attrs == null || attrs.get("sessionType") != SessionType.MATCH_PLAYER) {
                throw new BadRequestException(
                        "Only verified match players may subscribe to match destinations");
            }

            UUID sessionMatchPlayerId = (UUID) attrs.get("matchPlayerId");
            String sessionInviteToken = attrs.get("inviteToken").toString();
            if (!matchPlayerService.existsByMatchInviteTokenAndId(sessionInviteToken, sessionMatchPlayerId)) {
                throw new BadRequestException(
                        "You may only subscribe to your own match destination");
            }
        }

        return message;
    }


    private static boolean isMatchDestination(String dest) {
        return dest.startsWith(MATCH_TOPIC_PREFIX) || dest.startsWith(MATCH_QUEUE_PREFIX);
    }
}