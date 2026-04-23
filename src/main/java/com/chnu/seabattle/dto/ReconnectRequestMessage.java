package com.chnu.seabattle.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Message sent by the client to request reconnection data.
 * Sent after the client has subscribed to all necessary topics/queues.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconnectRequestMessage {
    private Long matchId;
    private UUID matchPlayerId;
}

