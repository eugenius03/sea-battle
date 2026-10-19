import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthState } from '@core/state/auth.state';
import { ToastService } from '@shared/components/toast/toast.service';
import { ButtonComponent } from '@shared/components/button/button.component';
import {
  SegmentedControlComponent,
  SegmentOption,
} from '@shared/components/segmented-control/segmented-control.component';
import { IconComponent } from '@shared/components/icon/icon.component';
import { UserRegistrationRequest } from '@core/models/auth.model';
import { safeReturnUrl } from '@core/utils/return-url.util';
import { generateGuestNickname } from '@shared/utils/guest-nickname.util';

type AuthMode = 'login' | 'register';

@Component({
  selector: 'app-auth',
  standalone: true,
  imports: [FormsModule, RouterLink, ButtonComponent, SegmentedControlComponent, IconComponent],
  templateUrl: './auth.component.html',
})
export class AuthComponent implements OnInit {
  private readonly authState = inject(AuthState);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toast = inject(ToastService);

  readonly mode = signal<AuthMode>('login');
  readonly loading = signal<boolean>(false);
  readonly guestLoading = signal<boolean>(false);

  username = '';
  password = '';
  email = '';
  guestNickname = '';

  private returnUrl(): string {
    return safeReturnUrl(this.route.snapshot.queryParamMap.get('returnUrl'));
  }

  get isEmailInvalid(): boolean {
    const trimmed = this.email.trim();
    if (!trimmed) {
      return false;
    }
    return !/^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/.test(trimmed);
  }

  readonly modeOptions: SegmentOption<AuthMode>[] = [
    { value: 'login', label: 'Sign In' },
    { value: 'register', label: 'Register' },
  ];

  ngOnInit(): void {
    this.guestNickname = generateGuestNickname();
  }

  onModeChange(newMode: AuthMode): void {
    this.mode.set(newMode);
  }

  rerollGuestNickname(): void {
    this.guestNickname = generateGuestNickname();
  }

  suggestRegisterUsername(): void {
    this.username = generateGuestNickname();
  }

  onGuestSubmit(): void {
    const nick = this.guestNickname.trim();
    if (!nick) {
      this.toast.error('Please enter a nickname.');
      return;
    }

    this.guestLoading.set(true);
    this.authState.guestLogin(nick).subscribe({
      next: (user) => {
        this.guestLoading.set(false);
        if (user) {
          this.toast.success(`Welcome aboard, ${user.username}!`);
          this.router.navigateByUrl(this.returnUrl());
        } else {
          this.toast.error('Failed to establish guest session.');
        }
      },
      error: (err) => {
        this.guestLoading.set(false);
        this.toast.error(err?.error?.message || 'Failed to enter as guest.');
      },
    });
  }

  onSubmit(): void {
    if (!this.username.trim() || !this.password.trim()) {
      this.toast.error('Please provide both username and password.');
      return;
    }

    if (this.mode() === 'register' && this.isEmailInvalid) {
      this.toast.error('Please enter a valid email address or leave it blank.');
      return;
    }

    this.loading.set(true);

    if (this.mode() === 'login') {
      const credentials = { username: this.username.trim(), password: this.password.trim() };
      this.authState.login(credentials).subscribe({
        next: () => {
          this.loading.set(false);
          this.toast.success('Welcome back, Commander!');
          this.router.navigateByUrl(this.returnUrl());
        },
        error: (err) => {
          this.loading.set(false);
          this.toast.error(err.error?.message || 'Login failed. Check your credentials.');
        },
      });
    } else {
      const registerPayload: UserRegistrationRequest = {
        username: this.username.trim(),
        password: this.password.trim(),
      };
      const trimmedEmail = this.email.trim();
      if (trimmedEmail) {
        registerPayload.email = trimmedEmail;
      }

      this.authState.register(registerPayload).subscribe({
        next: () => {
          this.loading.set(false);
          this.toast.success('Commission accepted! Logged in.');
          this.router.navigateByUrl(this.returnUrl());
        },
        error: (err) => {
          this.loading.set(false);
          this.toast.error(err.error?.message || 'Registration failed. Username might be taken.');
        },
      });
    }
  }
}
