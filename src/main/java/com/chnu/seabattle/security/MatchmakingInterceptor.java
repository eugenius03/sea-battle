package com.chnu.seabattle.security;

import com.chnu.seabattle.constants.ErrorConstants;
import com.chnu.seabattle.exception.BadRequestException;
import com.chnu.seabattle.service.UserService;
import lombok.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class MatchmakingInterceptor implements ChannelInterceptor {

    private static final String H_USER_ID = "X-User-Id";
    private static final String MATCHMAKING_TOPIC_PREFIX = "/topic/matchmaking/";
    private final UserService userService;

    public MatchmakingInterceptor(UserService userService) {
        this.userService = userService;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        StompCommand cmd = accessor.getCommand();

        if (StompCommand.CONNECT.equals(cmd)) {

            String userId = accessor.getFirstNativeHeader(H_USER_ID);
            if (userId == null) {
                return message;
            }

            if (!userService.existsById(UUID.fromString(userId))) {
                throw new BadRequestException(ErrorConstants.USER_NOT_FOUND);
            }

            Map<String, Object> attrs = accessor.getSessionAttributes();
            if (attrs != null) {
                attrs.put("sessionType", SessionType.MATCHMAKING);
                attrs.put("userId", userId);
            }
        } else if (StompCommand.SUBSCRIBE.equals(cmd)) {

            Map<String, Object> attrs = accessor.getSessionAttributes();
            if (attrs == null) return message;

            SessionType sessionType = (SessionType) attrs.get("sessionType");

            if (sessionType == SessionType.MATCHMAKING) {
                String userId = (String) attrs.get("userId");
                String dest = accessor.getDestination();
                String allowedDest = MATCHMAKING_TOPIC_PREFIX + userId;

                if (dest == null || !dest.equals(allowedDest)) {
                    throw new BadRequestException(
                            "Matchmaking session may only subscribe to " + allowedDest);
                }
            } else if (sessionType == null) {
                throw new BadRequestException(
                        "Cannot subscribe before a valid CONNECT handshake");
            }
        }

        return message;
    }
}