import { MatchParticipantResponse, ReplayMoveResponse } from '@core/models/match.model';
import { describeStep, groupReplaySteps, resolveShooterIds } from './replay.engine';

const mv = (over: Partial<ReplayMoveResponse>): ReplayMoveResponse => ({
  shooterId: 'a',
  targetX: 0,
  targetY: 0,
  moveType: 'STANDARD',
  moveResult: 'MISS',
  isMoveOrigin: true,
  ...over,
});

const player = (playerId: string, ships: MatchParticipantResponse['ships'] = []) =>
  ({ playerId, username: playerId, isWinner: false, ships }) as MatchParticipantResponse;

describe('replay.engine', () => {
  it('groupReplaySteps absorbs buffer misses after a sunk shot into one step', () => {
    const moves = [
      mv({ targetX: 1, moveResult: 'SUNK' }),
      mv({ targetX: 2, moveResult: 'MISS', isMoveOrigin: false }),
      mv({ shooterId: 'b', targetX: 5 }),
    ];
    const steps = groupReplaySteps(moves);
    expect(steps).toHaveLength(2);
    expect(steps[0].allMoves).toHaveLength(2);
    expect(steps[0].primaryMove).toBe(moves[0]);
  });

  it('groupReplaySteps bundles a surveillance scan into one step', () => {
    const scan = Array.from({ length: 9 }, (_, i) =>
      mv({ moveType: 'SURVEILLANCE_DRONE', moveResult: 'SURVEILLANCE', targetX: i, isMoveOrigin: i === 4 }),
    );
    const steps = groupReplaySteps(scan);
    expect(steps).toHaveLength(1);
    expect(steps[0].primaryMove.targetX).toBe(4);
  });

  it('resolveShooterIds uses playerIds when present', () => {
    expect(resolveShooterIds(player('p1'), player('p2'), [])).toEqual({
      p1ShooterId: 'p1',
      p2ShooterId: 'p2',
    });
  });

  it('describeStep handles start and a standard hit', () => {
    expect(describeStep(null, 'x')).toContain('Match Start');
    const [step] = groupReplaySteps([mv({ targetX: 2, targetY: 4, moveResult: 'HIT' })]);
    expect(describeStep(step, 'Ann')).toBe('Ann fired at C5 → [HIT!]');
  });
});
