import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { safeReturnUrl } from '@core/utils/return-url.util';
import { AuthState } from '@core/state/auth.state';

export const authGuard: CanActivateFn = (_route, state) => {
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
      const target = authState.backendUnavailable() ? '/unavailable' : '/auth';
      return router.createUrlTree([target], { queryParams: { returnUrl: state.url } });
    }),
  );
};

export const guestGuard: CanActivateFn = (route) => {
  const authState = inject(AuthState);
  const router = inject(Router);

  if (authState.isAuthenticated()) {
    return router.parseUrl(safeReturnUrl(route.queryParamMap.get('returnUrl')));
  }

  return true;
};
