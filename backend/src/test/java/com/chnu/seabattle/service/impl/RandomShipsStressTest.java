package com.chnu.seabattle.service.impl;

import com.chnu.seabattle.entity.Match;
import com.chnu.seabattle.entity.MatchPlayer;
import com.chnu.seabattle.entity.MatchStatus;
import com.chnu.seabattle.entity.Orientation;
import com.chnu.seabattle.entity.Ship;
import com.chnu.seabattle.entity.ShipType;
import com.chnu.seabattle.repository.ShipRepository;
import com.chnu.seabattle.service.GameService;
import com.chnu.seabattle.util.BoardUtils;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomShipsStressTest {

    private static final ShipType[] TYPES = {
            ShipType.QUADRO_DECK, ShipType.TRIPLE_DECK, ShipType.TRIPLE_DECK, ShipType.DOUBLE_DECK, ShipType.DOUBLE_DECK,
            ShipType.DOUBLE_DECK, ShipType.SINGLE_DECK, ShipType.SINGLE_DECK, ShipType.SINGLE_DECK, ShipType.SINGLE_DECK
    };

    private MatchPlayer player() {
        Match match = new Match();
        match.setStatus(MatchStatus.PLANNING);
        MatchPlayer p = new MatchPlayer();
        p.setId(UUID.randomUUID());
        p.setMatch(match);
        p.setShips(new ArrayList<>());
        return p;
    }

    private GameService service() {
        return new GameService(null, Mockito.mock(ShipRepository.class), null, null, null, null, null, null, null);
    }

    @Test
    void repeatedRandomGenerationAlwaysValid() {
        GameService svc = service();
        for (int run = 0; run < 2000; run++) {
            MatchPlayer p = player();
            for (int k = 0; k < 5; k++) {
                svc.generateRandomShips(p);
                assertTrue(BoardUtils.isValidFleet(p.getShips()), "invalid after run " + run + " gen " + k + ": " + dump(p));
            }
        }
    }

    @Test
    void randomAfterPartialManualPlacementKeepsTypesAndStaysValid() {
        GameService svc = service();
        for (int run = 0; run < 2000; run++) {
            MatchPlayer p = player();
            int manual = run % 10;
            List<Ship> placed = new ArrayList<>();
            for (int i = 0; i < manual; i++) {
                ShipType type = TYPES[(TYPES.length - 1 - i + run) % TYPES.length];
                Ship ship = Ship.builder().matchPlayer(p).shipType(type).startX(0).startY(i * 2).orientation(Orientation.HORIZONTAL).build();
                placed.add(ship);
                p.getShips().add(ship);
            }
            List<ShipType> typesBefore = placed.stream().map(Ship::getShipType).toList();

            svc.generateRandomShips(p);

            assertEquals(typesBefore, placed.stream().map(Ship::getShipType).toList(), "type changed with " + manual + " manual ships");
            assertTrue(p.getShips().containsAll(placed), "existing ships were replaced");
            assertTrue(BoardUtils.isValidFleet(p.getShips()), "invalid with " + manual + " manual ships: " + dump(p));
        }
    }

    private String dump(MatchPlayer p) {
        StringBuilder sb = new StringBuilder();
        for (Ship s : p.getShips()) {
            sb.append(s.getShipType()).append('@').append(s.getStartX()).append(',').append(s.getStartY()).append(s.getOrientation() == Orientation.HORIZONTAL ? 'H' : 'V').append(' ');
        }
        return sb.toString();
    }
}
