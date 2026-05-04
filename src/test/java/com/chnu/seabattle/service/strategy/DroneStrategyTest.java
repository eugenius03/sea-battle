package com.chnu.seabattle.service.strategy;

import com.chnu.seabattle.config.DroneConfig;
import com.chnu.seabattle.converter.MoveConverter;
import com.chnu.seabattle.dto.move.MoveRequest;
import com.chnu.seabattle.entity.Match;
import com.chnu.seabattle.entity.MoveType;
import com.chnu.seabattle.exception.GameRuleViolationException;
import com.chnu.seabattle.service.MoveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Drone strategy validation")
class DroneStrategyTest {

    @Mock
    private MoveService moveService;
    @Mock
    private MoveConverter moveConverter;
    @Mock
    private DroneConfig droneConfig;

    private AttackDroneStrategy attackStrategy;
    private SurveillanceDroneStrategy surveillanceStrategy;

    private Match match;
    private UUID shooterId;
    private MoveRequest attackMoveRequest;
    private MoveRequest surveillanceMoveRequest;

    @BeforeEach
    void setUp() {
        attackStrategy = new AttackDroneStrategy(moveService, moveConverter, droneConfig);
        surveillanceStrategy = new SurveillanceDroneStrategy(moveService, droneConfig);

        match = new Match();
        match.setId(1L);
        shooterId = UUID.randomUUID();
        attackMoveRequest = new MoveRequest(5, 5, MoveType.ATTACK_DRONE);
        surveillanceMoveRequest = new MoveRequest(5, 5, MoveType.SURVEILLANCE_DRONE);
    }

    @Nested
    @DisplayName("AttackDroneStrategy")
    class AttackDrone {

        @Test
        @DisplayName("throws when limit reached")
        void throwsWhenLimitReached() {
            when(droneConfig.getAttackMaxUsages()).thenReturn(2);
            when(moveService.countUsagesForMoveType(match.getId(), shooterId, MoveType.ATTACK_DRONE)).thenReturn(2L);

            assertThrows(GameRuleViolationException.class,
                    () -> attackStrategy.validate(match, shooterId, attackMoveRequest));
        }

        @Test
        @DisplayName("throws when limit exceeded")
        void throwsWhenLimitExceeded() {
            when(droneConfig.getAttackMaxUsages()).thenReturn(2);
            when(moveService.countUsagesForMoveType(match.getId(), shooterId, MoveType.ATTACK_DRONE)).thenReturn(3L);

            assertThrows(GameRuleViolationException.class,
                    () -> attackStrategy.validate(match, shooterId, attackMoveRequest));
        }

        @Test
        @DisplayName("passes when under limit")
        void passesWhenUnderLimit() {
            when(droneConfig.getAttackMaxUsages()).thenReturn(2);
            when(moveService.countUsagesForMoveType(match.getId(), shooterId, MoveType.ATTACK_DRONE)).thenReturn(1L);

            assertDoesNotThrow(() -> attackStrategy.validate(match, shooterId, attackMoveRequest));
        }
    }

    @Nested
    @DisplayName("SurveillanceDroneStrategy")
    class SurveillanceDrone {

        @Test
        @DisplayName("throws when limit reached")
        void throwsWhenLimitReached() {
            when(droneConfig.getSurveillanceMaxUsages()).thenReturn(1);
            when(moveService.countUsagesForMoveType(match.getId(), shooterId, MoveType.SURVEILLANCE_DRONE)).thenReturn(1L);

            assertThrows(GameRuleViolationException.class,
                    () -> surveillanceStrategy.validate(match, shooterId, surveillanceMoveRequest));
        }

        @Test
        @DisplayName("throws when limit exceeded")
        void throwsWhenLimitExceeded() {
            when(droneConfig.getSurveillanceMaxUsages()).thenReturn(1);
            when(moveService.countUsagesForMoveType(match.getId(), shooterId, MoveType.SURVEILLANCE_DRONE)).thenReturn(2L);

            assertThrows(GameRuleViolationException.class,
                    () -> surveillanceStrategy.validate(match, shooterId, surveillanceMoveRequest));
        }

        @Test
        @DisplayName("passes when under limit")
        void passesWhenUnderLimit() {
            when(droneConfig.getSurveillanceMaxUsages()).thenReturn(1);
            when(moveService.countUsagesForMoveType(match.getId(), shooterId, MoveType.SURVEILLANCE_DRONE)).thenReturn(0L);

            assertDoesNotThrow(() -> surveillanceStrategy.validate(match, shooterId, surveillanceMoveRequest));
        }
    }
}
