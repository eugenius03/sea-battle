import {setState, SHIP_SIZES, shipTypes, state} from './state.js';
import {$, showMessage} from './utils.js';

export function lockEnemyBoard() {
    document.querySelectorAll('#enemyBoard .cell').forEach(c => {
        c.classList.remove('drone-preview');
        c.classList.add('inactive');
    });
}

export function unlockEnemyBoard() {
    document.querySelectorAll('#enemyBoard .cell').forEach(c => {
        if (!c.classList.contains('hit') && !c.classList.contains('miss')) {
            c.classList.remove('inactive');
        }
    });
}

export function applyTurn(isItMyTurn) {
    if (isItMyTurn == null) return;
    if (isItMyTurn) {
        unlockEnemyBoard();
    } else {
        lockEnemyBoard();
    }
}

export function lockPlayerBoard() {
    document.querySelectorAll('#playerBoard .cell').forEach(cell => cell.classList.add('inactive'));
}

export function clearDronePreview() {
    document.querySelectorAll('#enemyBoard .cell.drone-preview').forEach(c => c.classList.remove('drone-preview'));
}

export function showDronePreview(cx, cy) {
    clearDronePreview();
    for (let dx = -1; dx <= 1; dx++) {
        for (let dy = -1; dy <= 1; dy++) {
            const nx = cx + dx;
            const ny = cy + dy;
            if (nx < 0 || nx > 9 || ny < 0 || ny > 9) continue;
            const cell = document.querySelector(`#enemyBoard [data-x="${nx}"][data-y="${ny}"]`);
            if (cell) cell.classList.add('drone-preview');
        }
    }
}

export function markEnemyCell(x, y, result) {
    result = String(result);
    const enemyBoard = $('enemyBoard');
    if (!enemyBoard) return;
    const cell = enemyBoard.querySelector(`[data-x="${x}"][data-y="${y}"]`);
    if (!cell) return;
    cell.classList.remove('drone-preview');
    if (result === 'MISS') cell.classList.add('miss');
    else if (result === 'SURVEILLANCE') cell.classList.add('surveillance');
    else {
        cell.classList.remove('surveillance');
        cell.classList.add('hit');
    }
    if (result !== 'SURVEILLANCE') cell.classList.add('inactive');
}

export function markPlayerCell(x, y, result) {
    const playerBoard = $('playerBoard');
    if (!playerBoard) return;
    const cell = playerBoard.querySelector(`[data-x="${x}"][data-y="${y}"]`);
    if (!cell) return;
    cell.classList.add('inactive');
    if (result === 'HIT' || result === 'SUNK' || result === 'FINISHED') {
        cell.classList.remove('surveillance');
        cell.classList.add('hit');
    } else if (result === 'SURVEILLANCE') cell.classList.add('surveillance');
    else cell.classList.add('miss');
}

export function renderMoveResponses(moves, enemyBoard) {
    if (!Array.isArray(moves)) return {hasMiss: false, hasFinished: false, hasSunk: false};
    let hasMiss = false;
    let hasFinished = false;
    let hasSunk = false;
    for (const move of moves) {
        if (enemyBoard) {
            markEnemyCell(move.targetX, move.targetY, move.moveResult);
            if (move.moveResult !== 'SURVEILLANCE') {
                state.firedCells.add(`${move.targetX},${move.targetY}`);
            }
        } else {
            markPlayerCell(move.targetX, move.targetY, move.moveResult);
        }
        if (move.moveResult === 'MISS') hasMiss = true;
        if (move.moveResult === 'FINISHED') hasFinished = true;
        if (move.moveResult === 'SUNK') hasSunk = true;
    }
    return {hasMiss, hasFinished, hasSunk};
}

export function revealOpponentShips(ships) {
    const board = $('enemyBoard');
    if (!board) return;
    for (const ship of ships) {
        const size = SHIP_SIZES[ship.shipType] ?? 1;
        for (let i = 0; i < size; i++) {
            const x = ship.orientation === 'HORIZONTAL' ? ship.startX + i : ship.startX;
            const y = ship.orientation === 'VERTICAL' ? ship.startY + i : ship.startY;
            const cell = board.querySelector(`[data-x="${x}"][data-y="${y}"]`);
            if (cell && !cell.classList.contains('hit')) cell.classList.add('ship');
        }
    }
}

export function showMatchEndModal(isWinner) {
    setState({finalWinState: isWinner});

    const overlay = $('matchEndModal');
    const card = $('matchEndCard');
    const eyebrow = $('matchEndEyebrow');
    const title = $('matchEndTitle');
    const sub = $('matchEndSub');
    const statusText = $('rematchStatus');
    const rematchBtn = $('rematchBtn');
    const showResultsBtn = $('showResultsBtn');

    if (!overlay || !card) return;

    card.classList.remove('modal-card--win', 'modal-card--lose');
    card.classList.add(isWinner ? 'modal-card--win' : 'modal-card--lose');
    if (eyebrow) eyebrow.textContent = isWinner ? 'Victory' : 'Defeat';
    if (title) title.textContent = isWinner ? 'You win.' : 'You lose.';
    if (sub) sub.textContent = isWinner
        ? 'All enemy ships sunk. Nicely played.'
        : 'Your fleet is down. Try another round.';
    if (statusText) statusText.textContent = '';
    if (rematchBtn) rematchBtn.disabled = false;

    if (showResultsBtn) showResultsBtn.style.display = 'none';

    overlay.classList.add('is-open');
}

export function hideMatchEndModal() {
    const overlay = $('matchEndModal');
    if (overlay) overlay.classList.remove('is-open');
}

export function checkAllShipsPlaced() {
    const totalShips = shipTypes.reduce((sum, t) => sum + t.count, 0);
    const readyBtn = $('readyBtn');
    if (state.placedShips.size === totalShips) {
        if (readyBtn) readyBtn.disabled = false;
        showMessage('All ships placed! You can now mark ready.', 'info');
    }
}
