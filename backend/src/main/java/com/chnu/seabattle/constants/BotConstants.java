package com.chnu.seabattle.constants;

import com.chnu.seabattle.entity.ShipType;

import java.time.Duration;
import java.util.Arrays;

public final class BotConstants {

    private BotConstants() {
    }

    public static final String BOT_USERNAME = "SERVER_BOT";
    public static final Duration TURN_DELAY = Duration.ofMillis(1000);
    public static final int BOOST_VALUE = Arrays.stream(ShipType.values()).map(
            shipType -> {
                int value = shipType.allowedCount() * shipType.getSize();
                if (!shipType.equals(ShipType.SINGLE_DECK)) {
                    return value * 2;
                }
                return value;
            }
    ).mapToInt(Integer::intValue).sum();

    public static boolean isReservedUsername(String username) {
        return username != null && BOT_USERNAME.equalsIgnoreCase(username.trim());
    }
}
