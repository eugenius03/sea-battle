import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthState } from '@core/state/auth.state';
import { IconComponent } from '../icon/icon.component';
import { ButtonComponent } from '../button/button.component';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [RouterLink, IconComponent, ButtonComponent],
  templateUrl: './navbar.component.html',
  styles: [
    `
      :host {
        display: block;
        height: 3.5rem;
        flex-shrink: 0;
      }
    `,
  ],
})
export class NavbarComponent {
  readonly authState = inject(AuthState);

  onSignOut(): void {
    this.authState.logout();
  }
}
