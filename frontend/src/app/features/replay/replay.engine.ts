import { BoardCell, ShipResponse } from '@core/models/game.model';
import { MatchParticipantResponse, ReplayMoveResponse } from '@core/models/match.model';
import {
  GRID_SIZE,
  cellKey,
  getHaloCells,
  getShipCells,
  isHitResult,
} from '@shared/utils/board.util';

const COLUMNS = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J'];

export function isShipSunk(ship: ShipResponse, incomingMoves: ReplayMoveResponse[]): boolean {
  const hitKeys = new Set(
    incomingMoves.filter((m) => isHitResult(m.moveResult)).map((m) => cellKey(m.targetX, m.targetY)),
  );
  return getShipCells(ship).every((c) => hitKeys.has(cellKey(c.x, c.y)));
}

export interface ReplayStep {
  primaryMove: ReplayMoveResponse;
  allMoves: ReplayMoveResponse[];
}

export function groupReplaySteps(
  moves: ReplayMoveResponse[],
  player1Ships: ShipResponse[] = [],
  player2Ships: ShipResponse[] = [],
): ReplayStep[] {
  if (!moves || moves.length === 0) return [];

  const steps: ReplayStep[] = [];
  const allShips = [...player1Ships, ...player2Ships];
  const hasOriginFlags = moves.some((m) => m.isMoveOrigin !== undefined);

  let i = 0;
  while (i < moves.length) {
    const current = moves[i];

    // Case 1: SURVEILLANCE_DRONE (3x3 scan bundled into 1 single replay step)
    if (current.moveType === 'SURVEILLANCE_DRONE') {
      const droneMoves: ReplayMoveResponse[] = [current];
      const shooter = current.shooterId;
      i++;
      while (
        i < moves.length &&
        moves[i].moveType === 'SURVEILLANCE_DRONE' &&
        moves[i].shooterId === shooter
      ) {
        // If a new scan explicitly starts with isMoveOrigin === true and we already recorded an origin
        if (
          hasOriginFlags &&
          moves[i].isMoveOrigin === true &&
          droneMoves.some((m) => m.isMoveOrigin === true)
        ) {
          break;
        }
        droneMoves.push(moves[i]);
        i++;
      }
      const primary = droneMoves.find((m) => m.isMoveOrigin === true) ?? droneMoves[0];
      steps.push({
        primaryMove: primary,
        allMoves: droneMoves,
      });
      continue;
    }

    // Case 2: ATTACK_DRONE (all salvo strikes + buffer misses bundled into 1 replay step)
    if (current.moveType === 'ATTACK_DRONE') {
      const droneMoves: ReplayMoveResponse[] = [current];
      const shooter = current.shooterId;
      i++;
      while (i < moves.length) {
        const next = moves[i];
        if (next.shooterId !== shooter) break;

        if (next.moveType === 'ATTACK_DRONE') {
          if (
            hasOriginFlags &&
            next.isMoveOrigin === true &&
            droneMoves.some((m) => m.isMoveOrigin === true)
          ) {
            break;
          }
          droneMoves.push(next);
          i++;
        } else if (hasOriginFlags && next.isMoveOrigin === false && next.moveResult === 'MISS') {
          // Surrounding buffer misses if a ship sank during attack drone
          droneMoves.push(next);
          i++;
        } else {
          break;
        }
      }
      const primary = droneMoves.find((m) => m.isMoveOrigin === true) ?? droneMoves[0];
      steps.push({
        primaryMove: primary,
        allMoves: droneMoves,
      });
      continue;
    }

    // Case 3: STANDARD move with origin flags
    if (hasOriginFlags) {
      if (current.isMoveOrigin !== false) {
        const stepMoves: ReplayMoveResponse[] = [current];
        i++;
        // Absorb auto-generated buffer misses around a sunk ship (isMoveOrigin === false)
        while (i < moves.length && moves[i].isMoveOrigin === false) {
          stepMoves.push(moves[i]);
          i++;
        }
        steps.push({
          primaryMove: current,
          allMoves: stepMoves,
        });
      } else {
        // Stray buffer miss: attach to previous step if possible
        if (steps.length > 0) {
          steps[steps.length - 1].allMoves.push(current);
        } else {
          steps.push({ primaryMove: current, allMoves: [current] });
        }
        i++;
      }
      continue;
    }

    // Case 4: Legacy matches without isMoveOrigin flags
    const stepMoves: ReplayMoveResponse[] = [current];
    i++;
    if (current.moveResult === 'SUNK' || current.moveResult === 'FINISHED') {
      const hitShip = allShips.find((s) => {
        const cells = getShipCells(s);
        return cells.some((c) => c.x === current.targetX && c.y === current.targetY);
      });
      if (hitShip) {
        const bufferCells = new Set(
          getHaloCells(getShipCells(hitShip)).map((c) => cellKey(c.x, c.y)),
        );
        while (
          i < moves.length &&
          moves[i].moveResult === 'MISS' &&
          bufferCells.has(cellKey(moves[i].targetX, moves[i].targetY))
        ) {
          stepMoves.push(moves[i]);
          i++;
        }
      }
    }
    steps.push({
      primaryMove: current,
      allMoves: stepMoves,
    });
  }

  return steps;
}

