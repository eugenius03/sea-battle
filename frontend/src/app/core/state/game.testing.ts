import { provideHttpClient } from '@angular/common/http';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatchStatusMessage, PresenceMessage } from '@core/models/ws.model';
import { GameService } from '@core/services/game.service';
import { WebSocketService } from '@core/services/websocket.service';
import { of, Subject } from 'rxjs';
import { BoardViewState } from './board-view.state';
import { GameFlowService } from './game-flow.service';
import { MatchState } from './match.state';
import { MoveState } from './move.state';
import { ShipState } from './ship.state';

export function setupGameTestBed() {
  const presenceSubject = new Subject<PresenceMessage>();
  const statusSubject = new Subject<MatchStatusMessage>();
  const reconnectedSubject = new Subject<void>();

  const mockGameService: Partial<GameService> = {
    getGameInfo: vi.fn().mockReturnValue(
      of({
        playerShips: [],
        playerMoves: [],
        opponentMoves: [],
        matchStatus: 'WAITING',
        matchPlayerId: 'p1',
        droneUsage: { STANDARD: 0, SURVEILLANCE_DRONE: 0, ATTACK_DRONE: 0 },
        currentTurnPlayerId: null,
      }),
    ),
    placeShip: vi.fn().mockReturnValue(of(101)),
    generateRandomShips: vi.fn().mockReturnValue(of([])),
    moveShip: vi.fn().mockReturnValue(of(101)),
    markReady: vi.fn().mockReturnValue(of(undefined)),
    fire: vi.fn().mockReturnValue(of({ moveResponses: [], currentTurnPlayerId: null })),
  };

  const mockWsService: Partial<WebSocketService> = {
    watchMatchPresence: vi.fn().mockReturnValue(presenceSubject.asObservable()),
    watchMatchStatus: vi.fn().mockReturnValue(statusSubject.asObservable()),
    watchMatchMoves: vi.fn().mockReturnValue(of({})),
    reconnected$: reconnectedSubject as any,
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

  return {
    flow: TestBed.inject(GameFlowService),
    view: TestBed.inject(BoardViewState),
    moves: TestBed.inject(MoveState),
    match: TestBed.inject(MatchState),
    ships: TestBed.inject(ShipState),
    mockGameService,
    mockWsService,
    presenceSubject,
    statusSubject,
    reconnectedSubject,
  };
}
