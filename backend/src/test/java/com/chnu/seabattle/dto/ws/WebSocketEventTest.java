package com.chnu.seabattle.dto.ws;

import com.chnu.seabattle.dto.move.MoveResponse;
import com.chnu.seabattle.entity.MatchStatus;
import com.chnu.seabattle.entity.MoveResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("WebSocketEvent routing and JSON contract")
class WebSocketEventTest {

    private static final String INVITE = "invite-1";
    private static final String NEW_INVITE = "invite-2";
    private static final String MATCHMAKING_ID = "user-1";
    private static final UUID PLAYER = UUID.randomUUID();
    private static final UUID OPPONENT = UUID.randomUUID();

    private final JsonMapper mapper = JsonMapper.builder().build();

    private final PresenceMessage presence =
            new PresenceMessage(INVITE, PLAYER, PresenceEventType.OPPONENT_READY);
    private final RematchMessage rematch =
            new RematchMessage(INVITE, PLAYER, PresenceEventType.REMATCH_AGREED, NEW_INVITE);
    private final MatchStatusMessage status =
            new MatchStatusMessage(INVITE, MatchStatus.IN_PROGRESS, PLAYER, null);
    private final MatchmakingStatusResponse queue =
            new MatchmakingStatusResponse(MATCHMAKING_ID, 3L, null);
    private final MatchmakingStatusResponse found =
            new MatchmakingStatusResponse(MATCHMAKING_ID, null, INVITE);
    private final MoveMessage move = new MoveMessage(OPPONENT, INVITE,
            List.of(new MoveResponse(1, 2, MoveResult.HIT)), PLAYER);

    private JsonNode json(Object event) {
        return mapper.readTree(mapper.writeValueAsString(event));
    }

    @Test
    @DisplayName("Every permitted event type is covered by this test")
    void everyPermittedTypeHasASample() {
        List<Class<?>> covered = List.of(presence, rematch, status, queue, move).stream()
                .<Class<?>>map(Object::getClass).toList();

        for (Class<?> permitted : WebSocketEvent.class.getPermittedSubclasses()) {
            assertTrue(covered.contains(permitted),
                    permitted.getSimpleName() + " has no sample in WebSocketEventTest; add it");
        }
    }

    @Test
    @DisplayName("Destinations match the topics the client subscribes to")
    void destinationsMatchClientSubscriptions() {
        assertEquals("/topic/match/invite-1/presence", presence.destination());
        assertEquals("/topic/match/invite-1/presence", rematch.destination());
        assertEquals("/topic/match/invite-1/status", status.destination());
        assertEquals("/topic/matchmaking/user-1", queue.destination());
        assertEquals("/queue/match/invite-1", move.destination());
    }

    @Test
    @DisplayName("Rematch is routed by the old token and carries the new token as inviteToken")
    void rematchRoutesByOldTokenAndPayloadIsNewToken() {
        assertEquals("/topic/match/invite-1/presence", rematch.destination());

        JsonNode node = json(rematch);
        assertEquals(NEW_INVITE, node.get("inviteToken").asString());
        assertEquals("REMATCH_AGREED", node.get("presenceEventType").asString());
    }

    @Test
    @DisplayName("Anything routed to the private queue is a PrivateWebSocketEvent")
    void privateQueueDestinationsArePrivateEvents() {
        String privatePrefix = "/queue/";

        for (WebSocketEvent event : List.of(presence, rematch, status, queue, found, move)) {
            if (event.destination().startsWith(privatePrefix)) {
                assertInstanceOf(PrivateWebSocketEvent.class, event,
                        event.getClass().getSimpleName() + " targets " + event.destination()
                                + " but does not implement PrivateWebSocketEvent");
            } else {
                assertFalse(event instanceof PrivateWebSocketEvent,
                        event.getClass().getSimpleName() + " is private but targets a topic");
            }
        }
    }

    @Test
    @DisplayName("Private event recipient is the opponent, not the player whose turn is next")
    void moveRecipientIsOpponent() {
        assertEquals(OPPONENT.toString(), move.recipient());
    }

    @Test
    @DisplayName("Routing fields are not serialized to the client")
    void routingFieldsAreNotSerialized() {
        assertFalse(json(presence).has("inviteToken"));
        assertFalse(json(status).has("inviteToken"));
        assertFalse(json(queue).has("matchmakingId"));
        assertFalse(json(move).has("recipientMatchPlayerId"));
    }

    @Test
    @DisplayName("Payload fields the client reads are still serialized")
    void payloadFieldsAreSerialized() {
        JsonNode presenceNode = json(presence);
        assertEquals(PLAYER.toString(), presenceNode.get("matchPlayerId").asString());
        assertEquals("OPPONENT_READY", presenceNode.get("presenceEventType").asString());

        JsonNode statusNode = json(status);
        assertEquals("IN_PROGRESS", statusNode.get("matchStatus").asString());
        assertEquals(PLAYER.toString(), statusNode.get("currentTurnPlayerId").asString());

        JsonNode foundNode = json(found);
        assertEquals(INVITE, foundNode.get("inviteToken").asString());

        JsonNode queueNode = json(queue);
        assertEquals(3L, queueNode.get("queuePosition").asLong());

        JsonNode moveNode = json(move);
        assertEquals(PLAYER.toString(), moveNode.get("currentTurnPlayerId").asString());
        assertEquals(1, moveNode.get("moveResponses").size());
    }
}
