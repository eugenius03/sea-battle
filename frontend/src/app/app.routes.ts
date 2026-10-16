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
    path: 'game/:inviteToken',
    canActivate: [authGuard],
    loadComponent: () => import('@features/game/game.component').then((m) => m.GameComponent),
  },
  {
    path: '**',
    redirectTo: 'lobby',
  },
];
