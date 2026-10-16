import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ReplayComponent } from './replay.component';
import { MatchService } from '@core/services/match.service';
import { MatchDetailsResponse } from '@core/models/match.model';

describe('ReplayComponent', () => {
  let component: ReplayComponent;
  let fixture: ComponentFixture<ReplayComponent>;
  let mockMatchService: Partial<MatchService>;

  const sampleMatch: MatchDetailsResponse = {
    inviteToken: 'test-token-123',
    status: 'FINISHED',
    createdAt: '2026-10-09T18:00:00Z',
    finishedAt: '2026-10-09T18:05:00Z',
    players: [
      {
        playerId: 'p1-id',
        userId: 'u1-id',
        username: 'Admiral_Nelson',
        isWinner: true,
        ships: [
          {
            id: 1,
            shipType: 'SINGLE_DECK',
            startX: 0,
            startY: 0,
            orientation: 'HORIZONTAL',
          },
        ],
      },
      {
        playerId: 'p2-id',
        userId: 'u2-id',
        username: 'Captain_Hook',
        isWinner: false,
        ships: [
          {
            id: 2,
            shipType: 'DOUBLE_DECK',
            startX: 5,
            startY: 5,
            orientation: 'HORIZONTAL',
          },
        ],
      },
    ],
    moves: [
      {
        shooterId: 'p1-id',
        targetX: 5,
        targetY: 5,
        moveType: 'STANDARD',
        moveResult: 'HIT',
      },
      {
        shooterId: 'p1-id',
        targetX: 6,
        targetY: 5,
        moveType: 'STANDARD',
        moveResult: 'FINISHED',
      },
    ],
  };

  beforeEach(async () => {
    mockMatchService = {
      getMatch: vi.fn().mockReturnValue(of(sampleMatch)),
    };

    await TestBed.configureTestingModule({
      imports: [ReplayComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideRouter([]),
        { provide: MatchService, useValue: mockMatchService },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: {
                get: (key: string) => (key === 'inviteToken' ? 'test-token-123' : null),
              },
              queryParamMap: {
                get: (key: string) => (key === 'focus' ? 'Captain_Hook' : null),
              },
            },
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ReplayComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create the replay component and load match data', () => {
    expect(component).toBeTruthy();
    expect(component.match()).toEqual(sampleMatch);
    expect(component.loading()).toBe(false);
    expect(component.error()).toBeNull();
    expect(component.totalMoves()).toBe(2);
  });

  it('should orient the focused player on the left board (player1)', () => {
    expect(component.player1()?.username).toBe('Captain_Hook');
    expect(component.player2()?.username).toBe('Admiral_Nelson');
  });

  it('should allow stepping forward, backward, to start, and to end', () => {
    expect(component.currentStep()).toBe(0);

    component.stepForward();
    expect(component.currentStep()).toBe(1);

    component.stepForward();
    expect(component.currentStep()).toBe(2);

    // Cannot step past total
    component.stepForward();
    expect(component.currentStep()).toBe(2);

    component.stepBackward();
    expect(component.currentStep()).toBe(1);

    component.jumpToStart();
    expect(component.currentStep()).toBe(0);

    component.jumpToEnd();
    expect(component.currentStep()).toBe(2);
  });

  it('should accurately compute alive ship counts as shots are executed', () => {
    // Hook's double deck ship is targeted by Nelson
    expect(component.p1AliveCount()).toBe(1);

    // After first hit at (5,5), double deck ship is hit but not sunk yet
    component.stepForward();
    expect(component.p1AliveCount()).toBe(1);

    // After second hit at (6,5) with FINISHED result, double deck is sunk
    component.stepForward();
    expect(component.p1AliveCount()).toBe(0);
  });

  it('should support playback toggle and speed changes', () => {
    component.setSpeed(2);
    expect(component.playbackSpeed()).toBe(2);

    component.togglePlay();
    expect(component.isPlaying()).toBe(true);

    component.togglePlay();
    expect(component.isPlaying()).toBe(false);
  });

  it('should handle match loading errors gracefully', async () => {
    const errorMatchService: Partial<MatchService> = {
      getMatch: vi.fn().mockReturnValue(throwError(() => ({ status: 404 }))),
    };

    const errFixture = TestBed.createComponent(ReplayComponent);
    // Overwrite the matchService instance
    (errFixture.componentInstance as any).matchService = errorMatchService;
    errFixture.componentInstance.ngOnInit();

    expect(errFixture.componentInstance.error()).toBe('Match not found or expired.');
  });

  it('should place moves only on the targeted board and not both', () => {
    // Nelson shoots Hook at (5,5)
    component.stepForward();

    // Hook is player1, so Hook's board at (5,5) is hit
    const p1Cell = component.player1Cells().find((c) => c.x === 5 && c.y === 5);
    expect(p1Cell?.isHit).toBe(true);

    // Nelson is player2, so Nelson's board at (5,5) is untouched
    const p2Cell = component.player2Cells().find((c) => c.x === 5 && c.y === 5);
    expect(p2Cell?.isHit).toBeFalsy();
    expect(p2Cell?.isMiss).toBeFalsy();
  });

  it('should render buffer misses around sunk ships and filter buffer moves from timeline', () => {
    const matchWithBuffer: MatchDetailsResponse = {
      ...sampleMatch,
      moves: [
        {
          shooterId: 'p1-id',
          targetX: 5,
          targetY: 5,
          moveType: 'STANDARD',
          moveResult: 'HIT',
          isMoveOrigin: true,
        },
        {
          shooterId: 'p1-id',
          targetX: 6,
          targetY: 5,
          moveType: 'STANDARD',
          moveResult: 'SUNK',
          isMoveOrigin: true,
        },
        // Auto-generated buffer misses
        {
          shooterId: 'p1-id',
          targetX: 4,
          targetY: 5,
          moveType: 'STANDARD',
          moveResult: 'MISS',
          isMoveOrigin: false,
        },
        {
          shooterId: 'p1-id',
          targetX: 5,
          targetY: 4,
          moveType: 'STANDARD',
          moveResult: 'MISS',
          isMoveOrigin: false,
        },
        // Next real player move
        {
          shooterId: 'p2-id',
          targetX: 0,
          targetY: 0,
          moveType: 'STANDARD',
          moveResult: 'FINISHED',
          isMoveOrigin: true,
        },
      ],
    };

    mockMatchService.getMatch = vi.fn().mockReturnValue(of(matchWithBuffer));
    const bufFixture = TestBed.createComponent(ReplayComponent);
    bufFixture.detectChanges();

    // Total moves should only include the 3 player-made moves!
    expect(bufFixture.componentInstance.totalMoves()).toBe(3);

    // Step 2 sinks Hook's ship (5,5)-(6,5)
    bufFixture.componentInstance.jumpToStart();
    bufFixture.componentInstance.stepForward(); // Step 1: HIT
    bufFixture.componentInstance.stepForward(); // Step 2: SUNK

    // Surrounding cell (4,5) should be rendered as a miss automatically!
    const bufferCell = bufFixture.componentInstance.player1Cells().find((c) => c.x === 4 && c.y === 5);
    expect(bufferCell?.isMiss).toBe(true);

    // Stepping forward once more goes directly to next player move (Step 3)
    bufFixture.componentInstance.stepForward();
    expect(bufFixture.componentInstance.currentStep()).toBe(3);
    expect(bufFixture.componentInstance.lastMove()?.shooterId).toBe('p2-id');
  });

  it('should collapse 9 surveillance drone moves into 1 single step and render all detections and misses', () => {
    // Player 1 (Hook) has ship at (5,5)-(6,5).
    // Player 2 (Nelson) scans 3x3 centered at (5,5).
    // Center is (5,5) which hits ship cell -> SURVEILLANCE with isMoveOrigin: true.
    // (6,5) hits ship cell -> SURVEILLANCE with isMoveOrigin: false.
    // Other 7 cells are MISS with isMoveOrigin: false.
    const surveillanceMoves = [];
    for (let dx = -1; dx <= 1; dx++) {
      for (let dy = -1; dy <= 1; dy++) {
        const x = 5 + dx;
        const y = 5 + dy;
        const isShip = (x === 5 && y === 5) || (x === 6 && y === 5);
        const isCenter = x === 5 && y === 5;
        surveillanceMoves.push({
          shooterId: 'p1-id',
          targetX: x,
          targetY: y,
          moveType: 'SURVEILLANCE_DRONE' as const,
          moveResult: (isShip ? 'SURVEILLANCE' : 'MISS') as any,
          isMoveOrigin: isCenter,
        });
      }
    }

    const matchWithSurveillance: MatchDetailsResponse = {
      ...sampleMatch,
      moves: [
        ...surveillanceMoves,
        {
          shooterId: 'p2-id',
          targetX: 0,
          targetY: 0,
          moveType: 'STANDARD',
          moveResult: 'MISS',
          isMoveOrigin: true,
        },
      ],
    };

    mockMatchService.getMatch = vi.fn().mockReturnValue(of(matchWithSurveillance));
    const droneFixture = TestBed.createComponent(ReplayComponent);
    droneFixture.detectChanges();

    // The 9 surveillance moves + 1 standard move must collapse into exactly 2 steps!
    expect(droneFixture.componentInstance.totalMoves()).toBe(2);

    // Initial state: 0 active moves
    expect(droneFixture.componentInstance.currentStep()).toBe(0);

    // Step 1: Surveillance scan executed
    droneFixture.componentInstance.stepForward();
    expect(droneFixture.componentInstance.currentStep()).toBe(1);

    // Hook is player1, targeted by Nelson (p1-id in sampleMatch)
    const p1Cells = droneFixture.componentInstance.player1Cells();

    // Check detected ship cells
    const cell55 = p1Cells.find((c) => c.x === 5 && c.y === 5);
    const cell65 = p1Cells.find((c) => c.x === 6 && c.y === 5);
    expect(cell55?.isSurveillance).toBe(true);
    expect(cell65?.isSurveillance).toBe(true);

    // Check a water cell in the 3x3 scan
    const cell44 = p1Cells.find((c) => c.x === 4 && c.y === 4);
    expect(cell44?.isMiss).toBe(true);
    expect(cell44?.isSurveillance).toBeFalsy();

    // Check that all 9 cells are highlighted as recent move
    expect(cell55?.isRecentMove).toBe(true);
    expect(cell65?.isRecentMove).toBe(true);
    expect(cell44?.isRecentMove).toBe(true);

    // Description reflects surveillance detection
    expect(droneFixture.componentInstance.lastMoveDescription()).toContain('scanned 3×3 at F6 → [DETECTED (2 ship cells)]');

    // Stepping to step 2 advances to the next player's move
    droneFixture.componentInstance.stepForward();
    expect(droneFixture.componentInstance.currentStep()).toBe(2);
    expect(droneFixture.componentInstance.lastMove()?.shooterId).toBe('p2-id');
  });

  it('should collapse attack drone salvo strikes and sunk buffer misses into 1 single step', () => {
    const matchWithAttackDrone: MatchDetailsResponse = {
      ...sampleMatch,
      moves: [
        {
          shooterId: 'p1-id',
          targetX: 5,
          targetY: 5,
          moveType: 'ATTACK_DRONE',
          moveResult: 'HIT',
          isMoveOrigin: true,
        },
        {
          shooterId: 'p1-id',
          targetX: 6,
          targetY: 5,
          moveType: 'ATTACK_DRONE',
          moveResult: 'SUNK',
          isMoveOrigin: false,
        },
        // Buffer misses from sunk ship
        {
          shooterId: 'p1-id',
          targetX: 4,
          targetY: 5,
          moveType: 'STANDARD',
          moveResult: 'MISS',
          isMoveOrigin: false,
        },
        // Another strike in the salvo
        {
          shooterId: 'p1-id',
          targetX: 5,
          targetY: 6,
          moveType: 'ATTACK_DRONE',
          moveResult: 'MISS',
          isMoveOrigin: false,
        },
        // Opponent next move
        {
          shooterId: 'p2-id',
          targetX: 0,
          targetY: 0,
          moveType: 'STANDARD',
          moveResult: 'MISS',
          isMoveOrigin: true,
        },
      ],
    };

    mockMatchService.getMatch = vi.fn().mockReturnValue(of(matchWithAttackDrone));
    const attackFixture = TestBed.createComponent(ReplayComponent);
    attackFixture.detectChanges();

    // Entire salvo + buffer misses collapse to step 1, opponent is step 2
    expect(attackFixture.componentInstance.totalMoves()).toBe(2);

    attackFixture.componentInstance.stepForward();
    expect(attackFixture.componentInstance.currentStep()).toBe(1);

    const p1Cells = attackFixture.componentInstance.player1Cells();
    const cell55 = p1Cells.find((c) => c.x === 5 && c.y === 5);
    const cell65 = p1Cells.find((c) => c.x === 6 && c.y === 5);
    const cell56 = p1Cells.find((c) => c.x === 5 && c.y === 6);
    const cell45 = p1Cells.find((c) => c.x === 4 && c.y === 5);

    expect(cell55?.isSunk).toBe(true);
    expect(cell65?.isSunk).toBe(true);
    expect(cell56?.isMiss).toBe(true);
    expect(cell45?.isMiss).toBe(true);

    // Attack drone strikes highlighted as recent
    expect(cell55?.isRecentMove).toBe(true);
    expect(cell65?.isRecentMove).toBe(true);
    expect(cell56?.isRecentMove).toBe(true);

    expect(attackFixture.componentInstance.lastMoveDescription()).toContain('airstrike near F6 → [2/3 HITS - SUNK!]');
  });

  it('should faithfully render the real match scenario: surveillance drone scan, miss, and sunk ship with buffer misses', () => {
    const realMatchData: MatchDetailsResponse = {
      inviteToken: '528d9df533164822',
      status: 'FINISHED',
      createdAt: '2026-10-10T12:00:00Z',
      finishedAt: '2026-10-10T12:05:00Z',
      players: [
        {
          playerId: 'p-skipper',
          userId: 'u-skipper',
          username: 'Silent_Skipper_5895',
          isWinner: false,
          ships: [
            {
              id: 183,
              shipType: 'TRIPLE_DECK',
              startX: 6,
              startY: 0,
              orientation: 'HORIZONTAL',
            },
          ],
        },
        {
          playerId: 'p-vas',
          userId: 'u-vas',
          username: 'vas',
          isWinner: true,
          ships: [
            {
              id: 191,
              shipType: 'QUADRO_DECK',
              startX: 5,
              startY: 1,
              orientation: 'VERTICAL',
            },
          ],
        },
      ],
      moves: [
        // Vas scans 3x3 at center (7,1) covering (6,0), (7,0), (8,0) which contain the Triple Deck!
        { shooterId: 'p-vas', targetX: 6, targetY: 0, moveType: 'SURVEILLANCE_DRONE', moveResult: 'SURVEILLANCE', isMoveOrigin: false },
        { shooterId: 'p-vas', targetX: 6, targetY: 1, moveType: 'SURVEILLANCE_DRONE', moveResult: 'MISS', isMoveOrigin: false },
        { shooterId: 'p-vas', targetX: 6, targetY: 2, moveType: 'SURVEILLANCE_DRONE', moveResult: 'MISS', isMoveOrigin: false },
        { shooterId: 'p-vas', targetX: 7, targetY: 0, moveType: 'SURVEILLANCE_DRONE', moveResult: 'SURVEILLANCE', isMoveOrigin: false },
        { shooterId: 'p-vas', targetX: 7, targetY: 1, moveType: 'SURVEILLANCE_DRONE', moveResult: 'MISS', isMoveOrigin: true },
        { shooterId: 'p-vas', targetX: 7, targetY: 2, moveType: 'SURVEILLANCE_DRONE', moveResult: 'MISS', isMoveOrigin: false },
        { shooterId: 'p-vas', targetX: 8, targetY: 0, moveType: 'SURVEILLANCE_DRONE', moveResult: 'SURVEILLANCE', isMoveOrigin: false },
        { shooterId: 'p-vas', targetX: 8, targetY: 1, moveType: 'SURVEILLANCE_DRONE', moveResult: 'MISS', isMoveOrigin: false },
        { shooterId: 'p-vas', targetX: 8, targetY: 2, moveType: 'SURVEILLANCE_DRONE', moveResult: 'MISS', isMoveOrigin: false },
        // Skipper fires at (0,0) -> MISS
        { shooterId: 'p-skipper', targetX: 0, targetY: 0, moveType: 'STANDARD', moveResult: 'MISS', isMoveOrigin: true },
        // Vas fires at (6,0) -> HIT
        { shooterId: 'p-vas', targetX: 6, targetY: 0, moveType: 'STANDARD', moveResult: 'HIT', isMoveOrigin: true },
        // Vas fires at (7,0) -> HIT
        { shooterId: 'p-vas', targetX: 7, targetY: 0, moveType: 'STANDARD', moveResult: 'HIT', isMoveOrigin: true },
        // Vas fires at (8,0) -> SUNK
        { shooterId: 'p-vas', targetX: 8, targetY: 0, moveType: 'STANDARD', moveResult: 'SUNK', isMoveOrigin: true },
        // Auto-generated buffer misses around sunk triple deck
        { shooterId: 'p-vas', targetX: 5, targetY: 0, moveType: 'STANDARD', moveResult: 'MISS', isMoveOrigin: false },
        { shooterId: 'p-vas', targetX: 5, targetY: 1, moveType: 'STANDARD', moveResult: 'MISS', isMoveOrigin: false },
        { shooterId: 'p-vas', targetX: 9, targetY: 0, moveType: 'STANDARD', moveResult: 'MISS', isMoveOrigin: false },
        { shooterId: 'p-vas', targetX: 9, targetY: 1, moveType: 'STANDARD', moveResult: 'MISS', isMoveOrigin: false },
      ],
    };

    mockMatchService.getMatch = vi.fn().mockReturnValue(of(realMatchData));
    const realFixture = TestBed.createComponent(ReplayComponent);
    realFixture.detectChanges();

    // Steps should be:
    // 1: Vas surveillance drone (all 9 cells)
    // 2: Skipper standard shot
    // 3: Vas hit (6,0)
    // 4: Vas hit (7,0)
    // 5: Vas sunk (8,0) + buffer misses
    expect(realFixture.componentInstance.totalMoves()).toBe(5);

    // Step 1: Vas surveillance
    realFixture.componentInstance.stepForward();
    expect(realFixture.componentInstance.currentStep()).toBe(1);
    expect(realFixture.componentInstance.lastMoveDescription()).toBe('vas scanned 3×3 at H2 → [DETECTED (3 ship cells)]');

    // On Skipper's board (targeted by Vas), check that all 3 ship cells show surveillance and water cells show miss
    const p1Cells = realFixture.componentInstance.player1Cells();
    const cell60 = p1Cells.find((c) => c.x === 6 && c.y === 0);
    const cell70 = p1Cells.find((c) => c.x === 7 && c.y === 0);
    const cell80 = p1Cells.find((c) => c.x === 8 && c.y === 0);
    const cell71 = p1Cells.find((c) => c.x === 7 && c.y === 1);

    expect(cell60?.isSurveillance).toBe(true);
    expect(cell70?.isSurveillance).toBe(true);
    expect(cell80?.isSurveillance).toBe(true);
    expect(cell71?.isMiss).toBe(true);

    // Step 2: Skipper misses at (0,0)
    realFixture.componentInstance.stepForward();
    expect(realFixture.componentInstance.currentStep()).toBe(2);
    expect(realFixture.componentInstance.lastMoveDescription()).toBe('Silent_Skipper_5895 fired at A1 → [MISS]');

    // Step 3: Vas hits (6,0)
    realFixture.componentInstance.stepForward();
    expect(realFixture.componentInstance.currentStep()).toBe(3);
    const cell60AfterHit = realFixture.componentInstance.player1Cells().find((c) => c.x === 6 && c.y === 0);
    expect(cell60AfterHit?.isHit).toBe(true);
    expect(cell60AfterHit?.isSurveillance).toBeFalsy();

    // Step 4: Vas hits (7,0)
    realFixture.componentInstance.stepForward();
    expect(realFixture.componentInstance.currentStep()).toBe(4);

    // Step 5: Vas sinks (8,0)
    realFixture.componentInstance.stepForward();
    expect(realFixture.componentInstance.currentStep()).toBe(5);
    const cell60Sunk = realFixture.componentInstance.player1Cells().find((c) => c.x === 6 && c.y === 0);
    const cell70Sunk = realFixture.componentInstance.player1Cells().find((c) => c.x === 7 && c.y === 0);
    const cell80Sunk = realFixture.componentInstance.player1Cells().find((c) => c.x === 8 && c.y === 0);
    const bufferCell50 = realFixture.componentInstance.player1Cells().find((c) => c.x === 5 && c.y === 0);

    expect(cell60Sunk?.isSunk).toBe(true);
    expect(cell70Sunk?.isSunk).toBe(true);
    expect(cell80Sunk?.isSunk).toBe(true);
    expect(bufferCell50?.isMiss).toBe(true);
    expect(realFixture.componentInstance.lastMoveDescription()).toBe('vas fired at I1 → [SUNK!]');
  });
});
