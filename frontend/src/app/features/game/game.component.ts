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
import { GameState } from '@core/state/game.state';
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
  readonly gameState = inject(GameState);
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

  readonly inviteUrl = computed<string>(() => buildInviteUrl(this.gameState.inviteToken()));

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
          this.gameState.initMatch(token);
        }
      });
    });

    effect(() => {
      const status = this.gameState.matchStatus();
      untracked(() => {
        if (status === 'FINISHED') {
          this.showResultModal.set(true);
        } else {
          this.showResultModal.set(false);
        }
      });
    });

    effect(() => {
      const newToken = this.gameState.rematchAgreedToken();
      untracked(() => {
        if (newToken) {
          this.toast.success('Rematch accepted! Starting new game.');
          this.router.navigate(['/game', newToken]);
        }
      });
    });

    effect(() => {
      const status = this.gameState.matchStatus();
      untracked(() => {
        if (status !== 'WAITING') {
          this.showInviteModal.set(false);
        }
      });
    });
  }

  onPlacementCellClick(cell: BoardCell): void {
    if (this.gameState.isPlayerReady()) return;

    const draggedId = this.gameState.draggedShipId();
    if (draggedId != null) {
      if (this.gameState.canMoveShipTo(draggedId, cell.x, cell.y)) {
        this.gameState.moveShipTo(draggedId, cell.x, cell.y);
      }
      this.gameState.resetPlacementInteraction();
      return;
    }

    if (cell.hasShip && cell.shipId) {
      if (this.gameState.selectedPlacedShipId() === cell.shipId) {
        this.gameState.rotateShip(cell.shipId);
        this.gameState.resetPlacementInteraction();
      } else {
        this.gameState.selectPlacedShip(cell.shipId);
        this.gameState.setHoveredCell(null);
      }
      return;
    }

    const selectedPlacedId = this.gameState.selectedPlacedShipId();
    if (selectedPlacedId != null) {
      if (this.gameState.canMoveShipTo(selectedPlacedId, cell.x, cell.y)) {
        this.gameState.moveShipTo(selectedPlacedId, cell.x, cell.y);
        this.gameState.resetPlacementInteraction();
        return;
      } else {
        this.gameState.resetPlacementInteraction();
        return;
      }
    }

    if (this.gameState.selectedShipToPlace()) {
      const hovered = this.gameState.hoveredCell();
      const isSameCell = hovered && hovered.x === cell.x && hovered.y === cell.y;

      if (isSameCell || cell.isPlacementValid) {
        if (cell.isPlacementInvalid && isSameCell) {
          this.gameState.setHoveredCell(null);
          return;
        }

        this.gameState.placeShipAt(hovered ? hovered.x : cell.x, hovered ? hovered.y : cell.y);
        return;
      }

      this.gameState.setHoveredCell({ x: cell.x, y: cell.y });
      return;
    }
  }

  onPlacementCellDblClick(cell: BoardCell): void {
    if (this.gameState.isPlayerReady()) return;
    if (cell.hasShip && cell.shipId) {
      this.gameState.rotateShip(cell.shipId);
      this.gameState.selectPlacedShip(null);
      this.gameState.setHoveredCell(null);
    }
  }

  onRotateClicked(): void {
    const selectedPlacedId = this.gameState.selectedPlacedShipId();
    if (selectedPlacedId != null) {
      this.gameState.rotateShip(selectedPlacedId);
      this.gameState.selectPlacedShip(null);
      this.gameState.setHoveredCell(null);
    } else {
      this.gameState.toggleOrientation();
    }
  }

  onCellMouseEnter(cell: BoardCell, isEnemy: boolean): void {
    if (isEnemy) {
      this.gameState.setHoveredEnemyCell({ x: cell.x, y: cell.y });
    } else {
      this.gameState.setHoveredCell({ x: cell.x, y: cell.y });
    }
  }

  onCellMouseLeave(isEnemy: boolean): void {
    if (isEnemy) {
      this.gameState.setHoveredEnemyCell(null);
    } else {
      this.gameState.setHoveredCell(null);
    }
  }

  onMainClick(event: Event): void {
    const target = event.target as HTMLElement;
    if (
      !target.closest('.cell') &&
      !target.closest('button') &&
      !target.closest('.cursor-pointer')
    ) {
      this.gameState.resetPlacementInteraction();
      this.gameState.setHoveredEnemyCell(null);
    }
  }

  onCellDragStart(event: DragEvent, cell: BoardCell): void {
    if (this.gameState.isPlayerReady()) return;
    if (cell.hasShip && cell.shipId) {
      this.gameState.setDraggedShipId(cell.shipId);
      if (event.dataTransfer) {
        event.dataTransfer.effectAllowed = 'move';
        event.dataTransfer.setData('text/plain', String(cell.shipId));
      }
    }
  }

  onCellDragOver(event: DragEvent, cell: BoardCell): void {
    if (this.gameState.isPlayerReady()) return;
    event.preventDefault();
    this.gameState.setDraggedOverKey(cell.key);
    this.gameState.setHoveredCell({ x: cell.x, y: cell.y });
  }

  onCellDragLeave(cell: BoardCell): void {
    this.gameState.setDraggedOverKey(null);
  }

  onCellDragEnd(): void {
    this.gameState.resetPlacementInteraction();
  }

  @HostListener('window:dragend')
  @HostListener('window:mouseup')
  onWindowPointerUp(): void {
    if (this.gameState.draggedShipId() != null) {
      this.gameState.resetPlacementInteraction();
    }
  }

  @HostListener('window:keydown.escape')
  onEscapePressed(): void {
    if (this.showResultModal()) {
      this.closeResultModal();
    } else if (this.showInviteModal()) {
      this.closeInviteModal();
    } else {
      this.gameState.resetPlacementInteraction();
    }
  }

  resetFleet(): void {
    this.gameState.reloadFleet();
    this.toast.info('Fleet placement reset.');
  }

  onCellDrop(event: DragEvent, cell: BoardCell): void {
    if (this.gameState.isPlayerReady()) return;
    event.preventDefault();

    const draggedShipId = this.gameState.draggedShipId();
    this.gameState.resetPlacementInteraction();

    if (draggedShipId != null) {
      if (this.gameState.canMoveShipTo(draggedShipId, cell.x, cell.y)) {
        this.gameState.moveShipTo(draggedShipId, cell.x, cell.y);
      }
      return;
    }

    const shipType = event.dataTransfer?.getData('application/x-ship-type') as ShipType;
    if (shipType) {
      this.gameState.placeShipAt(cell.x, cell.y, shipType);
    }
  }

  onTrayShipDragStart(event: DragEvent, type: ShipType): void {
    if (this.gameState.isPlayerReady()) return;
    this.gameState.selectedShipToPlace.set(type);
    if (event.dataTransfer) {
      event.dataTransfer.effectAllowed = 'copyMove';
      event.dataTransfer.setData('application/x-ship-type', type);
    }
  }

  selectShipType(type: ShipType): void {
    if (this.gameState.selectedShipToPlace() === type) {
      this.gameState.selectedShipToPlace.set(null);
      this.gameState.setHoveredCell(null);
    } else {
      this.gameState.selectedShipToPlace.set(type);
      this.gameState.setHoveredCell(null);
    }
  }

  onEnemyCellClick(cell: BoardCell): void {
    if (!this.gameState.isItMyTurn() || this.gameState.matchStatus() !== 'IN_PROGRESS') return;
    if (cell.isHit || cell.isMiss || cell.isSunk) return;

    const isDrone = this.gameState.selectedMoveType() !== 'STANDARD';
    if (isDrone) {
      const hovered = this.gameState.hoveredEnemyCell();
      const isAlreadyAiming = hovered && hovered.x === cell.x && hovered.y === cell.y;
      if (!isAlreadyAiming) {
        this.gameState.setHoveredEnemyCell({ x: cell.x, y: cell.y });
        return;
      }
    }

    this.gameState.fireAt(cell.x, cell.y);
  }

  selectWeapon(type: MoveType): void {
    if (this.gameState.selectedMoveType() === type && type !== 'STANDARD') {
      this.gameState.selectedMoveType.set('STANDARD');
      this.gameState.setHoveredEnemyCell(null);
    } else {
      this.gameState.selectedMoveType.set(type);
      this.gameState.setHoveredEnemyCell(null);
    }
  }

  async copyToken(): Promise<void> {
    const token = this.gameState.inviteToken();
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
    const token = this.gameState.inviteToken();
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
    const token = this.gameState.inviteToken();
    if (!token) return;

    this.myRematchRequested.set(true);
    this.matchService.rematch(token).subscribe({
      next: (res) => {
        if (res && res.inviteToken) {
          this.toast.success('Rematch accepted! Starting new game.');
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
    this.gameState.reset();
    this.router.navigate(['/lobby']);
  }

  ngOnDestroy(): void {
    this.gameState.clearSubscriptions();
  }
}
