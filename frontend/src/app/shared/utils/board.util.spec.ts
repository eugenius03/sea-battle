import { ShipResponse } from '@core/models/game.model';
import {
  getHaloCells,
  getShipCells,
  isHitResult,
  isValidPlacement,
  resolveOrientation,
} from './board.util';

const ship = (over: Partial<ShipResponse> = {}): ShipResponse => ({
  id: 1,
  shipType: 'TRIPLE_DECK',
  startX: 2,
  startY: 3,
  orientation: 'HORIZONTAL',
  ...over,
});

describe('board.util', () => {
  it('getShipCells expands by orientation and ship size', () => {
    expect(getShipCells(ship())).toEqual([
      { x: 2, y: 3 },
      { x: 3, y: 3 },
      { x: 4, y: 3 },
    ]);
    expect(getShipCells(ship({ orientation: 'VERTICAL', shipType: 'DOUBLE_DECK' }))).toEqual([
      { x: 2, y: 3 },
      { x: 2, y: 4 },
    ]);
  });

  it('getHaloCells clips to the grid and has no duplicates', () => {
    const halo = getHaloCells([{ x: 0, y: 0 }]);
    expect(halo).toHaveLength(4);
    const keys = getHaloCells(getShipCells(ship())).map((c) => `${c.x},${c.y}`);
    expect(new Set(keys).size).toBe(keys.length);
    expect(keys).toHaveLength(15);
  });

  it('isValidPlacement rejects out of bounds and touching ships, honours ignore id', () => {
    const ships = [ship()];
    expect(isValidPlacement(ships, 'SINGLE_DECK', 9, 9, 'HORIZONTAL')).toBe(true);
    expect(isValidPlacement(ships, 'DOUBLE_DECK', 9, 0, 'HORIZONTAL')).toBe(false);
    expect(isValidPlacement(ships, 'SINGLE_DECK', 5, 4, 'HORIZONTAL')).toBe(false);
    expect(isValidPlacement(ships, 'SINGLE_DECK', 5, 3, 'HORIZONTAL', 1)).toBe(true);
  });

  it('resolveOrientation flips only on overflow and when the flip is valid', () => {
    expect(resolveOrientation([], 'TRIPLE_DECK', 8, 0, 'HORIZONTAL')).toBe('VERTICAL');
    expect(resolveOrientation([], 'TRIPLE_DECK', 2, 0, 'HORIZONTAL')).toBe('HORIZONTAL');
    expect(resolveOrientation([], 'TRIPLE_DECK', 8, 8, 'HORIZONTAL')).toBe('HORIZONTAL');
  });

  it('isHitResult covers HIT, SUNK, FINISHED only', () => {
    expect(['HIT', 'SUNK', 'FINISHED'].every(isHitResult)).toBe(true);
    expect(['MISS', 'SURVEILLANCE'].some(isHitResult)).toBe(false);
  });
});
