import {setState, state} from './state.js';
import {$, readShipId, showMessage} from './utils.js';
import {checkAllShipsPlaced, lockEnemyBoard, lockPlayerBoard, renderMoveResponses} from './ui.js';
import {clearShipFromBoard, setPlayerBoardDraggable, visualizeShipOnBoard} from './board.js';
import {renderGameState, renderPlayerShips} from './renderer.js';

export async function fireSurveillanceDrone(x, y) {
    try {
        const response = await fetch(`/api/game/${state.currentInviteToken}/fire`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({x, y, moveType: 'SURVEILLANCE_DRONE'})
        });

        if (!response.ok) {
            showMessage(`Drone error: ${await response.text()}`, 'error');
            return;
        }

        const moves = await response.json();

        state.surveillanceDroneUsesLeft--;
        const badge = $('surveillanceDroneUsesLeft');
        if (badge) badge.textContent = String(state.surveillanceDroneUsesLeft);
        if (state.surveillanceDroneUsesLeft <= 0) {
            const drone = $('surveillanceDrone');
            if (drone) drone.classList.add('exhausted');
        }

        renderMoveResponses(moves, true);
        lockEnemyBoard();

        if (moves.length === 0) {
            showMessage('Drone scan complete — no ships in area.', 'info');
        } else {
            showMessage(`Drone detected ${moves.length} ship cell(s)!`, 'success');
        }
    } catch (err) {
        showMessage(`Drone error: ${err.message}`, 'error');
    }
}

export async function fireAttackDrone(x, y) {
    try {
        const response = await fetch(`/api/game/${state.currentInviteToken}/fire`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({x, y, moveType: 'ATTACK_DRONE'})
        });

        if (!response.ok) {
            showMessage(`Attack Drone error: ${await response.text()}`, 'error');
            return;
        }

        const moves = await response.json();

        state.attackDroneUsesLeft--;
        const badge = $('attackDroneUsesLeft');
        if (badge) badge.textContent = String(state.attackDroneUsesLeft);
        if (state.attackDroneUsesLeft <= 0) {
            const drone = $('attackDrone');
            if (drone) drone.classList.add('exhausted');
        }

        const {hasFinished} = renderMoveResponses(moves, true);
        const hitCount = moves.filter(m => m.moveResult === 'HIT' || m.moveResult === 'SUNK' || m.moveResult === 'FINISHED').length;

        if (hasFinished) {
            showMessage('Attack Drone sunk the last ship! You win!', 'success');
            lockEnemyBoard();
        } else if (hitCount > 0) {
            showMessage(`Attack Drone hit ${hitCount} cell(s)!`, 'success');
        } else {
            showMessage('Attack Drone missed all shots!', 'info');
        }
        lockEnemyBoard();
    } catch (err) {
        showMessage(`Attack Drone error: ${err.message}`, 'error');
    }
}

export async function placeShip(ship, x, y) {
    const orientation = ship.orientation || state.currentOrientation;
    try {
        const response = await fetch(`/api/game/${state.currentInviteToken}/place-ship`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({
                shipType: ship.type,
                startX: x,
                startY: y,
                orientation
            })
        });

        if (!response.ok) {
            showMessage(`Error: ${await response.text()}`, 'error');
            return;
        }

        const shipId = await readShipId(response);
        if (!shipId) {
            showMessage('Ship placed but server did not return a ship id.', 'error');
            return;
        }

        ship.element.classList.add('placed');
        ship.element.draggable = false;
        const shipControls = ship.element.parentElement.querySelector('.ship-rotate-btn');
        if (shipControls) shipControls.disabled = true;
        state.placedShips.add(`${ship.type}_${ship.index}`);
        state.shipRegistry.set(String(shipId), {size: ship.size, orientation, startX: x, startY: y});
        visualizeShipOnBoard(x, y, ship.size, orientation, shipId);
        showMessage('Ship placed successfully!', 'success');

        checkAllShipsPlaced();
    } catch (e) {
        showMessage(`Error placing ship: ${e.message}`, 'error');
    }
}

