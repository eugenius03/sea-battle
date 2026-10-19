import { computed, inject, Injectable } from '@angular/core';
import { BoardCell, SHIP_DEFINITIONS, ShipResponse } from '@core/models/game.model';
import { MoveResponse } from '@core/models/move.model';
import { MatchState } from '@core/state/match.state';
import { MoveState } from '@core/state/move.state';
import { ShipState } from '@core/state/ship.state';
import { cellKey, getShipCells, GRID_SIZE, isHitResult } from '@shared/utils/board.util';

function computeSunkPlayerCells(ships: ShipResponse[], oppMoves: MoveResponse[]): Set<string> {
  const sunkCells = new Set<string>();

  for (const ship of ships) {
    const cells = getShipCells(ship);
    let hitsOnShip = 0;
    let anyCellReportedSunk = false;

    for (const { x, y } of cells) {
      const move = oppMoves.filter((m) => m.targetX === x && m.targetY === y).at(-1);
      if (move && isHitResult(move.moveResult)) {
        hitsOnShip++;
        if (move.moveResult !== 'HIT') anyCellReportedSunk = true;
      }
    }

    if (anyCellReportedSunk || hitsOnShip === cells.length) {
      for (const { x, y } of cells) sunkCells.add(cellKey(x, y));
    }
  }

  return sunkCells;
}

function buildShipCellMap(ships: ShipResponse[]): Map<string, number> {
  const map = new Map<string, number>();
  for (const ship of ships) {
    for (const { x, y } of getShipCells(ship)) map.set(cellKey(x, y), ship.id);
  }
  return map;
}

const DIRECTIONS = [
  { dx: -1, dy: 0 },
  { dx: 1, dy: 0 },
  { dx: 0, dy: -1 },
  { dx: 0, dy: 1 },
];

function computeSunkEnemyCells(moves: MoveResponse[]): Set<string> {
  const sunkCells = new Set<string>();
  const hitKeys = new Set<string>();
  const sunkOrigins: { x: number; y: number }[] = [];

  for (const move of moves) {
    if (isHitResult(move.moveResult)) hitKeys.add(cellKey(move.targetX, move.targetY));
    if (move.moveResult === 'SUNK' || move.moveResult === 'FINISHED') {
      sunkOrigins.push({ x: move.targetX, y: move.targetY });
    }
  }

  for (const origin of sunkOrigins) {
    sunkCells.add(cellKey(origin.x, origin.y));
    for (const { dx, dy } of DIRECTIONS) {
      let x = origin.x + dx;
      let y = origin.y + dy;
      while (x >= 0 && x < GRID_SIZE && y >= 0 && y < GRID_SIZE && hitKeys.has(cellKey(x, y))) {
        sunkCells.add(cellKey(x, y));
        x += dx;
        y += dy;
      }
    }
  }

  return sunkCells;
}

function ownShipsSurvive(ships: ShipResponse[], oppMoves: MoveResponse[]): boolean {
  const sunkCells = computeSunkPlayerCells(ships, oppMoves);
  const totalShipCells = ships.reduce(
    (sum, s) => sum + (SHIP_DEFINITIONS[s.shipType]?.size ?? 1),
    0,
  );
  return sunkCells.size < totalShipCells;
}

function applyOpponentMoves(cell: BoardCell, oppMoves: MoveResponse[]): void {
  for (const move of oppMoves) {
    if (move.targetX !== cell.x || move.targetY !== cell.y) continue;
    if (isHitResult(move.moveResult)) {
      if (cell.hasShip) cell.isHit = true;
      else cell.isMiss = true;
    } else if (move.moveResult === 'MISS') {
      cell.isMiss = true;
    } else if (move.moveResult === 'SURVEILLANCE') {
      cell.isSurveillance = true;
    }
  }
}

function applyPlayerMoves(cell: BoardCell, myMoves: MoveResponse[]): void {
  for (const move of myMoves) {
    if (move.targetX !== cell.x || move.targetY !== cell.y) continue;
    if (isHitResult(move.moveResult)) {
      cell.isHit = true;
    } else if (move.moveResult === 'MISS') {
      cell.isMiss = true;
    } else if (move.moveResult === 'SURVEILLANCE') {
      cell.isSurveillance = true;
    }
  }
}

function applyPlacementPreview(
  cell: BoardCell,
  dragKey: string | null,
  preview: { keys: Set<string>; valid: boolean } | null,
): void {
  if (dragKey === cell.key) cell.isDragOver = true;
  if (preview?.keys.has(cell.key)) {
    if (preview.valid) cell.isPlacementValid = true;
    else cell.isPlacementInvalid = true;
  }
}

