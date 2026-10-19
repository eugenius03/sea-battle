import { Routes } from '@angular/router';
import { authGuard, guestGuard } from '@core/guards/auth.guard';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'lobby',
    pathMatch: 'full',
  },
  {
    path: 'auth',
    canActivate: [guestGuard],
    loadComponent: () => import('@features/auth/auth.component').then((m) => m.AuthComponent),
  },
  {
    path: 'forgot-password',
    loadComponent: () =>
      import('@features/auth/forgot-password.component').then((m) => m.ForgotPasswordComponent),
  },
  {
    path: 'reset-password',
    loadComponent: () =>
      import('@features/auth/reset-password.component').then((m) => m.ResetPasswordComponent),
  },
  {
    path: 'verify-email',
    loadComponent: () =>
      import('@features/auth/verify-email.component').then((m) => m.VerifyEmailComponent),
  },
  {
    path: 'unavailable',
    loadComponent: () =>
      import('@features/unavailable/unavailable.component').then((m) => m.UnavailableComponent),
  },
  {
    path: 'lobby',
    canActivate: [authGuard],
    loadComponent: () => import('@features/lobby/lobby.component').then((m) => m.LobbyComponent),
  },
  {
    path: 'profile/me',
    canActivate: [authGuard],
    loadComponent: () =>
      import('@features/profile/profile.component').then((m) => m.ProfileComponent),
  },
  {
    path: 'profile/:username',
    loadComponent: () =>
      import('@features/profile/profile.component').then((m) => m.ProfileComponent),
  },
  {
    path: 'game/:inviteToken',
    canActivate: [authGuard],
    loadComponent: () => import('@features/game/game.component').then((m) => m.GameComponent),
  },
  {
    path: 'replay/:inviteToken',
    loadComponent: () => import('@features/replay/replay.component').then((m) => m.ReplayComponent),
  },
  {
    path: '**',
    redirectTo: 'lobby',
  },
];
