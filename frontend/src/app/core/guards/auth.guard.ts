import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthState } from '@core/state/auth.state';

export const authGuard: CanActivateFn = () => {
  const authState = inject(AuthState);
  const router = inject(Router);

  if (authState.isAuthenticated()) {
    return true;
  }

  return authState.loadCurrentUser().pipe(
    map((user) => {
      if (user) {
        return true;
      }
      return router.createUrlTree(['/auth']);
    }),
  );
};

export const guestGuard: CanActivateFn = () => {
  const authState = inject(AuthState);
  const router = inject(Router);

  if (authState.isAuthenticated()) {
    return router.createUrlTree(['/lobby']);
  }

  return true;
};
