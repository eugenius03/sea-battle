import {state} from './state.js';
import {$, showMessage} from './utils.js';
import {initBoards} from './board.js';
import {initShips, rotateOrientation} from './dragDrop.js';
import {generateRandomShips, markReady, requestRematch} from './api.js';
import {connectWs} from './websocket.js';
import {clearDronePreview, hideMatchEndModal, lockEnemyBoard, showMatchEndModal} from './ui.js';


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
        surveillanceDrone.addEventListener('dragstart', (e) => {
            if (state.surveillanceDroneUsesLeft <= 0) {
                e.preventDefault();
                return;
            }
            state.draggedDroneType = 'SURVEILLANCE_DRONE';
            e.dataTransfer.effectAllowed = 'move';
        });
        surveillanceDrone.addEventListener('dragend', () => {
            state.draggedDroneType = null;
            clearDronePreview();
        });
    }

    const attackDrone = $('attackDrone');
    if (attackDrone) {
        attackDrone.addEventListener('dragstart', (e) => {
            if (state.attackDroneUsesLeft <= 0) {
                e.preventDefault();
                return;
            }
            state.draggedDroneType = 'ATTACK_DRONE';
            e.dataTransfer.effectAllowed = 'move';
        });
        attackDrone.addEventListener('dragend', () => {
            state.draggedDroneType = null;
            clearDronePreview();
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
