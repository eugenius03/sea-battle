package com.chnu.seabattle.config;

import com.chnu.seabattle.entity.MoveType;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class DroneConfig {

    @Value("${game.drone.surveillance.max-usages}")
    private int surveillanceMaxUsages;

    @Value("${game.drone.attack.max-usages}")
    private int attackMaxUsages;

    public int getMaxUsagesFor(MoveType type) {
        return switch (type) {
            case SURVEILLANCE_DRONE -> surveillanceMaxUsages;
            case ATTACK_DRONE -> attackMaxUsages;
            default -> Integer.MAX_VALUE;
        };
    }
}
