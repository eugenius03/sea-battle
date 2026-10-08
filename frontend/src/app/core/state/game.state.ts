import { computed, inject, Injectable, signal } from '@angular/core';
import {
  BoardCell,
  GameInfoResponse,
  Orientation,
  SHIP_DEFINITIONS,
  ShipRequest,
  ShipResponse,
  ShipType,
} from '@core/models/game.model';
import { MatchStatus } from '@core/models/match.model';
import { MoveRequest, MoveResponse, MoveType } from '@core/models/move.model';
import { GameService } from '@core/services/game.service';
import { MatchService } from '@core/services/match.service';
import { WebSocketService } from '@core/services/websocket.service';

import { Subscription } from 'rxjs';

const GRID_SIZE = 10;
const MAX_SURVEILLANCE_USAGES = 1;
const MAX_ATTACK_DRONE_USAGES = 2;

function isPlacementValid(
  ships: ShipResponse[],
  type: ShipType,
  startX: number,
  startY: number,
  orientation: Orientation,
  ignoreShipId?: number | null,
): boolean {
  const size = SHIP_DEFINITIONS[type].size;
  if (orientation === 'HORIZONTAL') {
    if (startX < 0 || startX + size > GRID_SIZE || startY < 0 || startY >= GRID_SIZE) return false;
  } else {
    if (startX < 0 || startX >= GRID_SIZE || startY < 0 || startY + size > GRID_SIZE) return false;
  }

  const targetCells: { x: number; y: number }[] = [];
  for (let i = 0; i < size; i++) {
    targetCells.push({
      x: orientation === 'HORIZONTAL' ? startX + i : startX,
      y: orientation === 'VERTICAL' ? startY + i : startY,
    });
  }

  for (const ship of ships) {
    if (ignoreShipId != null && ship.id === ignoreShipId) continue;
    const def = SHIP_DEFINITIONS[ship.shipType];
    for (let j = 0; j < def.size; j++) {
      const sx = ship.orientation === 'HORIZONTAL' ? ship.startX + j : ship.startX;
      const sy = ship.orientation === 'VERTICAL' ? ship.startY + j : ship.startY;

      for (const tc of targetCells) {
        if (Math.abs(tc.x - sx) <= 1 && Math.abs(tc.y - sy) <= 1) {
          return false;
        }
      }
    }
  }

  return true;
}

