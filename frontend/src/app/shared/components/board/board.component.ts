import { Component, EventEmitter, HostListener, Input, OnDestroy, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BoardCell } from '@core/models/game.model';
import { IconComponent } from '../icon/icon.component';

function createSafeDragEvent(type: string): DragEvent {
  if (typeof DragEvent !== 'undefined') {
    try {
      return new DragEvent(type);
    } catch {}
  }
  return new CustomEvent(type) as unknown as DragEvent;
}

@Component({
  selector: 'app-board',
  standalone: true,
  imports: [CommonModule, IconComponent],
  templateUrl: './board.component.html',
  styleUrl: './board.component.scss',
})
export class BoardComponent implements OnDestroy {
  @Input() boardId = 'board';
  @Input() title = 'Board';
  @Input() subtitle = '';
  @Input() badge = '';
  @Input() badgeType: 'neutral' | 'accent' | 'danger' | 'success' | 'warn' = 'neutral';
  @Input() cells: BoardCell[] = [];
  @Input() isPlayerBoard = false;
  @Input() isReady = false;
  @Input() isLocked = false;
  @Input() selectedPlacedShipId: number | null = null;
  @Input() hasShipToPlace = false;
  @Input() isTurn = false;

  @Output() cellClick = new EventEmitter<BoardCell>();
  @Output() cellDblClick = new EventEmitter<BoardCell>();
  @Output() cellMouseEnter = new EventEmitter<BoardCell>();
  @Output() cellMouseLeave = new EventEmitter<BoardCell | void>();
  @Output() cellDragStart = new EventEmitter<{ event: DragEvent; cell: BoardCell }>();
  @Output() cellDragOver = new EventEmitter<{ event: DragEvent; cell: BoardCell }>();
  @Output() cellDragLeave = new EventEmitter<BoardCell>();
  @Output() cellDragEnd = new EventEmitter<{ event: DragEvent; cell: BoardCell }>();
  @Output() cellDrop = new EventEmitter<{ event: DragEvent; cell: BoardCell }>();

