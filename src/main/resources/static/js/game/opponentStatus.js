const LABELS = {
    awaiting: 'Waiting for opponent',
    connected: 'Opponent connected',
    placing: 'Opponent placing ships',
    ready: 'Opponent ready',
    disconnected: 'Opponent disconnected',
};

const VALID = new Set(Object.keys(LABELS));

export function setOpponentStatus(nextState) {
    if (!VALID.has(nextState)) return;
    const el = document.getElementById('opponentStatus');
    if (!el) return;

    const prev = el.dataset.state;
    if (prev === nextState) return;

    el.dataset.state = nextState;
    const label = el.querySelector('.opponent-status__label');
    if (label) label.textContent = LABELS[nextState];
}

document.addEventListener('DOMContentLoaded', () => {
    const el = document.getElementById('opponentStatus');
    if (!el) return;
    if (!el.dataset.state) setOpponentStatus('awaiting');
});
