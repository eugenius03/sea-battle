import { setupGameTestBed } from './game.testing';
import { BoardViewState } from './board-view.state';
import { MatchState } from './match.state';
import { MoveState } from './move.state';
import { ShipState } from './ship.state';

describe('BoardViewState', () => {
  let view: BoardViewState;
  let moves: MoveState;
  let match: MatchState;
  let ships: ShipState;

  beforeEach(() => {
    ({ view, moves, match, ships } = setupGameTestBed());
  });

  it('should initialize with 100 playerCells and 100 enemyCells', () => {
    expect(view.playerCells().length).toBe(100);
    expect(view.enemyCells().length).toBe(100);
  });

  it('should render ship in playerCells when playerShips signal has vessels', () => {
    ships.playerShips.set([
      {
        id: 1,
        shipType: 'SINGLE_DECK',
        startX: 3,
        startY: 4,
        orientation: 'HORIZONTAL',
      },
    ]);

    const cells = view.playerCells();
    const shipCell = cells.find((c) => c.x === 3 && c.y === 4);
    expect(shipCell).toBeDefined();
    expect(shipCell?.hasShip).toBe(true);
    expect(shipCell?.shipId).toBe(1);

    const waterCell = cells.find((c) => c.x === 0 && c.y === 0);
    expect(waterCell?.hasShip).toBeFalsy();
  });

  it('should turn ALL cells of a sunk enemy vessel to isSunk (red)', () => {
    moves.playerMoves.set([
      { targetX: 2, targetY: 5, moveResult: 'HIT' },
      { targetX: 3, targetY: 5, moveResult: 'HIT' },
      { targetX: 4, targetY: 5, moveResult: 'SUNK' },
    ]);

    const cells = view.enemyCells();
    const c2 = cells.find((c) => c.x === 2 && c.y === 5);
    const c3 = cells.find((c) => c.x === 3 && c.y === 5);
    const c4 = cells.find((c) => c.x === 4 && c.y === 5);

    expect(c2?.isSunk).toBe(true);
    expect(c3?.isSunk).toBe(true);
    expect(c4?.isSunk).toBe(true);
  });

  it('should turn ALL cells of a sunk friendly vessel to isSunk (red)', () => {
    ships.playerShips.set([
      {
        id: 99,
        shipType: 'DOUBLE_DECK',
        startX: 1,
        startY: 1,
        orientation: 'VERTICAL',
      },
    ]);
    moves.opponentMoves.set([
      { targetX: 1, targetY: 1, moveResult: 'HIT' },
      { targetX: 1, targetY: 2, moveResult: 'SUNK' },
    ]);

    const cells = view.playerCells();
    const c11 = cells.find((c) => c.x === 1 && c.y === 1);
    const c12 = cells.find((c) => c.x === 1 && c.y === 2);

    expect(c11?.isSunk).toBe(true);
    expect(c12?.isSunk).toBe(true);
  });

  it('should mark a non-ship cell as isMiss and NOT isHit even if opponent move reports HIT or SUNK', () => {
    ships.playerShips.set([
      {
        id: 99,
        shipType: 'SINGLE_DECK',
        startX: 0,
        startY: 0,
        orientation: 'HORIZONTAL',
      },
    ]);
    moves.opponentMoves.set([
      { targetX: 5, targetY: 5, moveResult: 'HIT' },
      { targetX: 6, targetY: 6, moveResult: 'SUNK' },
    ]);

    const cells = view.playerCells();
    const c55 = cells.find((c) => c.x === 5 && c.y === 5);
    const c66 = cells.find((c) => c.x === 6 && c.y === 6);

    expect(c55?.hasShip).toBeFalsy();
    expect(c55?.isHit).toBeFalsy();
    expect(c55?.isMiss).toBe(true);

    expect(c66?.hasShip).toBeFalsy();
    expect(c66?.isHit).toBeFalsy();
    expect(c66?.isMiss).toBe(true);
  });

  it('should turn friendly ship to isSunk even when prior moves include SURVEILLANCE on the same cell', () => {
    ships.playerShips.set([
      {
        id: 99,
        shipType: 'DOUBLE_DECK',
        startX: 1,
        startY: 1,
        orientation: 'VERTICAL',
      },
    ]);
    moves.opponentMoves.set([
      { targetX: 1, targetY: 1, moveResult: 'SURVEILLANCE' },
      { targetX: 1, targetY: 1, moveResult: 'HIT' },
      { targetX: 1, targetY: 2, moveResult: 'SURVEILLANCE' },
      { targetX: 1, targetY: 2, moveResult: 'SUNK' },
    ]);

    const cells = view.playerCells();
    const c11 = cells.find((c) => c.x === 1 && c.y === 1);
    const c12 = cells.find((c) => c.x === 1 && c.y === 2);

    expect(c11?.isSunk).toBe(true);
    expect(c12?.isSunk).toBe(true);
  });

  it('should correctly evaluate isWinner as true when winnerPlayerId equals matchPlayerId', () => {
    match.matchPlayerId.set('p1');
    match.matchStatus.set('FINISHED');
    match.winnerPlayerId.set('p1');

    expect(view.isWinner()).toBe(true);
  });

  it('should correctly evaluate isWinner as false when winnerPlayerId is opponent', () => {
    match.matchPlayerId.set('p1');
    match.matchStatus.set('FINISHED');
    match.winnerPlayerId.set('p2');

    expect(view.isWinner()).toBe(false);
  });

  it('should tag 3x3 drone preview cells with dronePreviewType and center cell with isDroneCenter', () => {
    match.matchStatus.set('IN_PROGRESS');
    moves.selectedMoveType.set('SURVEILLANCE_DRONE');
    moves.hoveredEnemyCell.set({ x: 3, y: 3 });

    const cells = view.enemyCells();
    const center = cells.find((c) => c.x === 3 && c.y === 3);
    const adjacent = cells.find((c) => c.x === 2 && c.y === 2);
    const outside = cells.find((c) => c.x === 5 && c.y === 5);

    expect(center?.isDronePreview).toBe(true);
    expect(center?.dronePreviewType).toBe('SURVEILLANCE_DRONE');
    expect(center?.isDroneCenter).toBe(true);

    expect(adjacent?.isDronePreview).toBe(true);
    expect(adjacent?.dronePreviewType).toBe('SURVEILLANCE_DRONE');
    expect(adjacent?.isDroneCenter).toBeUndefined();

    expect(outside?.isDronePreview).toBeUndefined();
  });

  it('should evaluate isWinner as true if player made the FINISHED move', () => {
    match.matchPlayerId.set('p1');
    match.matchStatus.set('FINISHED');
    moves.playerMoves.set([{ targetX: 0, targetY: 0, moveResult: 'FINISHED' }]);

    expect(view.isWinner()).toBe(true);
  });

  it('should evaluate isWinner as false if opponent made the FINISHED move', () => {
    match.matchPlayerId.set('p1');
    match.matchStatus.set('FINISHED');
    moves.opponentMoves.set([{ targetX: 0, targetY: 0, moveResult: 'FINISHED' }]);

    expect(view.isWinner()).toBe(false);
  });

  it('should reveal opponent ships on enemyCells when player loses', () => {
    match.matchStatus.set('FINISHED');
    match.winnerPlayerId.set('opponent-id');
    match.matchPlayerId.set('p1');
    match.winnerShips.set([
      { id: 99, shipType: 'SINGLE_DECK', startX: 7, startY: 8, orientation: 'HORIZONTAL' },
    ]);

    const enemy = view.enemyCells();
    const cell78 = enemy.find((c) => c.x === 7 && c.y === 8);
    expect(cell78?.hasShip).toBe(true);
    expect(cell78?.shipId).toBe(99);
  });

  it('should NOT reveal player ships on enemyCells when player wins', () => {
    match.matchStatus.set('FINISHED');
    match.winnerPlayerId.set('p1');
    match.matchPlayerId.set('p1');
    ships.playerShips.set([
      { id: 10, shipType: 'SINGLE_DECK', startX: 2, startY: 3, orientation: 'HORIZONTAL' },
    ]);
    match.winnerShips.set([
      { id: 10, shipType: 'SINGLE_DECK', startX: 2, startY: 3, orientation: 'HORIZONTAL' },
    ]);

    const enemy = view.enemyCells();
    const cell23 = enemy.find((c) => c.x === 2 && c.y === 3);
    expect(cell23?.hasShip).toBeUndefined();
  });
});