function computeSunkPlayerCells(ships: ShipResponse[], oppMoves: MoveResponse[]): Set<string> {
  const sunkCells = new Set<string>();

  for (const ship of ships) {
    const def = SHIP_DEFINITIONS[ship.shipType];
    let hitsOnShip = 0;
    let anyCellReportedSunk = false;
    const shipCellKeys: string[] = [];

    for (let i = 0; i < def.size; i++) {
      const sx = ship.orientation === 'HORIZONTAL' ? ship.startX + i : ship.startX;
      const sy = ship.orientation === 'VERTICAL' ? ship.startY + i : ship.startY;
      const k = `${sx},${sy}`;
      shipCellKeys.push(k);

      const move = oppMoves.find((m) => m.targetX === sx && m.targetY === sy);
      if (move) {
        if (move.moveResult === 'HIT') {
          hitsOnShip++;
        } else if (move.moveResult === 'SUNK' || move.moveResult === 'FINISHED') {
          hitsOnShip++;
          anyCellReportedSunk = true;
        }
      }
    }

    if (anyCellReportedSunk || hitsOnShip === def.size) {
      for (const k of shipCellKeys) {
        sunkCells.add(k);
      }
    }
  }

  return sunkCells;
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

@Injectable({
  providedIn: 'root',
})
export class GameState {
  private readonly gameService = inject(GameService);
  private readonly matchService = inject(MatchService);
  private readonly wsService = inject(WebSocketService);

  readonly inviteToken = signal<string>('');
  readonly matchPlayerId = signal<string>('');
  readonly matchStatus = signal<MatchStatus>('WAITING');
  readonly isItMyTurn = signal<boolean>(false);
  readonly isOpponentConnected = signal<boolean>(false);
  readonly isOpponentReady = signal<boolean>(false);
  readonly isPlayerReady = signal<boolean>(false);
  readonly winnerPlayerId = signal<string | null>(null);
  readonly winnerShips = signal<ShipResponse[]>([]);

  readonly playerShips = signal<ShipResponse[]>([]);
  readonly playerMoves = signal<MoveResponse[]>([]);
  readonly opponentMoves = signal<MoveResponse[]>([]);

  readonly isWinner = computed<boolean>(() => {
    if (this.matchStatus() !== 'FINISHED') return false;

    const winnerId = this.winnerPlayerId();
    const myId = this.matchPlayerId();
    if (winnerId && myId) {
      return String(winnerId) === String(myId);
    }

    const myMoves = this.playerMoves();
    if (myMoves.some((m) => m.moveResult === 'FINISHED')) {
      return true;
    }

    const oppMoves = this.opponentMoves();
    if (oppMoves.some((m) => m.moveResult === 'FINISHED')) {
      return false;
    }

    const myShips = this.playerShips();
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

  readonly hoveredCell = signal<{ x: number; y: number } | null>(null);
  readonly hoveredEnemyCell = signal<{ x: number; y: number } | null>(null);
  readonly draggedOverKey = signal<string | null>(null);
  readonly draggedShipId = signal<number | null>(null);
  readonly selectedPlacedShipId = signal<number | null>(null);

  readonly selectedMoveType = signal<MoveType>('STANDARD');
  readonly droneUsage = signal<Record<MoveType, number>>({
    STANDARD: 0,
    SURVEILLANCE_DRONE: MAX_SURVEILLANCE_USAGES,
    ATTACK_DRONE: MAX_ATTACK_DRONE_USAGES,
  });

  readonly rematchRequestedByOpponent = signal<boolean>(false);
  readonly rematchAgreedToken = signal<string | null>(null);

  readonly selectedShipToPlace = signal<ShipType | null>(null);
  readonly placementOrientation = signal<Orientation>('HORIZONTAL');

  private subscriptions: Subscription[] = [];

  readonly remainingShipsToPlace = computed(() => {
    const placed = this.playerShips();
    const counts: Record<ShipType, number> = {
      QUADRO_DECK: SHIP_DEFINITIONS.QUADRO_DECK.allowedCount,
      TRIPLE_DECK: SHIP_DEFINITIONS.TRIPLE_DECK.allowedCount,
      DOUBLE_DECK: SHIP_DEFINITIONS.DOUBLE_DECK.allowedCount,
      SINGLE_DECK: SHIP_DEFINITIONS.SINGLE_DECK.allowedCount,
    };

    for (const ship of placed) {
      if (counts[ship.shipType] > 0) {
        counts[ship.shipType]--;
      }
    }
    return counts;
  });

  readonly allShipsPlaced = computed(() => {
    const counts = this.remainingShipsToPlace();
    return (
      counts.QUADRO_DECK === 0 &&
      counts.TRIPLE_DECK === 0 &&
      counts.DOUBLE_DECK === 0 &&
      counts.SINGLE_DECK === 0
    );
  });

  readonly canMarkReady = computed<boolean>(() => {
    return this.allShipsPlaced() && this.matchStatus() !== 'WAITING' && !this.isPlayerReady();
  });

  readonly surveillanceRemaining = computed(() => {
    return this.droneUsage().SURVEILLANCE_DRONE ?? MAX_SURVEILLANCE_USAGES;
  });

  readonly attackDronesRemaining = computed(() => {
    return this.droneUsage().ATTACK_DRONE ?? MAX_ATTACK_DRONE_USAGES;
  });

  readonly playerCells = computed<BoardCell[]>(() => {
    const cells: BoardCell[] = [];
    const ships = this.playerShips();
    const oppMoves = this.opponentMoves();
    const isLocked =
      this.isPlayerReady() ||
      this.matchStatus() === 'IN_PROGRESS' ||
      this.matchStatus() === 'FINISHED';
    const hovered = this.hoveredCell();
    const selectedType = this.selectedShipToPlace();
    const orientation = this.placementOrientation();
    const dragKey = this.draggedOverKey();
    const draggedId = this.draggedShipId();

    const sunkShipCellKeys = computeSunkPlayerCells(ships, oppMoves);

    let previewValid = false;
    let previewCells: Set<string> = new Set();

    if (!isLocked && hovered) {
      let shipTypeToPreview: ShipType | null = selectedType;
      let shipOrientationToPreview: Orientation = orientation;
      let ignoreId: number | null = null;

      const selectedPlacedId = this.selectedPlacedShipId();
      if (draggedId != null) {
        const draggingShip = ships.find((s) => s.id === draggedId);
        if (draggingShip) {
          shipTypeToPreview = draggingShip.shipType;
          shipOrientationToPreview = draggingShip.orientation;
          ignoreId = draggingShip.id;
        }
      } else if (selectedPlacedId != null) {
        const placedShip = ships.find((s) => s.id === selectedPlacedId);
        if (placedShip) {
          shipTypeToPreview = placedShip.shipType;
          shipOrientationToPreview = placedShip.orientation;
          ignoreId = placedShip.id;
        }
      }

      if (shipTypeToPreview) {
        const size = SHIP_DEFINITIONS[shipTypeToPreview].size;

        if (shipOrientationToPreview === 'HORIZONTAL' && hovered.x + size > GRID_SIZE) {
          if (
            isPlacementValid(ships, shipTypeToPreview, hovered.x, hovered.y, 'VERTICAL', ignoreId)
          ) {
            shipOrientationToPreview = 'VERTICAL';
          }
        } else if (shipOrientationToPreview === 'VERTICAL' && hovered.y + size > GRID_SIZE) {
          if (
            isPlacementValid(ships, shipTypeToPreview, hovered.x, hovered.y, 'HORIZONTAL', ignoreId)
          ) {
            shipOrientationToPreview = 'HORIZONTAL';
          }
        }

        previewValid = isPlacementValid(
          ships,
          shipTypeToPreview,
          hovered.x,
          hovered.y,
          shipOrientationToPreview,
          ignoreId,
        );

        for (let i = 0; i < size; i++) {
          const px = shipOrientationToPreview === 'HORIZONTAL' ? hovered.x + i : hovered.x;
          const py = shipOrientationToPreview === 'VERTICAL' ? hovered.y + i : hovered.y;
          if (px >= 0 && px < GRID_SIZE && py >= 0 && py < GRID_SIZE) {
            previewCells.add(`${px},${py}`);
          }
        }
      }
    }

    for (let y = 0; y < GRID_SIZE; y++) {
      for (let x = 0; x < GRID_SIZE; x++) {
        const key = `${x},${y}`;
        const cell: BoardCell = { x, y, key };

        for (const ship of ships) {
          const def = SHIP_DEFINITIONS[ship.shipType];
          for (let i = 0; i < def.size; i++) {
            const sx = ship.orientation === 'HORIZONTAL' ? ship.startX + i : ship.startX;
            const sy = ship.orientation === 'VERTICAL' ? ship.startY + i : ship.startY;
            if (sx === x && sy === y) {
              cell.hasShip = true;
              cell.shipId = ship.id;
              break;
            }
          }
          if (cell.hasShip) break;
        }

        for (const move of oppMoves) {
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

        if (sunkShipCellKeys.has(key)) {
          cell.isHit = true;
          cell.isSunk = true;
        }

        if (dragKey === key) {
          cell.isDragOver = true;
        }

        if (previewCells.has(key)) {
          if (previewValid) {
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
    const myMoves = this.playerMoves();
    const isWin = this.isWinner();
    const myShipIds = new Set(this.playerShips().map((s) => s.id));
    const winners =
      !isWin && this.matchStatus() === 'FINISHED'
        ? this.winnerShips().filter((s) => !myShipIds.has(s.id))
        : [];
    const hovered = this.hoveredEnemyCell();
    const moveType = this.selectedMoveType();
    const isDrone =
      (moveType === 'SURVEILLANCE_DRONE' || moveType === 'ATTACK_DRONE') &&
      this.matchStatus() === 'IN_PROGRESS';

    const sunkEnemyCellKeys = computeSunkEnemyCells(myMoves);

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

        for (const ship of winners) {
          const def = SHIP_DEFINITIONS[ship.shipType];
          for (let i = 0; i < def.size; i++) {
            const sx = ship.orientation === 'HORIZONTAL' ? ship.startX + i : ship.startX;
            const sy = ship.orientation === 'VERTICAL' ? ship.startY + i : ship.startY;
            if (sx === x && sy === y) {
              cell.hasShip = true;
              cell.shipId = ship.id;
              break;
            }
          }
          if (cell.hasShip) break;
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

  initMatch(inviteToken: string): void {
    this.reset();
    this.inviteToken.set(inviteToken);

    this.gameService.getGameInfo(inviteToken).subscribe({
      next: (info: GameInfoResponse) => {
        this.updateFromGameInfo(info);
        this.setupWebSocketSubscriptions(inviteToken, info.matchPlayerId);
        this.autoSelectNextShip();
      },
      error: (err) => {
        if (err?.status === 404 || err?.error?.message?.includes('PLAYER_NOT_FOUND')) {
          this.matchService.joinMatch(inviteToken).subscribe({
            next: () => {
              this.gameService.getGameInfo(inviteToken).subscribe({
                next: (info: GameInfoResponse) => {
                  this.updateFromGameInfo(info);
                  this.setupWebSocketSubscriptions(inviteToken, info.matchPlayerId);
                  this.autoSelectNextShip();
                },
                error: (joinInfoErr) =>
                  console.error('Failed to get match info after joining:', joinInfoErr),
              });
            },
            error: (joinErr) => console.error('Failed to auto-join match:', joinErr),
          });
          return;
        }
        console.error('Failed to get match info:', err);
      },
    });
  }

  updateFromGameInfo(info: GameInfoResponse): void {
    this.playerShips.set(info.playerShips || []);
    this.playerMoves.set(info.playerMoves || []);
    this.opponentMoves.set(info.opponentMoves || []);
    const resolvedStatus =
      info.matchStatus === 'WAITING' && this.isOpponentConnected() ? 'PLANNING' : info.matchStatus;
    this.matchStatus.set(resolvedStatus);
    this.matchPlayerId.set(info.matchPlayerId);
    this.isItMyTurn.set(info.isItMyTurn);
    if (info.droneUsage) {
      this.droneUsage.set(info.droneUsage);
    }
    if (info.matchStatus === 'FINISHED') {
      if (info.isItMyTurn) {
        this.winnerPlayerId.set(info.matchPlayerId);
      }
    }
  }

  private setupWebSocketSubscriptions(inviteToken: string, matchPlayerId: string): void {
    if (!matchPlayerId) return;

    this.subscriptions.push(
      this.wsService.watchMatchPresence(inviteToken, matchPlayerId).subscribe((presence) => {
        switch (presence.presenceEventType) {
          case 'OPPONENT_CONNECTED':
          case 'RECONNECTED':
            this.isOpponentConnected.set(true);
            if (this.matchStatus() === 'WAITING') {
              this.matchStatus.set('PLANNING');
            }
            break;
          case 'DISCONNECTED':
            this.isOpponentConnected.set(false);
            break;
          case 'OPPONENT_READY':
            this.isOpponentReady.set(true);
            break;
          case 'REMATCH_REQUESTED':
            this.rematchRequestedByOpponent.set(true);
            break;
          case 'REMATCH_AGREED':
            if (presence.inviteToken) {
              this.rematchAgreedToken.set(presence.inviteToken);
            }
            break;
        }
      }),
    );

    this.subscriptions.push(
      this.wsService.watchMatchStatus(inviteToken, matchPlayerId).subscribe((statusMsg) => {
        this.matchStatus.set(statusMsg.matchStatus);
        if (statusMsg.currentTurnPlayerId) {
          this.isItMyTurn.set(statusMsg.currentTurnPlayerId === this.matchPlayerId());
        }
        if (statusMsg.matchStatus === 'FINISHED') {
          if (statusMsg.currentTurnPlayerId) {
            this.winnerPlayerId.set(statusMsg.currentTurnPlayerId);
          }
        }
        if (statusMsg.winnerShips && statusMsg.winnerShips.length > 0) {
          this.winnerShips.set(statusMsg.winnerShips);
        }
      }),
    );

    this.subscriptions.push(
      this.wsService.watchMatchMoves(inviteToken, matchPlayerId).subscribe((moveMsg) => {
        if (moveMsg.moveResponses && moveMsg.moveResponses.length > 0) {
          this.opponentMoves.update((prev) => [...prev, ...moveMsg.moveResponses]);
          const oppFinished = moveMsg.moveResponses.some((r) => r.moveResult === 'FINISHED');
          if (oppFinished) {
            this.matchStatus.set('FINISHED');
            this.winnerPlayerId.set('opponent');
          }
        }
        this.isItMyTurn.set(moveMsg.isItMyTurn);
      }),
    );
  }

  setHoveredCell(cell: { x: number; y: number } | null): void {
    this.hoveredCell.set(cell);
  }

  setHoveredEnemyCell(cell: { x: number; y: number } | null): void {
    this.hoveredEnemyCell.set(cell);
  }

  setDraggedOverKey(key: string | null): void {
    this.draggedOverKey.set(key);
  }

  setDraggedShipId(id: number | null): void {
    this.draggedShipId.set(id);
  }

  selectPlacedShip(id: number | null): void {
    this.selectedPlacedShipId.set(id);
  }

  randomizeShips(): void {
    const token = this.inviteToken();
    if (!token) return;

    this.gameService.generateRandomShips(token).subscribe({
      next: (ships) => {
        this.playerShips.set(ships);
        this.selectedShipToPlace.set(null);
      },
      error: (err) => console.error('Failed to generate random fleet:', err),
    });
  }

  placeShipAt(
    startX: number,
    startY: number,
    shipTypeOverride?: ShipType,
    orientationOverride?: Orientation,
  ): void {
    const token = this.inviteToken();
    const shipType = shipTypeOverride || this.selectedShipToPlace();
    let orientation = orientationOverride || this.placementOrientation();
    if (!token || !shipType) return;

    const size = SHIP_DEFINITIONS[shipType].size;
    if (orientation === 'HORIZONTAL' && startX + size > GRID_SIZE) {
      if (isPlacementValid(this.playerShips(), shipType, startX, startY, 'VERTICAL')) {
        orientation = 'VERTICAL';
      }
    } else if (orientation === 'VERTICAL' && startY + size > GRID_SIZE) {
      if (isPlacementValid(this.playerShips(), shipType, startX, startY, 'HORIZONTAL')) {
        orientation = 'HORIZONTAL';
      }
    }

    const req: ShipRequest = { shipType, startX, startY, orientation };

    this.gameService.placeShip(token, req).subscribe({
      next: (shipId) => {
        this.playerShips.update((prev) => [
          ...prev,
          { id: shipId, shipType, startX, startY, orientation },
        ]);
        this.autoSelectNextShip();
      },
      error: (err) => console.error('Invalid ship placement:', err),
    });
  }

  rotateShip(shipId: number): void {
    const token = this.inviteToken();
    if (!token) return;

    const ship = this.playerShips().find((s) => s.id === shipId);
    if (!ship) return;

    const newOrientation: Orientation =
      ship.orientation === 'HORIZONTAL' ? 'VERTICAL' : 'HORIZONTAL';

    this.gameService.moveShip(token, shipId, ship.startX, ship.startY, newOrientation).subscribe({
      next: () => {
        this.playerShips.update((prev) =>
          prev.map((s) => (s.id === shipId ? { ...s, orientation: newOrientation } : s)),
        );
        if (this.selectedPlacedShipId() === shipId) {
          this.selectedPlacedShipId.set(null);
          this.hoveredCell.set(null);
        }
      },
      error: (err) => console.error('Failed to rotate ship:', err),
    });
  }

  canMoveShipTo(shipId: number, newX: number, newY: number): boolean {
    const ship = this.playerShips().find((s) => s.id === shipId);
    if (!ship) return false;

    let orientation = ship.orientation;
    const size = SHIP_DEFINITIONS[ship.shipType].size;
    if (orientation === 'HORIZONTAL' && newX + size > GRID_SIZE) {
      if (isPlacementValid(this.playerShips(), ship.shipType, newX, newY, 'VERTICAL', shipId)) {
        orientation = 'VERTICAL';
      }
    } else if (orientation === 'VERTICAL' && newY + size > GRID_SIZE) {
      if (isPlacementValid(this.playerShips(), ship.shipType, newX, newY, 'HORIZONTAL', shipId)) {
        orientation = 'HORIZONTAL';
      }
    }

    return isPlacementValid(this.playerShips(), ship.shipType, newX, newY, orientation, shipId);
  }

  moveShipTo(shipId: number, newX: number, newY: number): void {
    const token = this.inviteToken();
    if (!token) return;

    const ship = this.playerShips().find((s) => s.id === shipId);
    if (!ship) return;

    let orientation = ship.orientation;
    const size = SHIP_DEFINITIONS[ship.shipType].size;
    if (orientation === 'HORIZONTAL' && newX + size > GRID_SIZE) {
      if (isPlacementValid(this.playerShips(), ship.shipType, newX, newY, 'VERTICAL', shipId)) {
        orientation = 'VERTICAL';
      }
    } else if (orientation === 'VERTICAL' && newY + size > GRID_SIZE) {
      if (isPlacementValid(this.playerShips(), ship.shipType, newX, newY, 'HORIZONTAL', shipId)) {
        orientation = 'HORIZONTAL';
      }
    }

    this.gameService.moveShip(token, shipId, newX, newY, orientation).subscribe({
      next: () => {
        this.playerShips.update((prev) =>
          prev.map((s) =>
            s.id === shipId ? { ...s, startX: newX, startY: newY, orientation } : s,
          ),
        );
      },
      error: (err) => console.error('Failed to move ship:', err),
    });
  }

  toggleOrientation(): void {
    this.placementOrientation.update((curr) => (curr === 'HORIZONTAL' ? 'VERTICAL' : 'HORIZONTAL'));
  }

  autoSelectNextShip(): void {
    const remaining = this.remainingShipsToPlace();
    for (const [type, count] of Object.entries(remaining) as [ShipType, number][]) {
      if (count > 0) {
        this.selectedShipToPlace.set(type);
        return;
      }
    }
    this.selectedShipToPlace.set(null);
  }

  resetPlacementInteraction(): void {
    this.draggedShipId.set(null);
    this.draggedOverKey.set(null);
    this.selectedPlacedShipId.set(null);
    this.hoveredCell.set(null);
  }

  reloadFleet(): void {
    const token = this.inviteToken();
    this.resetPlacementInteraction();
    if (!token) return;

    this.gameService.getGameInfo(token).subscribe({
      next: (info: GameInfoResponse) => {
        this.playerShips.set(info.playerShips || []);
        this.autoSelectNextShip();
      },
      error: (err) => console.error('Failed to reload fleet:', err),
    });
  }

  markReady(): void {
    const token = this.inviteToken();
    if (!token || !this.canMarkReady()) return;

    this.gameService.markReady(token).subscribe({
      next: () => {
        this.isPlayerReady.set(true);
      },
      error: (err) => console.error('Failed to mark ready:', err),
    });
  }

  fireAt(x: number, y: number): void {
    const token = this.inviteToken();
    if (!token || !this.isItMyTurn()) return;

    const moveType = this.selectedMoveType();
    const req: MoveRequest = { x, y, moveType };

    this.gameService.fire(token, req).subscribe({
      next: (responses) => {
        this.playerMoves.update((prev) => [...prev, ...responses]);
        const hasFinished = responses.some((r) => r.moveResult === 'FINISHED');
        if (hasFinished) {
          this.matchStatus.set('FINISHED');
          this.winnerPlayerId.set(this.matchPlayerId());
        }
        if (moveType !== 'STANDARD') {
          this.droneUsage.update((prev) => ({
            ...prev,
            [moveType]: Math.max(0, (prev[moveType] ?? 0) - 1),
          }));
          this.selectedMoveType.set('STANDARD');
          this.isItMyTurn.set(false);
        } else {
          const hasHit = responses.some(
            (r) => r.moveResult === 'HIT' || r.moveResult === 'SUNK' || r.moveResult === 'FINISHED',
          );
          if (!hasHit) {
            this.isItMyTurn.set(false);
          } else {
            this.isItMyTurn.set(true);
          }
        }
      },
      error: (err) => console.error('Fire command failed:', err),
    });
  }

  clearSubscriptions(): void {
    for (const sub of this.subscriptions) {
      sub.unsubscribe();
    }
    this.subscriptions = [];
    this.wsService.disconnect();
  }

  reset(): void {
    this.clearSubscriptions();
    this.inviteToken.set('');
    this.matchPlayerId.set('');
    this.matchStatus.set('WAITING');
    this.isItMyTurn.set(false);
    this.isOpponentConnected.set(false);
    this.isOpponentReady.set(false);
    this.isPlayerReady.set(false);
    this.winnerPlayerId.set(null);
    this.winnerShips.set([]);
    this.playerShips.set([]);
    this.playerMoves.set([]);
    this.opponentMoves.set([]);
    this.hoveredCell.set(null);
    this.hoveredEnemyCell.set(null);
    this.draggedOverKey.set(null);
    this.draggedShipId.set(null);
    this.selectedPlacedShipId.set(null);
    this.selectedMoveType.set('STANDARD');
    this.selectedShipToPlace.set(null);
    this.droneUsage.set({
      STANDARD: 0,
      SURVEILLANCE_DRONE: MAX_SURVEILLANCE_USAGES,
      ATTACK_DRONE: MAX_ATTACK_DRONE_USAGES,
    });
    this.rematchRequestedByOpponent.set(false);
    this.rematchAgreedToken.set(null);
  }
}
