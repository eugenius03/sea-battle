package com.chnu.seabattle.enums;

public enum EventTopic {
    PRESENCE("/topic/match/%s/presence"),
    STATUS("/topic/match/%s/status"),
    PRIVATE("/queue/match/%s"),
    MATCHMAKING("/topic/matchmaking/%s");

    private final String topicString;

    public String forId(String id) {
        return String.format(topicString, id);
    }

    EventTopic(String s) {
        this.topicString = s;
    }
}
