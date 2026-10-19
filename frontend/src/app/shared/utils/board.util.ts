import { Orientation, SHIP_DEFINITIONS, ShipResponse, ShipType } from '@core/models/game.model';

export const GRID_SIZE = 10;

export interface Coord {
  x: number;
  y: number;
}

export function cellKey(x: number, y: number): string {
  return `${x},${y}`;
}

export function isInBounds(x: number, y: number): boolean {
  return x >= 0 && x < GRID_SIZE && y >= 0 && y < GRID_SIZE;
}

export function isHitResult(result: string): boolean {
  return result === 'HIT' || result === 'SUNK' || result === 'FINISHED';
}

export function getOccupiedCells(
  type: ShipType,
  startX: number,
  startY: number,
  orientation: Orientation,
): Coord[] {
  const size = SHIP_DEFINITIONS[type]?.size ?? 1;
  const cells: Coord[] = [];
  for (let i = 0; i < size; i++) {
    cells.push({
      x: orientation === 'HORIZONTAL' ? startX + i : startX,
      y: orientation === 'VERTICAL' ? startY + i : startY,
    });
  }
  return cells;
}

export function getShipCells(ship: ShipResponse): Coord[] {
  return getOccupiedCells(ship.shipType, ship.startX, ship.startY, ship.orientation);
}

export function getHaloCells(cells: Coord[]): Coord[] {
  const seen = new Set<string>();
  const halo: Coord[] = [];
  for (const c of cells) {
    for (let dx = -1; dx <= 1; dx++) {
      for (let dy = -1; dy <= 1; dy++) {
        const x = c.x + dx;
        const y = c.y + dy;
        const key = cellKey(x, y);
        if (isInBounds(x, y) && !seen.has(key)) {
          seen.add(key);
          halo.push({ x, y });
        }
      }
    }
  }
  return halo;
}

export function isShipWithinBounds(
  type: ShipType,
  startX: number,
  startY: number,
  orientation: Orientation,
): boolean {
  return getOccupiedCells(type, startX, startY, orientation).every((c) => isInBounds(c.x, c.y));
}

export function isValidPlacement(
  ships: ShipResponse[],
  type: ShipType,
  startX: number,
  startY: number,
  orientation: Orientation,
  ignoreShipId?: number | null,
): boolean {
  if (!isShipWithinBounds(type, startX, startY, orientation)) return false;

  const target = getOccupiedCells(type, startX, startY, orientation);
  for (const ship of ships) {
    if (ignoreShipId != null && ship.id === ignoreShipId) continue;
    for (const s of getShipCells(ship)) {
      if (target.some((t) => Math.abs(t.x - s.x) <= 1 && Math.abs(t.y - s.y) <= 1)) {
        return false;
      }
    }
  }
  return true;
}

export function resolveOrientation(
  ships: ShipResponse[],
  type: ShipType,
  startX: number,
  startY: number,
  orientation: Orientation,
  ignoreShipId?: number | null,
): Orientation {
  if (isShipWithinBounds(type, startX, startY, orientation)) return orientation;
  const flipped: Orientation = orientation === 'HORIZONTAL' ? 'VERTICAL' : 'HORIZONTAL';
  return isValidPlacement(ships, type, startX, startY, flipped, ignoreShipId)
    ? flipped
    : orientation;
}