function applyDronePreview(
  cell: BoardCell,
  hovered: { x: number; y: number } | null,
  moveType: 'SURVEILLANCE_DRONE' | 'ATTACK_DRONE',
): void {
  if (!hovered || Math.abs(cell.x - hovered.x) > 1 || Math.abs(cell.y - hovered.y) > 1) return;
  cell.isDronePreview = true;
  cell.dronePreviewType = moveType;
  if (cell.x === hovered.x && cell.y === hovered.y) cell.isDroneCenter = true;
}

function markSunk(cell: BoardCell, sunkKeys: Set<string>): void {
  if (sunkKeys.has(cell.key)) {
    cell.isHit = true;
    cell.isSunk = true;
  }
}

@Injectable({ providedIn: 'root' })
export class BoardViewState {
  private readonly match = inject(MatchState);
  private readonly ships = inject(ShipState);
  private readonly moves = inject(MoveState);

  readonly isWinner = computed<boolean>(() => {
    if (this.match.matchStatus() !== 'FINISHED') return false;

    const winnerId = this.match.winnerPlayerId();
    const myId = this.match.matchPlayerId();
    if (winnerId && myId) {
      return String(winnerId) === String(myId);
    }

    const myMoves = this.moves.playerMoves();
    if (myMoves.some((m) => m.moveResult === 'FINISHED')) {
      return true;
    }

    const oppMoves = this.moves.opponentMoves();
    if (oppMoves.some((m) => m.moveResult === 'FINISHED')) {
      return false;
    }

    const myShips = this.ships.playerShips();
    return myShips.length > 0 && ownShipsSurvive(myShips, oppMoves);
  });

  readonly playerCells = computed<BoardCell[]>(() => {
    const cells: BoardCell[] = [];
    const ships = this.ships.playerShips();
    const oppMoves = this.moves.opponentMoves();
    const dragKey = this.ships.draggedOverKey();
    const preview = this.ships.placementPreview();

    const sunkShipCellKeys = computeSunkPlayerCells(ships, oppMoves);
    const shipCellMap = buildShipCellMap(ships);

    for (let y = 0; y < GRID_SIZE; y++) {
      for (let x = 0; x < GRID_SIZE; x++) {
        const cell: BoardCell = { x, y, key: cellKey(x, y) };

        const shipId = shipCellMap.get(cell.key);
        if (shipId !== undefined) {
          cell.hasShip = true;
          cell.shipId = shipId;
        }

        applyOpponentMoves(cell, oppMoves);
        markSunk(cell, sunkShipCellKeys);
        applyPlacementPreview(cell, dragKey, preview);
        cells.push(cell);
      }
    }

    return cells;
  });

  readonly enemyCells = computed<BoardCell[]>(() => {
    const cells: BoardCell[] = [];
    const myMoves = this.moves.playerMoves();
    const isWin = this.isWinner();
    const myShipIds = new Set(this.ships.playerShips().map((s) => s.id));
    const winners =
      !isWin && this.match.matchStatus() === 'FINISHED'
        ? this.match.winnerShips().filter((s) => !myShipIds.has(s.id))
        : [];
    const hovered = this.moves.hoveredEnemyCell();
    const moveType = this.moves.selectedMoveType();
    const droneType =
      (moveType === 'SURVEILLANCE_DRONE' || moveType === 'ATTACK_DRONE') &&
      this.match.matchStatus() === 'IN_PROGRESS'
        ? moveType
        : null;

    const sunkEnemyCellKeys = computeSunkEnemyCells(myMoves);
    const winnerCellMap = buildShipCellMap(winners);

    for (let y = 0; y < GRID_SIZE; y++) {
      for (let x = 0; x < GRID_SIZE; x++) {
        const cell: BoardCell = { x, y, key: cellKey(x, y) };

        applyPlayerMoves(cell, myMoves);
        markSunk(cell, sunkEnemyCellKeys);

        const winnerShipId = winnerCellMap.get(cell.key);
        if (winnerShipId !== undefined) {
          cell.hasShip = true;
          cell.shipId = winnerShipId;
        }

        if (droneType) applyDronePreview(cell, hovered, droneType);
        if (cell.isHit || cell.isMiss || cell.isSunk) cell.isInactive = true;
        cells.push(cell);
      }
    }

    return cells;
  });
}
