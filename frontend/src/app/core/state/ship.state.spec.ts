import { setupGameTestBed } from './game.testing';
import { BoardViewState } from './board-view.state';
import { MatchState } from './match.state';
import { ShipState } from './ship.state';
import { of, Subject } from 'rxjs';

describe('ShipState', () => {
  let view: BoardViewState;
  let match: MatchState;
  let ships: ShipState;
  let mockGameService: ReturnType<typeof setupGameTestBed>['mockGameService'];

  beforeEach(() => {
    ({ view, match, ships, mockGameService } = setupGameTestBed());
  });

  it('should allow placing ships during WAITING state', () => {
    match.inviteToken.set('test-token');
    ships.selectedShipToPlace.set('SINGLE_DECK');

    ships.placeShipAt(2, 2);

    expect(mockGameService.placeShip).toHaveBeenCalledWith('test-token', {
      shipType: 'SINGLE_DECK',
      startX: 2,
      startY: 2,
      orientation: 'HORIZONTAL',
    });

    expect(ships.playerShips().length).toBe(1);
    expect(ships.playerShips()[0].id).toBe(101);
  });

  it('should compute valid hover preview when hovering empty cell', () => {
    ships.selectedShipToPlace.set('DOUBLE_DECK');
    ships.placementOrientation.set('HORIZONTAL');
    ships.setHoveredCell({ x: 0, y: 0 });

    const cells = view.playerCells();
    const c0 = cells.find((c) => c.x === 0 && c.y === 0);
    const c1 = cells.find((c) => c.x === 1 && c.y === 0);

    expect(c0?.isPlacementValid).toBe(true);
    expect(c1?.isPlacementValid).toBe(true);
  });

  it('should compute invalid hover preview when ship would exceed board boundaries in both axes', () => {
    ships.selectedShipToPlace.set('QUADRO_DECK'); // length 4
    ships.placementOrientation.set('HORIZONTAL');
    ships.setHoveredCell({ x: 8, y: 8 }); // 8 + 4 = 12 > 10 in both axes

    const cells = view.playerCells();
    const c88 = cells.find((c) => c.x === 8 && c.y === 8);
    expect(c88?.isPlacementInvalid).toBe(true);
  });

  it('should auto-rotate horizontal ship to vertical along the right border when it fits vertically', () => {
    ships.selectedShipToPlace.set('QUADRO_DECK'); // length 4
    ships.placementOrientation.set('HORIZONTAL');
    ships.setHoveredCell({ x: 8, y: 0 }); // 8 + 4 = 12 > 10 horizontally, but fits vertically (0..3)

    const cells = view.playerCells();
    const c80 = cells.find((c) => c.x === 8 && c.y === 0);
    const c83 = cells.find((c) => c.x === 8 && c.y === 3);
    expect(c80?.isPlacementValid).toBe(true);
    expect(c83?.isPlacementValid).toBe(true);
  });

  it('should auto-rotate vertical ship to horizontal along the bottom border when it fits horizontally', () => {
    ships.selectedShipToPlace.set('QUADRO_DECK'); // length 4
    ships.placementOrientation.set('VERTICAL');
    ships.setHoveredCell({ x: 0, y: 8 }); // 8 + 4 = 12 > 10 vertically, but fits horizontally (0..3)

    const cells = view.playerCells();
    const c08 = cells.find((c) => c.x === 0 && c.y === 8);
    const c38 = cells.find((c) => c.x === 3 && c.y === 8);
    expect(c08?.isPlacementValid).toBe(true);
    expect(c38?.isPlacementValid).toBe(true);
  });

  it('should rotate a placed ship', () => {
    match.inviteToken.set('test-token');
    ships.playerShips.set([
      {
        id: 42,
        shipType: 'DOUBLE_DECK',
        startX: 3,
        startY: 3,
        orientation: 'HORIZONTAL',
      },
    ]);

    ships.rotateShip(42);

    expect(mockGameService.moveShip).toHaveBeenCalledWith('test-token', 42, 3, 3, 'VERTICAL');
    expect(ships.playerShips()[0].orientation).toBe('VERTICAL');
  });

  it('should validate canMoveShipTo properly with auto-rotation along borders', () => {
    ships.playerShips.set([
      { id: 10, shipType: 'TRIPLE_DECK', startX: 0, startY: 0, orientation: 'HORIZONTAL' },
    ]);

    // Move to (5, 5) is valid
    expect(ships.canMoveShipTo(10, 5, 5)).toBe(true);

    // Move to right edge (8, 2) auto-rotates to vertical and is valid
    expect(ships.canMoveShipTo(10, 8, 2)).toBe(true);

    // Invalid: out of bounds even with auto-rotation (8, 9)
    expect(ships.canMoveShipTo(10, 8, 9)).toBe(false);
  });

  it('should preview selected placed ship at hovered cell', () => {
    ships.playerShips.set([
      { id: 20, shipType: 'DOUBLE_DECK', startX: 0, startY: 0, orientation: 'HORIZONTAL' },
    ]);
    ships.selectPlacedShip(20);
    ships.setHoveredCell({ x: 4, y: 4 });

    const cells = view.playerCells();
    const cell44 = cells.find((c) => c.x === 4 && c.y === 4);
    const cell54 = cells.find((c) => c.x === 5 && c.y === 4);

    expect(cell44?.isPlacementValid).toBe(true);
    expect(cell54?.isPlacementValid).toBe(true);
  });

  describe('placement interaction reset', () => {
    it('should reset all in-flight placement and drag states via resetPlacementInteraction', () => {
      ships.draggedShipId.set(42);
      ships.draggedOverKey.set('3,4');
      ships.selectedPlacedShipId.set(42);
      ships.hoveredCell.set({ x: 3, y: 4 });

      ships.resetPlacementInteraction();

      expect(ships.draggedShipId()).toBeNull();
      expect(ships.draggedOverKey()).toBeNull();
      expect(ships.selectedPlacedShipId()).toBeNull();
      expect(ships.hoveredCell()).toBeNull();
    });

    it('should reload fleet from game info service via reloadFleet', () => {
      match.inviteToken.set('test-token');
      ships.draggedShipId.set(10);
      mockGameService.getGameInfo = vi.fn().mockReturnValue(
        of({
          playerShips: [
            { id: 5, shipType: 'SINGLE_DECK', startX: 1, startY: 1, orientation: 'HORIZONTAL' },
          ],
          playerMoves: [],
          opponentMoves: [],
          matchStatus: 'PLANNING',
          matchPlayerId: 'p1',
          currentTurnPlayerId: null,
        }),
      );

      ships.reloadFleet();

      expect(ships.draggedShipId()).toBeNull();
      expect(mockGameService.getGameInfo).toHaveBeenCalledWith('test-token');
      expect(ships.playerShips().length).toBe(1);
      expect(ships.playerShips()[0].id).toBe(5);
    });
  });
});
