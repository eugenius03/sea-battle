import {setState, state} from './state.js';
import {safeJson, showMessage} from './utils.js';
import {
    applyTurn,
    lockEnemyBoard,
    renderMoveResponses,
    revealOpponentShips,
    showMatchEndModal,
    unlockEnemyBoard
} from './ui.js';
import {applyMatchStatus, renderGameState} from './renderer.js';
import {getMatchInfo} from './api.js';
import {setOpponentStatus} from './opponentStatus.js';

export function clearSubs() {
    state.subs.forEach(s => {
        try {
            s.unsubscribe();
        } catch {
        }
    });
    state.subs = [];
}

export async function connectWs() {
    if (state.stompClient?.connected) {
        return;
    }

    setState({currentInviteToken: globalThis.location.pathname.split('/').pop()});

    await getMatchInfo();

    const wsMatchId = state.currentInviteToken || '';

    const socket = new SockJS('/ws');
    state.stompClient = Stomp.over(socket);
    state.stompClient.debug = null;

    state.stompClient.connect(
        {'X-Invite-Token': wsMatchId, 'X-Match-Player-Id': state.myMatchPlayerId || ''},
        (frame) => onWsConnected(frame, wsMatchId),
        (err) => onWsError(err)
    );
}

function onWsConnected(frame, wsMatchId) {
    state.subs.push(state.stompClient.subscribe(`/topic/match/${wsMatchId}/presence`, onPresenceMessage));
    state.subs.push(state.stompClient.subscribe(`/topic/match/${wsMatchId}/status`, onStatusMessage));
    state.subs.push(state.stompClient.subscribe(`/user/queue/match/${wsMatchId}`, onMoveMessage));
}

function onWsError(err) {
    clearSubs();
    if (typeof showMessage !== 'undefined') {
        showMessage('WebSocket error (check token & match id)', 'error');
    }
    lockEnemyBoard();
}

function onPresenceMessage(message) {
    const p = safeJson(message.body);
    if (!p) return;

    const t = p.presenceEventType;
    const isOpponent = String(p.matchPlayerId) !== String(state.myMatchPlayerId);

    if (t === 'OPPONENT_CONNECTED') {
        showMessage('Opponent connected!', 'success');
        setOpponentStatus('placing');
    } else if (t === 'DISCONNECTED') {
        if (isOpponent) {
            showMessage('Opponent disconnected', 'error');
            setOpponentStatus('disconnected');
        }
    } else if (t === 'RECONNECTED') {
        if (isOpponent) {
            showMessage('Opponent reconnected', 'success');
            setOpponentStatus('connected');
        }
    } else if (t === 'OPPONENT_READY') {
        if (!isOpponent) {
            setOpponentStatus('ready');
            showMessage('Opponent is ready', 'info');
        }
    } else if (t === 'REMATCH_REQUESTED') {
        if (isOpponent) {
            const overlay = document.getElementById('matchEndModal');
            const isModalOpen = overlay && overlay.classList.contains('is-open');

            if (isModalOpen) {
                const status = document.getElementById('rematchStatus');
                if (status) status.textContent = 'Opponent wants a rematch — click Rematch to accept.';
                showMessage('Opponent wants a rematch', 'info');
            } else {
                const notif = document.getElementById('rematchNotification');
                if (notif) notif.classList.add('is-open');
            }
        }
    } else if (t === 'REMATCH_AGREED') {
        const status = document.getElementById('rematchStatus');
        if (status) status.textContent = 'Both agreed! Loading new match…';

        const notif = document.getElementById('rematchNotification');
        if (notif) notif.classList.remove('is-open');

        showMessage('Rematch agreed! Redirecting...', 'success');

        if (p.inviteToken) {
            setTimeout(() => {
                globalThis.location.href = `/game/${p.inviteToken}`;
            }, 600);
        }
    }
}

function onStatusMessage(message) {
    const s = safeJson(message.body);
    if (!s) return;

    applyMatchStatus(s.matchStatus);

    if (s.matchStatus === 'IN_PROGRESS') {
        showMessage('Game started!', 'info');
        if (s.currentTurnPlayerId) {
            if (String(s.currentTurnPlayerId) === String(state.myMatchPlayerId)) {
                unlockEnemyBoard();
            } else {
                lockEnemyBoard();
            }
        } else {
            lockEnemyBoard();
        }
    } else if (s.matchStatus === 'FINISHED') {
        const isWinner = s.currentTurnPlayerId && String(s.currentTurnPlayerId) === String(state.myMatchPlayerId);
        if (!isWinner && Array.isArray(s.winnerShips)) revealOpponentShips(s.winnerShips);
        showMatchEndModal(isWinner);
    }
}

function onMoveMessage(message) {
    const m = safeJson(message.body);
    if (!m) return;

    if (m.playerShips !== undefined || m.playerMoves !== undefined || m.opponentMoves !== undefined) {
        renderGameState(m);
        return;
    }

    if (!Array.isArray(m.moveResponses)) return;

    if (m.moveResponses.length > 0) {
        renderMoveResponses(m.moveResponses, false);
    }

    applyTurn(m.isItMyTurn);
}
