import { CommonModule } from '@angular/common';
import {
  Component,
  HostListener,
  OnDestroy,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { BoardCell } from '@core/models/game.model';
import {
  MatchDetailsResponse,
  MatchParticipantResponse,
  ReplayMoveResponse,
} from '@core/models/match.model';
import { MatchService } from '@core/services/match.service';
import { BoardComponent } from '@shared/components/board/board.component';
import { IconComponent } from '@shared/components/icon/icon.component';
import {
  ReplayStep,
  ShooterIds,
  buildReplayBoardCells,
  describeStep,
  groupReplaySteps,
  isShipSunk,
  resolveShooterIds,
} from './replay.engine';

@Component({
  selector: 'app-replay',
  standalone: true,
  imports: [CommonModule, RouterLink, BoardComponent, IconComponent],
  templateUrl: './replay.component.html',
  styleUrl: './replay.component.scss',
})
export class ReplayComponent implements OnInit, OnDestroy {
  private readonly matchService = inject(MatchService);
  private readonly route = inject(ActivatedRoute);

  readonly loading = signal<boolean>(true);
  readonly error = signal<string | null>(null);
  readonly match = signal<MatchDetailsResponse | null>(null);
  readonly currentStep = signal<number>(0);
  readonly isPlaying = signal<boolean>(false);
  readonly playbackSpeed = signal<number>(1);
  readonly focusUsername = signal<string | null>(null);

  private playTimer: ReturnType<typeof setTimeout> | null = null;

  // Player ordering: place focused user on the left
  readonly player1 = computed<MatchParticipantResponse | null>(() => {
    const players = this.match()?.players ?? [];
    if (players.length === 0) return null;
    const focus = this.focusUsername();
    if (focus && players[1]?.username.toLowerCase() === focus.toLowerCase()) {
      return players[1];
    }
    return players[0];
  });

  readonly player2 = computed<MatchParticipantResponse | null>(() => {
    const players = this.match()?.players ?? [];
    if (players.length < 2) return null;
    const p1 = this.player1();
    return p1?.playerId === players[0].playerId ? players[1] : players[0];
  });

  // Replay steps grouped by player turn (1 step per shot or drone action)
  readonly replaySteps = computed<ReplayStep[]>(() => {
    const raw = this.match()?.moves ?? [];
    const p1Ships = this.player1()?.ships ?? [];
    const p2Ships = this.player2()?.ships ?? [];
    return groupReplaySteps(raw, p1Ships, p2Ships);
  });

  readonly playerMoves = computed<ReplayMoveResponse[]>(() => {
    return this.replaySteps().map((s) => s.primaryMove);
  });

  readonly totalMoves = computed<number>(() => this.replaySteps().length);

  // Robustly resolve shooter IDs even if missing from cache
  readonly resolvedShooterIds = computed<ShooterIds>(() =>
    resolveShooterIds(this.player1(), this.player2(), this.match()?.moves ?? []),
  );

  readonly activeMoves = computed<ReplayMoveResponse[]>(() => {
    return this.replaySteps().slice(0, this.currentStep()).flatMap((s) => s.allMoves);
  });

  readonly lastStep = computed<ReplayStep | null>(() => {
    const step = this.currentStep();
    if (step <= 0) return null;
    return this.replaySteps()[step - 1] ?? null;
  });

  readonly lastMove = computed<ReplayMoveResponse | null>(() => {
    return this.lastStep()?.primaryMove ?? null;
  });

  readonly lastMoveDescription = computed<string>(() => {
    const step = this.lastStep();
    const { p1ShooterId } = this.resolvedShooterIds();
    const shooterName =
      step?.primaryMove.shooterId === p1ShooterId
        ? this.player1()?.username ?? 'Player 1'
        : this.player2()?.username ?? 'Player 2';
    return describeStep(step, shooterName);
  });

  readonly nextStep = computed<ReplayStep | null>(() => {
    const step = this.currentStep();
    return this.replaySteps()[step] ?? null;
  });

  readonly nextMove = computed<ReplayMoveResponse | null>(() => {
    return this.nextStep()?.primaryMove ?? null;
  });

  readonly nextTurnLabel = computed<string>(() => {
    const step = this.currentStep();
    const total = this.totalMoves();
    if (step >= total) {
      const winner = (this.match()?.players ?? []).find((p) => p.isWinner);
      return winner ? `Victory for ${winner.username}` : 'Match Finished';
    }
    const next = this.nextMove();
    if (!next) return '';
    const { p1ShooterId } = this.resolvedShooterIds();
    const shooterName =
      next.shooterId === p1ShooterId
        ? this.player1()?.username ?? 'Player 1'
        : this.player2()?.username ?? 'Player 2';

    return `${shooterName}'s Turn`;
  });

  // Calculate alive ships
  readonly p1AliveCount = computed<number>(() => {
    const p1 = this.player1();
    if (!p1) return 0;
    const { p2ShooterId } = this.resolvedShooterIds();
    const incoming = this.activeMoves().filter((m) => m.shooterId === p2ShooterId);
    return p1.ships.filter((s) => !isShipSunk(s, incoming)).length;
  });

  readonly p2AliveCount = computed<number>(() => {
    const p2 = this.player2();
    if (!p2) return 0;
    const { p1ShooterId } = this.resolvedShooterIds();
    const incoming = this.activeMoves().filter((m) => m.shooterId === p1ShooterId);
    return p2.ships.filter((s) => !isShipSunk(s, incoming)).length;
  });

  // Board Cells for Player 1 (attacked by Player 2)
  readonly player1Cells = computed<BoardCell[]>(() => {
    const p1 = this.player1();
    if (!p1) return [];
    const { p2ShooterId } = this.resolvedShooterIds();
    return buildReplayBoardCells(p1, this.activeMoves(), this.lastStep(), p2ShooterId);
  });

  // Board Cells for Player 2 (attacked by Player 1)
  readonly player2Cells = computed<BoardCell[]>(() => {
    const p2 = this.player2();
    if (!p2) return [];
    const { p1ShooterId } = this.resolvedShooterIds();
    return buildReplayBoardCells(p2, this.activeMoves(), this.lastStep(), p1ShooterId);
  });

  // Turn status on each board (glows if player is currently shooting)
  readonly isP1Turn = computed<boolean>(() => {
    const next = this.nextMove();
    if (!next) return false;
    return next.shooterId === this.resolvedShooterIds().p1ShooterId;
  });

  readonly isP2Turn = computed<boolean>(() => {
    const next = this.nextMove();
    if (!next) return false;
    return next.shooterId === this.resolvedShooterIds().p2ShooterId;
  });

  ngOnInit(): void {
    const inviteToken = this.route.snapshot.paramMap.get('inviteToken') ?? '';
    const focus = this.route.snapshot.queryParamMap.get('focus');
    if (focus) {
      this.focusUsername.set(focus);
    }

    if (!inviteToken) {
      this.error.set('No replay token provided.');
      this.loading.set(false);
      return;
    }

    this.matchService.getMatch(inviteToken).subscribe({
      next: (details) => {
        this.match.set(details);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(
          err.status === 404
            ? 'Match not found or expired.'
            : 'Failed to load match replay.',
        );
        this.loading.set(false);
      },
    });
  }

  ngOnDestroy(): void {
    this.pausePlayback();
  }

  @HostListener('window:keydown', ['$event'])
  handleKeyDown(event: KeyboardEvent): void {
    if (event.target instanceof HTMLInputElement || event.target instanceof HTMLTextAreaElement) {
      return;
    }
    if (event.code === 'Space') {
      event.preventDefault();
      this.togglePlay();
    } else if (event.code === 'ArrowRight') {
      event.preventDefault();
      this.stepForward();
    } else if (event.code === 'ArrowLeft') {
      event.preventDefault();
      this.stepBackward();
    } else if (event.code === 'Home') {
      event.preventDefault();
      this.jumpToStart();
    } else if (event.code === 'End') {
      event.preventDefault();
      this.jumpToEnd();
    }
  }

  togglePlay(): void {
    if (this.isPlaying()) {
      this.pausePlayback();
    } else {
      this.startPlayback();
    }
  }

  startPlayback(): void {
    if (this.currentStep() >= this.totalMoves()) {
      this.currentStep.set(0);
    }
    this.isPlaying.set(true);
    this.scheduleNextTick();
  }

  pausePlayback(): void {
    this.isPlaying.set(false);
    if (this.playTimer) {
      clearTimeout(this.playTimer);
      this.playTimer = null;
    }
  }

  private scheduleNextTick(): void {
    if (this.playTimer) {
      clearTimeout(this.playTimer);
      this.playTimer = null;
    }
    if (!this.isPlaying()) return;

    const delay = Math.max(100, Math.round(1000 / this.playbackSpeed()));
    this.playTimer = setTimeout(() => {
      if (!this.isPlaying()) return;
      if (this.currentStep() < this.totalMoves()) {
        this.currentStep.update((s) => s + 1);
        if (this.currentStep() >= this.totalMoves()) {
          this.pausePlayback();
        } else {
          this.scheduleNextTick();
        }
      } else {
        this.pausePlayback();
      }
    }, delay);
  }

  setSpeed(speed: number): void {
    this.playbackSpeed.set(speed);
    if (this.isPlaying()) {
      this.scheduleNextTick();
    }
  }

  stepForward(): void {
    this.pausePlayback();
    if (this.currentStep() < this.totalMoves()) {
      this.currentStep.update((s) => s + 1);
    }
  }

  stepBackward(): void {
    this.pausePlayback();
    if (this.currentStep() > 0) {
      this.currentStep.update((s) => s - 1);
    }
  }

  jumpToStart(): void {
    this.pausePlayback();
    this.currentStep.set(0);
  }

  jumpToEnd(): void {
    this.pausePlayback();
    this.currentStep.set(this.totalMoves());
  }

  onScrub(event: Event): void {
    this.pausePlayback();
    const target = event.target as HTMLInputElement;
    if (target) {
      const val = parseInt(target.value, 10);
      if (!isNaN(val)) {
        this.currentStep.set(Math.max(0, Math.min(this.totalMoves(), val)));
      }
    }
  }

  formatDate(isoString?: string): string {
    if (!isoString) return '-';
    try {
      const date = new Date(isoString);
      const pad = (n: number) => n.toString().padStart(2, '0');
      const hours = pad(date.getHours());
      const minutes = pad(date.getMinutes());
      const day = pad(date.getDate());
      const month = pad(date.getMonth() + 1);
      const year = date.getFullYear();
      return `${hours}:${minutes} ${day}.${month}.${year}`;
    } catch {
      return isoString;
    }
  }

  formatDuration(startIso?: string, finishIso?: string): string {
    if (!startIso || !finishIso) return '-';
    try {
      const start = new Date(startIso).getTime();
      const end = new Date(finishIso).getTime();
      const diffSec = Math.max(0, Math.floor((end - start) / 1000));
      const m = Math.floor(diffSec / 60);
      const s = diffSec % 60;
      return `${m}m ${s}s`;
    } catch {
      return '-';
    }
  }
}
