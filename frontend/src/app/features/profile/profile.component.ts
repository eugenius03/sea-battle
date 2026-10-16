import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthState } from '@core/state/auth.state';
import { UserService } from '@core/services/user.service';
import { MatchService } from '@core/services/match.service';
import { ToastService } from '@shared/components/toast/toast.service';
import { ButtonComponent } from '@shared/components/button/button.component';
import { IconComponent } from '@shared/components/icon/icon.component';
import { PlayerCombatStats, PublicUserResponse, UserResponse } from '@core/models/auth.model';
import { MatchHistoryResponse } from '@core/models/match.model';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, RouterLink, ButtonComponent, IconComponent],
  templateUrl: './profile.component.html',
})
export class ProfileComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly userService = inject(UserService);
  private readonly matchService = inject(MatchService);
  private readonly toast = inject(ToastService);
  readonly authState = inject(AuthState);

  readonly loading = signal<boolean>(true);
  readonly user = signal<UserResponse | PublicUserResponse | null>(null);
  readonly username = signal<string>('');
  readonly isMe = signal<boolean>(false);

  // Match History & Recent Form
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

  // Recent Form (Last 10 matches) - static, uninteractable
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

  // Always exactly 10 slots for the Recent Form strip (W, L, or '-' if fewer than 10 games played)
  readonly recentFormSlots = computed<('W' | 'L' | '-')[]>(() => {
    const form = this.recentForm();
    const slots: ('W' | 'L' | '-')[] = [];
    for (let i = 0; i < 10; i++) {
      slots.push(form[i] ?? '-');
    }
    return slots;
  });

  ngOnInit(): void {
    this.route.paramMap.subscribe((params) => {
      const urlSegment = this.route.snapshot.url[0]?.path;
      const paramUser = params.get('username');

      if (urlSegment === 'me' || !paramUser) {
        this.isMe.set(true);
        this.loadMeProfile();
      } else {
        const currentUsername = this.authState.username();
        if (currentUsername && currentUsername.toLowerCase() === paramUser.toLowerCase()) {
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
          // Populate the 10 Recent Form blocks from the newest matches
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
