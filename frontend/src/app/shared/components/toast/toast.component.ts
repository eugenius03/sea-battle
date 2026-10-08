import { Component, inject } from '@angular/core';
import { ToastService, ToastType } from './toast.service';
import { IconComponent, IconName } from '../icon/icon.component';

@Component({
  selector: 'app-toast-container',
  standalone: true,
  imports: [IconComponent],
  templateUrl: './toast.component.html',
  styleUrl: './toast.component.scss',
})
export class ToastContainerComponent {
  readonly toastService = inject(ToastService);

  getIconName(type: ToastType): IconName {
    switch (type) {
      case 'success':
        return 'check';
      case 'error':
        return 'close';
      case 'warning':
        return 'zap';
      default:
        return 'pixel-anchor';
    }
  }

  getBadgeClasses(type: ToastType): string {
    switch (type) {
      case 'success':
        return 'bg-emerald-400 text-black border-2 border-black';
      case 'error':
        return 'bg-rose-500 text-white border-2 border-black';
      case 'warning':
        return 'bg-amber-300 text-black border-2 border-black';
      default:
        return 'bg-cyan-300 text-black border-2 border-black';
    }
  }
}
