import { Component, input } from '@angular/core';

export type IconName =
  | 'pixel-ship'
  | 'pixel-bracket-left'
  | 'pixel-bracket-right'
  | 'pixel-anchor'
  | 'crosshair'
  | 'target'
  | 'radar'
  | 'plane'
  | 'zap'
  | 'user'
  | 'users'
  | 'log-out'
  | 'rotate-cw'
  | 'shuffle'
  | 'dice'
  | 'copy'
  | 'check'
  | 'swords'
  | 'flame'
  | 'lock'
  | 'pixel-lock'
  | 'award'
  | 'plus'
  | 'link'
  | 'pixel-skull'
  | 'close'
  | 'spinner'
  | 'qrcode'
  | 'mail'
  | 'warning';

@Component({
  selector: 'app-icon',
  standalone: true,
  templateUrl: './icon.component.html',
  styles: [
    `
      :host {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        vertical-align: middle;
        line-height: 1;
      }
    `,
  ],
})
export class IconComponent {
  readonly name = input.required<IconName>();
  readonly size = input<number>(20);
  readonly className = input<string>('');
}
