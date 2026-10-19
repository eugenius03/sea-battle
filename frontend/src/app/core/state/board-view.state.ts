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

function computeSunkEnemyCells(moves: MoveResponse[]): Set<string> {
  const sunkCells = new Set<string>();
  const hitMap = new Map<string, string>();
  const sunkOrigins: { x: number; y: number }[] = [];

  for (const move of moves) {
    const key = `${move.targetX},${move.targetY}`;
    if (move.moveResult === 'HIT' || move.moveResult === 'SUNK' || move.moveResult === 'FINISHED') {
      hitMap.set(key, move.moveResult);
    }
    if (move.moveResult === 'SUNK' || move.moveResult === 'FINISHED') {
      sunkOrigins.push({ x: move.targetX, y: move.targetY });
    }
  }

  for (const origin of sunkOrigins) {
    sunkCells.add(`${origin.x},${origin.y}`);

    for (let x = origin.x - 1; x >= 0; x--) {
      const k = `${x},${origin.y}`;
      if (hitMap.has(k)) sunkCells.add(k);
      else break;
    }
    for (let x = origin.x + 1; x < GRID_SIZE; x++) {
      const k = `${x},${origin.y}`;
      if (hitMap.has(k)) sunkCells.add(k);
      else break;
    }

    for (let y = origin.y - 1; y >= 0; y--) {
      const k = `${origin.x},${y}`;
      if (hitMap.has(k)) sunkCells.add(k);
      else break;
    }
    for (let y = origin.y + 1; y < GRID_SIZE; y++) {
      const k = `${origin.x},${y}`;
      if (hitMap.has(k)) sunkCells.add(k);
      else break;
    }
  }

  return sunkCells;
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
    if (myShips.length > 0) {
      const sunkCells = computeSunkPlayerCells(myShips, oppMoves);
      let totalShipCells = 0;
      for (const s of myShips) {
        totalShipCells += SHIP_DEFINITIONS[s.shipType]?.size ?? 1;
      }
      if (sunkCells.size >= totalShipCells) {
        return false;
      }
      return true;
    }

    return false;
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
        const key = `${x},${y}`;
        const cell: BoardCell = { x, y, key };

        const shipId = shipCellMap.get(key);
        if (shipId !== undefined) {
          cell.hasShip = true;
          cell.shipId = shipId;
        }

        for (const move of oppMoves) {
          if (move.targetX === x && move.targetY === y) {
            if (
              move.moveResult === 'HIT' ||
              move.moveResult === 'SUNK' ||
              move.moveResult === 'FINISHED'
            ) {
              if (cell.hasShip) {
                cell.isHit = true;
              } else {
                cell.isMiss = true;
              }
            } else if (move.moveResult === 'MISS') {
              cell.isMiss = true;
            } else if (move.moveResult === 'SURVEILLANCE') {
              cell.isSurveillance = true;
            }
          }
        }

        if (sunkShipCellKeys.has(key)) {
          cell.isHit = true;
          cell.isSunk = true;
        }

        if (dragKey === key) {
          cell.isDragOver = true;
        }

        if (preview?.keys.has(key)) {
          if (preview.valid) {
            cell.isPlacementValid = true;
          } else {
            cell.isPlacementInvalid = true;
          }
        }

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
    const isDrone =
      (moveType === 'SURVEILLANCE_DRONE' || moveType === 'ATTACK_DRONE') &&
      this.match.matchStatus() === 'IN_PROGRESS';

    const sunkEnemyCellKeys = computeSunkEnemyCells(myMoves);
    const winnerCellMap = buildShipCellMap(winners);

    for (let y = 0; y < GRID_SIZE; y++) {
      for (let x = 0; x < GRID_SIZE; x++) {
        const key = `${x},${y}`;
        const cell: BoardCell = { x, y, key };

        for (const move of myMoves) {
          if (move.targetX === x && move.targetY === y) {
            if (
              move.moveResult === 'HIT' ||
              move.moveResult === 'SUNK' ||
              move.moveResult === 'FINISHED'
            ) {
              cell.isHit = true;
            } else if (move.moveResult === 'MISS') {
              cell.isMiss = true;
            } else if (move.moveResult === 'SURVEILLANCE') {
              cell.isSurveillance = true;
            }
          }
        }

        if (sunkEnemyCellKeys.has(key)) {
          cell.isHit = true;
          cell.isSunk = true;
        }

        const winnerShipId = winnerCellMap.get(key);
        if (winnerShipId !== undefined) {
          cell.hasShip = true;
          cell.shipId = winnerShipId;
        }

        if (isDrone && hovered) {
          if (Math.abs(x - hovered.x) <= 1 && Math.abs(y - hovered.y) <= 1) {
            cell.isDronePreview = true;
            cell.dronePreviewType = moveType as 'SURVEILLANCE_DRONE' | 'ATTACK_DRONE';
            if (x === hovered.x && y === hovered.y) {
              cell.isDroneCenter = true;
            }
          }
        }

        if (cell.isHit || cell.isMiss || cell.isSunk) {
          cell.isInactive = true;
        }

        cells.push(cell);
      }
    }

    return cells;
  });
}
