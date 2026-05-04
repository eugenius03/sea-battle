import {state} from './state.js';
import {$} from './utils.js';
import {
    handleBoardShipDoubleClick,
    handleBoardShipDragEnd,
    handleBoardShipDragStart,
    handleDragOver,
    handleDrop
} from './dragDrop.js';
import {fireAttackDrone, fireSurveillanceDrone, handleEnemyCellClick} from './api.js';
import {clearDronePreview, showDronePreview} from './ui.js';

export function initBoards() {
    const playerBoard = $('playerBoard');
    const enemyBoard = $('enemyBoard');
    if (!playerBoard || !enemyBoard) return;

    playerBoard.innerHTML = '';
    enemyBoard.innerHTML = '';

    for (let y = 0; y < 10; y++) {
        for (let x = 0; x < 10; x++) {
            playerBoard.appendChild(createCell(x, y, true));
            enemyBoard.appendChild(createCell(x, y, false));
        }
    }
}

export function createCell(x, y, isPlayerBoard) {
    const cell = document.createElement('div');
    cell.className = 'cell';
    cell.dataset.x = x;
    cell.dataset.y = y;

    if (isPlayerBoard) {
        cell.addEventListener('dragover', handleDragOver);
        cell.addEventListener('drop', handleDrop);
        cell.addEventListener('dragleave', (e) => e.target.classList.remove('drag-over'));
        cell.addEventListener('dragstart', handleBoardShipDragStart);
        cell.addEventListener('dragend', handleBoardShipDragEnd);
        cell.addEventListener('dblclick', handleBoardShipDoubleClick);
    } else {
        cell.addEventListener('click', handleEnemyCellClick);
        cell.addEventListener('dragover', (e) => {
            if (!state.draggedDroneType) return;
            e.preventDefault();
            showDronePreview(Number.parseInt(e.target.dataset.x), Number.parseInt(e.target.dataset.y));
        });
        cell.addEventListener('drop', (e) => {
            if (!state.draggedDroneType) return;
            e.preventDefault();
            clearDronePreview();
            const px = Number.parseInt(e.target.dataset.x);
            const py = Number.parseInt(e.target.dataset.y);
            if (state.draggedDroneType === 'ATTACK_DRONE') {
                fireAttackDrone(px, py);
            } else {
                fireSurveillanceDrone(px, py);
            }
        });
    }
    return cell;
}

export function visualizeShipOnBoard(startX, startY, size, orientation, shipId) {
    const playerBoard = $('playerBoard');
    if (!playerBoard) return;

    for (let i = 0; i < size; i++) {
        const x = orientation === 'HORIZONTAL' ? startX + i : startX;
        const y = orientation === 'VERTICAL' ? startY + i : startY;
        const cell = playerBoard.querySelector(`[data-x="${x}"][data-y="${y}"]`);
        if (cell) {
            cell.classList.add('ship');
            if (shipId != null) cell.dataset.shipId = String(shipId);
            if (!state.isReady) {
                cell.draggable = true;
                cell.classList.add('movable');
            }
        }
    }
}

export function clearShipFromBoard(shipId) {
    document.querySelectorAll(`#playerBoard .cell[data-ship-id="${shipId}"]`).forEach(cell => {
        cell.classList.remove('ship', 'movable');
        cell.removeAttribute('data-ship-id');
        cell.draggable = false;
    });
}

export function setPlayerBoardDraggable(enabled) {
    document.querySelectorAll('#playerBoard .cell.ship').forEach(cell => {
        cell.draggable = enabled;
        cell.classList.toggle('movable', enabled);
    });
}
