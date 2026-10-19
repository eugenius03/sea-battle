import { computed, inject, Injectable, signal } from '@angular/core';
import {
  GameInfoResponse,
  Orientation,
  SHIP_DEFINITIONS,
  ShipRequest,
  ShipResponse,
  ShipType,
} from '@core/models/game.model';
import { GameService } from '@core/services/game.service';
import { MatchState } from '@core/state/match.state';
import {
  cellKey,
  getOccupiedCells,
  isInBounds,
  isValidPlacement,
  resolveOrientation,
} from '@shared/utils/board.util';

export interface PlacementPreview {
  keys: Set<string>;
  valid: boolean;
}

@Injectable({ providedIn: 'root' })
export class ShipState {
  private readonly gameService = inject(GameService);
  private readonly match = inject(MatchState);

  readonly playerShips = signal<ShipResponse[]>([]);
  readonly hoveredCell = signal<{ x: number; y: number } | null>(null);
  readonly draggedOverKey = signal<string | null>(null);
  readonly draggedShipId = signal<number | null>(null);
  readonly selectedPlacedShipId = signal<number | null>(null);
  readonly selectedShipToPlace = signal<ShipType | null>(null);
  readonly placementOrientation = signal<Orientation>('HORIZONTAL');

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

  readonly isPlacementLocked = computed<boolean>(
    () =>
      this.match.isPlayerReady() ||
      this.match.matchStatus() === 'IN_PROGRESS' ||
      this.match.matchStatus() === 'FINISHED',
  );

  readonly placementPreview = computed<PlacementPreview | null>(() => {
    const hovered = this.hoveredCell();
    if (this.isPlacementLocked() || !hovered) return null;

    const ships = this.playerShips();
    let type: ShipType | null = this.selectedShipToPlace();
    let orientation: Orientation = this.placementOrientation();
    let ignoreId: number | null = null;

    const anchorId = this.draggedShipId() ?? this.selectedPlacedShipId();
    const anchor = anchorId != null ? ships.find((s) => s.id === anchorId) : undefined;
    if (anchor) {
      type = anchor.shipType;
      orientation = anchor.orientation;
      ignoreId = anchor.id;
    }
    if (!type) return null;

    orientation = resolveOrientation(ships, type, hovered.x, hovered.y, orientation, ignoreId);
    const keys = new Set<string>();
    for (const c of getOccupiedCells(type, hovered.x, hovered.y, orientation)) {
      if (isInBounds(c.x, c.y)) keys.add(cellKey(c.x, c.y));
    }
    return {
      keys,
      valid: isValidPlacement(ships, type, hovered.x, hovered.y, orientation, ignoreId),
    };
  });

  setPlayerShips(ships: ShipResponse[]): void {
    this.playerShips.set(ships);
  }

  setHoveredCell(cell: { x: number; y: number } | null): void {
    this.hoveredCell.set(cell);
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
    const token = this.match.inviteToken();
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
    const token = this.match.inviteToken();
    const shipType = shipTypeOverride || this.selectedShipToPlace();
    let orientation = orientationOverride || this.placementOrientation();
    if (!token || !shipType) return;

    orientation = resolveOrientation(this.playerShips(), shipType, startX, startY, orientation);

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
    const token = this.match.inviteToken();
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

    const orientation = resolveOrientation(
      this.playerShips(),
      ship.shipType,
      newX,
      newY,
      ship.orientation,
      shipId,
    );

    return isValidPlacement(this.playerShips(), ship.shipType, newX, newY, orientation, shipId);
  }

  moveShipTo(shipId: number, newX: number, newY: number): void {
    const token = this.match.inviteToken();
    if (!token) return;

    const ship = this.playerShips().find((s) => s.id === shipId);
    if (!ship) return;

    const orientation = resolveOrientation(
      this.playerShips(),
      ship.shipType,
      newX,
      newY,
      ship.orientation,
      shipId,
    );

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
    const token = this.match.inviteToken();
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

  reset(): void {
    this.playerShips.set([]);
    this.resetPlacementInteraction();
    this.selectedShipToPlace.set(null);
    this.placementOrientation.set('HORIZONTAL');
  }
}
