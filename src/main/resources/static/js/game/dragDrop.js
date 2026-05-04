import {shipTypes, state, unplacedShipOrientations} from './state.js';
import {$, showMessage} from './utils.js';
import {moveShip, placeShip} from './api.js';

export function initShips() {
    const container = $('shipsContainer');
    if (!container) return;

    container.innerHTML = '';
    unplacedShipOrientations.clear();

    shipTypes.forEach(shipType => {
        for (let i = 0; i < shipType.count; i++) {
            const shipKey = `${shipType.type}_${i}`;
            unplacedShipOrientations.set(shipKey, 'HORIZONTAL');

            const shipItem = document.createElement('div');
            shipItem.className = 'ship-item';
            shipItem.dataset.shipKey = shipKey;

            const shipVisual = document.createElement('div');
            shipVisual.className = 'ship-visual';
            shipVisual.draggable = true;
            shipVisual.dataset.type = shipType.type;
            shipVisual.dataset.size = shipType.size;
            shipVisual.dataset.index = i;
            shipVisual.dataset.orientation = 'HORIZONTAL';

            for (let j = 0; j < shipType.size; j++) {
                const segment = document.createElement('div');
                segment.className = 'ship-segment';
                shipVisual.appendChild(segment);
            }

            shipVisual.addEventListener('dragstart', (e) => {
                if (state.isReady) return;
                const shipKey = `${e.target.dataset.type}_${e.target.dataset.index}`;
                state.selectedShip = {
                    fromBoard: false,
                    type: e.target.dataset.type,
                    size: Number.parseInt(e.target.dataset.size),
                    index: e.target.dataset.index,
                    element: e.target,
                    orientation: unplacedShipOrientations.get(shipKey) || 'HORIZONTAL'
                };
                e.dataTransfer.effectAllowed = 'move';
            });

            shipVisual.addEventListener('dragend', () => {
                state.selectedShip = null;
                document.querySelectorAll('.cell.drag-over').forEach(cell => cell.classList.remove('drag-over'));
            });

            const shipInfo = document.createElement('div');
            shipInfo.className = 'ship-info';
            shipInfo.textContent = `${shipType.label} (${shipType.size})`;

            shipItem.appendChild(shipVisual);
            shipItem.appendChild(shipInfo);
            container.appendChild(shipItem);
        }
    });
}

export function rotateOrientation() {
    if (state.isReady) return;
    state.currentOrientation = state.currentOrientation === 'HORIZONTAL' ? 'VERTICAL' : 'HORIZONTAL';

    document.querySelectorAll('.ship-visual').forEach(ship => {
        if (ship.classList.contains('placed')) return;
        const shipKey = `${ship.dataset.type}_${ship.dataset.index}`;
        ship.dataset.orientation = state.currentOrientation;
        ship.style.flexDirection = state.currentOrientation === 'VERTICAL' ? 'column' : 'row';
        unplacedShipOrientations.set(shipKey, state.currentOrientation);
    });

    showMessage(`All ships rotated: ${state.currentOrientation}`, 'info', 1500);
}

export function handleDragOver(e) {
    if (!state.selectedShip || state.isReady) return;
    e.preventDefault();
    e.target.classList.add('drag-over');
}

export function handleDrop(e) {
    e.preventDefault();
    e.target.classList.remove('drag-over');
    if (!state.selectedShip || state.isReady) return;

    const x = Number.parseInt(e.target.dataset.x);
    const y = Number.parseInt(e.target.dataset.y);
    if (state.selectedShip.fromBoard) {
        moveShip(state.selectedShip, x, y);
    } else {
        placeShip(state.selectedShip, x, y);
    }
}

export function handleBoardShipDragStart(e) {
    if (state.isReady) return;
    const shipId = e.target.dataset.shipId;
    if (!shipId) {
        e.preventDefault();
        return;
    }

    const shipData = state.shipRegistry.get(String(shipId));
    if (!shipData) {
        e.preventDefault();
        return;
    }

    state.selectedShip = {
        fromBoard: true,
        id: String(shipId),
        size: shipData.size,
        orientation: shipData.orientation
    };

    state.currentOrientation = shipData.orientation;

    document.querySelectorAll('.ship-visual').forEach(ship => {
        ship.style.flexDirection = state.currentOrientation === 'VERTICAL' ? 'column' : 'row';
    });

    e.dataTransfer.effectAllowed = 'move';
    e.dataTransfer.setData('text/plain', String(shipId));
}

export function handleBoardShipDragEnd() {
    state.selectedShip = null;
    document.querySelectorAll('.cell.drag-over').forEach(cell => cell.classList.remove('drag-over'));
}

export async function handleBoardShipDoubleClick(e) {
    if (state.isReady) return;

    const shipId = e.target.dataset.shipId;
    if (!shipId) return;

    const shipData = state.shipRegistry.get(String(shipId));
    if (!shipData) return;

    e.preventDefault();

    await moveShip({
        fromBoard: true,
        id: String(shipId),
        size: shipData.size,
        orientation: shipData.orientation === 'HORIZONTAL' ? 'VERTICAL' : 'HORIZONTAL'
    }, shipData.startX, shipData.startY);
}
