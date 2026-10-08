import { Component, input, output } from '@angular/core';
import { IconComponent } from '../icon/icon.component';

export type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'ghost';
export type ButtonSize = 'sm' | 'md' | 'lg';

@Component({
  selector: 'app-button',
  standalone: true,
  imports: [IconComponent],
  templateUrl: './button.component.html',
})
export class ButtonComponent {
  readonly variant = input<ButtonVariant>('primary');
  readonly size = input<ButtonSize>('md');
  readonly type = input<'button' | 'submit' | 'reset'>('button');
  readonly disabled = input<boolean>(false);
  readonly loading = input<boolean>(false);
  readonly className = input<string>('');

  readonly clicked = output<MouseEvent>();

  onClick(event: MouseEvent): void {
    if (!this.disabled() && !this.loading()) {
      this.clicked.emit(event);
    }
  }

  buttonClasses(): string {
    const sizeClasses = {
      sm: 'px-3 py-1.5 text-xs min-h-[2.25rem]',
      md: 'px-4 py-2 text-sm min-h-[2.625rem]',
      lg: 'px-6 py-3 text-base min-h-[3rem]',
    }[this.size()];

    const baseClass =
      'rounded-none border-2 border-black font-bold uppercase cursor-pointer disabled:cursor-not-allowed disabled:opacity-50 disabled:shadow-none select-none transition-all inline-flex items-center justify-center';

    const variantClasses = {
      primary:
        'bg-black text-white hover:bg-neutral-800 shadow-[3px_3px_0px_#000] active:translate-x-[2px] active:translate-y-[2px] active:shadow-[1px_1px_0px_#000]',
      secondary:
        'bg-neutral-100 text-black hover:bg-neutral-200 shadow-[3px_3px_0px_#000] active:translate-x-[2px] active:translate-y-[2px] active:shadow-[1px_1px_0px_#000]',
      danger:
        'bg-red-500 text-white hover:bg-red-600 shadow-[3px_3px_0px_#000] active:translate-x-[2px] active:translate-y-[2px] active:shadow-[1px_1px_0px_#000]',
      ghost:
        'bg-transparent border-transparent shadow-none hover:bg-neutral-100 text-black active:bg-neutral-200',
    }[this.variant()];

    return `${baseClass} ${sizeClasses} ${variantClasses} ${this.className()}`;
  }
}
