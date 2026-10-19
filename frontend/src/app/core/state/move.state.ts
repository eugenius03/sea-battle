import { computed, inject, Injectable, signal } from '@angular/core';
import { MoveRequest, MoveResponse, MoveType } from '@core/models/move.model';
import { GameService } from '@core/services/game.service';
import { MatchState } from '@core/state/match.state';

const MAX_SURVEILLANCE_USAGES = 1;
const MAX_ATTACK_DRONE_USAGES = 2;

const initialDroneUsage = (): Record<MoveType, number> => ({
  STANDARD: 0,
  SURVEILLANCE_DRONE: MAX_SURVEILLANCE_USAGES,
  ATTACK_DRONE: MAX_ATTACK_DRONE_USAGES,
});

@Injectable({ providedIn: 'root' })
export class MoveState {
  private readonly gameService = inject(GameService);
  private readonly match = inject(MatchState);

  readonly playerMoves = signal<MoveResponse[]>([]);
  readonly opponentMoves = signal<MoveResponse[]>([]);
  readonly hoveredEnemyCell = signal<{ x: number; y: number } | null>(null);
  readonly selectedMoveType = signal<MoveType>('STANDARD');
  readonly droneUsage = signal<Record<MoveType, number>>(initialDroneUsage());
  readonly isFiring = signal<boolean>(false);

  readonly surveillanceRemaining = computed(
    () => this.droneUsage().SURVEILLANCE_DRONE ?? MAX_SURVEILLANCE_USAGES,
  );

  readonly attackDronesRemaining = computed(
    () => this.droneUsage().ATTACK_DRONE ?? MAX_ATTACK_DRONE_USAGES,
  );

  setMoves(
    playerMoves: MoveResponse[],
    opponentMoves: MoveResponse[],
    droneUsage?: Record<MoveType, number>,
  ): void {
    this.playerMoves.set(playerMoves);
    this.opponentMoves.set(opponentMoves);
    if (droneUsage) {
      this.droneUsage.set(droneUsage);
    }
  }

  appendOpponentMoves(responses: MoveResponse[]): void {
    this.opponentMoves.update((prev) => [...prev, ...responses]);
    if (responses.some((r) => r.moveResult === 'FINISHED')) {
      this.match.matchStatus.set('FINISHED');
      this.match.winnerPlayerId.set('opponent');
    }
  }

  setHoveredEnemyCell(cell: { x: number; y: number } | null): void {
    this.hoveredEnemyCell.set(cell);
  }

  fireAt(x: number, y: number): void {
    const token = this.match.inviteToken();
    if (!token || !this.match.isItMyTurn() || this.isFiring()) return;

    const moveType = this.selectedMoveType();
    const req: MoveRequest = { x, y, moveType };

    this.isFiring.set(true);
    this.gameService.fire(token, req).subscribe({
      next: ({ moveResponses: responses, currentTurnPlayerId }) => {
        this.isFiring.set(false);
        this.match.currentTurnPlayerId.set(currentTurnPlayerId);
        this.playerMoves.update((prev) => [...prev, ...responses]);
        if (responses.some((r) => r.moveResult === 'FINISHED')) {
          this.match.matchStatus.set('FINISHED');
          this.match.winnerPlayerId.set(this.match.matchPlayerId());
        }
        if (moveType !== 'STANDARD') {
          this.droneUsage.update((prev) => ({
            ...prev,
            [moveType]: Math.max(0, (prev[moveType] ?? 0) - 1),
          }));
          this.selectedMoveType.set('STANDARD');
        }
      },
      error: (err) => {
        this.isFiring.set(false);
        console.error('Fire command failed:', err);
      },
    });
  }

  reset(): void {
    this.playerMoves.set([]);
    this.opponentMoves.set([]);
    this.hoveredEnemyCell.set(null);
    this.selectedMoveType.set('STANDARD');
    this.droneUsage.set(initialDroneUsage());
    this.isFiring.set(false);
  }
}
