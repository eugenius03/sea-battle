import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '@core/services/auth.service';
import { AuthState } from '@core/state/auth.state';
import { IconComponent } from '@shared/components/icon/icon.component';

type VerifyStatus = 'loading' | 'success' | 'error';

@Component({
  selector: 'app-verify-email',
  standalone: true,
  imports: [RouterLink, IconComponent],
  template: `
    <div class="flex-1 flex items-center justify-center p-4 sm:p-6 lg:p-8 min-h-0">
      <div
        class="border-2 border-black shadow-[6px_6px_0px_#000] w-full max-w-sm p-6 sm:p-8 bg-white text-center"
      >
        @switch (status()) {
          @case ('loading') {
            <app-icon name="spinner" [size]="32" class="animate-spin text-black mb-3"></app-icon>
            <h1 class="text-xl font-extrabold uppercase tracking-tight text-black">
              Verifying email…
            </h1>
          }
          @case ('success') {
            <div
              class="w-14 h-14 bg-emerald-500 text-white mx-auto mb-4 flex items-center justify-center border-2 border-black shadow-[3px_3px_0px_#000]"
            >
              <app-icon name="check" [size]="32"></app-icon>
            </div>
            <h1 class="text-xl font-extrabold uppercase tracking-tight text-black">
              Email verified
            </h1>
            <p class="text-xs font-semibold text-neutral-500 mt-2 uppercase tracking-wider">
              You can now reset your password by email if you ever lose access.
            </p>
            <a
              routerLink="/profile/me"
              class="inline-block mt-5 px-4 py-2 text-sm font-bold uppercase bg-black text-white border-2 border-black shadow-[3px_3px_0px_#000]"
            >
              Go to profile
            </a>
          }
          @case ('error') {
            <h1 class="text-xl font-extrabold uppercase tracking-tight text-black">
              Link not valid
            </h1>
            <p class="text-xs font-semibold text-neutral-500 mt-2 uppercase tracking-wider">
              This verification link is invalid, expired or already used. Request a new one from
              your profile.
            </p>
            <a
              routerLink="/profile/me"
              class="inline-block mt-5 px-4 py-2 text-sm font-bold uppercase bg-black text-white border-2 border-black shadow-[3px_3px_0px_#000]"
            >
              Go to profile
            </a>
          }
        }
      </div>
    </div>
  `,
})
export class VerifyEmailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly authService = inject(AuthService);
  private readonly authState = inject(AuthState);

  readonly status = signal<VerifyStatus>('loading');

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');
    if (!token) {
      this.status.set('error');
      return;
    }

    this.authService.verifyEmail(token).subscribe({
      next: () => {
        this.status.set('success');
        this.authState.user.update((user) => (user ? { ...user, emailVerified: true } : user));
      },
      error: () => this.status.set('error'),
    });
  }
}
