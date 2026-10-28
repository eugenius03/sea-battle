import { Component, inject, OnDestroy, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { AuthState } from '@core/state/auth.state';
import { MatchService } from '@core/services/match.service';
import { WebSocketService } from '@core/services/websocket.service';
import { ToastService } from '@shared/components/toast/toast.service';
import { ButtonComponent } from '@shared/components/button/button.component';
import { IconComponent } from '@shared/components/icon/icon.component';
import { extractInviteCode } from '@shared/utils/invite.util';

@Component({
  selector: 'app-lobby',
  standalone: true,
  imports: [FormsModule, ButtonComponent, IconComponent],
  templateUrl: './lobby.component.html',
})
export class LobbyComponent implements OnDestroy {
  readonly authState = inject(AuthState);
  private readonly matchService = inject(MatchService);
  private readonly wsService = inject(WebSocketService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly isSearching = signal<boolean>(false);
  readonly queuePosition = signal<number | null>(null);
  readonly creatingMatch = signal<boolean>(false);
  readonly joiningMatch = signal<boolean>(false);
  readonly creatingBotMatch = signal<boolean>(false);

  inviteInput = '';
  private matchmakingSub: Subscription | null = null;

  constructor() {
    const joinParam =
      this.route.snapshot.queryParamMap.get('join') ||
      this.route.snapshot.queryParamMap.get('code');
    if (joinParam) {
      this.inviteInput = extractInviteCode(joinParam);
      if (this.inviteInput) {
        this.joinPrivateMatch();
      }
    }
  }

  startMatchmaking(): void {
    const userId = this.authState.userId();
    if (!userId) return;

    this.isSearching.set(true);
    this.queuePosition.set(null);

    this.matchmakingSub?.unsubscribe();
    this.matchmakingSub = this.wsService.watchMatchmaking(userId).subscribe({
      next: (status) => {
        if (status.queuePosition !== undefined && status.queuePosition !== null) {
          this.queuePosition.set(status.queuePosition);
        }
        if (status.inviteToken) {
          this.isSearching.set(false);
          this.toast.success('Opponent found! Joining game.');
          this.router.navigate(['/game', status.inviteToken]);
        }
      },
      error: (err) => {
        console.error('Matchmaking error:', err);
        this.cancelMatchmaking();
      },
    });

    this.wsService
      .connect({ 'X-User-Id': userId })
      .then(() => {
        if (!this.isSearching()) return;

        this.matchService.findGame().subscribe({
          error: (err) => {
            this.toast.error('Failed to join matchmaking queue.');
            this.cancelMatchmaking();
          },
        });
      })
      .catch((err) => {
        console.error('Failed to connect to matchmaking socket:', err);
        this.toast.error('Failed to connect to matchmaking service.');
        this.cancelMatchmaking();
      });
  }

  cancelMatchmaking(): void {
    this.isSearching.set(false);
    this.queuePosition.set(null);
    this.matchmakingSub?.unsubscribe();
    this.matchmakingSub = null;
    this.wsService.disconnect();
  }

  createPrivateMatch(): void {
    this.creatingMatch.set(true);
    this.matchService.createMatch().subscribe({
      next: (res) => {
        this.creatingMatch.set(false);
        this.router.navigate(['/game', res.inviteToken]);
      },
      error: (err) => {
        this.creatingMatch.set(false);
        this.toast.error('Failed to create private match.');
      },
    });
  }

  playAgainstBot(): void {
    this.creatingBotMatch.set(true);
    this.matchService.createBotMatch().subscribe({
      next: (res) => {
        this.creatingBotMatch.set(false);
        this.router.navigate(['/game', res.inviteToken]);
      },
      error: () => {
        this.creatingBotMatch.set(false);
        this.toast.error('Failed to start a game against the bot.');
      },
    });
  }

  onInviteInputChange(value: string): void {
    this.inviteInput = extractInviteCode(value);
  }

  joinPrivateMatch(): void {
    const raw = this.inviteInput.trim();
    if (!raw) return;

    const token = extractInviteCode(raw);
    if (!token) {
      this.toast.error('Invalid room code or invite link.');
      return;
    }

    this.inviteInput = token;
    this.joiningMatch.set(true);
    this.matchService.joinMatch(token).subscribe({
      next: (res) => {
        this.joiningMatch.set(false);
        this.toast.success('Joined game!');
        this.router.navigate(['/game', res.inviteToken]);
      },
      error: (err) => {
        this.joiningMatch.set(false);
        this.toast.error(err.error?.message || 'Invalid or expired invite token.');
      },
    });
  }

  ngOnDestroy(): void {
    this.matchmakingSub?.unsubscribe();
  }
}