export interface ShooterIds {
  p1ShooterId: string;
  p2ShooterId: string;
}

/** Resolves which shooter id belongs to which player, even when playerId is missing from cache. */
export function resolveShooterIds(
  p1: MatchParticipantResponse | null,
  p2: MatchParticipantResponse | null,
  rawMoves: ReplayMoveResponse[],
): ShooterIds {
  if (!p1 || !p2) return { p1ShooterId: 'p1', p2ShooterId: 'p2' };

  if (p1.playerId && p2.playerId) {
    return { p1ShooterId: p1.playerId, p2ShooterId: p2.playerId };
  }

  const uniqueShooters = Array.from(new Set(rawMoves.map((m) => m.shooterId).filter(Boolean)));
  if (uniqueShooters.length === 0) {
    return { p1ShooterId: 'p1', p2ShooterId: 'p2' };
  }
  if (uniqueShooters.length === 1) {
    return { p1ShooterId: uniqueShooters[0], p2ShooterId: 'p2' };
  }

  const idA = uniqueShooters[0];
  const idB = uniqueShooters[1];

  const p1Cells = new Set(p1.ships.flatMap((s) => getShipCells(s).map((c) => cellKey(c.x, c.y))));

  let idAHitsP1 = 0;
  for (const m of rawMoves) {
    if (m.shooterId === idA && isHitResult(m.moveResult)) {
      if (p1Cells.has(cellKey(m.targetX, m.targetY))) {
        idAHitsP1++;
      }
    }
  }

  // If idA hit Player 1's ships, idA was Player 2 shooting!
  if (idAHitsP1 > 0) {
    return { p1ShooterId: idB, p2ShooterId: idA };
  }
  return { p1ShooterId: idA, p2ShooterId: idB };
}

export function describeStep(step: ReplayStep | null, shooterName: string): string {
  if (!step) return 'Match Start - Fleets positioned';
  const move = step.primaryMove;
  const coord = `${COLUMNS[move.targetX] ?? move.targetX}${move.targetY + 1}`;

  if (move.moveType === 'SURVEILLANCE_DRONE') {
    const detectedCount = step.allMoves.filter((m) => m.moveResult === 'SURVEILLANCE').length;
    const resultText =
      detectedCount > 0
        ? `DETECTED (${detectedCount} ship ${detectedCount === 1 ? 'cell' : 'cells'})`
        : 'NO DETECTIONS';
    return `${shooterName} scanned 3×3 at ${coord} → [${resultText}]`;
  }

  if (move.moveType === 'ATTACK_DRONE') {
    const strikes = step.allMoves.filter((m) => m.moveType === 'ATTACK_DRONE');
    const hits = strikes.filter((m) => isHitResult(m.moveResult)).length;
    const hasFinished = strikes.some((m) => m.moveResult === 'FINISHED');
    const hasSunk = strikes.some((m) => m.moveResult === 'SUNK');

    let resultText = `${hits}/${strikes.length} HITS`;
    if (hasFinished) {
      resultText = 'FLEET DESTROYED (VICTORY)';
    } else if (hasSunk) {
      resultText = `${hits}/${strikes.length} HITS - SUNK!`;
    } else if (hits === 0) {
      resultText = `ALL MISS (${strikes.length} shots)`;
    }

    return `${shooterName} airstrike near ${coord} → [${resultText}]`;
  }

  let result = move.moveResult;
  if (move.moveResult === 'FINISHED') result = 'FLEET DESTROYED (VICTORY)';
  else if (move.moveResult === 'SUNK') result = 'SUNK!';
  else if (move.moveResult === 'HIT') result = 'HIT!';
  else if (move.moveResult === 'MISS') result = 'MISS';

  return `${shooterName} fired at ${coord} → [${result}]`;
}

