import {
  Component,
  computed,
  effect,
  HostListener,
  inject,
  input,
  OnDestroy,
  signal,
  untracked,
} from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { BoardViewState } from '@core/state/board-view.state';
import { GameFlowService } from '@core/state/game-flow.service';
import { MoveState } from '@core/state/move.state';
import { MatchState } from '@core/state/match.state';
import { ShipState } from '@core/state/ship.state';
import { MatchService } from '@core/services/match.service';
import { ToastService } from '@shared/components/toast/toast.service';
import { BoardCell, SHIP_DEFINITIONS, ShipType } from '@core/models/game.model';
import { MoveType } from '@core/models/move.model';
import { BoardComponent } from '@shared/components/board/board.component';
import { ButtonComponent } from '@shared/components/button/button.component';
import { IconComponent } from '@shared/components/icon/icon.component';
import { ModalComponent } from '@shared/components/modal/modal.component';
import { copyToClipboard } from '@shared/utils/clipboard.util';
import { buildInviteUrl } from '@shared/utils/invite.util';
import { generateQrCodeSvg } from '@shared/utils/qr-code.util';
import { NgClass } from '@angular/common';

@Component({
  selector: 'app-game',
  standalone: true,
  imports: [NgClass, BoardComponent, ButtonComponent, IconComponent, ModalComponent],
  templateUrl: './game.component.html',
  styleUrl: './game.component.scss',
})
export class GameComponent implements OnDestroy {
  readonly inviteToken = input<string>('');
  readonly gameFlow = inject(GameFlowService);
  readonly moveState = inject(MoveState);
  readonly boardView = inject(BoardViewState);
  readonly matchState = inject(MatchState);
  readonly shipState = inject(ShipState);
  private readonly matchService = inject(MatchService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly sanitizer = inject(DomSanitizer);

  private lastLoadedToken: string | null = null;
  readonly myRematchRequested = signal<boolean>(false);
  readonly showResultModal = signal<boolean>(false);
  readonly showInviteModal = signal<boolean>(false);
  readonly linkCopied = signal<boolean>(false);
  readonly codeCopied = signal<boolean>(false);

  readonly inviteUrl = computed<string>(() => buildInviteUrl(this.matchState.inviteToken()));

  readonly qrCodeSvg = computed<SafeHtml>(() => {
    const url = this.inviteUrl();
    if (!url) return '';
    const svg = generateQrCodeSvg(url);
    return this.sanitizer.bypassSecurityTrustHtml(svg);
  });

  readonly shipEntries = (Object.keys(SHIP_DEFINITIONS) as ShipType[]).map((type) => ({
    type,
    def: SHIP_DEFINITIONS[type],
  }));

  constructor() {
    effect(() => {
      const token = this.inviteToken();
      untracked(() => {
        if (token && token !== this.lastLoadedToken) {
          this.lastLoadedToken = token;
          this.myRematchRequested.set(false);
          this.showResultModal.set(false);
          this.gameFlow.initMatch(token);
        }
      });
    });

    effect(() => {
      const status = this.matchState.matchStatus();
      untracked(() => {
        if (status === 'FINISHED') {
          this.showResultModal.set(true);
        } else {
          this.showResultModal.set(false);
        }
      });
    });

    effect(() => {
      const newToken = this.matchState.rematchAgreedToken();
      untracked(() => {
        if (newToken) {
          this.toast.success('Rematch accepted! Starting new game.');
          this.router.navigate(['/game', newToken]);
        }
      });
    });

    effect(() => {
      const status = this.matchState.matchStatus();
      untracked(() => {
        if (status !== 'WAITING') {
          this.showInviteModal.set(false);
        }
      });
    });
  }

  onPlacementCellClick(cell: BoardCell): void {
    if (this.matchState.isPlayerReady()) return;
    if (this.tryDropDraggedShip(cell)) return;
    if (this.tryClickPlacedShip(cell)) return;
    if (this.tryMoveSelectedShip(cell)) return;
    this.placeSelectedShip(cell);
  }

  private tryDropDraggedShip(cell: BoardCell): boolean {
    const draggedId = this.shipState.draggedShipId();
    if (draggedId == null) return false;
    if (this.shipState.canMoveShipTo(draggedId, cell.x, cell.y)) {
      this.shipState.moveShipTo(draggedId, cell.x, cell.y);
    }
    this.shipState.resetPlacementInteraction();
    return true;
  }

  private tryClickPlacedShip(cell: BoardCell): boolean {
    if (!cell.hasShip || !cell.shipId) return false;
    if (this.shipState.selectedPlacedShipId() === cell.shipId) {
      this.shipState.rotateShip(cell.shipId);
      this.shipState.resetPlacementInteraction();
    } else {
      this.shipState.selectPlacedShip(cell.shipId);
      this.shipState.setHoveredCell(null);
    }
    return true;
  }

  private tryMoveSelectedShip(cell: BoardCell): boolean {
    const selectedPlacedId = this.shipState.selectedPlacedShipId();
    if (selectedPlacedId == null) return false;
    if (this.shipState.canMoveShipTo(selectedPlacedId, cell.x, cell.y)) {
      this.shipState.moveShipTo(selectedPlacedId, cell.x, cell.y);
    }
    this.shipState.resetPlacementInteraction();
    return true;
  }

  private placeSelectedShip(cell: BoardCell): void {
    if (!this.shipState.selectedShipToPlace()) return;

    const hovered = this.shipState.hoveredCell();
    const isSameCell = hovered?.x === cell.x && hovered.y === cell.y;

    if (!isSameCell && !cell.isPlacementValid) {
      this.shipState.setHoveredCell({ x: cell.x, y: cell.y });
    } else if (cell.isPlacementInvalid && isSameCell) {
      this.shipState.setHoveredCell(null);
    } else {
      this.shipState.placeShipAt(hovered?.x ?? cell.x, hovered?.y ?? cell.y);
    }
  }

  onPlacementCellDblClick(cell: BoardCell): void {
    if (this.matchState.isPlayerReady()) return;
    if (cell.hasShip && cell.shipId) {
      this.shipState.rotateShip(cell.shipId);
      this.shipState.selectPlacedShip(null);
      this.shipState.setHoveredCell(null);
    }
  }

  onRotateClicked(): void {
    const selectedPlacedId = this.shipState.selectedPlacedShipId();
    if (selectedPlacedId != null) {
      this.shipState.rotateShip(selectedPlacedId);
      this.shipState.selectPlacedShip(null);
      this.shipState.setHoveredCell(null);
    } else {
      this.shipState.toggleOrientation();
    }
  }

  onOwnCellMouseEnter(cell: BoardCell): void {
    this.shipState.setHoveredCell({ x: cell.x, y: cell.y });
  }

  onOwnCellMouseLeave(): void {
    this.shipState.setHoveredCell(null);
  }

  onEnemyCellMouseEnter(cell: BoardCell): void {
    this.moveState.setHoveredEnemyCell({ x: cell.x, y: cell.y });
  }

  onEnemyCellMouseLeave(): void {
    this.moveState.setHoveredEnemyCell(null);
  }

  onMainClick(event: Event): void {
    const target = event.target as HTMLElement;
    if (
      !target.closest('.cell') &&
      !target.closest('button') &&
      !target.closest('.cursor-pointer')
    ) {
      this.shipState.resetPlacementInteraction();
      this.moveState.setHoveredEnemyCell(null);
    }
  }

  onCellDragStart(event: DragEvent, cell: BoardCell): void {
    if (this.matchState.isPlayerReady()) return;
    if (cell.hasShip && cell.shipId) {
      this.shipState.setDraggedShipId(cell.shipId);
      if (event.dataTransfer) {
        event.dataTransfer.effectAllowed = 'move';
        event.dataTransfer.setData('text/plain', String(cell.shipId));
      }
    }
  }

  onCellDragOver(event: DragEvent, cell: BoardCell): void {
    if (this.matchState.isPlayerReady()) return;
    event.preventDefault();
    this.shipState.setDraggedOverKey(cell.key);
    this.shipState.setHoveredCell({ x: cell.x, y: cell.y });
  }

  onCellDragLeave(cell: BoardCell): void {
    this.shipState.setDraggedOverKey(null);
  }

  onCellDragEnd(): void {
    this.shipState.resetPlacementInteraction();
  }

  @HostListener('window:dragend')
  @HostListener('window:mouseup')
  onWindowPointerUp(): void {
    if (this.shipState.draggedShipId() != null) {
      this.shipState.resetPlacementInteraction();
    }
  }

  @HostListener('window:keydown.escape')
  onEscapePressed(): void {
    if (this.showResultModal()) {
      this.closeResultModal();
    } else if (this.showInviteModal()) {
      this.closeInviteModal();
    } else {
      this.shipState.resetPlacementInteraction();
    }
  }

  resetFleet(): void {
    this.shipState.reloadFleet();
    this.toast.info('Fleet placement reset.');
  }

  onCellDrop(event: DragEvent, cell: BoardCell): void {
    if (this.matchState.isPlayerReady()) return;
    event.preventDefault();

    const draggedShipId = this.shipState.draggedShipId();
    this.shipState.resetPlacementInteraction();

    if (draggedShipId != null) {
      if (this.shipState.canMoveShipTo(draggedShipId, cell.x, cell.y)) {
        this.shipState.moveShipTo(draggedShipId, cell.x, cell.y);
      }
      return;
    }

    const shipType = event.dataTransfer?.getData('application/x-ship-type') as ShipType;
    if (shipType) {
      this.shipState.placeShipAt(cell.x, cell.y, shipType);
    }
  }

  onTrayShipDragStart(event: DragEvent, type: ShipType): void {
    if (this.matchState.isPlayerReady()) return;
    this.shipState.selectedShipToPlace.set(type);
    if (event.dataTransfer) {
      event.dataTransfer.effectAllowed = 'copyMove';
      event.dataTransfer.setData('application/x-ship-type', type);
    }
  }

  selectShipType(type: ShipType): void {
    if (this.shipState.selectedShipToPlace() === type) {
      this.shipState.selectedShipToPlace.set(null);
      this.shipState.setHoveredCell(null);
    } else {
      this.shipState.selectedShipToPlace.set(type);
      this.shipState.setHoveredCell(null);
    }
  }

  onEnemyCellClick(cell: BoardCell): void {
    if (
      !this.matchState.isItMyTurn() ||
      this.moveState.isFiring() ||
      this.matchState.matchStatus() !== 'IN_PROGRESS'
    ) {
      return;
    }
    if (cell.isHit || cell.isMiss || cell.isSunk) return;

    const isDrone = this.moveState.selectedMoveType() !== 'STANDARD';
    if (isDrone) {
      const hovered = this.moveState.hoveredEnemyCell();
      const isAlreadyAiming = hovered?.x === cell.x && hovered.y === cell.y;
      if (!isAlreadyAiming) {
        this.moveState.setHoveredEnemyCell({ x: cell.x, y: cell.y });
        return;
      }
    }

    this.moveState.fireAt(cell.x, cell.y);
  }

  selectWeapon(type: MoveType): void {
    if (this.moveState.selectedMoveType() === type && type !== 'STANDARD') {
      this.moveState.selectedMoveType.set('STANDARD');
      this.moveState.setHoveredEnemyCell(null);
    } else {
      this.moveState.selectedMoveType.set(type);
      this.moveState.setHoveredEnemyCell(null);
    }
  }

  async copyToken(): Promise<void> {
    const token = this.matchState.inviteToken();
    if (token) {
      const ok = await copyToClipboard(token);
      if (ok) {
        this.toast.info('Room code copied to clipboard.');
      } else {
        this.toast.error('Failed to copy room code.');
      }
    }
  }

  openInviteModal(): void {
    this.showInviteModal.set(true);
  }

  closeInviteModal(): void {
    this.showInviteModal.set(false);
  }

  async copyInviteLink(): Promise<void> {
    const url = this.inviteUrl();
    if (!url) return;
    const ok = await copyToClipboard(url);
    if (ok) {
      this.linkCopied.set(true);
      this.toast.success('Invite link copied to clipboard.');
      setTimeout(() => this.linkCopied.set(false), 2000);
    } else {
      this.toast.error('Failed to copy invite link.');
    }
  }

  async copyRoomCode(): Promise<void> {
    const token = this.matchState.inviteToken();
    if (!token) return;
    const ok = await copyToClipboard(token);
    if (ok) {
      this.codeCopied.set(true);
      this.toast.info('Room code copied to clipboard.');
      setTimeout(() => this.codeCopied.set(false), 2000);
    } else {
      this.toast.error('Failed to copy room code.');
    }
  }

  closeResultModal(): void {
    this.showResultModal.set(false);
  }

  requestRematch(): void {
    const token = this.matchState.inviteToken();
    if (!token) return;

    this.myRematchRequested.set(true);
    this.matchService.rematch(token).subscribe({
      next: (res) => {
        if (res?.inviteToken) {
          // The REMATCH_AGREED message (shown as a toast) reaches both players, so only navigate here.
          this.router.navigate(['/game', res.inviteToken]);
        } else {
          this.toast.info('Rematch requested.');
        }
      },
      error: (err) => {
        this.myRematchRequested.set(false);
        this.toast.error(err?.error?.message || 'Failed to request rematch.');
      },
    });
  }

  exitMatch(): void {
    this.gameFlow.reset();
    this.router.navigate(['/lobby']);
  }

  ngOnDestroy(): void {
    this.gameFlow.clearSubscriptions();
  }
}
