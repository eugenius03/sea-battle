import { setupGameTestBed } from './game.testing';
import { GameFlowService } from './game-flow.service';
import { MatchState } from './match.state';
import { MoveState } from './move.state';
import { ShipState } from './ship.state';
import { of } from 'rxjs';

describe('GameFlowService', () => {
  let flow: GameFlowService;
  let moves: MoveState;
  let match: MatchState;
  let ships: ShipState;
  let mockGameService: ReturnType<typeof setupGameTestBed>['mockGameService'];
  let presenceSubject: ReturnType<typeof setupGameTestBed>['presenceSubject'];
  let reconnectedSubject: ReturnType<typeof setupGameTestBed>['reconnectedSubject'];

  beforeEach(() => {
    ({ flow, moves, match, ships, mockGameService, presenceSubject, reconnectedSubject } =
      setupGameTestBed());
  });

  it('should transition matchStatus from WAITING to PLANNING when opponent connects', () => {
    flow.initMatch('test-token');
    expect(match.matchStatus()).toBe('WAITING');

    presenceSubject.next({
      presenceEventType: 'OPPONENT_CONNECTED',
      matchPlayerId: 'p2',
    });

    expect(match.isOpponentConnected()).toBe(true);
    expect(match.matchStatus()).toBe('PLANNING');
  });

  it('should reset all states when initMatch is invoked for a new token', () => {
    match.matchStatus.set('FINISHED');
    match.winnerPlayerId.set('p1');
    match.rematchAgreedToken.set('token-2');

    flow.initMatch('token-2');

    expect(match.inviteToken()).toBe('token-2');
    expect(match.rematchAgreedToken()).toBeNull();
    expect(match.winnerPlayerId()).toBeNull();
  });

  describe('readiness and opponent gating', () => {
    const fullFleet = [
      {
        id: 1,
        shipType: 'QUADRO_DECK' as const,
        startX: 0,
        startY: 0,
        orientation: 'HORIZONTAL' as const,
      },
      {
        id: 2,
        shipType: 'TRIPLE_DECK' as const,
        startX: 0,
        startY: 2,
        orientation: 'HORIZONTAL' as const,
      },
      {
        id: 3,
        shipType: 'TRIPLE_DECK' as const,
        startX: 4,
        startY: 2,
        orientation: 'HORIZONTAL' as const,
      },
      {
        id: 4,
        shipType: 'DOUBLE_DECK' as const,
        startX: 0,
        startY: 4,
        orientation: 'HORIZONTAL' as const,
      },
      {
        id: 5,
        shipType: 'DOUBLE_DECK' as const,
        startX: 3,
        startY: 4,
        orientation: 'HORIZONTAL' as const,
      },
      {
        id: 6,
        shipType: 'DOUBLE_DECK' as const,
        startX: 6,
        startY: 4,
        orientation: 'HORIZONTAL' as const,
      },
      {
        id: 7,
        shipType: 'SINGLE_DECK' as const,
        startX: 0,
        startY: 6,
        orientation: 'HORIZONTAL' as const,
      },
      {
        id: 8,
        shipType: 'SINGLE_DECK' as const,
        startX: 2,
        startY: 6,
        orientation: 'HORIZONTAL' as const,
      },
      {
        id: 9,
        shipType: 'SINGLE_DECK' as const,
        startX: 4,
        startY: 6,
        orientation: 'HORIZONTAL' as const,
      },
      {
        id: 10,
        shipType: 'SINGLE_DECK' as const,
        startX: 6,
        startY: 6,
        orientation: 'HORIZONTAL' as const,
      },
    ];

    it('should disable canMarkReady when matchStatus is WAITING even if all ships are placed', () => {
      ships.playerShips.set(fullFleet);
      expect(ships.allShipsPlaced()).toBe(true);

      match.matchStatus.set('WAITING');
      expect(flow.canMarkReady()).toBe(false);

      match.inviteToken.set('token-123');
      flow.markReady();
      expect(mockGameService.markReady).not.toHaveBeenCalled();
    });

    it('should enable canMarkReady and allow markReady when matchStatus transitions to PLANNING', () => {
      ships.playerShips.set(fullFleet);
      match.matchStatus.set('PLANNING');
      expect(flow.canMarkReady()).toBe(true);

      match.inviteToken.set('token-123');
      flow.markReady();
      expect(mockGameService.markReady).toHaveBeenCalledWith('token-123');
      expect(match.isPlayerReady()).toBe(true);
    });
  });

  describe('reconnect', () => {
    it('should re-synchronize game state from getGameInfo when WebSocket reconnects', () => {
      flow.initMatch('reconnect-token');

      expect(mockGameService.getGameInfo).toHaveBeenCalledWith('reconnect-token');

      mockGameService.getGameInfo = vi.fn().mockReturnValue(
        of({
          playerShips: [],
          playerMoves: [],
          opponentMoves: [
            { shooterId: 'p2', targetX: 3, targetY: 4, moveType: 'STANDARD', moveResult: 'HIT' },
          ],
          matchStatus: 'IN_PROGRESS',
          matchPlayerId: 'p1',
          droneUsage: { STANDARD: 0, SURVEILLANCE_DRONE: 1, ATTACK_DRONE: 1 },
          currentTurnPlayerId: 'p1',
        }),
      );

      reconnectedSubject.next();

      expect(mockGameService.getGameInfo).toHaveBeenCalledWith('reconnect-token');
      expect(moves.opponentMoves().length).toBe(1);
      expect(moves.opponentMoves()[0].targetX).toBe(3);
      expect(match.isItMyTurn()).toBe(true);
    });
  });
});