export function buildReplayBoardCells(
  player: MatchParticipantResponse,
  activeMoves: ReplayMoveResponse[],
  lastStep: ReplayStep | null,
  incomingShooterId: string,
): BoardCell[] {
  // Only shots fired by the opponent at THIS player's board
  const incomingMoves = activeMoves.filter((m) => m.shooterId === incomingShooterId);
  const cells: BoardCell[] = [];

  // Map ship cells
  const shipCellMap = new Map<string, number>();
  for (const ship of player.ships) {
    for (const c of getShipCells(ship)) {
      shipCellMap.set(cellKey(c.x, c.y), ship.id);
    }
  }

  // Determine which ships are completely sunk
  const sunkCellKeys = new Set<string>();
  const bufferMissKeys = new Set<string>();

  for (const ship of player.ships) {
    if (isShipSunk(ship, incomingMoves)) {
      for (const c of getShipCells(ship)) {
        sunkCellKeys.add(cellKey(c.x, c.y));
      }
      // Mark 1-cell buffer halo around sunk ship as misses
      for (const h of getHaloCells(getShipCells(ship))) {
        const haloKey = cellKey(h.x, h.y);
        if (!shipCellMap.has(haloKey)) bufferMissKeys.add(haloKey);
      }
    }
  }

  // Determine recent move target keys on THIS player's board
  const recentTargetKeys = new Set<string>();
  if (lastStep !== null && lastStep.primaryMove.shooterId === incomingShooterId) {
    if (lastStep.primaryMove.moveType === 'SURVEILLANCE_DRONE') {
      // Highlight all 9 scan tiles
      for (const m of lastStep.allMoves) {
        recentTargetKeys.add(cellKey(m.targetX, m.targetY));
      }
    } else if (lastStep.primaryMove.moveType === 'ATTACK_DRONE') {
      // Highlight all drone strikes
      for (const m of lastStep.allMoves) {
        if (m.moveType === 'ATTACK_DRONE') {
          recentTargetKeys.add(cellKey(m.targetX, m.targetY));
        }
      }
    } else {
      // Standard shot: highlight the exact shot coordinate
      recentTargetKeys.add(cellKey(lastStep.primaryMove.targetX, lastStep.primaryMove.targetY));
    }
  }

  for (let y = 0; y < GRID_SIZE; y++) {
    for (let x = 0; x < GRID_SIZE; x++) {
      const key = cellKey(x, y);
      const cell: BoardCell = { x, y, key };

      if (shipCellMap.has(key)) {
        cell.hasShip = true;
        cell.shipId = shipCellMap.get(key);
      }

      for (const move of incomingMoves) {
        if (move.targetX === x && move.targetY === y) {
          if (isHitResult(move.moveResult)) {
            cell.isHit = true;
          } else if (move.moveResult === 'MISS') {
            cell.isMiss = true;
          } else if (move.moveResult === 'SURVEILLANCE') {
            cell.isSurveillance = true;
          }
        }
      }

      // Render buffer misses around sunk ships
      if (bufferMissKeys.has(key)) {
        cell.isMiss = true;
      }

      if (sunkCellKeys.has(key)) {
        cell.isHit = true;
        cell.isSunk = true;
      }

      // Damage or sinking supersedes surveillance detection
      if (cell.isHit || cell.isSunk) {
        cell.isSurveillance = false;
      }

      if (recentTargetKeys.has(key)) {
        cell.isRecentMove = true;
      }

      cells.push(cell);
    }
  }

  return cells;
}
