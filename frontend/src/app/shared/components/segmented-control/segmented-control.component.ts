import { Component, input, output } from '@angular/core';

export interface SegmentOption<T = string> {
  value: T;
  label: string;
  icon?: string;
}

@Component({
  selector: 'app-segmented-control',
  standalone: true,
  templateUrl: './segmented-control.component.html',
})
export class SegmentedControlComponent<T = string> {
  readonly options = input.required<SegmentOption<T>[]>();
  readonly selected = input.required<T>();
  readonly selectionChange = output<T>();

  selectOption(val: T): void {
    if (this.selected() !== val) {
      this.selectionChange.emit(val);
    }
  }
}
