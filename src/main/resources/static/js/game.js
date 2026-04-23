/* global Stomp, SockJS */

let matchId = null;
let myMatchPlayerId = null;

function $(id) {
    return document.getElementById(id);
}

function safeJson(s) {
    try {
        return JSON.parse(s);
    } catch {
        return null;
    }
}

function wsLog(line) {
    const el = $('wsLog');
    if (!el) return;
    const ts = new Date().toISOString().split('T')[1].replace('Z', '');
    el.textContent += `[${ts}] ${line}\n`;
    el.scrollTop = el.scrollHeight;
}

async function readMoveResult(response) {
    const ct = (response.headers.get('content-type') || '').toLowerCase();

    if (ct.includes('application/json')) {
        try {
            const v = await response.json();
            return String(v).trim().toUpperCase();
        } catch {
        }
    }

    const raw = (await response.text()) ?? '';
    const trimmed = String(raw).trim();

    if (trimmed.startsWith('"') && trimmed.endsWith('"')) {
        const parsed = safeJson(trimmed);
        if (parsed != null) return String(parsed).trim().toUpperCase();
    }

    return trimmed.toUpperCase();
}

async function readShipId(response) {
    try {
        const trimmed = (await response.text())?.trim();
        if (!trimmed) return null;
        try {
            const parsed = JSON.parse(trimmed);
            return String('shipId' in Object(parsed) ? parsed.shipId : parsed);
        } catch {
            return trimmed;
        }
    } catch {
        return null;
    }
}

function setWsState(state, ok) {
    const wsState = $('wsState');
    const wsDot = $('wsDot');
    if (!wsState || !wsDot) return;

    wsState.textContent = state;
    wsDot.classList.remove('ok', 'bad');
    if (ok === true) wsDot.classList.add('ok');
    if (ok === false) wsDot.classList.add('bad');
}

function lockEnemyBoard() {
    document.querySelectorAll('#enemyBoard .cell').forEach(c => c.classList.add('inactive'));
}

function unlockEnemyBoard() {
    document.querySelectorAll('#enemyBoard .cell').forEach(c => {
        if (!c.classList.contains('hit') && !c.classList.contains('miss')) {
            c.classList.remove('inactive');
        }
    });
}

function applyTurn(isItMyTurn) {
    if (isItMyTurn == null) return;
    if (isItMyTurn) {
        wsLog('TURN -> my turn');
        unlockEnemyBoard();
    } else {
        wsLog('TURN -> opponent turn');
        lockEnemyBoard();
    }
}

let currentOrientation = 'HORIZONTAL';
let isReady = false;
let placedShips = new Set();
let selectedShip = null;
let firedCells = new Set();
let shipRegistry = new Map();

const shipTypes = [
    {type: 'QUADRO_DECK', size: 4, count: 1, label: 'Battleship'},
    {type: 'TRIPLE_DECK', size: 3, count: 2, label: 'Cruiser'},
    {type: 'DOUBLE_DECK', size: 2, count: 3, label: 'Destroyer'},
    {type: 'SINGLE_DECK', size: 1, count: 4, label: 'Submarine'}
];

const shipSizeMap = Object.fromEntries(shipTypes.map(t => [t.type, t.size]));

function initBoards() {
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

function createCell(x, y, isPlayerBoard) {
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
    }
    return cell;
}

function markEnemyCell(x, y, result) {
    result = String(result);
    const enemyBoard = $('enemyBoard');
    if (!enemyBoard) return;
    const cell = enemyBoard.querySelector(`[data-x="${x}"][data-y="${y}"]`);
    if (!cell) return;
    if (result === 'MISS') cell.classList.add('miss');
    else cell.classList.add('hit');
    cell.classList.add('inactive');
}

function markPlayerCell(x, y, result) {
    const playerBoard = $('playerBoard');
    if (!playerBoard) return;
    const cell = playerBoard.querySelector(`[data-x="${x}"][data-y="${y}"]`);
    if (!cell) return;
    cell.classList.add('inactive');
    if (result === 'HIT' || result === 'SUNK' || result === 'FINISHED') cell.classList.add('hit');
    else cell.classList.add('miss');
}

const unplacedShipOrientations = new Map();

