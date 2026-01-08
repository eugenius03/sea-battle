package com.chnu.seabattle.entity;

import lombok.Getter;

@Getter
public enum ShipType {
    SINGLE_DECK(1),
    DOUBLE_DECK(2),
    TRIPLE_DECK(3),
    QUADRO_DECK(4);

    private final int size;

    ShipType(int size) {
        this.size = size;
    }

    public int AllowedCount() {
        return switch (this) {
            case SINGLE_DECK -> 4;
            case DOUBLE_DECK -> 3;
            case TRIPLE_DECK -> 2;
            case QUADRO_DECK -> 1;
        };
    }

}
