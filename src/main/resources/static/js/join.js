document.addEventListener('DOMContentLoaded', async function () {
    const inviteToken = globalThis.location.pathname.split('/').pop();
    const statusEl = document.getElementById('joinStatus');

    try {
        const response = await fetch(`/api/match/join?inviteToken=${encodeURIComponent(inviteToken)}`, {
            method: 'POST',
            credentials: 'same-origin'
        });

        if (response.ok) {
            globalThis.location.href = `/game/${inviteToken}`;
        } else {
            const text = await response.text();
            if (statusEl) statusEl.textContent = `Failed to join: ${text}`;
        }
    } catch (err) {
        if (statusEl) statusEl.textContent = `Error: ${err.message}`;
    }
});