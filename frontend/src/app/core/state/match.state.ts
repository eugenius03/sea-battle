import { computed, Injectable, signal } from '@angular/core';
import { GameInfoResponse, ShipResponse } from '@core/models/game.model';
import { MatchStatus } from '@core/models/match.model';

@Injectable({ providedIn: 'root' })
export class MatchState {
  readonly inviteToken = signal<string>('');
  readonly matchPlayerId = signal<string>('');
  readonly matchStatus = signal<MatchStatus>('WAITING');
  readonly currentTurnPlayerId = signal<string | null>(null);
  readonly isItMyTurn = computed(
    () =>
      this.currentTurnPlayerId() !== null && this.currentTurnPlayerId() === this.matchPlayerId(),
  );
  readonly isOpponentConnected = signal<boolean>(false);
  readonly isOpponentReady = signal<boolean>(false);
  readonly isPlayerReady = signal<boolean>(false);
  readonly winnerPlayerId = signal<string | null>(null);
  readonly winnerShips = signal<ShipResponse[]>([]);
  readonly rematchRequestedByOpponent = signal<boolean>(false);
  readonly rematchAgreedToken = signal<string | null>(null);

  applyGameInfo(info: GameInfoResponse): void {
    const resolvedStatus =
      info.matchStatus === 'WAITING' && this.isOpponentConnected() ? 'PLANNING' : info.matchStatus;
    this.matchStatus.set(resolvedStatus);
    this.matchPlayerId.set(info.matchPlayerId);
    this.currentTurnPlayerId.set(info.currentTurnPlayerId);
    if (info.matchStatus === 'FINISHED' && info.currentTurnPlayerId) {
      this.winnerPlayerId.set(info.currentTurnPlayerId);
    }
  }

  reset(): void {
    this.inviteToken.set('');
    this.matchPlayerId.set('');
    this.matchStatus.set('WAITING');
    this.currentTurnPlayerId.set(null);
    this.isOpponentConnected.set(false);
    this.isOpponentReady.set(false);
    this.isPlayerReady.set(false);
    this.winnerPlayerId.set(null);
    this.winnerShips.set([]);
    this.rematchRequestedByOpponent.set(false);
    this.rematchAgreedToken.set(null);
  }
}
