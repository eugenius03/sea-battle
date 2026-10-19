import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthState } from '@core/state/auth.state';
import { AuthService } from '@core/services/auth.service';
import { UserService } from '@core/services/user.service';
import { MatchService } from '@core/services/match.service';
import { ToastService } from '@shared/components/toast/toast.service';
import { ButtonComponent } from '@shared/components/button/button.component';
import { IconComponent } from '@shared/components/icon/icon.component';
import { PlayerCombatStats, PublicUserResponse, UserResponse } from '@core/models/auth.model';
import { MatchHistoryResponse } from '@core/models/match.model';
import { retryAfterSeconds } from '@core/utils/http-error.util';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ButtonComponent, IconComponent],
  templateUrl: './profile.component.html',
})
export class ProfileComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly userService = inject(UserService);
  private readonly matchService = inject(MatchService);
  private readonly toast = inject(ToastService);
  private readonly authService = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  readonly authState = inject(AuthState);

  readonly loading = signal<boolean>(true);
  readonly user = signal<UserResponse | PublicUserResponse | null>(null);
  readonly username = signal<string>('');
  readonly isMe = signal<boolean>(false);

  readonly historyLoading = signal<boolean>(false);
  readonly matchHistory = signal<MatchHistoryResponse[]>([]);
  readonly historyPage = signal<number>(0);
  readonly hasNextPage = signal<boolean>(false);
  readonly PAGE_SIZE = 10;

  readonly showPagination = computed<boolean>(() => {
    if (this.historyPage() > 0) return true;
    const total = this.stats()?.matchesPlayed;
    if (total !== undefined) {
      return total > 10;
    }
    return this.hasNextPage();
  });

  readonly recentForm = signal<('W' | 'L')[]>([]);

  readonly stats = computed<PlayerCombatStats | null>(() => this.user()?.stats ?? null);

  readonly isGuest = computed<boolean>(() => {
    const u = this.user();
    return Boolean((u as UserResponse)?.isGuest);
  });

  readonly email = computed<string | null>(() => {
    const u = this.user();
    return (u as UserResponse)?.email ?? null;
  });

  readonly emailVerified = computed<boolean>(() => {
    const u = this.user();
    return Boolean((u as UserResponse)?.emailVerified);
  });

  readonly memberSince = computed<string>(() => {
    const raw = this.user()?.memberSince;
    if (!raw) return 'Unknown';
    try {
      const d = new Date(raw);
      const pad = (n: number) => n.toString().padStart(2, '0');
      return `${pad(d.getDate())}.${pad(d.getMonth() + 1)}.${d.getFullYear()}`;
    } catch {
      return raw;
    }
  });

  readonly recentFormSlots = computed<('W' | 'L' | '-')[]>(() => {
    const form = this.recentForm();
    const slots: ('W' | 'L' | '-')[] = [];
    for (let i = 0; i < 10; i++) {
      slots.push(form[i] ?? '-');
    }
    return slots;
  });

  emailInput = '';
  readonly emailSending = signal<boolean>(false);
  readonly emailCooldown = signal<number>(0);
  private emailCooldownTimer: ReturnType<typeof setInterval> | null = null;

  readonly canManageEmail = computed<boolean>(() => this.isMe() && !this.isGuest());

  ngOnInit(): void {
    this.destroyRef.onDestroy(() => this.stopEmailCooldown());
    this.route.paramMap.subscribe((params) => {
      const urlSegment = this.route.snapshot.url[0]?.path;
      const paramUser = params.get('username');

      if (urlSegment === 'me' || !paramUser) {
        this.isMe.set(true);
        this.loadMeProfile();
      } else {
        const currentUsername = this.authState.username();
        if (currentUsername?.toLowerCase() === paramUser.toLowerCase()) {
          this.isMe.set(true);
          this.loadMeProfile();
        } else {
          this.isMe.set(false);
          this.username.set(paramUser);
          this.loadPublicProfile(paramUser);
        }
      }
    });
  }

  sendVerificationEmail(): void {
    const target = (this.email() ?? this.emailInput).trim();
    if (!target || this.emailSending() || this.emailCooldown() > 0) {
      return;
    }

    this.emailSending.set(true);
    this.authService.setEmail(target).subscribe({
      next: () => {
        this.emailSending.set(false);
        this.user.update((u) => (u ? ({ ...u, email: target } as UserResponse) : u));
        this.emailInput = '';
        this.toast.success(`Verification email sent to ${target}. Check your inbox.`);
        this.startEmailCooldown(30);
      },
      error: (err) => {
        this.emailSending.set(false);
        const wait = retryAfterSeconds(err);
        if (wait) {
          this.startEmailCooldown(wait);
        }
        this.toast.error(err.error?.message || 'Could not send the verification email.');
      },
    });
  }

  private startEmailCooldown(seconds: number): void {
    this.stopEmailCooldown();
    this.emailCooldown.set(seconds);
    this.emailCooldownTimer = setInterval(() => {
      this.emailCooldown.update((value) => value - 1);
      if (this.emailCooldown() <= 0) {
        this.stopEmailCooldown();
      }
    }, 1000);
  }

  private stopEmailCooldown(): void {
    if (this.emailCooldownTimer) {
      clearInterval(this.emailCooldownTimer);
      this.emailCooldownTimer = null;
    }
  }

  private loadMeProfile(): void {
    this.loading.set(true);
    this.userService.getMe().subscribe({
      next: (userData) => {
        this.user.set(userData);
        this.username.set(userData.username);
        this.loading.set(false);
        this.loadHistory(userData.username, 0, true);
      },
      error: (err) => {
        this.loading.set(false);
        this.toast.error('Failed to load profile');
      },
    });
  }

  private loadPublicProfile(name: string): void {
    this.loading.set(true);
    this.userService.getPublicProfile(name).subscribe({
      next: (userData) => {
        this.user.set(userData);
        this.username.set(userData.username);
        this.loading.set(false);
        this.loadHistory(userData.username, 0, true);
      },
      error: (err) => {
        this.loading.set(false);
        this.toast.error('User not found');
      },
    });
  }

  loadHistory(usernameToFetch: string, page: number, isInitial = false): void {
    this.historyLoading.set(true);
    this.matchService.getMatchHistory(usernameToFetch, page, this.PAGE_SIZE).subscribe({
      next: (matches) => {
        this.matchHistory.set(matches);
        this.historyPage.set(page);
        this.hasNextPage.set(matches.length === this.PAGE_SIZE);

        if (isInitial) {
          const formValues: ('W' | 'L')[] = matches
            .slice(0, 10)
            .map((m) => (m.result === 'WON' ? 'W' : 'L'));
          this.recentForm.set(formValues);
        }

        this.historyLoading.set(false);
      },
      error: () => {
        this.historyLoading.set(false);
      },
    });
  }

  onPrevPage(): void {
    if (this.historyPage() > 0 && !this.historyLoading()) {
      this.loadHistory(this.username(), this.historyPage() - 1);
    }
  }

  onNextPage(): void {
    if (this.hasNextPage() && !this.historyLoading()) {
      this.loadHistory(this.username(), this.historyPage() + 1);
    }
  }

  watchReplay(inviteToken: string): void {
    this.router.navigate(['/replay', inviteToken], {
      queryParams: { focus: this.username() },
    });
  }

  formatDuration(seconds: number): string {
    if (!seconds || seconds <= 0) return '0s';
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    if (m === 0) return `${s}s`;
    return `${m}m ${s}s`;
  }

  formatDate(dateStr: string): string {
    if (!dateStr) return '';
    try {
      const d = new Date(dateStr);
      const pad = (n: number) => n.toString().padStart(2, '0');
      const hours = pad(d.getHours());
      const minutes = pad(d.getMinutes());
      const day = pad(d.getDate());
      const month = pad(d.getMonth() + 1);
      const year = d.getFullYear();
      return `${hours}:${minutes} ${day}.${month}.${year}`;
    } catch {
      return dateStr;
    }
  }
}