  readonly columns = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J'];
  readonly rows = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];

  getCellAriaLabel(cell: BoardCell): string {
    const col = this.columns[cell.x] ?? cell.x;
    const row = cell.y + 1;
    let state = 'water';
    if (cell.isSunk) state = 'sunk ship';
    else if (cell.isHit) state = 'hit';
    else if (cell.isMiss) state = 'miss';
    else if (cell.hasShip) state = 'ship';
    else if (cell.isSurveillance) state = 'scanned';
    return `Coordinate ${col}${row}, ${state}`;
  }

  private touchStartX = 0;
  private touchStartY = 0;
  private lastTouchX = 0;
  private lastTouchY = 0;
  private isScrollGesture = false;
  private lastScrollEndTime = 0;
  private holdTimer: ReturnType<typeof setTimeout> | null = null;
  isHoldingShip = false;
  isHoldingNewShip = false;
  isHoldingDrone = false;
  private currentDragTargetCell: BoardCell | null = null;

  private lockBodyScroll(): void {
    if (typeof document !== 'undefined') {
      document.body.style.overflow = 'hidden';
      document.body.style.touchAction = 'none';
    }
  }

  private unlockBodyScroll(): void {
    if (typeof document !== 'undefined') {
      document.body.style.overflow = '';
      document.body.style.touchAction = '';
    }
  }

  ngOnDestroy(): void {
    this.unlockBodyScroll();
    if (this.holdTimer) {
      clearTimeout(this.holdTimer);
    }
  }

  onCellClick(cell: BoardCell): void {
    if (this.isLocked) return;
    if (Date.now() - this.lastScrollEndTime < 350) {
      return;
    }
    this.cellClick.emit(cell);
  }

  onWrapperClick(event: Event): void {
    const target = event.target as HTMLElement;
    if (!target.closest('.cell')) {
      this.cellMouseLeave.emit();
    }
  }

  onCellDblClick(cell: BoardCell): void {
    if (this.isLocked) return;
    this.cellDblClick.emit(cell);
  }

  onCellMouseEnter(cell: BoardCell): void {
    if (this.isLocked) return;
    this.cellMouseEnter.emit(cell);
  }

  onCellMouseLeave(cell: BoardCell): void {
    this.cellMouseLeave.emit(cell);
  }

  onDragStart(event: DragEvent, cell: BoardCell): void {
    if (this.isLocked) return;
    this.cellDragStart.emit({ event, cell });
  }

  onDragOver(event: DragEvent, cell: BoardCell): void {
    if (this.isLocked) return;
    event.preventDefault();
    this.cellDragOver.emit({ event, cell });
  }

  onDragLeave(cell: BoardCell): void {
    this.cellDragLeave.emit(cell);
  }

  onDragEnd(event: DragEvent, cell: BoardCell): void {
    this.isHoldingShip = false;
    this.isHoldingNewShip = false;
    this.isHoldingDrone = false;
    this.unlockBodyScroll();
    this.cellDragEnd.emit({ event, cell });
  }

  onDrop(event: DragEvent, cell: BoardCell): void {
    if (this.isLocked) return;
    event.preventDefault();
    this.cellDrop.emit({ event, cell });
  }

  onMatrixMouseLeave(): void {
    this.cellMouseLeave.emit();
  }

  onMatrixDragLeave(event: DragEvent): void {
    const related = event.relatedTarget as HTMLElement | null;
    const current = event.currentTarget as HTMLElement | null;
    if (!related || !current?.contains(related)) {
      this.cellMouseLeave.emit();
    }
  }

  @HostListener('window:dragend')
  @HostListener('window:mouseup')
  onWindowPointerUp(): void {
    if (this.isHoldingShip || this.isHoldingNewShip || this.isHoldingDrone) {
      this.isHoldingShip = false;
      this.isHoldingNewShip = false;
      this.isHoldingDrone = false;
      this.unlockBodyScroll();
      this.cellMouseLeave.emit();
    }
  }

  onTouchStart(event: TouchEvent): void {
    if (this.isLocked) return;
    const touch = event.touches[0];
    if (!touch) return;
    this.isScrollGesture = false;
    this.isHoldingShip = false;
    this.isHoldingNewShip = false;
    this.currentDragTargetCell = null;
    this.touchStartX = touch.clientX;
    this.touchStartY = touch.clientY;
    this.lastTouchX = touch.clientX;
    this.lastTouchY = touch.clientY;

    if (this.holdTimer) clearTimeout(this.holdTimer);

    if (this.isPlayerBoard && !this.isReady) {
      const target = this.getElementAtTouch(touch.clientX, touch.clientY);
      const cellEl = target?.closest('.cell') as HTMLElement | null;
      if (cellEl && cellEl.dataset['x'] !== undefined && cellEl.dataset['y'] !== undefined) {
        const x = parseInt(cellEl.dataset['x']!, 10);
        const y = parseInt(cellEl.dataset['y']!, 10);
        const cell = this.cells.find((c) => c.key === `${x},${y}`);

        if (cell && cell.hasShip) {
          this.holdTimer = setTimeout(() => {
            this.isHoldingShip = true;
            this.lockBodyScroll();
            this.cellDragStart.emit({
              event: createSafeDragEvent('dragstart'),
              cell,
            });
          }, 200);
        } else if (this.hasShipToPlace && cell) {
          this.holdTimer = setTimeout(() => {
            this.isHoldingNewShip = true;
            this.lockBodyScroll();
            this.currentDragTargetCell = cell;
            this.cellMouseEnter.emit(cell);
          }, 200);
        }
      }
    } else if (!this.isPlayerBoard && !this.isLocked) {
      const target = this.getElementAtTouch(touch.clientX, touch.clientY);
      const cellEl = target?.closest('.cell') as HTMLElement | null;
      if (cellEl && cellEl.dataset['x'] !== undefined && cellEl.dataset['y'] !== undefined) {
        const x = parseInt(cellEl.dataset['x']!, 10);
        const y = parseInt(cellEl.dataset['y']!, 10);
        const cell = this.cells.find((c) => c.key === `${x},${y}`);
        if (cell && !cell.isHit && !cell.isMiss && !cell.isSunk) {
          this.holdTimer = setTimeout(() => {
            this.isHoldingDrone = true;
            this.lockBodyScroll();
            this.currentDragTargetCell = cell;
            this.cellMouseEnter.emit(cell);
          }, 200);
        }
      }
    }
  }

  onTouchMove(event: TouchEvent): void {
    if (this.isLocked) return;
    const touch = event.touches[0];
    if (!touch) return;
    this.lastTouchX = touch.clientX;
    this.lastTouchY = touch.clientY;
    const dx = Math.abs(touch.clientX - this.touchStartX);
    const dy = Math.abs(touch.clientY - this.touchStartY);

    if (this.isHoldingShip) {
      if (event.cancelable) event.preventDefault();
      const target = this.getElementAtTouch(touch.clientX, touch.clientY);
      const cellEl = target?.closest('.cell') as HTMLElement | null;
      if (cellEl && cellEl.dataset['x'] !== undefined && cellEl.dataset['y'] !== undefined) {
        const x = parseInt(cellEl.dataset['x']!, 10);
        const y = parseInt(cellEl.dataset['y']!, 10);
        const cell = this.cells.find((c) => c.key === `${x},${y}`);
        if (cell) {
          this.currentDragTargetCell = cell;
          this.cellDragOver.emit({
            event: createSafeDragEvent('dragover'),
            cell,
          });
        }
      }
      return;
    }

    if (this.isHoldingNewShip) {
      if (event.cancelable) event.preventDefault();
      const target = this.getElementAtTouch(touch.clientX, touch.clientY);
      const cellEl = target?.closest('.cell') as HTMLElement | null;
      if (cellEl && cellEl.dataset['x'] !== undefined && cellEl.dataset['y'] !== undefined) {
        const x = parseInt(cellEl.dataset['x']!, 10);
        const y = parseInt(cellEl.dataset['y']!, 10);
        const cell = this.cells.find((c) => c.key === `${x},${y}`);
        if (cell && cell !== this.currentDragTargetCell) {
          this.currentDragTargetCell = cell;
          this.cellMouseEnter.emit(cell);
        }
      }
      return;
    }

    if (this.isHoldingDrone) {
      if (event.cancelable) event.preventDefault();
      const target = this.getElementAtTouch(touch.clientX, touch.clientY);
      const cellEl = target?.closest('.cell') as HTMLElement | null;
      if (cellEl && cellEl.dataset['x'] !== undefined && cellEl.dataset['y'] !== undefined) {
        const x = parseInt(cellEl.dataset['x']!, 10);
        const y = parseInt(cellEl.dataset['y']!, 10);
        const cell = this.cells.find((c) => c.key === `${x},${y}`);
        if (cell && cell !== this.currentDragTargetCell) {
          this.currentDragTargetCell = cell;
          this.cellMouseEnter.emit(cell);
        }
      }
      return;
    }

    if (dx > 8 || dy > 8) {
      if (this.holdTimer) clearTimeout(this.holdTimer);
      if (!this.isScrollGesture) {
        this.isScrollGesture = true;
        this.cellMouseLeave.emit();
      }
    }
  }

  onTouchEnd(): void {
    if (this.holdTimer) clearTimeout(this.holdTimer);
    this.unlockBodyScroll();

    if (this.isHoldingShip) {
      this.isHoldingShip = false;
      this.lastScrollEndTime = Date.now();

      const target = this.getElementAtTouch(this.lastTouchX, this.lastTouchY);
      const cellEl = target?.closest('.cell') as HTMLElement | null;
      let dropCell = this.currentDragTargetCell;
      if (cellEl && cellEl.dataset['x'] !== undefined && cellEl.dataset['y'] !== undefined) {
        const x = parseInt(cellEl.dataset['x']!, 10);
        const y = parseInt(cellEl.dataset['y']!, 10);
        dropCell = this.cells.find((c) => c.key === `${x},${y}`) ?? dropCell;
      }

      if (dropCell) {
        this.cellDrop.emit({
          event: createSafeDragEvent('drop'),
          cell: dropCell,
        });
      }
      this.currentDragTargetCell = null;
      return;
    }

    if (this.isHoldingNewShip) {
      this.isHoldingNewShip = false;
      this.lastScrollEndTime = Date.now();

      const target = this.getElementAtTouch(this.lastTouchX, this.lastTouchY);
      const cellEl = target?.closest('.cell') as HTMLElement | null;
      let dropCell = this.currentDragTargetCell;
      if (cellEl && cellEl.dataset['x'] !== undefined && cellEl.dataset['y'] !== undefined) {
        const x = parseInt(cellEl.dataset['x']!, 10);
        const y = parseInt(cellEl.dataset['y']!, 10);
        dropCell = this.cells.find((c) => c.key === `${x},${y}`) ?? dropCell;
      }

      if (dropCell) {
        if (dropCell.isPlacementValid) {
          this.cellClick.emit(dropCell);
        } else {
          this.cellMouseEnter.emit(dropCell);
        }
      }
      this.currentDragTargetCell = null;
      return;
    }

    if (this.isHoldingDrone) {
      this.isHoldingDrone = false;
      this.lastScrollEndTime = Date.now();

      const target = this.getElementAtTouch(this.lastTouchX, this.lastTouchY);
      const cellEl = target?.closest('.cell') as HTMLElement | null;
      let targetCell = this.currentDragTargetCell;
      if (cellEl && cellEl.dataset['x'] !== undefined && cellEl.dataset['y'] !== undefined) {
        const x = parseInt(cellEl.dataset['x']!, 10);
        const y = parseInt(cellEl.dataset['y']!, 10);
        targetCell = this.cells.find((c) => c.key === `${x},${y}`) ?? targetCell;
      }

      if (targetCell) {
        this.cellMouseEnter.emit(targetCell);
      }
      this.currentDragTargetCell = null;
      return;
    }

    if (this.isScrollGesture) {
      this.lastScrollEndTime = Date.now();
      this.cellMouseLeave.emit();
    }
    this.isScrollGesture = false;
    this.currentDragTargetCell = null;
  }

  onTouchCancel(): void {
    if (this.holdTimer) clearTimeout(this.holdTimer);
    this.unlockBodyScroll();
    if (this.isHoldingShip || this.isHoldingNewShip || this.isHoldingDrone) {
      this.isHoldingShip = false;
      this.isHoldingNewShip = false;
      this.isHoldingDrone = false;
      this.cellMouseLeave.emit();
    }
    if (this.isScrollGesture) {
      this.lastScrollEndTime = Date.now();
    }
    this.isScrollGesture = false;
    this.currentDragTargetCell = null;
    this.cellMouseLeave.emit();
  }

  private getElementAtTouch(x: number, y: number): HTMLElement | null {
    if (typeof document !== 'undefined' && typeof document.elementFromPoint === 'function') {
      return document.elementFromPoint(x, y) as HTMLElement | null;
    }
    return null;
  }
}