function initShips() {
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
                if (isReady) return;
                const shipKey = `${e.target.dataset.type}_${e.target.dataset.index}`;
                selectedShip = {
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
                selectedShip = null;
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

function rotateUnplacedShip(shipKey, shipVisual) {
    if (isReady) return;
    if (shipVisual.classList.contains('placed')) return;

    const current = unplacedShipOrientations.get(shipKey) || 'HORIZONTAL';
    const newOrientation = current === 'HORIZONTAL' ? 'VERTICAL' : 'HORIZONTAL';

    unplacedShipOrientations.set(shipKey, newOrientation);
    shipVisual.dataset.orientation = newOrientation;
    shipVisual.style.flexDirection = newOrientation === 'VERTICAL' ? 'column' : 'row';

    showMessage(`Ship orientation: ${newOrientation}`, 'info', 1500);
}

function handleDragOver(e) {
    if (!selectedShip || isReady) return;
    e.preventDefault();
    e.target.classList.add('drag-over');
}

function handleDrop(e) {
    e.preventDefault();
    e.target.classList.remove('drag-over');
    if (!selectedShip || isReady) return;

    const x = Number.parseInt(e.target.dataset.x);
    const y = Number.parseInt(e.target.dataset.y);
    if (selectedShip.fromBoard) {
        moveShip(selectedShip, x, y);
    } else {
        placeShip(selectedShip, x, y);
    }
}

function handleBoardShipDragStart(e) {
    if (isReady) return;
    const shipId = e.target.dataset.shipId;
    if (!shipId) {
        e.preventDefault();
        return;
    }

    const shipData = shipRegistry.get(String(shipId));
    if (!shipData) {
        e.preventDefault();
        return;
    }

    selectedShip = {
        fromBoard: true,
        id: String(shipId),
        size: shipData.size,
        orientation: shipData.orientation
    };

    currentOrientation = shipData.orientation;
    const orientationText = $('orientationText');
    if (orientationText) orientationText.textContent = currentOrientation;

    document.querySelectorAll('.ship-visual').forEach(ship => {
        ship.style.flexDirection = currentOrientation === 'VERTICAL' ? 'column' : 'row';
    });

    e.dataTransfer.effectAllowed = 'move';
    e.dataTransfer.setData('text/plain', String(shipId));
}

function handleBoardShipDragEnd() {
    selectedShip = null;
    document.querySelectorAll('.cell.drag-over').forEach(cell => cell.classList.remove('drag-over'));
}

async function handleBoardShipDoubleClick(e) {
    if (isReady) return;

    const shipId = e.target.dataset.shipId;
    if (!shipId) return;

    const shipData = shipRegistry.get(String(shipId));
    if (!shipData) return;

    e.preventDefault();

    await moveShip({
        fromBoard: true,
        id: String(shipId),
        size: shipData.size,
        orientation: shipData.orientation === 'HORIZONTAL' ? 'VERTICAL' : 'HORIZONTAL'
    }, shipData.startX, shipData.startY);
}

function visualizeShipOnBoard(startX, startY, size, orientation, shipId) {
    const playerBoard = $('playerBoard');
    if (!playerBoard) return;

    for (let i = 0; i < size; i++) {
        const x = orientation === 'HORIZONTAL' ? startX + i : startX;
        const y = orientation === 'VERTICAL' ? startY + i : startY;
        const cell = playerBoard.querySelector(`[data-x="${x}"][data-y="${y}"]`);
        if (cell) {
            cell.classList.add('ship');
            if (shipId != null) cell.dataset.shipId = String(shipId);
            if (!isReady) {
                cell.draggable = true;
                cell.classList.add('movable');
            }
        }
    }
}

function clearShipFromBoard(shipId) {
    document.querySelectorAll(`#playerBoard .cell[data-ship-id="${shipId}"]`).forEach(cell => {
        cell.classList.remove('ship', 'movable');
        cell.removeAttribute('data-ship-id');
        cell.draggable = false;
    });
}

function setPlayerBoardDraggable(enabled) {
    document.querySelectorAll('#playerBoard .cell.ship').forEach(cell => {
        cell.draggable = enabled;
        cell.classList.toggle('movable', enabled);
    });
}

function checkAllShipsPlaced() {
    const totalShips = shipTypes.reduce((sum, t) => sum + t.count, 0);
    const readyBtn = $('readyBtn');
    if (placedShips.size === totalShips) {
        if (readyBtn) readyBtn.disabled = false;
        showMessage('All ships placed! You can now mark ready.', 'info');
    }
}

async function placeShip(ship, x, y) {
    const orientation = ship.orientation || currentOrientation;

    try {
        const response = await fetch(`/api/game/${matchId}/place-ship?` + new URLSearchParams({
            playerId: myMatchPlayerId,
            type: ship.type,
            startX: x,
            startY: y,
            orientation
        }), {method: 'POST'});

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
        placedShips.add(`${ship.type}_${ship.index}`);
        shipRegistry.set(String(shipId), {size: ship.size, orientation, startX: x, startY: y});
        visualizeShipOnBoard(x, y, ship.size, orientation, shipId);
        showMessage('Ship placed successfully!', 'success');
        checkAllShipsPlaced();
    } catch (e) {
        showMessage(`Error placing ship: ${e.message}`, 'error');
    }
}

async function moveShip(ship, x, y) {
    const shipId = String(ship.id);
    const orientation = ship.orientation || currentOrientation;
    try {
        const response = await fetch(`/api/game/${matchId}/move-ship/${shipId}?` + new URLSearchParams({
            playerId: myMatchPlayerId,
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
        shipRegistry.set(shipId, {size: ship.size, orientation, startX: x, startY: y});
        showMessage('Ship moved!', 'success');
    } catch (e) {
        showMessage(`Error moving ship: ${e.message}`, 'error');
    }
}

async function handleEnemyCellClick(e) {
    if (e.target.classList.contains('inactive')) return;

    if (!isReady) {
        showMessage('You must be ready before firing!', 'error');
        return;
    }

    const x = Number.parseInt(e.target.dataset.x);
    const y = Number.parseInt(e.target.dataset.y);
    const cellKey = `${x},${y}`;

    if (firedCells.has(cellKey)) {
        showMessage('Already fired at this position!', 'error');
        return;
    }

    try {
        const response = await fetch(`/api/game/${matchId}/fire?` + new URLSearchParams({
            shooterId: myMatchPlayerId,
            x,
            y
        }), {method: 'POST'});

        if (!response.ok) {
            showMessage(`Error: ${await response.text()}`, 'error');
            return;
        }

        const result = await readMoveResult(response);

        firedCells.add(cellKey);
        markEnemyCell(x, y, result);
        showMessage(
            result.includes('MISS') ? 'Miss!' : result === 'SUNK' ? 'Sunk!' : result === 'FINISHED' ? 'Game Over!' : 'Hit!',
            result === 'MISS' ? 'info' : 'success'
        );
        if (result.includes('MISS')) lockEnemyBoard();
    } catch (e2) {
        showMessage(`Error firing: ${e2.message}`, 'error');
    }
}

function rotateOrientation() {
    if (isReady) return;
    currentOrientation = currentOrientation === 'HORIZONTAL' ? 'VERTICAL' : 'HORIZONTAL';

    document.querySelectorAll('.ship-visual').forEach(ship => {
        if (ship.classList.contains('placed')) return;
        const shipKey = `${ship.dataset.type}_${ship.dataset.index}`;
        ship.dataset.orientation = currentOrientation;
        ship.style.flexDirection = currentOrientation === 'VERTICAL' ? 'column' : 'row';
        unplacedShipOrientations.set(shipKey, currentOrientation);
    });

    showMessage(`All ships rotated: ${currentOrientation}`, 'info', 1500);
}

async function markReady() {
    try {
        const response = await fetch(`/api/game/${matchId}/mark-ready?` + new URLSearchParams({playerId: myMatchPlayerId}), {method: 'POST'});

        if (!response.ok) {
            showMessage(`Error: ${await response.text()}`, 'error');
            return;
        }

        isReady = true;
        const readyBtn = $('readyBtn');
        const rotateAllBtn = $('rotateAllBtn');
        const shipsPanel = $('shipsPanel');

        if (readyBtn) readyBtn.disabled = true;
        if (rotateAllBtn) rotateAllBtn.disabled = true;
        if (shipsPanel) shipsPanel.style.display = 'none';

        document.querySelectorAll('#playerBoard .cell').forEach(cell => cell.classList.add('inactive'));
        setPlayerBoardDraggable(false);
        lockPlayerBoard();
        showMessage('Ready! Waiting for game start...', 'success');
    } catch (e) {
        showMessage(`Error marking ready: ${e.message}`, 'error');
    }
}

function lockPlayerBoard() {
    document.querySelectorAll('#playerBoard .cell').forEach(cell => cell.classList.add('inactive'));
}

function unlockPlayerBoard() {
    document.querySelectorAll('#playerBoard .cell').forEach(cell => cell.classList.add('active'));
}

let stompClient = null;
let subs = [];

function clearSubs() {
    subs.forEach(s => {
        try {
            s.unsubscribe();
        } catch {
        }
    });
    subs = [];
    const wsSubs = $('wsSubs');
    if (wsSubs) wsSubs.textContent = '0';
}

async function connectWs() {
    if (stompClient?.connected) {
        wsLog('WS already connected');
        return;
    }

    const wsMatchIdInput = $('wsMatchId');
    const wsMatchPlayerIdInput = $('wsMatchPlayerId');
    const wsMatchIdEcho = $('wsMatchIdEcho');
    const wsMeEcho = $('wsMeEcho');
    const wsPrivateEcho = $('wsPrivateEcho');
    const inviteToken = globalThis.location.pathname.split('/').pop();

    await getMatchInfo(inviteToken);

    const wsMatchId = (wsMatchIdInput?.value || String(matchId)).trim();
    const wsPlayerId = wsMatchPlayerIdInput?.value.trim() || myMatchPlayerId;

    if (wsMatchIdEcho) wsMatchIdEcho.textContent = wsMatchId;
    if (wsMeEcho) wsMeEcho.textContent = String(myMatchPlayerId);
    if (wsPrivateEcho) wsPrivateEcho.textContent = `/user/queue/match/${wsMatchId}`;

    if (!wsPlayerId) {
        wsLog('ERROR: Missing Match Player Id(X-Match-Player-Id).');
        showMessage('Missing match player id for WS auth', 'error');
        return;
    }

    setWsState('CONNECTING...', null);
    const wsConnectBtn = $('wsConnectBtn');
    const wsDisconnectBtn = $('wsDisconnectBtn');
    if (wsConnectBtn) wsConnectBtn.disabled = true;
    if (wsDisconnectBtn) wsDisconnectBtn.disabled = true;

    wsLog(`CONNECT -> /ws headers: X-Match-Id=${wsMatchId}, X-Match-Player-Id=${myMatchPlayerId}`);

    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);
    stompClient.debug = null;

    stompClient.connect(
        {'X-Match-Id': wsMatchId, 'X-Match-Player-Id': wsPlayerId},
        (frame) => onWsConnected(frame, wsMatchId),
        (err) => onWsError(err)
    );
}

function onWsConnected(frame, wsMatchId) {
    setWsState('CONNECTED', true);
    wsLog('CONNECTED: ' + (frame?.command || 'OK'));

    const wsConnectBtn = $('wsConnectBtn');
    const wsDisconnectBtn = $('wsDisconnectBtn');
    if (wsConnectBtn) wsConnectBtn.disabled = true;
    if (wsDisconnectBtn) wsDisconnectBtn.disabled = false;

    subs.push(stompClient.subscribe(`/topic/match/${wsMatchId}/presence`, onPresenceMessage));
    subs.push(stompClient.subscribe(`/topic/match/${wsMatchId}/status`, onStatusMessage));
    subs.push(stompClient.subscribe(`/user/queue/match/${wsMatchId}`, onMoveMessage));

    const wsSubs = $('wsSubs');
    if (wsSubs) wsSubs.textContent = String(subs.length);

    wsLog('SUBSCRIBED: Now fetching game state...');
}

async function getMatchInfo(token) {
    const inviteToken = token || globalThis.location.pathname.split('/').pop();
    try {
        wsLog('SENT: GET /api/game/' + inviteToken + '/info');
        const response = await fetch(`/api/game/${inviteToken}/info`, {method: 'GET'});

        if (!response.ok) {
            wsLog('ERROR: Failed to fetch game info: ' + response.status);
            return;
        }

        const gameInfo = await response.json();
        if (gameInfo) {
            renderGameState(gameInfo);
        } else {
            wsLog('WARNING: Game info is null');
        }
    } catch (e) {
        wsLog('ERROR fetching game info: ' + e.message);
    }
}

function renderPlayerShips(ships) {
    placedShips.clear();
    shipRegistry.clear();
    document.querySelectorAll('#playerBoard .cell').forEach(cell => {
        cell.className = 'cell';
        cell.draggable = false;
        delete cell.dataset.shipId;
    });
    document.querySelectorAll('.ship-visual').forEach(el => {
        el.classList.remove('placed');
        el.draggable = true;
    });

    if (!Array.isArray(ships)) return;

    wsLog(`Rendering ${ships.length} player ships...`);
    const shipCountByType = {};

    ships.forEach(ship => {
        const shipType = ship.shipType || ship.type;
        const size = shipSizeMap[shipType] ?? 1;
        visualizeShipOnBoard(ship.startX, ship.startY, size, ship.orientation, ship.id);
        shipRegistry.set(String(ship.id), {
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
                placedShips.add(`${shipType}_${i}`);
            }
        }
    });
    checkAllShipsPlaced();
}

function renderGameState(gameInfo) {
    wsLog('RENDER: Processing game state...');
    matchId = gameInfo.matchId;
    myMatchPlayerId = gameInfo.matchPlayerId;

    const wsMatchIdInput = $('wsMatchId');
    const wsMatchPlayerIdInput = $('wsMatchPlayerId');
    const wsMeEcho = $('wsMeEcho');
    const wsMatchIdEcho = $('wsMatchIdEcho');
    const wsPrivateEcho = $('wsPrivateEcho');

    if (wsMatchIdInput) wsMatchIdInput.value = String(matchId);
    if (wsMatchPlayerIdInput) wsMatchPlayerIdInput.value = String(myMatchPlayerId);
    if (wsMeEcho) wsMeEcho.textContent = String(myMatchPlayerId);
    if (wsMatchIdEcho) wsMatchIdEcho.textContent = String(matchId);
    if (wsPrivateEcho) wsPrivateEcho.textContent = `/user/queue/match/${matchId}`;

    firedCells.clear();
    document.querySelectorAll('#enemyBoard .cell').forEach(cell => {
        cell.className = 'cell';
    });

    renderPlayerShips(gameInfo.playerShips);

    if (Array.isArray(gameInfo.playerMoves)) {
        wsLog(`Rendering ${gameInfo.playerMoves.length} player moves...`);
        gameInfo.playerMoves.forEach(move => {
            const x = move.targetX ?? move.x;
            const y = move.targetY ?? move.y;
            markEnemyCell(x, y, move.moveResult || move.result);
            firedCells.add(`${x},${y}`);
        });
    }

    if (Array.isArray(gameInfo.opponentMoves)) {
        wsLog(`Rendering ${gameInfo.opponentMoves.length} opponent moves...`);
        gameInfo.opponentMoves.forEach(move => {
            const x = move.targetX ?? move.x;
            const y = move.targetY ?? move.y;
            markPlayerCell(x, y, move.moveResult || move.result);
        });
    }

    const matchStatus = gameInfo.matchStatus;
    if (matchStatus) {
        wsLog(`Match status: ${matchStatus}`);
        if (matchStatus === 'IN_PROGRESS' || matchStatus === 'FINISHED') {
            showMessage(matchStatus === 'IN_PROGRESS' ? 'Game in progress!' : 'Game finished!', 'info');
            if (matchStatus === 'FINISHED') lockEnemyBoard();
            else lockPlayerBoard();
            setPlayerBoardDraggable(false);
            const shipsPanel = $('shipsPanel');
            if (shipsPanel) shipsPanel.style.display = 'none';
            isReady = true;
        }
    }

    const isItMyTurn = gameInfo.isItMyTurn ?? gameInfo.myTurn;
    if (isItMyTurn !== undefined) applyTurn(isItMyTurn);

    wsLog('Game state rendered successfully!');
}

function onWsError(err) {
    setWsState('ERROR', false);
    const wsConnectBtn = $('wsConnectBtn');
    const wsDisconnectBtn = $('wsDisconnectBtn');
    if (wsConnectBtn) wsConnectBtn.disabled = false;
    if (wsDisconnectBtn) wsDisconnectBtn.disabled = true;
    clearSubs();

    wsLog('WS ERROR: ' + (err?.body || err?.toString?.() || JSON.stringify(err)));
    showMessage('WebSocket error (check token & match id)', 'error');
    lockEnemyBoard();
}

function disconnectWs() {
    if (!stompClient) return;
    clearSubs();

    try {
        stompClient.disconnect(() => {
            wsLog('DISCONNECTED');
            setWsState('DISCONNECTED', false);
            const wsConnectBtn = $('wsConnectBtn');
            const wsDisconnectBtn = $('wsDisconnectBtn');
            if (wsConnectBtn) wsConnectBtn.disabled = false;
            if (wsDisconnectBtn) wsDisconnectBtn.disabled = true;
            lockEnemyBoard();
        });
    } catch (e) {
        wsLog('Disconnect error: ' + e);
    }
}

function onPresenceMessage(message) {
    wsLog('presence <- ' + message.body);
    const p = safeJson(message.body);
    if (!p) return;

    const t = p.presenseEventType;
    if (t === 'OPPONENT_CONNECTED') showMessage('Opponent connected!', 'success');
    else if (t === 'DISCONNECTED') showMessage('Opponent disconnected', 'error');
    else if (t === 'RECONNECTED') showMessage('Opponent reconnected', 'success');
    else showMessage('Presence update received', 'info');
}

function onStatusMessage(message) {
    wsLog('status <- ' + message.body);
    const s = safeJson(message.body);
    if (!s) return;

    if (s.matchStatus === 'IN_PROGRESS') {
        showMessage('Game started!', 'info');
        if (s.currentTurnPlayerId) {
            wsLog(`Status update: currentTurnPlayerId=${s.currentTurnPlayerId}, me=${myMatchPlayerId}`);
            if (String(s.currentTurnPlayerId) === String(myMatchPlayerId)) {
                wsLog('My turn - unlocking enemy board');
                unlockEnemyBoard();
            } else {
                wsLog('Opponent turn - locking enemy board');
                lockEnemyBoard();
            }
        } else {
            wsLog('No currentTurnPlayerId in status message');
            lockEnemyBoard();
        }
    } else if (s.matchStatus === 'FINISHED') {
        showMessage('Game finished!', 'info');
        lockEnemyBoard();
    }
}

function onMoveMessage(message) {
    wsLog('move(private) <- ' + message.body);
    const m = safeJson(message.body);
    if (!m) return;

    if (m.playerShips !== undefined || m.playerMoves !== undefined || m.opponentMoves !== undefined) {
        renderGameState(m);
        wsLog('Reconnect data processed, isItMyTurn=' + (m.isItMyTurn ?? 'undefined'));
        return;
    }

    markPlayerCell(m.x, m.y, m.result);
    showMessage(m.result === 'MISS' ? 'Opponent missed!' : 'Opponent hit!', m.result === 'MISS' ? 'info' : 'error');
    applyTurn(m.isItMyTurn);
}

async function generateRandomShips() {
    if (isReady) {
        showMessage('Cannot generate ships after marking ready', 'error');
        return;
    }

    try {
        const response = await fetch(`/api/game/${matchId}/generate-random-ships?` + new URLSearchParams({
            playerId: myMatchPlayerId
        }), {method: 'POST'});

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

document.addEventListener('DOMContentLoaded', function () {
    const readyBtn = $('readyBtn');
    if (readyBtn) readyBtn.addEventListener('click', markReady);

    const rotateAllBtn = $('rotateAllBtn');
    if (rotateAllBtn) rotateAllBtn.addEventListener('click', rotateOrientation);

    const randomBtn = $('randomBtn');
    if (randomBtn) randomBtn.addEventListener('click', generateRandomShips);

    const wsMatchIdInput = $('wsMatchId');
    const wsMatchPlayerIdInput = $('wsMatchPlayerId');
    if (wsMatchIdInput) wsMatchIdInput.value = '';
    if (wsMatchPlayerIdInput) wsMatchPlayerIdInput.value = '';

    const wsConnectBtn = $('wsConnectBtn');
    const wsDisconnectBtn = $('wsDisconnectBtn');
    const wsClearLogBtn = $('wsClearLogBtn');

    if (wsConnectBtn) wsConnectBtn.addEventListener('click', connectWs);
    if (wsDisconnectBtn) wsDisconnectBtn.addEventListener('click', disconnectWs);
    if (wsClearLogBtn) {
        wsClearLogBtn.addEventListener('click', () => {
            const wsLog = $('wsLog');
            if (wsLog) wsLog.textContent = '';
        });
    }

    const copyInviteBtn = $('copyInviteBtn');
    if (copyInviteBtn) {
        copyInviteBtn.addEventListener('click', async () => {
            const inviteToken = globalThis.location.pathname.split('/').pop();
            const link = globalThis.location.origin + "/game/join/" + inviteToken;
            const success = await copyToClipboard(link);
            showMessage(
                success ? 'Invite link copied to clipboard!' : 'Could not copy invite link. Please copy from the address bar.',
                success ? 'success' : 'error'
            );
        });
    }

    initBoards();
    initShips();
    lockEnemyBoard();

    wsLog('Ready. Auto-connecting WebSocket...');
    connectWs().catch(err => wsLog('Auto-connect error: ' + err.message));
});
