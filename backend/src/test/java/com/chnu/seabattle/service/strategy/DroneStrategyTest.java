package com.chnu.seabattle.service.strategy;

import com.chnu.seabattle.config.DroneConfig;
import com.chnu.seabattle.converter.MoveConverter;
import com.chnu.seabattle.dto.move.MoveRequest;
import com.chnu.seabattle.entity.Match;
import com.chnu.seabattle.entity.MatchPlayer;
import com.chnu.seabattle.entity.MatchStatus;
import com.chnu.seabattle.entity.Move;
import com.chnu.seabattle.entity.MoveResult;
import com.chnu.seabattle.entity.MoveType;
import com.chnu.seabattle.entity.Orientation;
import com.chnu.seabattle.entity.Ship;
import com.chnu.seabattle.entity.ShipType;
import com.chnu.seabattle.exception.GameRuleViolationException;
import com.chnu.seabattle.service.MoveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    @DisplayName("AttackDroneStrategy execution")
    class AttackDroneExecution {

        private MatchPlayer opponent;
        private Ship ship;

        @BeforeEach
        void setUpMatch() {
            when(droneConfig.getAttackMaxUsages()).thenReturn(2);
            when(moveService.countUsagesForMoveType(any(), any(), any())).thenReturn(0L);
            lenient().when(moveService.create(any(Move.class))).thenAnswer(invocation -> invocation.getArgument(0));

            opponent = new MatchPlayer();
            opponent.setId(UUID.randomUUID());
            ship = Ship.builder().shipType(ShipType.TRIPLE_DECK).startX(4).startY(5)
                    .orientation(Orientation.HORIZONTAL).hits(1).build();
            opponent.setShips(new ArrayList<>(List.of(ship)));
            MatchPlayer shooter = new MatchPlayer();
            shooter.setId(shooterId);
            match.setPlayers(new ArrayList<>(List.of(shooter, opponent)));
            match.setMoves(new ArrayList<>());
        }

        private Move fired(MoveType type, int x, int y) {
            return Move.builder().shooterId(shooterId).moveType(type).targetX(x).targetY(y)
                    .moveResult(MoveResult.HIT).build();
        }

        @Test
        @DisplayName("never fires at a cell the shooter already shot")
        void skipsAlreadyFiredCells() {
            match.getMoves().add(fired(MoveType.STANDARD, 4, 5));

            for (int i = 0; i < 30; i++) {
                List<Move> moves = attackStrategy.execute(match, shooterId, attackMoveRequest);

                assertTrue(moves.stream().noneMatch(m -> m.getTargetX() == 4 && m.getTargetY() == 5));
            }
        }

        @Test
        @DisplayName("does not count a hit twice on a cell that was already hit")
        void doesNotDoubleCountHit() {
            match.getMoves().add(fired(MoveType.STANDARD, 4, 5));

            for (int i = 0; i < 30; i++) {
                ship.setHits(1);
                ship.setSunk(false);
                match.setStatus(MatchStatus.IN_PROGRESS);

                List<Move> moves = attackStrategy.execute(match, shooterId, attackMoveRequest);

                long freshShipHits = moves.stream()
                        .filter(m -> m.getMoveType() == MoveType.ATTACK_DRONE)
                        .filter(m -> m.getTargetY() == 5 && (m.getTargetX() == 5 || m.getTargetX() == 6))
                        .count();
                assertEquals(1 + freshShipHits, ship.getHits());
            }
        }

        @Test
        @DisplayName("throws when every cell in range was already shot")
        void throwsWhenNothingLeftToFire() {
            for (int x = 4; x <= 6; x++) {
                for (int y = 4; y <= 6; y++) {
                    match.getMoves().add(fired(MoveType.STANDARD, x, y));
                }
            }

            assertThrows(GameRuleViolationException.class,
                    () -> attackStrategy.execute(match, shooterId, attackMoveRequest));
            verify(moveService, never()).create(any(Move.class));
        }

        @Test
        @DisplayName("cells only scanned by a surveillance drone can still be fired at")
        void surveillanceCellsStayTargetable() {
            for (int x = 4; x <= 6; x++) {
                for (int y = 4; y <= 6; y++) {
                    match.getMoves().add(fired(MoveType.SURVEILLANCE_DRONE, x, y));
                }
            }

            List<Move> moves = attackStrategy.execute(match, shooterId, attackMoveRequest);

            assertTrue(moves.size() >= 3);
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
