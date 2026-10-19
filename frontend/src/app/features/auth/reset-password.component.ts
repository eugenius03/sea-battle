import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '@core/services/auth.service';
import { ToastService } from '@shared/components/toast/toast.service';
import { ButtonComponent } from '@shared/components/button/button.component';
import { IconComponent } from '@shared/components/icon/icon.component';

const MIN_PASSWORD_LENGTH = 6;

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [FormsModule, RouterLink, ButtonComponent, IconComponent],
  template: `
    <div class="flex-1 flex items-center justify-center p-4 sm:p-6 lg:p-8 min-h-0">
      <div
        class="border-2 border-black shadow-[6px_6px_0px_#000] w-full max-w-sm p-6 sm:p-8 bg-white"
      >
        <h1 class="text-xl font-extrabold uppercase tracking-tight text-black">Reset password</h1>

        @if (!token) {
          <p class="border-2 border-black bg-rose-100 p-3 text-sm font-bold text-black mt-4">
            This reset link is missing its token. Request a new one.
          </p>
          <a
            routerLink="/forgot-password"
            class="inline-block mt-5 text-[11px] font-bold uppercase tracking-wider text-neutral-600 underline hover:text-black"
          >
            Request a new link
          </a>
        } @else {
          <p class="text-xs font-semibold text-neutral-500 mt-2 mb-5 uppercase tracking-wider">
            Choose a new password. You will be signed out of all other devices.
          </p>

          <form (ngSubmit)="onSubmit()" class="space-y-4">
            <div class="space-y-1.5">
              <label
                for="password"
                class="block text-xs font-bold text-black uppercase tracking-wider"
              >
                New password
              </label>
              <div class="relative">
                <div
                  class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-black"
                >
                  <app-icon name="lock" [size]="18"></app-icon>
                </div>
                <input
                  id="password"
                  name="password"
                  type="password"
                  [(ngModel)]="password"
                  class="w-full pl-9 pr-3 py-2 bg-neutral-50 border-2 border-black text-black font-mono text-sm focus:outline-none focus:bg-white transition-colors"
                  autocomplete="new-password"
                />
              </div>
            </div>

            <div class="space-y-1.5">
              <label
                for="confirm"
                class="block text-xs font-bold text-black uppercase tracking-wider"
              >
                Repeat password
              </label>
              <div class="relative">
                <div
                  class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-black"
                >
                  <app-icon name="lock" [size]="18"></app-icon>
                </div>
                <input
                  id="confirm"
                  name="confirm"
                  type="password"
                  [(ngModel)]="confirm"
                  class="w-full pl-9 pr-3 py-2 bg-neutral-50 border-2 border-black text-black font-mono text-sm focus:outline-none focus:bg-white transition-colors"
                  autocomplete="new-password"
                />
              </div>
            </div>

            @if (password && password.length < minLength) {
              <p class="text-[11px] font-bold text-red-600 tracking-wide">
                Password must be at least {{ minLength }} characters.
              </p>
            } @else if (confirm && password !== confirm) {
              <p class="text-[11px] font-bold text-red-600 tracking-wide">
                Passwords do not match.
              </p>
            }

            <app-button
              type="submit"
              variant="primary"
              size="lg"
              className="w-full"
              [disabled]="loading() || !isValid()"
              [loading]="loading()"
            >
              SET NEW PASSWORD
            </app-button>
          </form>
        }
      </div>
    </div>
  `,
})
export class ResetPasswordComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly authService = inject(AuthService);
  private readonly toast = inject(ToastService);

  readonly minLength = MIN_PASSWORD_LENGTH;
  readonly token = this.route.snapshot.queryParamMap.get('token');

  password = '';
  confirm = '';
  readonly loading = signal<boolean>(false);

  isValid(): boolean {
    return this.password.length >= MIN_PASSWORD_LENGTH && this.password === this.confirm;
  }

  onSubmit(): void {
    if (!this.token || !this.isValid()) {
      return;
    }

    this.loading.set(true);
    this.authService.confirmPasswordReset(this.token, this.password).subscribe({
      next: () => {
        this.loading.set(false);
        this.toast.success('Password updated. Sign in with your new password.');
        this.router.navigate(['/auth']);
      },
      error: (err) => {
        this.loading.set(false);
        this.toast.error(
          err.error?.message || 'Could not reset the password. The link may have expired.',
        );
      },
    });
  }
}
