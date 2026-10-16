import { FireResponse } from '@core/models/game.model';
import { setupGameTestBed } from './game.testing';
import { GameFlowService } from './game-flow.service';
import { MatchState } from './match.state';
import { MoveState } from './move.state';
import { Subject, throwError } from 'rxjs';

describe('MoveState', () => {
  let flow: GameFlowService;
  let moves: MoveState;
  let match: MatchState;
  let mockGameService: ReturnType<typeof setupGameTestBed>['mockGameService'];

  beforeEach(() => {
    ({ flow, moves, match, mockGameService } = setupGameTestBed());
  });

  it('should accurately reflect drone usages passed from /info', () => {
    flow.updateFromGameInfo({
      playerShips: [],
      playerMoves: [],
      opponentMoves: [],
      matchStatus: 'IN_PROGRESS',
      matchPlayerId: 'p1',
      currentTurnPlayerId: 'p1',
      droneUsage: {
        STANDARD: 0,
        SURVEILLANCE_DRONE: 1,
        ATTACK_DRONE: 2,
      },
    });

    expect(moves.surveillanceRemaining()).toBe(1);
    expect(moves.attackDronesRemaining()).toBe(2);
  });

  describe('fireAt', () => {
    it('should ignore a second shot while the first is still awaiting a response', () => {
      const pending = new Subject<FireResponse>();
      mockGameService.fire = vi.fn().mockReturnValue(pending.asObservable());
      match.inviteToken.set('tok');
      match.matchPlayerId.set('p1');
      match.currentTurnPlayerId.set('p1');

      moves.fireAt(1, 1);
      moves.fireAt(2, 2);

      expect(mockGameService.fire).toHaveBeenCalledTimes(1);
      expect(moves.isFiring()).toBe(true);

      pending.next({ moveResponses: [], currentTurnPlayerId: 'p2' });
      pending.complete();
      expect(moves.isFiring()).toBe(false);
      expect(match.currentTurnPlayerId()).toBe('p2');
      expect(match.isItMyTurn()).toBe(false);
    });

    it('should release the guard when the shot is rejected', () => {
      mockGameService.fire = vi.fn().mockReturnValue(throwError(() => new Error('400')));
      match.inviteToken.set('tok');
      match.matchPlayerId.set('p1');
      match.currentTurnPlayerId.set('p1');
      vi.spyOn(console, 'error').mockImplementation(() => undefined);

      moves.fireAt(1, 1);

      expect(moves.isFiring()).toBe(false);
    });
  });
});
