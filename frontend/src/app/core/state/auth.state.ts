import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, Observable, of, switchMap, tap } from 'rxjs';
import { UserLoginRequest, UserRegistrationRequest, UserResponse } from '@core/models/auth.model';
import { AuthService } from '@core/services/auth.service';
import { isAuthRejection } from '@core/utils/http-error.util';

@Injectable({
  providedIn: 'root',
})
export class AuthState {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly user = signal<UserResponse | null>(null);
  readonly isLoading = signal<boolean>(false);
  /** True when the last session check failed for a reason other than 401/403 (e.g. backend down). */
  readonly backendUnavailable = signal<boolean>(false);
  readonly isAuthenticated = computed(() => this.user() !== null);
  readonly username = computed(() => this.user()?.username ?? '');
  readonly userId = computed(() => this.user()?.id ?? '');

  private hasChecked = false;

  loadCurrentUser(): Observable<UserResponse | null> {
    if (this.hasChecked && !this.isLoading()) {
      return of(this.user());
    }

    this.isLoading.set(true);
    return this.authService.getCurrentUser().pipe(
      tap((userData) => {
        this.backendUnavailable.set(false);
        this.user.set(userData);
        this.isLoading.set(false);
        this.hasChecked = true;
      }),
      catchError((error) => {
        this.isLoading.set(false);
        if (isAuthRejection(error)) {
          this.backendUnavailable.set(false);
          this.user.set(null);
          this.hasChecked = true;
        } else {
          this.backendUnavailable.set(true);
        }
        return of(this.user());
      }),
    );
  }

  login(credentials: UserLoginRequest): Observable<UserResponse | null> {
    return this.authService.login(credentials).pipe(
      switchMap(() => {
        this.hasChecked = false;
        return this.loadCurrentUser();
      }),
    );
  }

  register(credentials: UserRegistrationRequest): Observable<UserResponse | null> {
    return this.authService.register(credentials).pipe(
      switchMap(() => {
        this.hasChecked = false;
        return this.loadCurrentUser();
      }),
    );
  }

  guestLogin(nickname: string): Observable<UserResponse | null> {
    return this.authService.guestLogin(nickname).pipe(
      switchMap(() => {
        this.hasChecked = false;
        return this.loadCurrentUser();
      }),
    );
  }

  logout(): void {
    this.authService.logout().subscribe({
      next: () => {
        this.user.set(null);
        this.hasChecked = true;
        this.router.navigate(['/auth']);
      },
      error: (error) => {
        if (!isAuthRejection(error)) return;
        this.user.set(null);
        this.hasChecked = true;
        this.router.navigate(['/auth']);
      },
    });
  }
}
