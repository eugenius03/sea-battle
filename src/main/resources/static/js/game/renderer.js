import {setState, shipSizeMap, state} from './state.js';
import {$, showMessage} from './utils.js';
import {setPlayerBoardDraggable, visualizeShipOnBoard} from './board.js';
import {applyTurn, checkAllShipsPlaced, lockEnemyBoard, lockPlayerBoard, markEnemyCell, markPlayerCell} from './ui.js';
import {setOpponentStatus} from './opponentStatus.js';

export function applyMatchStatus(status) {
    if (status === 'IN_PROGRESS' || status === 'FINISHED') {
        const shipsPanel = $('shipsPanel');
        if (shipsPanel) shipsPanel.style.display = 'none';
        const readyBtn = $('readyBtn');
        if (readyBtn) readyBtn.disabled = true;
        const rotateAllBtn = $('rotateAllBtn');
        if (rotateAllBtn) rotateAllBtn.disabled = true;
        const specialMovesPanel = $('specialMovesPanel');
        if (specialMovesPanel) specialMovesPanel.style.display = status === 'IN_PROGRESS' ? '' : 'none';
        setPlayerBoardDraggable(false);
        setOpponentStatus('ready');
        setState({isReady: true});
        if (status === 'FINISHED') lockEnemyBoard();
        else lockPlayerBoard();
    } else if (status === 'PLANNING') {
        setOpponentStatus('placing');
    }
}

export function renderPlayerShips(ships) {
    state.placedShips.clear();
    state.shipRegistry.clear();
    document.querySelectorAll('#playerBoard .cell').forEach(cell => {
        cell.className = 'cell';
        cell.draggable = false;
        delete cell.dataset.shipId;
    });
    document.querySelectorAll('.ship-visual').forEach(el => {
        el.classList.remove('placed');
        el.draggable = true;
    });

    const shipCountByType = {};

    ships.forEach(ship => {
        const shipType = ship.shipType;
        const size = shipSizeMap[shipType] ?? 1;
        visualizeShipOnBoard(ship.startX, ship.startY, size, ship.orientation, ship.id);
        state.shipRegistry.set(String(ship.id), {
            size,
            orientation: ship.orientation,
            startX: ship.startX,
            startY: ship.startY
        });
        shipCountByType[shipType] = (shipCountByType[shipType] ?? 0) + 1;
    });

    Object.entries(shipCountByType).forEach(([shipType, count]) => {
        for (let i = 0; i < count; i++) {
            const shipVisual = document.querySelector(`.ship-visual[data-type="${shipType}"][data-index="${i}"]`);
            if (shipVisual) {
                shipVisual.classList.add('placed');
                shipVisual.draggable = false;
                state.placedShips.add(`${shipType}_${i}`);
            }
        }
    });
    checkAllShipsPlaced();
}

export function renderGameState(gameInfo) {
    setState({myMatchPlayerId: gameInfo.matchPlayerId});

    state.firedCells.clear();
    document.querySelectorAll('#enemyBoard .cell').forEach(cell => {
        cell.className = 'cell';
    });

    renderPlayerShips(gameInfo.playerShips);

    if (Array.isArray(gameInfo.playerMoves)) {
        gameInfo.playerMoves.forEach(move => {
            markEnemyCell(move.targetX, move.targetY, move.moveResult);
            if (move.moveResult !== 'SURVEILLANCE')
                state.firedCells.add(`${move.targetX},${move.targetY}`);
        });
    }

    if (Array.isArray(gameInfo.opponentMoves)) {
        gameInfo.opponentMoves.forEach(move => {
            markPlayerCell(move.targetX, move.targetY, move.moveResult);
        });
    }

    if (gameInfo.matchStatus) {
        applyMatchStatus(gameInfo.matchStatus);
        if (gameInfo.matchStatus === 'IN_PROGRESS' || gameInfo.matchStatus === 'FINISHED') {
            showMessage(gameInfo.matchStatus === 'IN_PROGRESS' ? 'Game in progress!' : 'Game finished!', 'info');
        }
    }

    const isItMyTurn = gameInfo.isItMyTurn;
    if (isItMyTurn !== undefined) applyTurn(isItMyTurn);

    if (gameInfo.droneUsage) {
        state.attackDroneUsesLeft = gameInfo.droneUsage['ATTACK_DRONE'] ?? 0;
        state.surveillanceDroneUsesLeft = gameInfo.droneUsage['SURVEILLANCE_DRONE'] ?? 0;
        const attackBadge = $('attackDroneUsesLeft');
        if (attackBadge) attackBadge.textContent = String(state.attackDroneUsesLeft);
        const survBadge = $('surveillanceDroneUsesLeft');
        if (survBadge) survBadge.textContent = String(state.surveillanceDroneUsesLeft);
        const attackDrone = $('attackDrone');
        if (attackDrone) attackDrone.classList.toggle('exhausted', state.attackDroneUsesLeft <= 0);
        const survDrone = $('surveillanceDrone');
        if (survDrone) survDrone.classList.toggle('exhausted', state.surveillanceDroneUsesLeft <= 0);
    }
}
