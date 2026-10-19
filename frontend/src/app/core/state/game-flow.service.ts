import { computed, inject, Injectable } from '@angular/core';
import { GameInfoResponse } from '@core/models/game.model';
import { GameService } from '@core/services/game.service';
import { MatchService } from '@core/services/match.service';
import { WebSocketService } from '@core/services/websocket.service';
import { MatchState } from '@core/state/match.state';
import { MoveState } from '@core/state/move.state';
import { ShipState } from '@core/state/ship.state';
import { Subscription } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class GameFlowService {
  private readonly gameService = inject(GameService);
  private readonly matchService = inject(MatchService);
  private readonly wsService = inject(WebSocketService);
  private readonly match = inject(MatchState);
  private readonly ships = inject(ShipState);
  private readonly moves = inject(MoveState);

  private subscriptions: Subscription[] = [];

  readonly canMarkReady = computed<boolean>(
    () =>
      this.ships.allShipsPlaced() &&
      this.match.matchStatus() !== 'WAITING' &&
      !this.match.isPlayerReady(),
  );

  initMatch(inviteToken: string): void {
    this.reset();
    this.match.inviteToken.set(inviteToken);

    this.gameService.getGameInfo(inviteToken).subscribe({
      next: (info: GameInfoResponse) => {
        this.updateFromGameInfo(info);
        this.setupWebSocketSubscriptions(inviteToken, info.matchPlayerId);
        this.ships.autoSelectNextShip();
      },
      error: (err) => {
        if (err?.status === 404 || err?.error?.message?.includes('PLAYER_NOT_FOUND')) {
          this.matchService.joinMatch(inviteToken).subscribe({
            next: () => {
              this.gameService.getGameInfo(inviteToken).subscribe({
                next: (info: GameInfoResponse) => {
                  this.updateFromGameInfo(info);
                  this.setupWebSocketSubscriptions(inviteToken, info.matchPlayerId);
                  this.ships.autoSelectNextShip();
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
    this.ships.setPlayerShips(info.playerShips || []);
    this.moves.setMoves(info.playerMoves || [], info.opponentMoves || [], info.droneUsage);
    this.match.applyGameInfo(info);
  }

  private setupWebSocketSubscriptions(inviteToken: string, matchPlayerId: string): void {
    if (!matchPlayerId) return;

    this.subscriptions.push(
      this.wsService.watchMatchPresence(inviteToken, matchPlayerId).subscribe((presence) => {
        switch (presence.presenceEventType) {
          case 'OPPONENT_CONNECTED':
          case 'RECONNECTED':
            this.match.isOpponentConnected.set(true);
            if (this.match.matchStatus() === 'WAITING') {
              this.match.matchStatus.set('PLANNING');
            }
            break;
          case 'DISCONNECTED':
            this.match.isOpponentConnected.set(false);
            break;
          case 'OPPONENT_READY':
            this.match.isOpponentReady.set(true);
            break;
          case 'REMATCH_REQUESTED':
            this.match.rematchRequestedByOpponent.set(true);
            break;
          case 'REMATCH_AGREED':
            if (presence.inviteToken) {
              this.match.rematchAgreedToken.set(presence.inviteToken);
            }
            break;
        }
      }),
      this.wsService.watchMatchStatus(inviteToken, matchPlayerId).subscribe((statusMsg) => {
        this.match.matchStatus.set(statusMsg.matchStatus);
        if (statusMsg.currentTurnPlayerId) {
          this.match.currentTurnPlayerId.set(statusMsg.currentTurnPlayerId);
        }
        if (statusMsg.matchStatus === 'FINISHED') {
          if (statusMsg.currentTurnPlayerId) {
            this.match.winnerPlayerId.set(statusMsg.currentTurnPlayerId);
          }
        }
        if (statusMsg.winnerShips && statusMsg.winnerShips.length > 0) {
          this.match.winnerShips.set(statusMsg.winnerShips);
        }
      }),
      this.wsService.watchMatchMoves(inviteToken, matchPlayerId).subscribe((moveMsg) => {
        if (moveMsg.moveResponses && moveMsg.moveResponses.length > 0) {
          this.moves.appendOpponentMoves(moveMsg.moveResponses);
        }
        this.match.currentTurnPlayerId.set(moveMsg.currentTurnPlayerId);
      }),
      this.wsService.reconnected$.subscribe(() => {
        const token = this.match.inviteToken();
        if (token) {
          this.gameService.getGameInfo(token).subscribe({
            next: (info) => this.updateFromGameInfo(info),
            error: (err) => console.error('Failed to sync game state on reconnect:', err),
          });
        }
      }),
    );
  }

  markReady(): void {
    const token = this.match.inviteToken();
    if (!token || !this.canMarkReady()) return;

    this.gameService.markReady(token).subscribe({
      next: () => {
        this.match.isPlayerReady.set(true);
      },
      error: (err) => console.error('Failed to mark ready:', err),
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
    this.match.reset();
    this.ships.reset();
    this.moves.reset();
  }
}
