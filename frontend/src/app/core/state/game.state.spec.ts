import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { GameState } from './game.state';
import { GameService } from '@core/services/game.service';
import { WebSocketService } from '@core/services/websocket.service';
import { of, Subject } from 'rxjs';
import { MatchStatusMessage, PresenceMessage } from '@core/models/ws.model';

describe('GameState', () => {
  let state: GameState;
  let mockGameService: Partial<GameService>;
  let mockWsService: Partial<WebSocketService>;
  let presenceSubject: Subject<PresenceMessage>;
  let statusSubject: Subject<MatchStatusMessage>;

  beforeEach(() => {
    presenceSubject = new Subject<PresenceMessage>();
    statusSubject = new Subject<MatchStatusMessage>();

    mockGameService = {
      getGameInfo: vi.fn().mockReturnValue(
        of({
          playerShips: [],
          playerMoves: [],
          opponentMoves: [],
          matchStatus: 'WAITING',
          matchPlayerId: 'p1',
          droneUsage: { STANDARD: 0, SURVEILLANCE_DRONE: 0, ATTACK_DRONE: 0 },
          isItMyTurn: false,
        }),
      ),
      placeShip: vi.fn().mockReturnValue(of(101)),
      generateRandomShips: vi.fn().mockReturnValue(of([])),
      moveShip: vi.fn().mockReturnValue(of(101)),
      markReady: vi.fn().mockReturnValue(of(undefined)),
      fire: vi.fn().mockReturnValue(of([])),
    };

    mockWsService = {
      watchMatchPresence: vi.fn().mockReturnValue(presenceSubject.asObservable()),
      watchMatchStatus: vi.fn().mockReturnValue(statusSubject.asObservable()),
      watchMatchMoves: vi.fn().mockReturnValue(of({})),
      disconnect: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        { provide: GameService, useValue: mockGameService },
        { provide: WebSocketService, useValue: mockWsService },
      ],
    });

    state = TestBed.inject(GameState);
  });

  it('should initialize with 100 playerCells and 100 enemyCells', () => {
    expect(state.playerCells().length).toBe(100);
    expect(state.enemyCells().length).toBe(100);
  });

  it('should render ship in playerCells when playerShips signal has vessels', () => {
    state.playerShips.set([
      {
        id: 1,
        shipType: 'SINGLE_DECK',
        startX: 3,
        startY: 4,
        orientation: 'HORIZONTAL',
      },
    ]);

    const cells = state.playerCells();
    const shipCell = cells.find((c) => c.x === 3 && c.y === 4);
    expect(shipCell).toBeDefined();
    expect(shipCell?.hasShip).toBe(true);
    expect(shipCell?.shipId).toBe(1);

    const waterCell = cells.find((c) => c.x === 0 && c.y === 0);
    expect(waterCell?.hasShip).toBeFalsy();
  });

  it('should transition matchStatus from WAITING to PLANNING when opponent connects', () => {
    state.initMatch('test-token');
    expect(state.matchStatus()).toBe('WAITING');

    presenceSubject.next({
      presenceEventType: 'OPPONENT_CONNECTED',
      matchPlayerId: 'p2',
      at: new Date().toISOString(),
    });

    expect(state.isOpponentConnected()).toBe(true);
    expect(state.matchStatus()).toBe('PLANNING');
  });

  it('should allow placing ships during WAITING state', () => {
    state.inviteToken.set('test-token');
    state.selectedShipToPlace.set('SINGLE_DECK');

    state.placeShipAt(2, 2);

    expect(mockGameService.placeShip).toHaveBeenCalledWith('test-token', {
      shipType: 'SINGLE_DECK',
      startX: 2,
      startY: 2,
      orientation: 'HORIZONTAL',
    });

    expect(state.playerShips().length).toBe(1);
    expect(state.playerShips()[0].id).toBe(101);
  });

  it('should compute valid hover preview when hovering empty cell', () => {
    state.selectedShipToPlace.set('DOUBLE_DECK');
    state.placementOrientation.set('HORIZONTAL');
    state.setHoveredCell({ x: 0, y: 0 });

    const cells = state.playerCells();
    const c0 = cells.find((c) => c.x === 0 && c.y === 0);
    const c1 = cells.find((c) => c.x === 1 && c.y === 0);

    expect(c0?.isPlacementValid).toBe(true);
    expect(c1?.isPlacementValid).toBe(true);
  });

  it('should compute invalid hover preview when ship would exceed board boundaries in both axes', () => {
    state.selectedShipToPlace.set('QUADRO_DECK'); // length 4
    state.placementOrientation.set('HORIZONTAL');
    state.setHoveredCell({ x: 8, y: 8 }); // 8 + 4 = 12 > 10 in both axes

    const cells = state.playerCells();
    const c88 = cells.find((c) => c.x === 8 && c.y === 8);
    expect(c88?.isPlacementInvalid).toBe(true);
  });

  it('should auto-rotate horizontal ship to vertical along the right border when it fits vertically', () => {
    state.selectedShipToPlace.set('QUADRO_DECK'); // length 4
    state.placementOrientation.set('HORIZONTAL');
    state.setHoveredCell({ x: 8, y: 0 }); // 8 + 4 = 12 > 10 horizontally, but fits vertically (0..3)

    const cells = state.playerCells();
    const c80 = cells.find((c) => c.x === 8 && c.y === 0);
    const c83 = cells.find((c) => c.x === 8 && c.y === 3);
    expect(c80?.isPlacementValid).toBe(true);
    expect(c83?.isPlacementValid).toBe(true);
  });

  it('should auto-rotate vertical ship to horizontal along the bottom border when it fits horizontally', () => {
    state.selectedShipToPlace.set('QUADRO_DECK'); // length 4
    state.placementOrientation.set('VERTICAL');
    state.setHoveredCell({ x: 0, y: 8 }); // 8 + 4 = 12 > 10 vertically, but fits horizontally (0..3)

    const cells = state.playerCells();
    const c08 = cells.find((c) => c.x === 0 && c.y === 8);
    const c38 = cells.find((c) => c.x === 3 && c.y === 8);
    expect(c08?.isPlacementValid).toBe(true);
    expect(c38?.isPlacementValid).toBe(true);
  });

  it('should rotate a placed ship', () => {
    state.inviteToken.set('test-token');
    state.playerShips.set([
      {
        id: 42,
        shipType: 'DOUBLE_DECK',
        startX: 3,
        startY: 3,
        orientation: 'HORIZONTAL',
      },
    ]);

    state.rotateShip(42);

    expect(mockGameService.moveShip).toHaveBeenCalledWith('test-token', 42, 3, 3, 'VERTICAL');
    expect(state.playerShips()[0].orientation).toBe('VERTICAL');
  });

  it('should turn ALL cells of a sunk enemy vessel to isSunk (red)', () => {
    state.playerMoves.set([
      { targetX: 2, targetY: 5, moveResult: 'HIT' },
      { targetX: 3, targetY: 5, moveResult: 'HIT' },
      { targetX: 4, targetY: 5, moveResult: 'SUNK' },
    ]);

    const cells = state.enemyCells();
    const c2 = cells.find((c) => c.x === 2 && c.y === 5);
    const c3 = cells.find((c) => c.x === 3 && c.y === 5);
    const c4 = cells.find((c) => c.x === 4 && c.y === 5);

    expect(c2?.isSunk).toBe(true);
    expect(c3?.isSunk).toBe(true);
    expect(c4?.isSunk).toBe(true);
  });

  it('should turn ALL cells of a sunk friendly vessel to isSunk (red)', () => {
    state.playerShips.set([
      {
        id: 99,
        shipType: 'DOUBLE_DECK',
        startX: 1,
        startY: 1,
        orientation: 'VERTICAL',
      },
    ]);
    state.opponentMoves.set([
      { targetX: 1, targetY: 1, moveResult: 'HIT' },
      { targetX: 1, targetY: 2, moveResult: 'SUNK' },
    ]);

    const cells = state.playerCells();
    const c11 = cells.find((c) => c.x === 1 && c.y === 1);
    const c12 = cells.find((c) => c.x === 1 && c.y === 2);

    expect(c11?.isSunk).toBe(true);
    expect(c12?.isSunk).toBe(true);
  });

  it('should accurately reflect drone usages passed from /info', () => {
    state.updateFromGameInfo({
      playerShips: [],
      playerMoves: [],
      opponentMoves: [],
      matchStatus: 'IN_PROGRESS',
      matchPlayerId: 'p1',
      isItMyTurn: true,
      droneUsage: {
        STANDARD: 0,
        SURVEILLANCE_DRONE: 1,
        ATTACK_DRONE: 2,
      },
    });

    expect(state.surveillanceRemaining()).toBe(1);
    expect(state.attackDronesRemaining()).toBe(2);
  });

  it('should correctly evaluate isWinner as true when winnerPlayerId equals matchPlayerId', () => {
    state.matchPlayerId.set('p1');
    state.matchStatus.set('FINISHED');
    state.winnerPlayerId.set('p1');

    expect(state.isWinner()).toBe(true);
  });

  it('should correctly evaluate isWinner as false when winnerPlayerId is opponent', () => {
    state.matchPlayerId.set('p1');
    state.matchStatus.set('FINISHED');
    state.winnerPlayerId.set('p2');

    expect(state.isWinner()).toBe(false);
  });

  it('should tag 3x3 drone preview cells with dronePreviewType and center cell with isDroneCenter', () => {
    state.matchStatus.set('IN_PROGRESS');
    state.selectedMoveType.set('SURVEILLANCE_DRONE');
    state.hoveredEnemyCell.set({ x: 3, y: 3 });

    const cells = state.enemyCells();
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
    state.matchPlayerId.set('p1');
    state.matchStatus.set('FINISHED');
    state.playerMoves.set([{ targetX: 0, targetY: 0, moveResult: 'FINISHED' }]);

    expect(state.isWinner()).toBe(true);
  });

  it('should evaluate isWinner as false if opponent made the FINISHED move', () => {
    state.matchPlayerId.set('p1');
    state.matchStatus.set('FINISHED');
    state.opponentMoves.set([{ targetX: 0, targetY: 0, moveResult: 'FINISHED' }]);

    expect(state.isWinner()).toBe(false);
  });

  it('should reset all states when initMatch is invoked for a new token', () => {
    state.matchStatus.set('FINISHED');
    state.winnerPlayerId.set('p1');
    state.rematchAgreedToken.set('token-2');

    state.initMatch('token-2');

    expect(state.inviteToken()).toBe('token-2');
    expect(state.rematchAgreedToken()).toBeNull();
    // Initially after reset, before getGameInfo resolves with FINISHED, winnerPlayerId is null
    expect(state.winnerPlayerId()).toBeNull();
  });

  it('should validate canMoveShipTo properly with auto-rotation along borders', () => {
    state.playerShips.set([
      { id: 10, shipType: 'TRIPLE_DECK', startX: 0, startY: 0, orientation: 'HORIZONTAL' },
    ]);

    // Move to (5, 5) is valid
    expect(state.canMoveShipTo(10, 5, 5)).toBe(true);

    // Move to right edge (8, 2) auto-rotates to vertical and is valid
    expect(state.canMoveShipTo(10, 8, 2)).toBe(true);

    // Invalid: out of bounds even with auto-rotation (8, 9)
    expect(state.canMoveShipTo(10, 8, 9)).toBe(false);
  });

  it('should preview selected placed ship at hovered cell', () => {
    state.playerShips.set([
      { id: 20, shipType: 'DOUBLE_DECK', startX: 0, startY: 0, orientation: 'HORIZONTAL' },
    ]);
    state.selectPlacedShip(20);
    state.setHoveredCell({ x: 4, y: 4 });

    const cells = state.playerCells();
    const cell44 = cells.find((c) => c.x === 4 && c.y === 4);
    const cell54 = cells.find((c) => c.x === 5 && c.y === 4);

    expect(cell44?.isPlacementValid).toBe(true);
    expect(cell54?.isPlacementValid).toBe(true);
  });

  it('should reveal opponent ships on enemyCells when player loses', () => {
    state.matchStatus.set('FINISHED');
    state.winnerPlayerId.set('opponent-id');
    state.matchPlayerId.set('p1');
    state.winnerShips.set([
      { id: 99, shipType: 'SINGLE_DECK', startX: 7, startY: 8, orientation: 'HORIZONTAL' },
    ]);

    const enemy = state.enemyCells();
    const cell78 = enemy.find((c) => c.x === 7 && c.y === 8);
    expect(cell78?.hasShip).toBe(true);
    expect(cell78?.shipId).toBe(99);
  });

  it('should NOT reveal player ships on enemyCells when player wins', () => {
    state.matchStatus.set('FINISHED');
    state.winnerPlayerId.set('p1');
    state.matchPlayerId.set('p1');
    state.playerShips.set([
      { id: 10, shipType: 'SINGLE_DECK', startX: 2, startY: 3, orientation: 'HORIZONTAL' },
    ]);
    // Backend sends player's ships in winnerShips
    state.winnerShips.set([
      { id: 10, shipType: 'SINGLE_DECK', startX: 2, startY: 3, orientation: 'HORIZONTAL' },
    ]);

    const enemy = state.enemyCells();
    const cell23 = enemy.find((c) => c.x === 2 && c.y === 3);
    expect(cell23?.hasShip).toBeUndefined();
  });

  describe('readiness and opponent gating', () => {
    const fullFleet = [
      {
        id: 1,
        shipType: 'QUADRO_DECK' as const,
        startX: 0,
        startY: 0,
        orientation: 'HORIZONTAL' as const,
      },
      {
        id: 2,
        shipType: 'TRIPLE_DECK' as const,
        startX: 0,
        startY: 2,
        orientation: 'HORIZONTAL' as const,
      },
      {
        id: 3,
        shipType: 'TRIPLE_DECK' as const,
        startX: 4,
        startY: 2,
        orientation: 'HORIZONTAL' as const,
      },
      {
        id: 4,
        shipType: 'DOUBLE_DECK' as const,
        startX: 0,
        startY: 4,
        orientation: 'HORIZONTAL' as const,
      },
      {
        id: 5,
        shipType: 'DOUBLE_DECK' as const,
        startX: 3,
        startY: 4,
        orientation: 'HORIZONTAL' as const,
      },
      {
        id: 6,
        shipType: 'DOUBLE_DECK' as const,
        startX: 6,
        startY: 4,
        orientation: 'HORIZONTAL' as const,
      },
      {
        id: 7,
        shipType: 'SINGLE_DECK' as const,
        startX: 0,
        startY: 6,
        orientation: 'HORIZONTAL' as const,
      },
      {
        id: 8,
        shipType: 'SINGLE_DECK' as const,
        startX: 2,
        startY: 6,
        orientation: 'HORIZONTAL' as const,
      },
      {
        id: 9,
        shipType: 'SINGLE_DECK' as const,
        startX: 4,
        startY: 6,
        orientation: 'HORIZONTAL' as const,
      },
      {
        id: 10,
        shipType: 'SINGLE_DECK' as const,
        startX: 6,
        startY: 6,
        orientation: 'HORIZONTAL' as const,
      },
    ];

    it('should disable canMarkReady when matchStatus is WAITING even if all ships are placed', () => {
      state.playerShips.set(fullFleet);
      expect(state.allShipsPlaced()).toBe(true);

      state.matchStatus.set('WAITING');
      expect(state.canMarkReady()).toBe(false);

      state.inviteToken.set('token-123');
      state.markReady();
      expect(mockGameService.markReady).not.toHaveBeenCalled();
    });

    it('should enable canMarkReady and allow markReady when matchStatus transitions to PLANNING', () => {
      state.playerShips.set(fullFleet);
      state.matchStatus.set('PLANNING');
      expect(state.canMarkReady()).toBe(true);

      state.inviteToken.set('token-123');
      state.markReady();
      expect(mockGameService.markReady).toHaveBeenCalledWith('token-123');
      expect(state.isPlayerReady()).toBe(true);
    });
  });

  describe('placement interaction reset', () => {
    it('should reset all in-flight placement and drag states via resetPlacementInteraction', () => {
      state.draggedShipId.set(42);
      state.draggedOverKey.set('3,4');
      state.selectedPlacedShipId.set(42);
      state.hoveredCell.set({ x: 3, y: 4 });

      state.resetPlacementInteraction();

      expect(state.draggedShipId()).toBeNull();
      expect(state.draggedOverKey()).toBeNull();
      expect(state.selectedPlacedShipId()).toBeNull();
      expect(state.hoveredCell()).toBeNull();
    });

    it('should reload fleet from game info service via reloadFleet', () => {
      state.inviteToken.set('test-token');
      state.draggedShipId.set(10);
      mockGameService.getGameInfo = vi.fn().mockReturnValue(
        of({
          playerShips: [
            { id: 5, shipType: 'SINGLE_DECK', startX: 1, startY: 1, orientation: 'HORIZONTAL' },
          ],
          playerMoves: [],
          opponentMoves: [],
          matchStatus: 'PLANNING',
          matchPlayerId: 'p1',
          isItMyTurn: false,
        }),
      );

      state.reloadFleet();

      expect(state.draggedShipId()).toBeNull();
      expect(mockGameService.getGameInfo).toHaveBeenCalledWith('test-token');
      expect(state.playerShips().length).toBe(1);
      expect(state.playerShips()[0].id).toBe(5);
    });
  });
});
