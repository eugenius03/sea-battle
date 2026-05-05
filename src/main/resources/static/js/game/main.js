import {state} from './state.js';
import {$, showMessage} from './utils.js';
import {initBoards} from './board.js';
import {initShips, rotateOrientation} from './dragDrop.js';
import {generateRandomShips, markReady, requestRematch} from './api.js';
import {connectWs} from './websocket.js';
import {hideMatchEndModal, lockEnemyBoard, showMatchEndModal} from './ui.js';


document.addEventListener('DOMContentLoaded', function () {
    const readyBtn = $('readyBtn');
    if (readyBtn) readyBtn.addEventListener('click', markReady);

    const rotateAllBtn = $('rotateAllBtn');
    if (rotateAllBtn) rotateAllBtn.addEventListener('click', rotateOrientation);

    const randomBtn = $('randomBtn');
    if (randomBtn) randomBtn.addEventListener('click', generateRandomShips);

    const copyInviteBtn = $('copyInviteBtn');
    if (copyInviteBtn) {
        copyInviteBtn.addEventListener('click', async () => {
            const inviteToken = globalThis.location.pathname.split('/').pop();
            const link = globalThis.location.origin + "/game/join/" + inviteToken;
            if (typeof copyToClipboard !== 'undefined') {
                const success = await copyToClipboard(link);
                showMessage(
                    success ? 'Invite link copied to clipboard!' : 'Could not copy invite link. Please copy from the address bar.',
                    success ? 'success' : 'error'
                );
            }
        });
    }

    const surveillanceDrone = $('surveillanceDrone');
    if (surveillanceDrone) {
        surveillanceDrone.addEventListener('click', () => {
            if (state.surveillanceDroneUsesLeft <= 0) return;

            if (state.draggedDroneType === 'SURVEILLANCE_DRONE') {
                state.draggedDroneType = null;
                surveillanceDrone.classList.remove('selected');
            } else {
                state.draggedDroneType = 'SURVEILLANCE_DRONE';
                surveillanceDrone.classList.add('selected');
                if (attackDrone) attackDrone.classList.remove('selected'); // Deselect the other
            }
        });
    }

    const attackDrone = $('attackDrone');
    if (attackDrone) {
        attackDrone.addEventListener('click', () => {
            if (state.attackDroneUsesLeft <= 0) return;

            if (state.draggedDroneType === 'ATTACK_DRONE') {
                state.draggedDroneType = null;
                attackDrone.classList.remove('selected');
            } else {
                state.draggedDroneType = 'ATTACK_DRONE';
                attackDrone.classList.add('selected');
                if (surveillanceDrone) surveillanceDrone.classList.remove('selected');
            }
        });
    }

    const rematchBtn = document.getElementById('rematchBtn');
    if (rematchBtn) rematchBtn.addEventListener('click', requestRematch);
    const returnHomeBtn = document.getElementById('returnHomeBtn');
    if (returnHomeBtn) returnHomeBtn.addEventListener('click', () => {
        globalThis.location.href = '/game';
    });

    const rematchYesBtn = document.getElementById('rematchYesBtn');
    const rematchNoBtn = document.getElementById('rematchNoBtn');
    const rematchNotification = document.getElementById('rematchNotification');

    if (rematchYesBtn) {
        rematchYesBtn.addEventListener('click', () => {
            if (rematchNotification) rematchNotification.classList.remove('is-open');
            requestRematch();
        });
    }

    if (rematchNoBtn) {
        rematchNoBtn.addEventListener('click', () => {
            if (rematchNotification) rematchNotification.classList.remove('is-open');
        });
    }

    const viewBoardBtn = document.getElementById('viewBoardBtn');
    const showResultsBtn = document.getElementById('showResultsBtn');

    if (viewBoardBtn) {
        viewBoardBtn.addEventListener('click', () => {
            hideMatchEndModal();
            if (showResultsBtn) showResultsBtn.style.display = 'inline-flex';
        });
    }

    if (showResultsBtn) {
        showResultsBtn.addEventListener('click', () => {
            showMatchEndModal(state.finalWinState);
        });
    }

    initBoards();
    initShips();
    lockEnemyBoard();

    connectWs().catch(err => {
        // silently fail or rely on showMessage inside connectWs if implemented
    });
});
