import { Component, HostListener, input, output } from '@angular/core';
import { IconComponent } from '../icon/icon.component';

@Component({
  selector: 'app-modal',
  standalone: true,
  imports: [IconComponent],
  templateUrl: './modal.component.html',
  styleUrl: './modal.component.scss',
})
export class ModalComponent {
  readonly isOpen = input.required<boolean>();
  readonly showClose = input<boolean>(true);
  readonly title = input<string>();
  readonly closed = output<void>();

  @HostListener('window:keydown.escape')
  onEscape(): void {
    if (this.isOpen()) {
      this.closed.emit();
    }
  }
}
