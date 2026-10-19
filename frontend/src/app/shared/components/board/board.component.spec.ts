import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { BoardComponent } from './board.component';
import { BoardCell } from '@core/models/game.model';

describe('BoardComponent', () => {
  let component: BoardComponent;
  let fixture: ComponentFixture<BoardComponent>;

  const createMockCells = (): BoardCell[] => {
    const cells: BoardCell[] = [];
    for (let y = 0; y < 10; y++) {
      for (let x = 0; x < 10; x++) {
        cells.push({ x, y, key: `${x},${y}` });
      }
    }
    return cells;
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BoardComponent],
      providers: [provideZonelessChangeDetection()],
    }).compileComponents();

    fixture = TestBed.createComponent(BoardComponent);
    component = fixture.componentInstance;
    component.cells = createMockCells();
    fixture.detectChanges();
  });

  it('should create the board component', () => {
    expect(component).toBeTruthy();
  });

  it('should render 100 grid cells', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const cellElements = compiled.querySelectorAll('.board-matrix .cell');
    expect(cellElements.length).toBe(100);
  });

  it('should emit cellClick when a cell is clicked', () => {
    let clickedCell: BoardCell | null = null;
    component.cellClick.subscribe((cell) => (clickedCell = cell));

    const targetCell = component.cells[0];
    component.onCellClick(targetCell);

    expect(clickedCell).toEqual(targetCell);
  });

  it('should emit cellDblClick when a cell is double clicked', () => {
    let dblClickedCell: BoardCell | null = null;
    component.cellDblClick.subscribe((cell) => (dblClickedCell = cell));

    const targetCell = component.cells[5];
    component.onCellDblClick(targetCell);

    expect(dblClickedCell).toEqual(targetCell);
  });

  it('should NOT emit clicks when isLocked is true', () => {
    component.isLocked = true;
    let clicked = false;
    component.cellClick.subscribe(() => (clicked = true));

    component.onCellClick(component.cells[0]);
    expect(clicked).toBe(false);
  });

  it('should render ship class and movable class when appropriate', () => {
    const updatedCells = component.cells.map((c, i) =>
      i === 0 ? { ...c, hasShip: true, shipId: 101 } : c,
    );
    fixture.componentRef.setInput('cells', updatedCells);
    fixture.componentRef.setInput('isPlayerBoard', true);
    fixture.componentRef.setInput('isLocked', false);
    fixture.componentRef.setInput('isReady', false);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const firstCell = compiled.querySelector('.board-matrix .cell') as HTMLElement;
    expect(firstCell.classList.contains('ship')).toBe(true);
    expect(firstCell.classList.contains('movable')).toBe(true);
  });

  it('should emit cellMouseLeave when scrolling over cells via touchmove and suppress accidental clicks', () => {
    let leaveEmitted = false;
    component.cellMouseLeave.subscribe(() => (leaveEmitted = true));

    let clickEmitted = false;
    component.cellClick.subscribe(() => (clickEmitted = true));

    component.onTouchStart({
      touches: [{ clientX: 10, clientY: 10 } as Touch],
    } as unknown as TouchEvent);

    component.onTouchMove({
      touches: [{ clientX: 10, clientY: 60 } as Touch],
    } as unknown as TouchEvent);

    expect(leaveEmitted).toBe(true);

    component.onTouchEnd();

    component.onCellClick(component.cells[0]);
    expect(clickEmitted).toBe(false);
  });

  it('should emit cellMouseLeave when onWrapperClick is clicked outside cells', () => {
    let leaveEmitted = false;
    component.cellMouseLeave.subscribe(() => (leaveEmitted = true));

    const compiled = fixture.nativeElement as HTMLElement;
    const headerEl = compiled.querySelector('.board-header') as HTMLElement;
    expect(headerEl).toBeTruthy();

    component.onWrapperClick({ target: headerEl } as unknown as MouseEvent);
    expect(leaveEmitted).toBe(true);
  });

  it('should not emit cellMouseLeave or allow interaction on touch when isLocked is true', () => {
    component.isLocked = true;
    let leaveEmitted = false;
    component.cellMouseLeave.subscribe(() => (leaveEmitted = true));

    component.onTouchStart({
      touches: [{ clientX: 10, clientY: 10 } as Touch],
    } as unknown as TouchEvent);
    component.onTouchMove({
      touches: [{ clientX: 50, clientY: 50 } as Touch],
    } as unknown as TouchEvent);

    expect(leaveEmitted).toBe(false);
  });

  it('should initiate drag on ship cell after snappy 200ms hold', () => {
    vi.useFakeTimers();
    let dragStarted = false;
    component.cellDragStart.subscribe(() => (dragStarted = true));

    const updatedCells = component.cells.map((c, i) =>
      i === 0 ? { ...c, hasShip: true, shipId: 99 } : c,
    );
    fixture.componentRef.setInput('cells', updatedCells);
    fixture.componentRef.setInput('isPlayerBoard', true);
    fixture.componentRef.setInput('isLocked', false);
    fixture.componentRef.setInput('isReady', false);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const firstCell = compiled.querySelector(
      '.board-matrix .cell[data-x="0"][data-y="0"]',
    ) as HTMLElement;
    const originalElementFromPoint = document.elementFromPoint;
    document.elementFromPoint = () => firstCell;

    component.onTouchStart({
      touches: [{ clientX: 10, clientY: 10 } as Touch],
    } as unknown as TouchEvent);

    expect(dragStarted).toBe(false);

    vi.advanceTimersByTime(200);

    expect(dragStarted).toBe(true);

    document.elementFromPoint = originalElementFromPoint;
    vi.useRealTimers();
  });

  it('should initiate hover preview when holding on water cell if hasShipToPlace is true', () => {
    vi.useFakeTimers();
    let hoveredCell: BoardCell | null = null;
    component.cellMouseEnter.subscribe((cell) => (hoveredCell = cell));

    fixture.componentRef.setInput('isPlayerBoard', true);
    fixture.componentRef.setInput('isLocked', false);
    fixture.componentRef.setInput('isReady', false);
    fixture.componentRef.setInput('hasShipToPlace', true);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const targetCellEl = compiled.querySelector(
      '.board-matrix .cell[data-x="2"][data-y="3"]',
    ) as HTMLElement;
    const originalElementFromPoint = document.elementFromPoint;
    document.elementFromPoint = () => targetCellEl;

    component.onTouchStart({
      touches: [{ clientX: 10, clientY: 10 } as Touch],
    } as unknown as TouchEvent);

    expect(hoveredCell).toBeNull();

    vi.advanceTimersByTime(200);

    expect(hoveredCell).toBeTruthy();
    expect((hoveredCell as BoardCell | null)?.key).toBe('2,3');

    document.elementFromPoint = originalElementFromPoint;
    vi.useRealTimers();
  });

  it('should initiate drone preview when holding on enemy cell on player turn', () => {
    vi.useFakeTimers();
    let hoveredCell: BoardCell | null = null;
    component.cellMouseEnter.subscribe((cell) => (hoveredCell = cell));

    fixture.componentRef.setInput('isPlayerBoard', false);
    fixture.componentRef.setInput('isLocked', false);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const targetCellEl = compiled.querySelector(
      '.board-matrix .cell[data-x="4"][data-y="4"]',
    ) as HTMLElement;
    const originalElementFromPoint = document.elementFromPoint;
    document.elementFromPoint = () => targetCellEl;

    component.onTouchStart({
      touches: [{ clientX: 20, clientY: 20 } as Touch],
    } as unknown as TouchEvent);

    expect(hoveredCell).toBeNull();

    vi.advanceTimersByTime(200);

    expect(hoveredCell).toBeTruthy();
    expect((hoveredCell as BoardCell | null)?.key).toBe('4,4');

    document.elementFromPoint = originalElementFromPoint;
    vi.useRealTimers();
  });

  it('should emit cellDragEnd and clear holding state on onDragEnd', () => {
    component.isHoldingShip = true;
    let dragEndEmitted = false;
    component.cellDragEnd.subscribe(() => (dragEndEmitted = true));

    const mockEvent = new CustomEvent('dragend') as unknown as DragEvent;
    component.onDragEnd(mockEvent, component.cells[0]);

    expect(dragEndEmitted).toBe(true);
    expect(component.isHoldingShip).toBe(false);
  });

  it('should emit cellMouseLeave on onMatrixMouseLeave', () => {
    let leaveEmitted = false;
    component.cellMouseLeave.subscribe(() => (leaveEmitted = true));

    component.onMatrixMouseLeave();
    expect(leaveEmitted).toBe(true);
  });

  it('should clean up holding state on window pointer up', () => {
    component.isHoldingShip = true;
    let leaveEmitted = false;
    component.cellMouseLeave.subscribe(() => (leaveEmitted = true));

    component.onWindowPointerUp();

    expect(component.isHoldingShip).toBe(false);
    expect(leaveEmitted).toBe(true);
  });
});
