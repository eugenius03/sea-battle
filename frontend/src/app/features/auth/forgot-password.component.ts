import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '@core/services/auth.service';
import { ToastService } from '@shared/components/toast/toast.service';
import { ButtonComponent } from '@shared/components/button/button.component';
import { IconComponent } from '@shared/components/icon/icon.component';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [FormsModule, RouterLink, ButtonComponent, IconComponent],
  template: `
    <div class="flex-1 flex items-center justify-center p-4 sm:p-6 lg:p-8 min-h-0">
      <div
        class="border-2 border-black shadow-[6px_6px_0px_#000] w-full max-w-sm p-6 sm:p-8 bg-white"
      >
        <h1 class="text-xl font-extrabold uppercase tracking-tight text-black">Forgot password</h1>
        <p class="text-xs font-semibold text-neutral-500 mt-2 mb-5 uppercase tracking-wider">
          Enter the verified email on your account and we will send a reset link.
        </p>

        @if (sent()) {
          <p class="border-2 border-black bg-emerald-100 p-3 text-sm font-bold text-black mb-4">
            If an account with that verified email exists, a reset link is on its way. The link is
            valid for 1 hour.
          </p>
        }

        <form (ngSubmit)="onSubmit()" class="space-y-4">
          <div class="space-y-1.5">
            <label for="email" class="block text-xs font-bold text-black uppercase tracking-wider">
              Email
            </label>
            <div class="relative">
              <div
                class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-black"
              >
                <app-icon name="mail" [size]="18"></app-icon>
              </div>
              <input
                id="email"
                name="email"
                type="email"
                [(ngModel)]="email"
                placeholder="commander@navy.mil"
                class="w-full pl-9 pr-3 py-2 bg-neutral-50 border-2 border-black text-black font-mono text-sm placeholder:text-neutral-400 focus:outline-none focus:bg-white transition-colors"
                autocomplete="email"
                spellcheck="false"
              />
            </div>
          </div>

          <app-button
            type="submit"
            variant="primary"
            size="lg"
            className="w-full"
            [disabled]="loading() || cooldown() > 0 || !email.trim()"
            [loading]="loading()"
          >
            {{ cooldown() > 0 ? 'RESEND IN ' + cooldown() + 'S' : 'SEND RESET LINK' }}
          </app-button>
        </form>

        <a
          routerLink="/auth"
          class="inline-block mt-5 text-[11px] font-bold uppercase tracking-wider text-neutral-600 underline hover:text-black"
        >
          Back to sign in
        </a>
      </div>
    </div>
  `,
})
export class ForgotPasswordComponent {
  private readonly authService = inject(AuthService);
  private readonly toast = inject(ToastService);

  private static readonly COOLDOWN_SECONDS = 30;

  email = '';
  readonly loading = signal<boolean>(false);
  readonly sent = signal<boolean>(false);
  readonly cooldown = signal<number>(0);

  onSubmit(): void {
    const email = this.email.trim();
    if (!email) {
      return;
    }

    this.loading.set(true);
    this.authService.requestPasswordReset(email).subscribe({
      next: () => {
        this.loading.set(false);
        this.sent.set(true);
        this.startCooldown();
      },
      error: (err) => {
        this.loading.set(false);
        this.toast.error(err.error?.message || 'Could not send the reset link. Try again.');
      },
    });
  }

  private startCooldown(): void {
    this.cooldown.set(ForgotPasswordComponent.COOLDOWN_SECONDS);
    const timer = setInterval(() => {
      this.cooldown.update((value) => value - 1);
      if (this.cooldown() <= 0) {
        clearInterval(timer);
      }
    }, 1000);
  }
}