export async function moveShip(ship, x, y) {
    const shipId = String(ship.id);
    const orientation = ship.orientation || state.currentOrientation;
    try {
        const response = await fetch(`/api/game/${state.currentInviteToken}/move-ship/${shipId}?` + new URLSearchParams({
            startX: x,
            startY: y,
            orientation
        }), {method: 'POST'});

        if (!response.ok) {
            showMessage(`Error: ${await response.text()}`, 'error');
            return;
        }

        clearShipFromBoard(shipId);
        visualizeShipOnBoard(x, y, ship.size, orientation, shipId);
        state.shipRegistry.set(shipId, {size: ship.size, orientation, startX: x, startY: y});
        showMessage('Ship moved!', 'success');
    } catch (e) {
        showMessage(`Error moving ship: ${e.message}`, 'error');
    }
}

export async function handleEnemyCellClick(e) {
    if (e.target.classList.contains('inactive')) return;

    if (!state.isReady) {
        showMessage('You must be ready before firing!', 'error');
        return;
    }

    const x = Number.parseInt(e.target.dataset.x);
    const y = Number.parseInt(e.target.dataset.y);
    const cellKey = `${x},${y}`;

    if (state.firedCells.has(cellKey)) {
        showMessage('Already fired at this position!', 'error');
        return;
    }

    try {
        const response = await fetch(`/api/game/${state.currentInviteToken}/fire`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({x, y, moveType: 'STANDARD'})
        });

        if (!response.ok) {
            showMessage(`Error: ${await response.text()}`, 'error');
            return;
        }

        const moves = await response.json();
        const {hasMiss, hasFinished, hasSunk} = renderMoveResponses(moves, true);

        if (hasFinished) {
            showMessage('Game Over! You win!', 'success');
            lockEnemyBoard();
        } else if (hasMiss && !hasSunk) {
            showMessage('Miss!', 'info');
            lockEnemyBoard();
        } else {
            showMessage(hasSunk ? 'Sunk!' : 'Hit!', 'success');
        }
    } catch (e2) {
        showMessage(`Error firing: ${e2.message}`, 'error');
    }
}

export async function markReady() {
    try {
        const response = await fetch(`/api/game/${state.currentInviteToken}/mark-ready`, {method: 'POST'});

        if (!response.ok) {
            showMessage(`Error: ${await response.text()}`, 'error');
            return;
        }

        setState({isReady: true});
        const readyBtn = $('readyBtn');
        const rotateAllBtn = $('rotateAllBtn');
        const shipsPanel = $('shipsPanel');

        if (readyBtn) readyBtn.disabled = true;
        if (rotateAllBtn) rotateAllBtn.disabled = true;
        if (shipsPanel) shipsPanel.style.display = 'none';

        lockPlayerBoard();

        setPlayerBoardDraggable(false);

        showMessage('Ready! Waiting for game start...', 'success');
    } catch (e) {
        showMessage(`Error marking ready: ${e.message}`, 'error');
    }
}

export async function requestRematch() {
    const statusText = $('rematchStatus');
    const rematchBtn = $('rematchBtn');

    if (!state.currentInviteToken) {
        showMessage('Cannot request rematch: missing match info', 'error');
        return;
    }

    try {
        if (rematchBtn) rematchBtn.disabled = true;
        if (statusText) statusText.textContent = 'Waiting for opponent…';

        const response = await fetch(
            `/api/match/${state.currentInviteToken}/rematch`,
            {method: 'POST', credentials: 'same-origin'}
        );

        if (!response.ok) {
            showMessage(`Rematch failed: ${await response.text()}`, 'error');
            if (rematchBtn) rematchBtn.disabled = false;
            if (statusText) statusText.textContent = '';
        }
    } catch (e) {
        showMessage(`Rematch error: ${e.message}`, 'error');
        if (rematchBtn) rematchBtn.disabled = false;
        if (statusText) statusText.textContent = '';
    }
}

export async function generateRandomShips() {
    if (state.isReady) {
        showMessage('Cannot generate ships after marking ready', 'error');
        return;
    }

    try {
        const response = await fetch(`/api/game/${state.currentInviteToken}/generate-random-ships`, {method: 'POST'});

        if (!response.ok) {
            showMessage(`Error: ${await response.text()}`, 'error');
            return;
        }

        renderPlayerShips(await response.json());
        showMessage('Random ships generated!', 'success');
    } catch (e) {
        showMessage(`Error generating random ships: ${e.message}`, 'error');
    }
}

export async function getMatchInfo() {
    try {
        const response = await fetch(`/api/game/${state.currentInviteToken}/info`, {method: 'GET'});

        if (!response.ok) {
            return;
        }

        const gameInfo = await response.json();
        if (gameInfo) {
            renderGameState(gameInfo);
        }
    } catch (e) {
        console.error('getMatchInfo failed:', e);
    }
}
